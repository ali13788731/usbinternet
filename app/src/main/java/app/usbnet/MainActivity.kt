package app.usbnet

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsState(applicationContext)
        // After a language change or rotation the activity is recreated: skip the splash then.
        val startInMain = savedInstanceState != null
        setContent {
            UsbNetTheme(settings.theme) {
                UsbNetApp(
                    settings = settings,
                    startInMain = startInMain,
                    onRecreate = { recreate() }
                )
            }
        }
    }
}
