package app.usbnet

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object BillingConfig {
    val SKU_MONTHLY: String = BuildConfig.SKU_MONTHLY
    val SKU_YEARLY: String = BuildConfig.SKU_YEARLY
    val RSA_KEY: String = BuildConfig.BAZAAR_RSA_KEY
}

enum class BillingError { None, BazaarMissing, ConnectionFailed, QueryFailed }
enum class PurchaseResult { Success, Cancelled, BazaarMissing, Failed }

data class Entitlement(
    val pro: Boolean = false,
    val plan: String = "",          // "monthly" | "yearly" | "pro"
    val verifiedAtMs: Long = 0L,    // last time Cafe Bazaar confirmed the subscription
    val live: Boolean = false,      // confirmed in this process (not only from the signed cache)
    val checking: Boolean = false,
    val error: BillingError = BillingError.None,
    val everChecked: Boolean = false,
)

data class BillingEvent(val timeMs: Long, val type: String, val plan: String)

/**
 * Subscription state, always owned by Cafe Bazaar:
 *  - checked on every app start, every resume (if stale), after every connect and every 10 minutes while connected;
 *  - an answer from Bazaar ("no active subscription") revokes Pro immediately;
 *  - if Bazaar cannot be reached, a device-signed cache is trusted for 3 days at most.
 * Because the check is tied to the Bazaar account, reinstalling the app restores Pro automatically.
 */
object Billing {
    private const val PREFS = "billing"
    private const val GRACE_MS = 3L * 24L * 3600L * 1000L
    private const val TIMEOUT_MS = 12_000L

    private val main = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private val _state = MutableStateFlow(Entitlement())
    val state: StateFlow<Entitlement> = _state
    val isPro: Boolean get() = _state.value.pro

