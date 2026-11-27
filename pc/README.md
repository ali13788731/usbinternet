# CableNet — Windows app

Shares the computer's internet with an Android phone over a USB cable.
It bundles everything the customer needs, so there is nothing to install by hand:

* `adb.exe` (Android Platform Tools) — talks to the phone
* `gnirehtet.exe` (relay) — shares the internet
* `app.apk` — the phone app, installed automatically over the cable
* A modern custom-drawn UI (dark/light, English/Persian with RTL), animated splash, tray icon
* Pages: Home (live usage), Subscription (Free vs Pro), Tutorial, Requirements (auto-detected), Settings, About
* Usage: a side channel with the phone app (`adb reverse localabstract:peyvand-ctl tcp:31417`) shows the exact monthly usage and stops sharing when the free 300 MB ends
* Update check through the same `update/update.json` (`pc_*` fields); set `Brand.UpdateUrl`
* Owner contact links: fill `Brand.cs` (Telegram / Email / Website); empty rows are hidden

## How it is built

Push to GitHub. The workflow in `.github/workflows/build.yml`:

1. builds the Android APK,
2. downloads adb and gnirehtet,
3. builds the Windows app on .NET Framework 4.8 (built into Windows 10/11, so the exe is ~100 KB),
4. builds `CableNet-Setup.exe` with Inno Setup.

Download the results from the **Actions → run → Artifacts** section:

* `CableNet-Windows-Setup` — installer to give to customers
* `CableNet-Windows-portable` — same app as a folder (zip it yourself)
* `UsbNet-debug-apk` — the phone app

## Licences

Gnirehtet and Android Platform Tools are Apache-2.0. Their licence texts are copied to
`tools/licenses` by the workflow and must ship with the app.
