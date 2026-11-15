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
        setContent {
            UsbNetTheme {
                MainScreen(
                    onToggleLanguage = {
                        val next = if (Prefs.language(this) == "fa") "en" else "fa"
                        Prefs.setLanguage(this, next)
                        recreate()
                    }
                )
            }
        }
    }
}