    private var refreshing = false
    private val waiters = ArrayList<() -> Unit>()
    private var lastAttemptMs = 0L
    private var purchaseGraceUntil = 0L

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        val cached = readCache(ctx.applicationContext)
        if (cached != null) _state.value = cached
    }

    /** For background work (no UI, no Bazaar connection). */
    fun cachedPro(ctx: Context): Boolean = readCache(ctx.applicationContext) != null

    fun isBazaarInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(Brand.BAZAAR_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun refresh(onDone: (() -> Unit)? = null) {
        if (Looper.myLooper() == Looper.getMainLooper()) start(onDone) else main.post { start(onDone) }
    }

    fun refreshIfStale(maxAgeMs: Long) {
        if (System.currentTimeMillis() - lastAttemptMs >= maxAgeMs) refresh()
    }

    fun history(ctx: Context): List<BillingEvent> {
        val raw = prefs(ctx).getString("history", "") ?: ""
        val out = ArrayList<BillingEvent>()
        for (line in raw.split("\n")) {
            val p = line.split("|")
            if (p.size == 3) {
                val t = p[0].toLongOrNull() ?: continue
                out.add(BillingEvent(t, p[1], p[2]))
            }
        }
        out.reverse()
        return out
    }

    fun everSubscribed(ctx: Context): Boolean = prefs(ctx).getBoolean("ever", false)

    // ---------------------------------------------------------------- refresh

    private fun start(onDone: (() -> Unit)?) {
        val ctx = appCtx ?: return
        if (onDone != null) waiters.add(onDone)
        if (refreshing) return
        refreshing = true
        lastAttemptMs = System.currentTimeMillis()
        _state.value = _state.value.copy(checking = true, error = BillingError.None)

        if (!isBazaarInstalled(ctx)) {
            complete(null, BillingError.BazaarMissing)
            return
        }

        val done = AtomicBoolean(false)
        val cleanup = ArrayList<() -> Unit>()
        val timeout = Runnable {
            if (done.compareAndSet(false, true)) {
                runCleanup(cleanup)
                complete(null, BillingError.ConnectionFailed)
            }
        }
        main.postDelayed(timeout, TIMEOUT_MS)

        fun finish(active: List<String>?, err: BillingError) {
            if (!done.compareAndSet(false, true)) return
            main.removeCallbacks(timeout)
            runCleanup(cleanup)
            complete(active, err)
        }

        try {
            val payment = newPayment(ctx)
            val connection = connectTo(payment, {
                payment.getSubscribedProducts {
                    querySucceed { list ->
                        val active = ArrayList<String>()
                        for (p in list) {
                            if (!p.purchaseState.toString().equals("REFUNDED", ignoreCase = true)) {
                                active.add(p.productId)
                            }
                        }
                        finish(active, BillingError.None)
                    }
                    queryFailed {
                        finish(null, BillingError.QueryFailed)
                    }
                }
            }, {
                finish(null, BillingError.ConnectionFailed)
            })
            cleanup.add {
                try {
                    connection.disconnect()
                } catch (e: Exception) {
                }
            }
            if (done.get()) runCleanup(cleanup)
        } catch (e: Exception) {
            finish(null, BillingError.ConnectionFailed)
        }
    }

    private fun complete(active: List<String>?, err: BillingError) {
        refreshing = false
        val ctx = appCtx
        if (ctx != null) {
            val now = System.currentTimeMillis()
            if (active != null) {
                if (active.isNotEmpty()) {
                    applyActive(ctx, planOf(active[0]), "active")
                } else if (now < purchaseGraceUntil) {
                    _state.value = _state.value.copy(checking = false, everChecked = true)
                } else {
                    applyFree(ctx, BillingError.None)
                }
            } else {
                val cached = readCache(ctx)
                if (cached != null) {
                    _state.value = cached.copy(checking = false, error = err, everChecked = true)
                } else {
                    applyFree(ctx, err)
                }
            }
        }
        val w = ArrayList(waiters)
        waiters.clear()
        for (cb in w) cb()
    }

    // ---------------------------------------------------------------- purchase

    fun purchase(activity: ComponentActivity, sku: String, onResult: (PurchaseResult) -> Unit) {
        val ctx = appCtx ?: activity.applicationContext
        if (!isBazaarInstalled(ctx)) {
            onResult(PurchaseResult.BazaarMissing)
            return
        }
        val done = AtomicBoolean(false)
        val cleanup = ArrayList<() -> Unit>()
        fun finish(r: PurchaseResult) {
            if (!done.compareAndSet(false, true)) return
            runCleanup(cleanup)
            onResult(r)
        }
        try {
            val payment = newPayment(ctx)
            val connection = connectTo(payment, {
                payment.subscribeProduct(
                    activity.activityResultRegistry,
                    PurchaseRequest(productId = sku, payload = UUID.randomUUID().toString())
                ) {
                    purchaseFlowBegan { }
                    failedToBeginFlow { finish(PurchaseResult.Failed) }
                    purchaseSucceed { p ->
                        purchaseGraceUntil = System.currentTimeMillis() + 2L * 60L * 1000L
                        applyActive(ctx, planOf(p.productId), "purchase")
                        finish(PurchaseResult.Success)
                        main.postDelayed({ refresh() }, 4000L)
                    }
                    purchaseCanceled { finish(PurchaseResult.Cancelled) }
                    purchaseFailed { finish(PurchaseResult.Failed) }
                }
            }, {
                finish(PurchaseResult.Failed)
            })
            cleanup.add {
                try {
                    connection.disconnect()
                } catch (e: Exception) {
                }
            }
            if (done.get()) runCleanup(cleanup)
        } catch (e: Exception) {
            finish(PurchaseResult.Failed)
        }
    }

    // ---------------------------------------------------------------- state changes

    private fun applyActive(ctx: Context, plan: String, source: String) {
        val now = System.currentTimeMillis()
        val sp = prefs(ctx)
        if (!sp.getBoolean("was_pro", false)) {
            logEvent(ctx, if (source == "purchase") "purchased" else "active", plan)
        }
        val mac = Integrity.mac(ctx, "pro|$plan|$now")
        sp.edit()
            .putBoolean("pro", true).putString("plan", plan).putLong("at", now).putString("mac", mac)
            .putBoolean("was_pro", true).putBoolean("ever", true)
            .apply()
        _state.value = Entitlement(
            pro = true, plan = plan, verifiedAtMs = now, live = true,
            checking = false, error = BillingError.None, everChecked = true
        )
    }

    private fun applyFree(ctx: Context, err: BillingError) {
        val sp = prefs(ctx)
        if (sp.getBoolean("was_pro", false)) logEvent(ctx, "ended", sp.getString("plan", "") ?: "")
        sp.edit().putBoolean("pro", false).putBoolean("was_pro", false).remove("mac").remove("at").apply()
        _state.value = Entitlement(pro = false, checking = false, error = err, everChecked = true)
    }

    private fun readCache(ctx: Context): Entitlement? {
        val sp = prefs(ctx)
        if (!sp.getBoolean("pro", false)) return null
        val plan = sp.getString("plan", "") ?: ""
        val at = sp.getLong("at", 0L)
        val mac = sp.getString("mac", "") ?: ""
        if (mac != Integrity.mac(ctx, "pro|$plan|$at")) return null
        val now = System.currentTimeMillis()
        if (at <= 0L || at > now + 5L * 60L * 1000L) return null   // clock rolled back
        if (now - at > GRACE_MS) return null                         // not verified for too long
        return Entitlement(pro = true, plan = plan, verifiedAtMs = at, live = false, checking = true)
    }

    private fun logEvent(ctx: Context, type: String, plan: String) {
        val sp = prefs(ctx)
        val old = sp.getString("history", "") ?: ""
        val list = old.split("\n").filter { it.isNotEmpty() }.toMutableList()
        list.add("${System.currentTimeMillis()}|$type|$plan")
        while (list.size > 40) list.removeAt(0)
        sp.edit().putString("history", list.joinToString("\n")).apply()
    }

    // ---------------------------------------------------------------- helpers

    private fun planOf(sku: String): String = when (sku) {
        BillingConfig.SKU_MONTHLY -> "monthly"
        BillingConfig.SKU_YEARLY -> "yearly"
        else -> "pro"
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun runCleanup(list: ArrayList<() -> Unit>) {
        val copy = ArrayList(list)
        list.clear()
        for (c in copy) c()
    }

    private fun newPayment(ctx: Context): Payment {
        val check = if (BillingConfig.RSA_KEY.isBlank()) {
            SecurityCheck.Disable
        } else {
            SecurityCheck.Enable(rsaPublicKey = BillingConfig.RSA_KEY)
        }
        return Payment(context = ctx, config = PaymentConfiguration(localSecurityCheck = check))
    }

    private fun connectTo(payment: Payment, onOk: () -> Unit, onFail: () -> Unit) = payment.connect {
        connectionSucceed { onOk() }
        connectionFailed { onFail() }
        disconnected { }
    }
}
