package app.usbnet

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class QuotaState(
    val usedFree: Long = 0L,
    val usedPro: Long = 0L,
    val limit: Long = Quota.FREE_LIMIT,
    val period: String = "",
    val tampered: Boolean = false,
) {
    val remaining: Long get() = (limit - usedFree).coerceAtLeast(0L)
    val exhausted: Boolean get() = usedFree >= limit
    val total: Long get() = usedFree + usedPro
    val fraction: Float get() = (usedFree.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}

/**
 * Monthly free allowance (100 MB, counted in both directions). Renews on the 1st of each Persian month.
 * Counted bytes are those that really travel through the tunnel. Stored with a device-bound signature.
 */
object Quota {
    const val FREE_LIMIT: Long = 100L * 1024L * 1024L
    private const val PREFS = "quota"

    private var appCtx: Context? = null
    private val freeBytes = AtomicLong(0L)
    private val proBytes = AtomicLong(0L)
    private val pendingDay = AtomicLong(0L)

    /** True while the user has an active subscription: nothing is blocked. */
    @Volatile
    var unlimited: Boolean = false

    /** Update-only mode: traffic is neither counted nor blocked. */
    @Volatile
    var frozen: Boolean = false

    @Volatile
    private var period: String = ""
    private var tampered = false
    private var lastSeenMs = 0L
    private var daily = HashMap<String, Long>()

    private val _state = MutableStateFlow(QuotaState())
    val state: StateFlow<QuotaState> = _state

    @Synchronized
    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        load()
        rollIfNeeded()
        persist()
        publish()
    }

    /** Called for every forwarded packet. Returns false when the free allowance is used up. */
    fun charge(n: Int): Boolean {
        if (frozen) return true
        val v = n.toLong()
        pendingDay.addAndGet(v)
        if (unlimited) {
            proBytes.addAndGet(v)
            return true
        }
        return freeBytes.addAndGet(v) <= FREE_LIMIT
    }

    /** Once per second while the tunnel runs. */
    @Synchronized
    fun tick() {
        if (appCtx == null) return
        rollIfNeeded()
        persist()
        publish()
    }

    @Synchronized
    fun flush() {
        if (appCtx == null) return
        persist()
        publish()
    }

    /** The PC keeps a ledger per phone; it is authoritative while connected (survives reinstall / clear data). */
    @Synchronized
    fun adoptFromPc(pcPeriod: String, pcUsed: Long) {
        if (appCtx == null || pcPeriod.length != 7 || pcUsed < 0L) return
        if (pcPeriod == period) {
            if (pcUsed > freeBytes.get()) freeBytes.set(minOf(pcUsed, FREE_LIMIT * 2))
        } else {
            archive(period, freeBytes.get(), proBytes.get())
            period = pcPeriod
            freeBytes.set(minOf(pcUsed, FREE_LIMIT * 2))
            proBytes.set(0L)
        }
        persist()
        publish()
    }

    /** Returns true the first time a threshold (80 or 100) is reached in the current period. */
    @Synchronized
    fun markAlert(level: Int): Boolean {
        val sp = prefs() ?: return false
        val key = "alert${level}_$period"
        if (sp.getBoolean(key, false)) return false
        sp.edit().putBoolean(key, true).apply()
        return true
    }

    fun dailyHistory(days: Int): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        for (i in days - 1 downTo 0) {
            c.timeInMillis = now
            c.add(Calendar.DAY_OF_MONTH, -i)
            val k = dayKey(c)
            out.add(Pair(k, synchronized(this) { daily[k] ?: 0L }))
        }
        return out
    }

    /** Previous months: (period, freeUsed, proUsed), newest first. */
    fun monthHistory(): List<Triple<String, Long, Long>> {
        val raw = prefs()?.getString("months", "") ?: ""
        val out = ArrayList<Triple<String, Long, Long>>()
        for (line in raw.split(";")) {
            val p = line.split(":")
            if (p.size == 3) {
                val a = p[1].toLongOrNull()
                val b = p[2].toLongOrNull()
                if (a != null && b != null) out.add(Triple(p[0], a, b))
            }
        }
        out.reverse()
        return out
    }

    // ---- internals ----

    private fun prefs() = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun macFor(p: String, f: Long, pr: Long): String =
        Integrity.mac(appCtx!!, "q|$p|$f|$pr")

    private fun load() {
        val sp = prefs() ?: return
        val hasData = sp.contains("period")
        period = sp.getString("period", "") ?: ""
        val f = sp.getLong("free", 0L)
        val p = sp.getLong("pro", 0L)
        lastSeenMs = sp.getLong("seen", 0L)
        val mac = sp.getString("mac", "") ?: ""
        if (hasData && mac != macFor(period, f, p)) {
            // edited by hand: fail closed for this period
            tampered = true
            freeBytes.set(FREE_LIMIT)
            proBytes.set(p)
        } else {
            freeBytes.set(f)
            proBytes.set(p)
        }
        daily = parseDaily(sp.getString("daily", "") ?: "")
    }

    private fun rollIfNeeded() {
        val now = maxOf(System.currentTimeMillis(), lastSeenMs)
        lastSeenMs = now
        val cur = Jalali.periodKey(now)
        if (period != cur) {
            if (period.isNotEmpty()) archive(period, freeBytes.get(), proBytes.get())
            period = cur
            freeBytes.set(0L)
            proBytes.set(0L)
            tampered = false
        }
    }

    private fun archive(p: String, f: Long, pr: Long) {
        if (p.isEmpty()) return
        val sp = prefs() ?: return
        val old = sp.getString("months", "") ?: ""
        val list = old.split(";").filter { it.isNotEmpty() && !it.startsWith("$p:") }.toMutableList()
        list.add("$p:$f:$pr")
        while (list.size > 12) list.removeAt(0)
        sp.edit().putString("months", list.joinToString(";")).apply()
    }

    private fun persist() {
        val sp = prefs() ?: return
        val add = pendingDay.getAndSet(0L)
        if (add > 0L) {
            val c = Calendar.getInstance()
            c.timeInMillis = maxOf(System.currentTimeMillis(), lastSeenMs)
            val k = dayKey(c)
            daily[k] = (daily[k] ?: 0L) + add
            if (daily.size > 62) {
                val keys = daily.keys.sorted()
                for (i in 0 until keys.size - 62) daily.remove(keys[i])
            }
        }
        val f = freeBytes.get()
        val p = proBytes.get()
        sp.edit()
            .putString("period", period)
            .putLong("free", f)
            .putLong("pro", p)
            .putLong("seen", lastSeenMs)
            .putString("mac", macFor(period, f, p))
            .putString("daily", daily.entries.joinToString(";") { it.key + ":" + it.value })
            .apply()
    }

    private fun publish() {
        _state.value = QuotaState(freeBytes.get(), proBytes.get(), FREE_LIMIT, period, tampered)
    }

    private fun dayKey(c: Calendar): String = String.format(
        Locale.US, "%04d%02d%02d",
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
    )

    private fun parseDaily(raw: String): HashMap<String, Long> {
        val m = HashMap<String, Long>()
        for (e in raw.split(";")) {
            val p = e.split(":")
            if (p.size == 2) {
                val v = p[1].toLongOrNull()
                if (v != null) m[p[0]] = v
            }
        }
        return m
    }
}
