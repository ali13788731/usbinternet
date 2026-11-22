package app.usbnet

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Device-bound signatures for locally stored state (subscription cache, usage counters). */
object Integrity {
    private const val SALT = "pv|k9#Pv!7|q7!Zr#2m"

    fun mac(ctx: Context, data: String): String {
        val id = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "x"
        } catch (e: Exception) {
            "x"
        }
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest("$id|${ctx.packageName}|$SALT".toByteArray(Charsets.UTF_8))
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val out = m.doFinal(data.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in out) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}
