package app.usbnet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Captures all IPv4 traffic of the phone through a VPN interface and forwards the raw IP packets
 * over a local socket. "adb reverse" (set up by the PC program) carries that socket through the
 * USB cable to the relay on the computer, which performs the real network access.
 *
 * Wire protocol (same as the open source "gnirehtet" relay, Apache License 2.0):
 *  - connect to the abstract local socket "gnirehtet"
 *  - the relay first sends a 4 byte client id
 *  - after that, raw IPv4 packets flow in both directions
 */
class TunnelService : VpnService() {

    companion object {
        const val ACTION_START = "app.usbnet.START"
        const val ACTION_STOP = "app.usbnet.STOP"
        const val EXTRA_DNS = "dns"
        const val EXTRA_UPDATE_ONLY = "update_only"

        private const val TAG = "TunnelService"
        private const val CHANNEL_ID = Notif.CH_STATUS
        private const val NOTIFICATION_ID = 1
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val MTU = 0x4000
        private const val SOCKET_NAME = "gnirehtet"

        fun start(context: Context, dns: String, updateOnly: Boolean = false) {
            val intent = Intent(context, TunnelService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DNS, dns)
                .putExtra(EXTRA_UPDATE_ONLY, updateOnly)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TunnelService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                context.stopService(Intent(context, TunnelService::class.java))
            }
        }
    }

    private var vpn: ParcelFileDescriptor? = null
    private var session: Session? = null
    private var link: ControlLink? = null
    private var updateOnly = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTunnel(
                intent.getStringExtra(EXTRA_DNS) ?: "8.8.8.8",
                intent.getBooleanExtra(EXTRA_UPDATE_ONLY, false)
            )
            ACTION_STOP -> {
                if (TunnelMonitor.stopReason.value == StopReason.None) TunnelMonitor.stopReason.value = StopReason.User
                stopTunnel("user")
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        // The user revoked the VPN permission or another VPN app took over.
        stopTunnel("revoked")
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel("destroyed")
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------------

    private fun startTunnel(dns: String, onlyUpdate: Boolean) {
        showForeground(LinkState.Starting)
        if (vpn != null) return // already running

        Billing.init(this)
        Quota.init(this)
        Updates.init(this)

        // Gate 1: a forced update blocks everything except the update-only mode.
        if (Updates.required(this) && !onlyUpdate) {
            TunnelMonitor.stopReason.value = StopReason.Update
            Notif.updateBlocked(this)
            abortStart()
            return
        }
        // Gate 2: free allowance used up (Pro and update-only mode are exempt).
        val pro = Billing.isPro || Billing.cachedPro(this)
        if (!pro && !onlyUpdate && Quota.state.value.exhausted) {
            TunnelMonitor.stopReason.value = StopReason.Quota
            Notif.quotaAlert(this, 100)
            abortStart()
            return
        }

        updateOnly = onlyUpdate
        Quota.unlimited = pro
        Quota.frozen = onlyUpdate
        TunnelMonitor.stopReason.value = StopReason.None
        TunnelMonitor.reset()
        TunnelMonitor.setState(LinkState.Starting)

        val pfd = try {
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(VPN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(MTU)
                .setBlocking(true)
            for (d in dns.split(",")) {
                val x = d.trim()
                if (x.isNotEmpty()) builder.addDnsServer(x)
            }
            if (!applyRouting(builder, onlyUpdate)) {
                null
            } else {
                if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
                builder.establish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPN setup failed", e)
            null
        }

        if (pfd == null) {
            Toast.makeText(this, R.string.vpn_failed, Toast.LENGTH_LONG).show()
            Quota.frozen = false
            abortStart()
            return
        }

        vpn = pfd
        markVpnAsUnderlyingNetwork()
        link = ControlLink().also { it.updateOnly = onlyUpdate; it.start(this) }
        session = Session(pfd).also { it.start() }
    }

    private fun abortStart() {
        TunnelMonitor.setState(LinkState.Idle)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Which apps go through the cable. Cafe Bazaar is always allowed through (and the own app),
     * otherwise the subscription check could not reach the store. Returns false if nothing is left to route.
     */
    private fun applyRouting(b: VpnService.Builder, onlyUpdate: Boolean): Boolean {
        val self = packageName
        try {
            if (onlyUpdate) {
                b.addAllowedApplication(Brand.BAZAAR_PACKAGE)
                return true
            }
            when (Prefs.routeMode(this)) {
                Prefs.ROUTE_ONLY -> {
                    val apps = Prefs.routeApps(this)
                    var n = 0
                    for (p in apps + Brand.BAZAAR_PACKAGE + self) {
                        try {
                            b.addAllowedApplication(p)
                            n++
                        } catch (e: Exception) { // not installed
                        }
                    }
                    return n > 0
                }
                Prefs.ROUTE_EXCEPT -> {
                    for (p in Prefs.routeApps(this)) {
                        if (p == Brand.BAZAAR_PACKAGE || p == self) continue
                        try { b.addDisallowedApplication(p) } catch (e: Exception) {}
                    }
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "routing setup failed", e)
        }
        return true
    }

    /** Called from worker threads when the allowance/update/timer ends the session. */
    private fun stopForReason(reason: StopReason) {
        TunnelMonitor.stopReason.value = reason
        when (reason) {
            StopReason.Quota -> Notif.quotaAlert(this, 100)
            StopReason.Update -> Notif.updateBlocked(this)
            else -> {}
        }
        Thread {
            stopTunnel(reason.name.lowercase())
            stopSelf()
        }.start()
    }

    @Synchronized
    private fun stopTunnel(why: String) {
        val s = session
        val pfd = vpn
        val l = link
        session = null
        vpn = null
        link = null
        l?.stop(why)
        s?.stop()
        try {
            pfd?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Closing VPN interface failed", e)
        }
        Quota.frozen = false
        Quota.flush()
        Trial.pushNow(this)
        if (s != null || pfd != null) {
            TunnelMonitor.setState(LinkState.Idle)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /** Lets apps know that the VPN network is usable (same trick as gnirehtet). */
    @Suppress("DEPRECATION")
    private fun markVpnAsUnderlyingNetwork() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val vpnAddress = InetAddress.getByName(VPN_ADDRESS)
            val network: Network? = cm.allNetworks.firstOrNull { n ->
                cm.getLinkProperties(n)?.linkAddresses?.any { it.address == vpnAddress } == true
            }
            if (network != null) setUnderlyingNetworks(arrayOf(network))
        } catch (e: Exception) {
            Log.w(TAG, "Could not set underlying network", e)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Notification

    private fun showForeground(state: LinkState) {
        Notif.ensureChannels(this)
        val notification = buildNotification(state, "")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: LinkState, extra: String) {
        if (!Notif.canPost(this)) return
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(state, extra))
        } catch (e: Exception) {
        }
    }

    private fun buildNotification(state: LinkState, extra: String): Notification {
        val text = getString(
            when (state) {
                LinkState.Connected -> R.string.notif_connected
                LinkState.WaitingForPc -> R.string.notif_waiting
                else -> R.string.notif_starting
            }
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TunnelService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_net)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (extra.isEmpty()) text else extra)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notif.contentIntent(this, null, 0))
            .addAction(0, getString(R.string.notif_stop), stop)
        if (extra.isNotEmpty()) b.setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + extra))
        return b.build()
    }

    // ---------------------------------------------------------------------------------------------

    /** One running tunnel: three worker threads plus the link to the relay. */
    private inner class Session(private val pfd: ParcelFileDescriptor) {

        @Volatile private var running = true

        /** Socket that is connected and has completed the handshake. */
        @Volatile private var current: LocalSocket? = null
        @Volatile private var currentOut: OutputStream? = null

        /** Socket that is still connecting / waiting for the client id (so stop() can close it). */
        @Volatile private var pending: LocalSocket? = null

        private val tunToRelay = Thread({ pumpTunToRelay() }, "usbnet-up")
        private val relayToTun = Thread({ relayLoop() }, "usbnet-down")
        private val ticker = Thread({ tickLoop() }, "usbnet-ticker")

        fun start() {
            tunToRelay.start()
            relayToTun.start()
            ticker.start()
        }

        fun stop() {
            running = false
            closeQuietly(pending)
            closeQuietly(current)
            relayToTun.interrupt()
            ticker.interrupt()

            // A blocking read() on the TUN device is not woken up by closing it. Sending a packet
            // into the (still open) VPN interface makes the read return.
            val wake = Thread {
                try {
                    DatagramSocket().use {
                        it.send(
                            DatagramPacket(
                                ByteArray(0), 0,
                                InetAddress.getByAddress(byteArrayOf(42, 42, 42, 42)), 4242
                            )
                        )
                    }
                } catch (_: Exception) {
                }
            }
            wake.start()
            wake.join(500)
            tunToRelay.join(500)
        }

        // ---- phone -> computer -----------------------------------------------------------------

        private fun pumpTunToRelay() {
            val input = FileInputStream(pfd.fileDescriptor)
            val buffer = ByteArray(1 shl 16)
            try {
                while (running) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    if ((buffer[0].toInt() and 0xF0) != 0x40) continue // IPv4 only

                    val out = currentOut ?: continue // no link yet: drop, TCP will retry
                    if (!Quota.charge(n)) {
                        onQuotaHit()
                        break
                    }
                    try {
                        out.write(buffer, 0, n)
                        TunnelMonitor.addUp(n)
                    } catch (e: IOException) {
                        closeQuietly(current) // makes the relay loop reconnect
                    }
                }
            } catch (e: IOException) {
                if (running) Log.w(TAG, "TUN read ended", e)
            }
        }

        // ---- computer -> phone -----------------------------------------------------------------

        private fun relayLoop() {
            val tunOut = FileOutputStream(pfd.fileDescriptor)
            while (running) {
                val socket = LocalSocket()
                pending = socket
                try {
                    socket.connect(LocalSocketAddress(SOCKET_NAME))
                    val input = socket.inputStream
                    DataInputStream(input).readInt() // client id; EOF here = relay not running
                    current = socket
                    currentOut = socket.outputStream
                    TunnelMonitor.setState(LinkState.Connected)
                    pumpRelayToTun(input, tunOut)
                } catch (e: IOException) {
                    // cable unplugged, relay not started, ...: fall through and retry
                } finally {
                    current = null
                    currentOut = null
                    pending = null
                    closeQuietly(socket)
                }
                if (!running) break
                TunnelMonitor.setState(LinkState.WaitingForPc)
                try {
                    Thread.sleep(800)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }

        /**
         * The relay sends a byte stream; several IP packets may be glued together or a packet may be
         * split. Cut the stream into packets using the total length field of the IPv4 header.
         */
        private fun pumpRelayToTun(input: InputStream, tunOut: OutputStream) {
            val buf = ByteArray(1 shl 17)
            var fill = 0
            while (running) {
                val n = input.read(buf, fill, buf.size - fill)
                if (n < 0) return
                TunnelMonitor.addDown(n)
                fill += n

                var pos = 0
                while (fill - pos >= 4) {
                    val version = (buf[pos].toInt() and 0xF0) shr 4
                    val length = ((buf[pos + 2].toInt() and 0xFF) shl 8) or
                        (buf[pos + 3].toInt() and 0xFF)
                    if (version != 4 || length < 20) { // stream out of sync: drop everything
                        fill = 0
                        pos = 0
                        break
                    }
                    if (length > fill - pos) break // packet not complete yet
                    if (!Quota.charge(length)) {
                        onQuotaHit()
                        return
                    }
                    tunOut.write(buf, pos, length)
                    pos += length
                }
                if (pos > 0) {
                    System.arraycopy(buf, pos, buf, 0, fill - pos)
                    fill -= pos
                }
            }
        }

        private val quotaHit = java.util.concurrent.atomic.AtomicBoolean(false)

        private fun onQuotaHit() {
            if (quotaHit.compareAndSet(false, true)) stopForReason(StopReason.Quota)
        }

        // ---- statistics + notification ---------------------------------------------------------

        private fun tickLoop() {
            var ticks = 0
            var seconds = 0L
            var connectedBefore = false
            val autoStopMs = Prefs.autoStopMinutes(this@TunnelService) * 60_000L
            val startedMs = System.currentTimeMillis()
            while (running) {
                try {
                    // Half-second step: refresh the live counter on the phone and push it to the PC.
                    Thread.sleep(500)
                    if (TunnelMonitor.snapshot.value.state != LinkState.Idle) {
                        Quota.liveRefresh()
                        val lq = Quota.state.value
                        link?.status(Billing.isPro, lq.usedFree, lq.limit, lq.period)
                    }
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    return
                }
                ticks++
                TunnelMonitor.tick()
                val snap = TunnelMonitor.snapshot.value
                val state = snap.state
                if (state == LinkState.Idle) continue

                val pro = Billing.isPro
                Quota.unlimited = pro
                Quota.tick()
                Trial.pushIfDue(this@TunnelService)
                val q = Quota.state.value

                // Subscription check: at first connection and every 10 minutes.
                if (state == LinkState.Connected && !connectedBefore) {
                    connectedBefore = true
                    Billing.refresh()
                } else if (ticks % 600 == 0) {
                    Billing.refresh()
                }
                if (ticks % 1800 == 0) Updates.checkAsync(this@TunnelService)

                if (!updateOnly) {
                    if (Updates.required(this@TunnelService)) {
                        stopForReason(StopReason.Update)
                        return
                    }
                    if (!pro && q.exhausted) {
                        onQuotaHit()
                        return
                    }
                    if (!pro && q.fraction >= 0.8f && Quota.markAlert(80)) {
                        Notif.quotaAlert(this@TunnelService, 80)
                    }
                    if (autoStopMs > 0 && System.currentTimeMillis() - startedMs >= autoStopMs) {
                        stopForReason(StopReason.Timer)
                        return
                    }
                }
                if (state == LinkState.Connected) seconds++

                link?.status(pro, q.usedFree, q.limit, q.period)

                val extra = when {
                    updateOnly -> getString(R.string.notif_update_only)
                    pro -> formatSpeed(snap.downBps + snap.upBps, Prefs.speedUnit(this@TunnelService))
                    else -> formatBytes(q.usedFree) + " / " + formatBytes(q.limit) + "  ·  " +
                        formatSpeed(snap.downBps + snap.upBps, Prefs.speedUnit(this@TunnelService))
                }
                if (state == LinkState.Connected) updateNotification(state, extra)
                else if (ticks % 5 == 1) updateNotification(state, "")
            }
        }

        private fun closeQuietly(socket: LocalSocket?) {
            if (socket == null) return
            try { socket.shutdownInput() } catch (_: Exception) {}
            try { socket.shutdownOutput() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
