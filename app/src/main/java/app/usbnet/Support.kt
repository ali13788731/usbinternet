package app.usbnet

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Small wrapper around SharedPreferences. */
object Prefs {
    const val DNS_GOOGLE = 0
    const val DNS_CLOUDFLARE = 1
    const val DNS_CUSTOM = 2

    private val IPV4 = Regex(
        "^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$"
    )

    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** "fa" (default) or "en". */
    fun language(c: Context): String = sp(c).getString("lang", "fa") ?: "fa"
    fun setLanguage(c: Context, lang: String) = sp(c).edit().putString("lang", lang).apply()

    fun dnsChoice(c: Context): Int = sp(c).getInt("dns_choice", DNS_GOOGLE)
    fun setDnsChoice(c: Context, v: Int) = sp(c).edit().putInt("dns_choice", v).apply()

    fun customDns(c: Context): String = sp(c).getString("dns_custom", "") ?: ""
    fun setCustomDns(c: Context, v: String) = sp(c).edit().putString("dns_custom", v).apply()

    fun isValidIpv4(s: String): Boolean = IPV4.matches(s.trim())

    /** The DNS address that will actually be used (falls back to Google). */
    fun resolveDns(c: Context): String = when (dnsChoice(c)) {
        DNS_CLOUDFLARE -> "1.1.1.1"
        DNS_CUSTOM -> customDns(c).trim().takeIf { isValidIpv4(it) } ?: "8.8.8.8"
        else -> "8.8.8.8"
    }
}

/** Forces the app language (Persian by default) independent of the phone language. */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val locale = Locale(Prefs.language(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
