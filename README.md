# Peyvand (پیوند)

Use your computer's internet on your Android phone over a USB cable.
Android app (Kotlin / Jetpack Compose) + Windows companion (`pc/`).

## What you must fill in (owner checklist)

| Where | Value | Why |
|---|---|---|
| Cafe Bazaar panel | Create two subscription products | The app only connects to them |
| `gradle.properties` or CI variables | `SKU_MONTHLY`, `SKU_YEARLY` | The product ids you created (defaults `pro_monthly`, `pro_yearly`) |
| `gradle.properties` or CI secret | `BAZAAR_RSA_KEY` | The RSA public key of your app from the Bazaar developer panel (required for purchase verification) |
| `app/.../Brand.kt` | `PC_DOWNLOAD_URL` | Direct link of the Windows installer. With the included workflow it is `https://github.com/<user>/<repo>/releases/download/latest/Peyvand-Setup.exe` |
| `app/.../Brand.kt` | `UPDATE_URL` | Public URL of `update/update.json` (e.g. GitHub raw link). Empty = no update check |
| `app/.../Brand.kt`, `pc/Peyvand/Brand.cs` | contact links, store link | Optional |
| `res/values*/strings.xml` | `price_monthly`, `price_yearly` | Optional price text shown on the buttons. Leave empty: Bazaar shows the real price in its payment screen |
| CI secrets | `KEYSTORE_B64` (base64 of your .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Release-signed APK. Without them a debug APK is built (do not publish that) |

Use the **same signing key** every time: Cafe Bazaar identifies the app by it, and the Windows installer installs the same APK.

## Rename the app

Display name: `app_name` in `app/src/main/res/values/strings.xml` (English) and `values-fa/strings.xml` (Persian). Windows: `Loc.cs` key `app`, installer name in `pc/installer/Peyvand.iss`.

## How the rules work

* **Free plan**: 100 MB per Persian (Jalali) month, counted in both directions, shown live on the phone and the PC. Renews on the 1st of each Persian month.
* **Pro**: subscription checked with Cafe Bazaar at every app start, every resume, after each connect and every 10 minutes while connected. Bazaar ties it to the user's account, so reinstalling restores it. If Bazaar can't be reached the last signed confirmation is trusted for 3 days.
* **Reinstall tricks**: the PC keeps a signed ledger per phone (`%AppData%\Peyvand\usage.ini`) and sends it to the phone, so reinstalling the app cannot reset the free allowance while the PC app is used.
* **Free data ends**: the phone stops the tunnel and tells the PC, which stops sharing (balloon + sound).
* **Updates**: edit `update/update.json` and host it. `versionCode` greater than the installed one = notification + one popup. `minVersionCode` (or `"force": true`) = old versions stop working until updated; an "update-only" connection (only Cafe Bazaar is routed) lets people without internet download the update through the PC. Windows uses the `pc_*` fields.
* **Notifications**: usage notification at app start, 80 % / 100 % alerts, upgrade reminders every 2–3 days for non-Pro users (WorkManager), update notices.

## Honest limits

Everything runs on the user's device, so a determined person with a rooted phone can still tamper with local data. The checks above (signed local state, Bazaar verification at every start, PC-side ledger, fail-closed on tampering) stop normal users and casual tricks, not reverse engineering. Nothing here was compiled with Gradle or run on a device in the environment where it was written, so build and test once before publishing.

## Windows app

See `pc/README.md`. GitHub Actions builds the APK, the Windows app and the installer, and (on `main`) publishes both as a rolling release called `latest`.
