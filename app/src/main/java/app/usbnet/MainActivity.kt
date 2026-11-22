package app.usbnet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
        }
        setContent {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route = intent.getStringExtra(Notif.EXTRA_ROUTE)
    }

    override fun onResume() {
        super.onResume()
        Billing.refreshIfStale(5 * 60 * 1000L)
        Updates.checkIfStale(applicationContext)
    }
}
