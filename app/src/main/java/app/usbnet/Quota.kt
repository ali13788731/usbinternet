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
    val period: String = Quota.PERIOD,
    val tampered: Boolean = false,
    val verified: Boolean = false,
    val account: String = "",
) {
    val remaining: Long get() = if (!verified) 0L else (limit - usedFree).coerceAtLeast(0L)

    /** Not verified with a Bazaar account counts as "used up" (fail closed). */
    val exhausted: Boolean get() = !verified || usedFree >= limit
    val total: Long get() = usedFree + usedPro
    val fraction: Float get() = (usedFree.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}

/**
 * ONE-TIME free trial (300 MB in both directions) per Bazaar account - it never renews.
 *
 * Anti-abuse design:
 *  - the used amount is mirrored to Bazaar's per-user cloud storage (Trial.kt), so uninstalling the app or
 *    clearing its data does not reset it: on the next start the cloud value is read back and the larger of
 *    (local, cloud, PC ledger) wins;
 *  - switching to another Bazaar account on the same install keeps the local amount (max rule);
 *  - local state is signed (device bound); a hand-edited file fails closed;
 *  - without a verified Bazaar account nothing is allowed for free users.
 * Known limit: a brand-new Bazaar account + a fresh install gets a fresh trial (only a server of your own
 * with phone-number / device attestation can stop that).
 */
object Quota {
    const val FREE_LIMIT: Long = 300L * 1024L * 1024L

    /** Constant "period" kept only for the PC protocol (7 chars); the trial never rolls over. */
    const val PERIOD = "onetime"
    private const val PREFS = "trial_quota"
    private const val PUSH_STEP = 16L * 1024L

    private var appCtx: Context? = null
    private val freeBytes = AtomicLong(0L)
    private val proBytes = AtomicLong(0L)
    private val pendingDay = AtomicLong(0L)
    private val lastPushed = AtomicLong(-1L)

    /** True while the user has an active subscription: nothing is blocked. */
    @Volatile
    var unlimited: Boolean = false

    /** Update-only mode: traffic is neither counted nor blocked. */
    @Volatile
    var frozen: Boolean = false

    @Volatile
    private var verified = false

    @Volatile
    private var account: String = ""
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
        lastSeenMs = maxOf(System.currentTimeMillis(), lastSeenMs)
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
        if (!verified) return false
        return freeBytes.addAndGet(v) <= FREE_LIMIT
    }

    /** Once per second while the tunnel runs. */
    @Synchronized
    fun tick() {
        if (appCtx == null) return
        lastSeenMs = maxOf(System.currentTimeMillis(), lastSeenMs)
        persist()
        publish()
    }

    /** Cheap UI refresh (no disk write): called twice a second while the tunnel runs so the counter is truly live. */
    @Synchronized
    fun liveRefresh() {
        if (appCtx == null) return
        publish()
    }

    /** Short anonymous tag of the Bazaar account, sent to the PC so its ledger is kept per account. */
    fun accountTag(): String =
        if (account.isEmpty()) "" else Integrity.macPlain("tag|$account").take(12)

    @Synchronized
    fun flush() {
        if (appCtx == null) return
        persist()
        publish()
    }

    /**
     * Binds this install to the Bazaar account [id]. [cloudUsed] is what Bazaar's cloud storage says this
     * account has used (0 = nothing saved yet, null = could not be read).
     * Returns false when the account can't be verified (new account/fresh install and no cloud answer).
     */
    @Synchronized
    fun bindAccount(id: String, cloudUsed: Long?): Boolean {
        if (appCtx == null || id.isEmpty()) return false
        if (cloudUsed == null && account != id) return false
        val merged = maxOf(freeBytes.get(), cloudUsed ?: 0L)
        freeBytes.set(minOf(merged, FREE_LIMIT * 2))
        account = id
        verified = true
        lastPushed.set(cloudUsed ?: -1L)
        persist()
        publish()
        return true
    }

    /** The PC keeps a ledger per account: it can only RAISE the usage here (a lower number never erases real usage). */
    @Synchronized
    fun adoptFromPc(pcPeriod: String, pcUsed: Long) {
        if (appCtx == null || pcUsed < 0L) return
        val v = minOf(pcUsed, FREE_LIMIT * 2)
        if (v > freeBytes.get()) {
            freeBytes.set(v)
            persist()
            publish()
        }
    }

    // ---- cloud mirror (see Trial.kt) ----

    /** Record written to Bazaar storage: "v1|used|signature". */
    fun cloudRecord(): String {
        val used = freeBytes.get()
        return "v1|$used|" + Integrity.macPlain("c|$account|$used")
    }

    /** Used bytes from a cloud record of [acct]; a broken or edited record counts as fully used. */
    fun parseCloud(acct: String, text: String): Long {
        val p = text.trim().split("|")
        if (p.size != 3 || p[0] != "v1") return FREE_LIMIT
        val used = p[1].toLongOrNull() ?: return FREE_LIMIT
        if (used < 0L || p[2] != Integrity.macPlain("c|$acct|$used")) return FREE_LIMIT
        return used
    }

    fun needsPush(force: Boolean): Boolean {
        if (!verified) return false
        val diff = freeBytes.get() - lastPushed.get()
        return if (force) diff > 0L else diff >= PUSH_STEP
    }

    fun markPushed(v: Long) {
        if (v > lastPushed.get()) lastPushed.set(v)
    }

    fun currentFree(): Long = freeBytes.get()

    /** Returns true the first time a threshold (80 or 100) is reached. */
    @Synchronized
    fun markAlert(level: Int): Boolean {
        val sp = prefs() ?: return false
        val key = "alert$level"
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

    /** No monthly history any more (one-time trial). */
    fun monthHistory(): List<Triple<String, Long, Long>> = emptyList()

    // ---- internals ----

    private fun prefs() = appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun macFor(a: String, f: Long, pr: Long): String =
        Integrity.mac(appCtx!!, "q|$a|$f|$pr")

    private fun load() {
        val sp = prefs() ?: return
        val hasData = sp.contains("acct")
        account = sp.getString("acct", "") ?: ""
        val f = sp.getLong("free", 0L)
        val p = sp.getLong("pro", 0L)
        lastSeenMs = sp.getLong("seen", 0L)
        val mac = sp.getString("mac", "") ?: ""
        if (hasData && mac != macFor(account, f, p)) {
            // edited by hand: fail closed
            tampered = true
            freeBytes.set(FREE_LIMIT)
            proBytes.set(p)
        } else {
            freeBytes.set(f)
            proBytes.set(p)
        }
        verified = hasData && account.isNotEmpty()
        daily = parseDaily(sp.getString("daily", "") ?: "")
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
            .putString("acct", account)
            .putLong("free", f)
            .putLong("pro", p)
            .putLong("seen", lastSeenMs)
            .putString("mac", macFor(account, f, p))
            .putString("daily", daily.entries.joinToString(";") { it.key + ":" + it.value })
            .apply()
    }

    private fun publish() {
        _state.value = QuotaState(freeBytes.get(), proBytes.get(), FREE_LIMIT, PERIOD, tampered, verified, account)
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
