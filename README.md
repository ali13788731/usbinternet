# Peyvand (پیوند)

Use your computer's internet on your Android phone over a USB cable.
Android app (Kotlin / Jetpack Compose) + Windows companion (`pc/`).

## What you must fill in (owner checklist)

| Where | Value | Why |
|---|---|---|
| Cafe Bazaar panel | Create ONE subscription product that holds your plans (monthly / yearly) | The "Buy subscription" button opens it directly; Bazaar shows the plans |
| `gradle.properties` or CI variables | `SKU_MONTHLY` (id of that product, default `pro_monthly`) | `SKU_YEARLY` is optional (only to recognise an old separate yearly product) |
| `gradle.properties` or CI secret | `BAZAAR_RSA_KEY` | The RSA public key of your app from the Bazaar developer panel (required for purchase verification) |
| `app/.../Brand.kt` | `PC_DOWNLOAD_URL` | Direct link of the Windows installer. With the included workflow it is `https://github.com/<user>/<repo>/releases/download/latest/Peyvand-Setup.exe` |
| `app/.../Brand.kt` | `UPDATE_URL` | Public URL of `update/update.json` (e.g. GitHub raw link). Empty = no update check |
| `app/.../Brand.kt`, `pc/Peyvand/Brand.cs` | contact links, store link | Optional |
| `res/values*/strings.xml` | `price_monthly`, `price_yearly` | Optional price text shown on the buttons. Leave empty: Bazaar shows the real price in its payment screen |
| CI secrets | `KEYSTORE_B64` (base64 of your .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Release-signed APK. Without them a debug APK is built (do not publish that) |

Use the **same signing key** every time: Cafe Bazaar identifies the app by it, and the Windows installer installs the same APK.

## Font

The UI uses **B Titr** (`BTITRBD.ttf`) on both the phone (`app/src/main/res/font/btitr_bold.ttf`) and the PC (`pc/Peyvand/fonts`). It has only a bold face and no Latin letters, so English text falls back to the system font. Check the font's licence before publishing.

## Rename the app

Display name: `app_name` in `app/src/main/res/values/strings.xml` (English) and `values-fa/strings.xml` (Persian). Windows: `Loc.cs` key `app`, installer name in `pc/installer/Peyvand.iss`.

## How the rules work

* **Free plan**: a ONE-TIME 300 MB trial per Cafe Bazaar account (counted in both directions, it never renews). The app forces a Bazaar sign-in on first launch and mirrors the used amount to Bazaar's per-user cloud storage, so uninstalling/reinstalling cannot reset it. The counter is live (twice a second) on the phone and every change is written to Bazaar's cloud immediately (chained writes with retries, exact to the byte, never counted past 300 MB); the PC shows plan and usage only while the phone is connected (Pro shows no volume).
* **Pro**: subscription checked with Cafe Bazaar at every app start, every resume, after each connect and every 10 minutes while connected. Bazaar ties it to the user's account, so reinstalling restores it. If Bazaar can't be reached the last signed confirmation is trusted for 3 days.
* **Reinstall tricks**: besides the Bazaar cloud record, the PC keeps a signed ledger per Bazaar account (`%AppData%\Peyvand\usage.ini`) and sends it to the phone, so reinstalling the app cannot reset the free allowance while the PC app is used.
* **Plan detection on the PC**: when the phone links, the PC app announces the plan (tray notification). On Pro it unlocks the desktop features (start with Windows, tray mode) automatically, once.
* **Free data ends**: the phone stops the tunnel and tells the PC, which stops sharing (balloon + sound).
* **Updates**: edit `update/update.json` and host it. `versionCode` greater than the installed one = notification + one popup. `minVersionCode` (or `"force": true`) = old versions stop working until updated; an "update-only" connection (only Cafe Bazaar is routed) lets people without internet download the update through the PC. Windows uses the `pc_*` fields.
* **Notifications**: usage notification at app start, 80 % / 100 % alerts, upgrade reminders every 2–3 days for non-Pro users (WorkManager), update notices.

## Honest limits

Everything runs on the user's device, so a determined person with a rooted phone can still tamper with local data. The checks above (signed local state, Bazaar verification at every start, PC-side ledger, fail-closed on tampering) stop normal users and casual tricks, not reverse engineering. Nothing here was compiled with Gradle or run on a device in the environment where it was written, so build and test once before publishing.

## Windows app

See `pc/README.md`. GitHub Actions builds the APK, the Windows app and the installer, and (on `main`) publishes both as a rolling release called `latest`.
