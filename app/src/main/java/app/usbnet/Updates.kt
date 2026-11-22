package app.usbnet

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

fun currentVersionCode(ctx: Context): Int = try {
    @Suppress("DEPRECATION")
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
} catch (e: Exception) {
    0
}

fun currentVersionName(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

data class UpdateInfo(
    val code: Int,
    val name: String,
    val force: Boolean,
    val minCode: Int,
    val url: String,
    val notesFa: List<String>,
    val notesEn: List<String>,
) {
    /** Versions below this must update before the app works again. */
    val effectiveMin: Int get() = maxOf(minCode, if (force) code else 0)

    fun notes(fa: Boolean): List<String> =
        if (fa) notesFa.ifEmpty { notesEn } else notesEn.ifEmpty { notesFa }
}

/**
 * Update check against a small JSON file you host (see update/update.json).
 * The last answer is stored, so a forced update stays enforced even when the phone is offline.
 */
object Updates {
    private const val PREFS = "updates"
    private const val MIN_INTERVAL_MS = 6L * 3600L * 1000L

    private var appCtx: Context? = null
    private val _info = MutableStateFlow<UpdateInfo?>(null)
    val info: StateFlow<UpdateInfo?> = _info
    private var lastCheckMs = 0L
    private var checking = false

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        _info.value = load(ctx.applicationContext)
    }

    fun required(ctx: Context): Boolean {
        val i = _info.value ?: return false
        return currentVersionCode(ctx) < i.effectiveMin
    }

    fun available(ctx: Context): Boolean {
        val i = _info.value ?: return false
        return i.code > currentVersionCode(ctx)
    }

    fun checkIfStale(ctx: Context) {
        if (System.currentTimeMillis() - lastCheckMs >= MIN_INTERVAL_MS) checkAsync(ctx)
    }

    /** Fetches in the background. onDone(true) = got an answer from the server. */
    fun checkAsync(ctx: Context, onDone: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        if (Brand.UPDATE_URL.isEmpty()) {
            onDone?.invoke(false)
            return
        }
        if (checking) return
        checking = true
        Thread({
            val ok = fetchBlocking(app)
            checking = false
            onDone?.invoke(ok)
        }, "usbnet-update").start()
    }

    /** Blocking version (also used by the background worker). Returns true on success. */
    fun fetchBlocking(ctx: Context): Boolean {
        if (Brand.UPDATE_URL.isEmpty()) return false
        lastCheckMs = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(Brand.UPDATE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (conn.responseCode != 200) return false
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val info = parse(text) ?: return false
            save(ctx, info)
            _info.value = info
            // Notify once per version.
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (info.code > currentVersionCode(ctx) && sp.getInt("notified", 0) < info.code) {
                sp.edit().putInt("notified", info.code).apply()
                Notif.update(ctx, info, currentVersionCode(ctx) < info.effectiveMin)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
            }
        }
    }

    /** True once per version: the in-app "what's new" popup. */
    fun consumePopup(ctx: Context): Boolean {
        val i = _info.value ?: return false
        if (i.code <= currentVersionCode(ctx)) return false
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getInt("popup", 0) >= i.code) return false
        sp.edit().putInt("popup", i.code).apply()
        return true
    }

    fun openStore(ctx: Context) {
        val i = _info.value
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("bazaar://details?id=" + ctx.packageName))
                .setPackage(Brand.BAZAAR_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            return
        } catch (e: Exception) {
        }
        try {
            val url = if (i != null && i.url.isNotEmpty()) i.url else Brand.STORE_URL
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    // ---- storage ----

    private fun parse(text: String): UpdateInfo? {
        val o = JSONObject(text)
        val code = o.optInt("versionCode", 0)
        if (code <= 0) return null
        val cl = o.optJSONObject("changelog")
        return UpdateInfo(
            code = code,
            name = o.optString("versionName", code.toString()),
            force = o.optBoolean("force", false),
            minCode = o.optInt("minVersionCode", 0),
            url = o.optString("url", ""),
            notesFa = readList(cl, "fa"),
            notesEn = readList(cl, "en"),
        )
    }

    private fun readList(cl: JSONObject?, lang: String): List<String> {
        val arr = cl?.optJSONArray(lang) ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val t = arr.optString(i, "")
            if (t.isNotEmpty()) out.add(t)
        }
        return out
    }

    private fun save(ctx: Context, i: UpdateInfo) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("code", i.code).putString("name", i.name).putBoolean("force", i.force)
            .putInt("min", i.minCode).putString("url", i.url)
            .putString("fa", i.notesFa.joinToString("\n")).putString("en", i.notesEn.joinToString("\n"))
            .apply()
    }

    private fun load(ctx: Context): UpdateInfo? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = sp.getInt("code", 0)
        if (code <= 0) return null
        return UpdateInfo(
            code, sp.getString("name", "") ?: "", sp.getBoolean("force", false), sp.getInt("min", 0),
            sp.getString("url", "") ?: "",
            (sp.getString("fa", "") ?: "").split("\n").filter { it.isNotEmpty() },
            (sp.getString("en", "") ?: "").split("\n").filter { it.isNotEmpty() },
        )
    }
}
