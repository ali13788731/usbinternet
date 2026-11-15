# Cable Internet — Windows app

Shares the computer's internet with an Android phone over a USB cable.
It bundles everything the customer needs, so there is nothing to install by hand:

* `adb.exe` (Android Platform Tools) — talks to the phone
* `gnirehtet.exe` (relay) — shares the internet
* `app.apk` — the phone app, installed automatically over the cable
* A guided wizard that explains how to turn on USB debugging (per phone brand)

## How it is built

Push to GitHub. The workflow in `.github/workflows/build.yml`:

1. builds the Android APK,
2. downloads adb and gnirehtet,
3. publishes the Windows app as a self-contained exe (no .NET install needed),
4. builds `CableInternet-Setup.exe` with Inno Setup.

Download the results from the **Actions → run → Artifacts** section:

* `CableInternet-Windows-Setup` — installer to give to customers
* `CableInternet-Windows-portable` — same app as a folder (zip it yourself)
* `UsbNet-debug-apk` — the phone app

## Licences

Gnirehtet and Android Platform Tools are Apache-2.0. Their licence texts are copied to
`tools/licenses` by the workflow and must ship with the app.
