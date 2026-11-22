package app.usbnet

import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Small side channel to the PC program: abstract socket "peyvand-ctl" -> adb reverse -> PC.
 * Newline separated JSON. Phone sends hello/status/bye, PC answers with "sync" (its usage ledger
 * for this phone, so reinstalling the app can't reset the free allowance while the PC is used).
 */
class ControlLink {
    companion object {
        private const val NAME = "peyvand-ctl"
    }

    @Volatile private var running = false
    @Volatile var updateOnly = false
    @Volatile private var out: OutputStream? = null
    @Volatile private var sock: LocalSocket? = null
    @Volatile private var serial: String = ""
    /** False until the PC ledger answer ("sync") has been adopted on this link. */
    @Volatile private var synced = false
    private var thread: Thread? = null

    fun start(ctx: android.content.Context) {
        if (running) return
        running = true
        val id = "ph-" + Integrity.mac(ctx, "id").take(12)
        serial = id
        thread = Thread({ loop(id) }, "usbnet-ctl").also { it.start() }
    }

    fun stop(reason: String) {
        if (!running) return
        send(JSONObject().put("t", "bye").put("reason", reason))
        running = false
        val s = sock
        val t = thread
        Thread {
            try { Thread.sleep(150) } catch (_: Exception) {}
            try { s?.close() } catch (_: Exception) {}
        }.start()
        try { t?.join(300) } catch (_: Exception) {}
    }

    /** Sent about once per second. */
    fun status(pro: Boolean, used: Long, limit: Long, period: String) {
        send(
            JSONObject().put("t", "status").put("serial", serial).put("pro", pro).put("only", updateOnly).put("used", used)
                .put("limit", limit).put("period", period).put("acct", Quota.accountTag()).put("synced", synced)
        )
    }

    /** Signs a message so the PC program can tell it really comes from the Peyvand app (not another program). */
    private fun sign(o: JSONObject): JSONObject {
        val ts = System.currentTimeMillis() / 1000L
        val body = listOf(
            o.optString("t"), o.optString("serial"), o.optBoolean("pro").toString(),
            o.optLong("used").toString(), o.optString("period"), ts.toString()
        ).joinToString("|")
        o.put("ts", ts).put("sig", hmacHex(BuildConfig.CONTROL_SECRET, body))
        return o
    }

    private fun hmacHex(secret: String, data: String): String {
        val m = javax.crypto.Mac.getInstance("HmacSHA256")
        m.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sb = StringBuilder()
        for (b in m.doFinal(data.toByteArray(Charsets.UTF_8))) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun send(o: JSONObject) {
        val w = out ?: return
        if (o.optString("t") != "bye") sign(o)
        try {
            synchronized(this) {
                w.write((o.toString() + "\n").toByteArray(Charsets.UTF_8))
                w.flush()
            }
        } catch (e: Exception) {
            try { sock?.close() } catch (_: Exception) {}
        }
    }

    private fun loop(serial: String) {
        while (running) {
            val s = LocalSocket()
            try {
                s.connect(LocalSocketAddress(NAME))
                sock = s
                out = s.outputStream
                synced = false
                val q = Quota.state.value
                send(
                    JSONObject().put("t", "hello").put("serial", serial).put("pro", Billing.isPro).put("only", updateOnly)
                        .put("used", q.usedFree).put("limit", q.limit).put("period", q.period)
                        .put("acct", Quota.accountTag()).put("v", 1).put("synced", false)
                )
                val r = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
                while (running) {
                    val line = r.readLine() ?: break
                    handle(line)
                }
            } catch (e: Exception) {
                // PC not there (yet): retry
            } finally {
                out = null
                sock = null
                try { s.close() } catch (_: Exception) {}
            }
            if (!running) break
            try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
        }
    }

    private fun handle(line: String) {
        try {
            val o = JSONObject(line)
            if (o.optString("t") == "sync") {
                Quota.adoptFromPc(o.optString("period"), o.optLong("used", 0L))
                synced = true
            }
        } catch (e: Exception) {
        }
    }
}
