package app.usbnet

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Small wrapper around SharedPreferences. */
object Prefs {
    const val DNS_GOOGLE = 0
    const val DNS_CLOUDFLARE = 1
    const val DNS_CUSTOM = 2
    const val DNS_QUAD9 = 3

    const val LANG_SYSTEM = "system"
    const val LANG_EN = "en"
    const val LANG_FA = "fa"

    const val THEME_SYSTEM = "system"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"

    const val UNIT_MBS = 0
    const val UNIT_MBPS = 1

    private val IPV4 = Regex(
        "^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$"
    )

    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Stored choice: "system" (default), "en" or "fa". */
    fun language(c: Context): String = sp(c).getString("lang", LANG_SYSTEM) ?: LANG_SYSTEM
    fun setLanguage(c: Context, v: String) = sp(c).edit().putString("lang", v).apply()

    /** The language that is really used: Persian when asked for (or when the phone is Persian), else English. */
    fun effectiveLanguage(c: Context): String = when (language(c)) {
        LANG_FA -> "fa"
        LANG_EN -> "en"
        else -> {
            val sys: Locale? = Resources.getSystem().configuration.locales.get(0)
            if (sys != null && sys.language == "fa") "fa" else "en"
        }
    }

    fun theme(c: Context): String = sp(c).getString("theme", THEME_SYSTEM) ?: THEME_SYSTEM
    fun setTheme(c: Context, v: String) = sp(c).edit().putString("theme", v).apply()

    fun dnsChoice(c: Context): Int = sp(c).getInt("dns_choice", DNS_GOOGLE)
    fun setDnsChoice(c: Context, v: Int) = sp(c).edit().putInt("dns_choice", v).apply()

    fun customDns(c: Context): String = sp(c).getString("dns_custom", "") ?: ""
    fun setCustomDns(c: Context, v: String) = sp(c).edit().putString("dns_custom", v).apply()

    fun keepScreenOn(c: Context): Boolean = sp(c).getBoolean("keep_screen", false)
    fun setKeepScreenOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("keep_screen", v).apply()

    fun haptics(c: Context): Boolean = sp(c).getBoolean("haptics", true)
    fun setHaptics(c: Context, v: Boolean) = sp(c).edit().putBoolean("haptics", v).apply()

    fun showGraph(c: Context): Boolean = sp(c).getBoolean("show_graph", true)
    fun setShowGraph(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_graph", v).apply()

    fun speedUnit(c: Context): Int = sp(c).getInt("speed_unit", UNIT_MBS)
    fun setSpeedUnit(c: Context, v: Int) = sp(c).edit().putInt("speed_unit", v).apply()

    fun onboardingDone(c: Context): Boolean = sp(c).getBoolean("onboarding_done", false)
    fun setOnboardingDone(c: Context, v: Boolean) = sp(c).edit().putBoolean("onboarding_done", v).apply()

    /** Removes every stored option (the intro is shown again only if the caller asks for it). */
    fun clearAll(c: Context) = sp(c).edit().clear().apply()

    fun isValidIpv4(s: String): Boolean = IPV4.matches(s.trim())

    /** The DNS address that will actually be used (falls back to Google). */
    fun resolveDns(c: Context): String = when (dnsChoice(c)) {
        DNS_CLOUDFLARE -> "1.1.1.1"
        DNS_QUAD9 -> "9.9.9.9"
        DNS_CUSTOM -> customDns(c).trim().takeIf { isValidIpv4(it) } ?: "8.8.8.8"
        else -> "8.8.8.8"
    }
}

/** Observable copy of the settings so Compose recomposes when an option changes. */
class SettingsState(context: Context) {
    private val ctx: Context = context.applicationContext

    var language by mutableStateOf(Prefs.language(ctx))
        private set
    var theme by mutableStateOf(Prefs.theme(ctx))
        private set
    var dnsChoice by mutableStateOf(Prefs.dnsChoice(ctx))
        private set
    var customDns by mutableStateOf(Prefs.customDns(ctx))
        private set
    var keepScreenOn by mutableStateOf(Prefs.keepScreenOn(ctx))
        private set
    var haptics by mutableStateOf(Prefs.haptics(ctx))
        private set
    var showGraph by mutableStateOf(Prefs.showGraph(ctx))
        private set
    var speedUnit by mutableStateOf(Prefs.speedUnit(ctx))
        private set
    var onboardingDone by mutableStateOf(Prefs.onboardingDone(ctx))
        private set

    fun updateLanguage(v: String) { language = v; Prefs.setLanguage(ctx, v) }
    fun updateTheme(v: String) { theme = v; Prefs.setTheme(ctx, v) }
    fun updateDnsChoice(v: Int) { dnsChoice = v; Prefs.setDnsChoice(ctx, v) }
    fun updateCustomDns(v: String) { customDns = v; Prefs.setCustomDns(ctx, v) }
    fun updateKeepScreenOn(v: Boolean) { keepScreenOn = v; Prefs.setKeepScreenOn(ctx, v) }
    fun updateHaptics(v: Boolean) { haptics = v; Prefs.setHaptics(ctx, v) }
    fun updateShowGraph(v: Boolean) { showGraph = v; Prefs.setShowGraph(ctx, v) }
    fun updateSpeedUnit(v: Int) { speedUnit = v; Prefs.setSpeedUnit(ctx, v) }
    fun updateOnboardingDone(v: Boolean) { onboardingDone = v; Prefs.setOnboardingDone(ctx, v) }

    /** Restores every option to its default. The intro stays marked as seen. */
    fun resetAll() {
        Prefs.clearAll(ctx)
        Prefs.setOnboardingDone(ctx, true)
        language = Prefs.language(ctx)
        theme = Prefs.theme(ctx)
        dnsChoice = Prefs.dnsChoice(ctx)
        customDns = Prefs.customDns(ctx)
        keepScreenOn = Prefs.keepScreenOn(ctx)
        haptics = Prefs.haptics(ctx)
        showGraph = Prefs.showGraph(ctx)
        speedUnit = Prefs.speedUnit(ctx)
        onboardingDone = Prefs.onboardingDone(ctx)
    }
}

/** Forces the app language (system language if Persian, otherwise English) independent of other settings. */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val locale = Locale(Prefs.effectiveLanguage(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
