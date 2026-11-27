package app.usbnet

/**
 * Everything the owner is expected to fill in lives here.
 * An empty value hides the related row (contact links) or disables the action (download / update links).
 */
object Brand {
    // ---- Contact (About page). Use full links, e.g. "https://t.me/yourname" ----
    const val TELEGRAM = ""
    const val EMAIL = ""
    const val WEBSITE = ""

    /** Public store page (rate / share / update fallback). */
    const val STORE_URL = "https://cafebazaar.ir/app/app.usbnet"

    const val BAZAAR_PACKAGE = "com.farsitel.bazaar"
    const val BAZAAR_INSTALL_URL = "https://cafebazaar.ir/install"

    // ---- Windows version ----
    /** Direct link to the Windows installer (e.g. a GitHub release asset). */
    /** Filled automatically from the PC_DOWNLOAD_URL build property / CI variable (see app/build.gradle.kts). */
    val PC_DOWNLOAD_URL: String = BuildConfig.PC_DOWNLOAD_URL
    const val PC_INSTALLER_NAME = "CableNet-Setup.exe"

    // ---- Update check ----
    /** URL of the update.json file (see update/update.json). Empty = update check disabled. */
    const val UPDATE_URL = ""

    // ---- Windows prerequisites (official pages) ----
    const val URL_DOTNET = "https://dotnet.microsoft.com/download/dotnet-framework/net48"
    const val URL_VCREDIST = "https://aka.ms/vs/17/release/vc_redist.x64.exe"
    const val URL_USB_DRIVER = "https://developer.android.com/studio/run/win-usb"
}
