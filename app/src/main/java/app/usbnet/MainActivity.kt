package app.usbnet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {

    private var route by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Core services: subscription state, monthly allowance, update info (all read from signed local caches first).
        Billing.init(applicationContext)
        Quota.init(applicationContext)
        Trial.init(applicationContext)
        Updates.init(applicationContext)
        Notif.ensureChannels(applicationContext)
        Notif.schedule(applicationContext)
        route = intent?.getStringExtra(Notif.EXTRA_ROUTE)

        val settings = SettingsState(applicationContext)
        // After a language change or rotation the activity is recreated: skip the splash then.
        val startInMain = savedInstanceState != null
        if (savedInstanceState == null) {
            // Cold start: ask Cafe Bazaar first thing, then tell the user how much data is left.
            Billing.refresh { Notif.launch(applicationContext) }
            // Forced Bazaar sign-in + read back the one-time trial ledger from Bazaar's cloud storage.
            Trial.refresh(this)
        }
        // Force the layout direction from the app language (the wrapped context alone is not always
        // honoured by the window), so Persian is really right-to-left everywhere.
        val rtl = Prefs.effectiveLanguage(applicationContext) == "fa"
        window.decorView.layoutDirection =
            if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                UsbNetTheme(settings.theme) {
                    UsbNetApp(
                        settings = settings,
                        startInMain = startInMain,
                        route = route,
                        onRouteConsumed = { route = null },
                        onRecreate = { recreate() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route = intent.getStringExtra(Notif.EXTRA_ROUTE)
    }

    override fun onResume() {
        super.onResume()
        Billing.refreshIfStale(5 * 60 * 1000L)
        if (Trial.state.value.status == TrialStatus.NeedBazaar) Trial.refresh(this)
        Updates.checkIfStale(applicationContext)
    }
}
