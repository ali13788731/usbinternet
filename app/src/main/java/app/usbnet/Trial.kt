package app.usbnet

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.ProcessLifecycleOwner
import com.farsitel.bazaar.core.BazaarSignIn
import com.farsitel.bazaar.core.callback.BazaarSignInCallback
import com.farsitel.bazaar.core.model.BazaarSignInOptions
import com.farsitel.bazaar.core.model.SignInOption
import com.farsitel.bazaar.storage.BazaarStorage
import com.farsitel.bazaar.storage.callback.BazaarStorageCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


enum class TrialStatus { Checking, NeedLogin, NeedBazaar, Ready }

data class TrialState(val status: TrialStatus = TrialStatus.NeedLogin, val error: Boolean = false)

/**
 * Forced Bazaar login + one-time trial ledger kept in Bazaar's per-user cloud storage.
 *  - first launch (or after reinstall / clear data): the user must sign in with Bazaar; the stored usage of that
 *    account is read back, so deleting the app never gives a new 300 MB;
 *  - no answer from Bazaar on a fresh install = still locked (never "assume zero");
 *  - while connected the used amount is pushed to the cloud every ~256 KB (at most every 10 s) and on stop.
 */
object Trial {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(TrialState())
    val state: StateFlow<TrialState> = _state
    val isReady: Boolean get() = _state.value.status == TrialStatus.Ready

    @Volatile private var pushing = false
    @Volatile private var pushStartedMs = 0L
    @Volatile private var lastPushMs = 0L

    /** Call after Quota.init(). A signed local binding lets the app start offline. */
    fun init(ctx: Context) {
        val q = Quota.state.value
        _state.value = when {
            q.verified -> TrialState(TrialStatus.Ready)
            !Billing.isBazaarInstalled(ctx) -> TrialState(TrialStatus.NeedBazaar)
            else -> TrialState(TrialStatus.NeedLogin)
        }
    }

    /** Asks Bazaar who is signed in and syncs the cloud ledger. Safe to call repeatedly. */
    fun refresh(a: ComponentActivity) {
        val ctx = a.applicationContext
        if (!Billing.isBazaarInstalled(ctx)) {
            if (!isReady) set(TrialStatus.NeedBazaar)
            return
        }
        if (!isReady) set(TrialStatus.Checking)
        try {
            BazaarSignIn.getLastSignedInAccount(a, a, BazaarSignInCallback { resp ->
                val id = resp?.data?.accountId
                main.post { onAccount(a, id) }
            })
        } catch (e: Throwable) {
            main.post { if (!isReady) set(TrialStatus.NeedLogin, true) }
        }
    }

    private fun onAccount(a: ComponentActivity, id: String?) {
        if (id.isNullOrEmpty()) {
            // not signed in (or permission not granted): a fresh install must log in
            if (!isReady) set(TrialStatus.NeedLogin)
            return
        }
        try {
            BazaarStorage.getSavedData(
                context = a,
                owner = a,
                callback = BazaarStorageCallback { r ->
                    val cloud: Long? = if (r == null) {
                        null
                    } else {
                        val t = r.data?.let { String(it, Charsets.UTF_8) }.orEmpty().trim()
                        if (t.isEmpty()) 0L else Quota.parseCloud(id, t)
                    }
                    main.post { bind(a.applicationContext, id, cloud) }
                }
            )
        } catch (e: Throwable) {
            main.post { bind(a.applicationContext, id, null) }
        }
    }

    private fun bind(ctx: Context, id: String, cloud: Long?) {
        if (Quota.bindAccount(id, cloud)) {
            set(TrialStatus.Ready)
            if (Quota.needsPush(true)) push(ctx)
        } else {
            // could not verify with the cloud: stay locked and let the user retry
            set(TrialStatus.NeedLogin, true)
        }
    }

    /** From the tunnel service (about once per second). */
    fun pushIfDue(ctx: Context) {
        if (!Quota.needsPush(false)) return
        if (System.currentTimeMillis() - lastPushMs < 10_000L) return
        push(ctx)
    }

    /** Immediate write (tunnel stopped, quota hit). */
    fun pushNow(ctx: Context) {
        if (Quota.needsPush(true)) push(ctx)
    }

    private fun push(ctx: Context) {
        val now = System.currentTimeMillis()
        if (pushing && now - pushStartedMs < 15_000L) return
        val q = Quota.state.value
        if (!q.verified || q.account.isEmpty()) return
        val used = Quota.currentFree()
        val record = Quota.cloudRecord()
        pushing = true
        pushStartedMs = now
        lastPushMs = now
        val app = ctx.applicationContext
        main.post {
            try {
                BazaarStorage.saveData(
                    context = app,
                    owner = ProcessLifecycleOwner.get(),
                    data = record.toByteArray(Charsets.UTF_8),
                    callback = BazaarStorageCallback { r ->
                        pushing = false
                        if (r != null) Quota.markPushed(used)
                    }
                )
            } catch (e: Throwable) {
                pushing = false
            }
        }
    }

    private fun set(s: TrialStatus, error: Boolean = false) {
        _state.value = TrialState(s, error)
    }

    // ---- sign-in helpers for the gate screen ----

    fun signInIntent(ctx: Context) = try {
        BazaarSignIn.getClient(
            ctx,
            BazaarSignInOptions.Builder(SignInOption.DEFAULT_SIGN_IN).build()
        ).getSignInIntent()
    } catch (e: Throwable) {
        null
    }
}
