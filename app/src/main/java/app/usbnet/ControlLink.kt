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
    private var thread: Thread? = null

    fun start(ctx: android.content.Context) {
        if (running) return
        running = true
        val serial = "ph-" + Integrity.mac(ctx, "id").take(12)
        thread = Thread({ loop(serial) }, "usbnet-ctl").also { it.start() }
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
            JSONObject().put("t", "status").put("pro", pro).put("only", updateOnly).put("used", used)
                .put("limit", limit).put("period", period)
        )
    }

    private fun send(o: JSONObject) {
        val w = out ?: return
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
                val q = Quota.state.value
                send(
                    JSONObject().put("t", "hello").put("serial", serial).put("pro", Billing.isPro).put("only", updateOnly)
                        .put("used", q.usedFree).put("limit", q.limit).put("period", q.period)
                        .put("v", 1)
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
            }
        } catch (e: Exception) {
        }
    }
}
