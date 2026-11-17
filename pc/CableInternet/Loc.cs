using System;
using System.Collections.Generic;
using System.Globalization;

namespace CableInternet;

/// <summary>English / Persian text tables. Auto = Persian when Windows UI language is Persian, otherwise English.</summary>
static class Loc
{
    public static string Lang = "en";
    public static bool IsRtl => Lang == "fa";

    public static void Apply(string setting)
    {
        if (setting == "fa" || setting == "en") Lang = setting;
        else Lang = CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "fa" ? "fa" : "en";
    }

    public static string T(string key)
    {
        var d = Lang == "fa" ? Fa : En;
        if (d.TryGetValue(key, out var v)) return v;
        return En.TryGetValue(key, out v) ? v : key;
    }

    public static string[] Lines(string key) => T(key).Split('\n');

    static readonly Dictionary<string, string> En = new()
    {
        ["app"] = "Cable Internet",
        ["tagline"] = "Share your PC's internet with your phone over a USB cable",
        ["designed"] = "Designed by Mr.PC",
        ["nav0"] = "Home", ["nav1"] = "USB Guide", ["nav2"] = "Settings", ["nav3"] = "About",
        ["home_t"] = "Connection", ["home_s"] = "Everything is automatic. Just plug in your phone.",
        ["ph_start_t"] = "Starting…", ["ph_start_s"] = "Preparing tools",
        ["ph_tools_t"] = "Program files are incomplete", ["ph_tools_s"] = "The tools folder was not found next to the app. Please reinstall the program.",
        ["ph_nodev_t"] = "Plug in your phone with a USB cable", ["ph_nodev_s"] = "Wait a few seconds after plugging in. If USB debugging is off, open the USB Guide.",
        ["ph_nodev2_t"] = "Phone not found", ["ph_nodev2_s"] = "• Use a data cable (not charge-only)\n• Unlock the phone and set USB mode to File transfer\n• USB debugging must be turned on",
        ["ph_auth_t"] = "Tap Allow on your phone", ["ph_auth_s"] = "An \"Allow USB debugging?\" prompt is on your phone. Tick \"Always allow\" and tap Allow. If you can't see it, turn the screen on or pull down the notification bar.",
        ["ph_off_t"] = "Phone is not ready", ["ph_off_s"] = "Unplug and re-plug the cable. If that doesn't help, turn USB debugging off and on again.",
        ["ph_prep_t"] = "Preparing your phone…", ["ph_prep_s"] = "The phone app is being installed. If your phone asks for confirmation, accept it.",
        ["ph_fail_t"] = "Something went wrong",
        ["ph_ready_t"] = "Phone is ready", ["ph_ready_s"] = "Press Start sharing to give your phone internet.",
        ["ph_wait_t"] = "Now tap Connect on your phone", ["ph_wait_s"] = "The Cable Internet app is open on your phone. Tap the big connect button and accept the VPN request.",
        ["ph_conn_t"] = "Connected", ["ph_conn_s"] = "Your phone is using this computer's internet. Keep the cable plugged in and this app open.",
        ["b_guide"] = "USB Guide", ["b_retry"] = "Try again", ["b_start"] = "Start sharing", ["b_restart"] = "Restart connection",
        ["st0"] = "Phone connected", ["st1"] = "USB debugging allowed", ["st2"] = "Phone app ready", ["st3"] = "Internet connected",
        ["tl_phone"] = "Phone", ["tl_none"] = "Not connected", ["tl_share"] = "Sharing", ["tl_on"] = "Running", ["tl_off"] = "Stopped",
        ["tl_sess"] = "Session", ["tl_port"] = "Relay port",
        ["qa_open"] = "Open app on phone", ["qa_reinst"] = "Reinstall phone app", ["qa_log"] = "Technical log", ["qa_hide"] = "Hide log",
        ["e_apk"] = "The phone app file (tools\\app.apk) is missing. Install the app on your phone from the store, then try again.",
        ["e_restricted"] = "Your phone blocks installs over USB. In Developer options turn on \"Install via USB\" (on Xiaomi also \"USB debugging (Security settings)\"), then press Try again.",
        ["e_cancel"] = "The install was cancelled on the phone. Try again and accept the install prompt on your phone.",
        ["e_storage"] = "The phone storage is full. Free some space and try again.",
        ["e_install"] = "The phone app could not be installed. Install it from the store on your phone, then try again. (Details are in the technical log.)",
        ["e_notinst"] = "The phone app is not installed and automatic install is turned off in Settings.",
        ["e_reverse"] = "Could not create the USB route. Unplug and re-plug the cable.",
        ["e_relay"] = "The sharing component could not start. Your antivirus may have blocked tools\\gnirehtet.exe. Allow it and reopen the app.",
        ["g_t"] = "USB debugging guide", ["g_s"] = "Turn it on once and you are done.", ["g_status"] = "Phone status",
        ["g_check"] = "Check again",
        ["gt0"] = "All phones", ["gt1"] = "Samsung", ["gt2"] = "Xiaomi / Redmi / Poco", ["gt3"] = "Huawei / Honor", ["gt4"] = "Oppo / Realme / OnePlus", ["gt5"] = "Vivo / iQOO", ["gt6"] = "Not detected?",
        ["g0"] = "Open Settings on your phone and go to About phone.\nTap Build number 7 times in a row. Enter your PIN if asked. You will see \"You are now a developer\".\nGo back to Settings and open Developer options (inside System or Additional settings, or at the very bottom).\nTurn on USB debugging and confirm.\nPlug the phone into the computer. When \"Allow USB debugging?\" appears, tick Always allow and tap Allow.\nNames can differ slightly between phone models. This is needed only once.",
        ["g1"] = "Settings → About phone → Software information.\nTap Build number 7 times and enter your PIN.\nGo back: Developer options now appears at the bottom of Settings.\nTurn on USB debugging.\nPlug in the phone and accept the Allow prompt.\nIf it fails on newer One UI: Settings → Security and privacy → Auto Blocker → turn it off (or turn off \"Block commands via USB cable\").",
        ["g2"] = "Settings → About phone.\nTap MIUI version (or OS version on HyperOS) 7 times.\nSettings → Additional settings → Developer options.\nTurn on: USB debugging, Install via USB, and USB debugging (Security settings) if you see them.\nPlug in the phone and accept the Allow prompt.\nWithout \"Install via USB\" Xiaomi blocks installing the phone app. Some models need a Mi account and internet to enable it.",
        ["g3"] = "Settings → About phone.\nTap Build number 7 times.\nSettings → System & updates → Developer options.\nTurn on USB debugging (and \"Allow ADB debugging in charge only mode\" if you see it).\nPlug in the phone, set USB mode to Transfer files, and accept the Allow prompt.",
        ["g4"] = "Settings → About device → Version.\nTap Build number 7 times.\nSettings → Additional settings → Developer options.\nTurn on USB debugging.\nPlug in the phone and accept the Allow prompt.\nIf installing the app is refused, also turn on \"Install via USB\" or \"Disable permission monitoring\" in the same screen.",
        ["g5"] = "Settings → More settings (or System) → About phone.\nTap Software version (or Build number) 7 times.\nGo back and open Developer options.\nTurn on USB debugging.\nPlug in the phone and accept the Allow prompt.",
        ["g6"] = "Cable: some cables only charge. Use the original data cable.\nPort: plug straight into the computer, not into a hub.\nScreen: keep the phone unlocked. Pull down the notification bar and set USB mode to File transfer (MTP).\nNo Allow prompt: in Developer options tap Revoke USB debugging authorizations, turn USB debugging off and on, unplug and plug again.\nDriver: run Windows Update. If still not detected, install the phone maker's PC app (e.g. Samsung Smart Switch) once to get the driver.\nOther programs: close Mi PC Suite, HiSuite, Smart Switch or Android Studio — they can hold adb.\nStill nothing? Restart the phone and the computer.",
        ["s_t"] = "Settings", ["s_s"] = "Make the app work your way.",
        ["sg0"] = "General", ["sg1"] = "Startup and tray", ["sg2"] = "Connection", ["sg3"] = "Maintenance",
        ["s_lang"] = "Language", ["s_lang_d"] = "Auto follows your Windows language.",
        ["o_auto"] = "Auto", ["o_en"] = "English", ["o_fa"] = "فارسی",
        ["s_theme"] = "Theme", ["s_theme_d"] = "Dark, light, or follow Windows.",
        ["o_dark"] = "Dark", ["o_light"] = "Light", ["o_sys"] = "System",
        ["s_anim"] = "Animations", ["s_anim_d"] = "Smooth motion in the app. Turn off on slow computers.",
        ["s_win"] = "Start with Windows", ["s_win_d"] = "Open the app automatically when you sign in.",
        ["s_min"] = "Start minimized", ["s_min_d"] = "Start in the system tray without opening the window.",
        ["s_tray"] = "Minimize to tray on close", ["s_tray_d"] = "Keep sharing in the background when you close the window.",
        ["s_open"] = "Open the phone app automatically", ["s_open_d"] = "Launch the app on your phone when it is ready.",
        ["s_inst"] = "Install / update the phone app", ["s_inst_d"] = "Install the bundled phone app over the cable when needed.",
        ["s_relay"] = "Start sharing automatically", ["s_relay_d"] = "Start sharing as soon as the phone is ready.",
        ["s_adb"] = "Restart adb", ["s_adb_d"] = "Fixes most detection problems.",
        ["s_reinst"] = "Reinstall phone app", ["s_reinst_d"] = "Install the bundled phone app again.",
        ["s_tools"] = "Open tools folder", ["s_tools_d"] = "Where adb and the relay are stored.",
        ["s_diag"] = "Copy diagnostics", ["s_diag_d"] = "Copy technical info to send to support.",
        ["s_reset"] = "Reset settings", ["s_reset_d"] = "Restore all settings to their defaults.",
        ["s_reset_q"] = "Reset all settings to defaults?",
        ["b_run"] = "Run", ["b_open"] = "Open", ["b_copy"] = "Copy", ["b_reset"] = "Reset", ["copied"] = "Copied to clipboard",
        ["a_t"] = "About", ["a_desc"] = "Cable Internet gives your Android phone the internet of your computer through a USB cable. No Wi-Fi, no hotspot, no mobile data.",
        ["a_ver"] = "Version", ["a_feat"] = "Features",
        ["a_f"] = "One-click automatic setup\nWorks over any USB data cable\nInstalls the phone app for you\nStep-by-step USB debugging help for every brand\nLive status, English and Persian, dark and light themes",
        ["a_how"] = "How it works",
        ["a_h"] = "This program talks to your phone through adb.\nThe phone app creates a local VPN that sends traffic into the cable.\nThe sharing component on this PC forwards it to the internet.",
        ["a_priv"] = "Privacy",
        ["a_priv_t"] = "We collect nothing. No accounts, no analytics, no ads. Your phone's traffic only travels through the USB cable to your own computer and is not stored.",
        ["a_oss"] = "Open-source components",
        ["a_oss_t"] = "Gnirehtet relay by Genymobile (Apache License 2.0)\nAndroid Platform Tools / adb by Google (Apache License 2.0)",
        ["a_lic"] = "Open licenses", ["a_sys"] = "System", ["a_contact"] = "Contact", ["a_tg"] = "Telegram", ["a_mail"] = "Email", ["a_web"] = "Website",
        ["tray_show"] = "Show", ["tray_exit"] = "Exit",
        ["running"] = "The app is already running.",
        ["phase_log"] = "Status",
    };

    static readonly Dictionary<string, string> Fa = new()
    {
        ["app"] = "کابل اینترنت",
        ["tagline"] = "اینترنت کامپیوتر را با کابل USB به گوشی بده",
        ["designed"] = "طراحی شده توسط Mr.PC",
        ["nav0"] = "خانه", ["nav1"] = "راهنمای USB", ["nav2"] = "تنظیمات", ["nav3"] = "درباره",
        ["home_t"] = "اتصال", ["home_s"] = "همه‌چیز خودکار است. فقط گوشی را وصل کن.",
        ["ph_start_t"] = "در حال شروع…", ["ph_start_s"] = "آماده‌سازی ابزارها",
        ["ph_tools_t"] = "فایل‌های برنامه ناقص است", ["ph_tools_s"] = "پوشهٔ tools کنار برنامه پیدا نشد. برنامه را دوباره نصب کن.",
        ["ph_nodev_t"] = "گوشی را با کابل USB وصل کن", ["ph_nodev_s"] = "بعد از وصل کردن چند ثانیه صبر کن. اگر USB Debugging خاموش است، راهنمای USB را باز کن.",
        ["ph_nodev2_t"] = "گوشی پیدا نشد", ["ph_nodev2_s"] = "• از کابل داده استفاده کن (نه فقط شارژ)\n• قفل گوشی را باز کن و حالت USB را روی «انتقال فایل» بگذار\n• USB Debugging باید روشن باشد",
        ["ph_auth_t"] = "روی گوشی تأیید کن", ["ph_auth_s"] = "پیام «Allow USB debugging?» روی گوشی آمده. تیک «Always allow» را بزن و Allow را بزن. اگر ندیدی، صفحه را روشن کن یا نوار اعلان را بکش پایین.",
        ["ph_off_t"] = "گوشی آماده نیست", ["ph_off_s"] = "کابل را بکش و دوباره وصل کن. اگر درست نشد، USB Debugging را یک بار خاموش و روشن کن.",
        ["ph_prep_t"] = "در حال آماده‌سازی گوشی…", ["ph_prep_s"] = "اپ روی گوشی نصب می‌شود. اگر گوشی تأیید خواست، قبول کن.",
        ["ph_fail_t"] = "مشکلی پیش آمد",
        ["ph_ready_t"] = "گوشی آماده است", ["ph_ready_s"] = "برای دادن اینترنت به گوشی «شروع اشتراک» را بزن.",
        ["ph_wait_t"] = "حالا روی گوشی دکمهٔ اتصال را بزن", ["ph_wait_s"] = "اپ «کابل اینترنت» روی گوشی باز شده. دکمهٔ بزرگ اتصال را بزن و درخواست VPN را تأیید کن.",
        ["ph_conn_t"] = "متصل است", ["ph_conn_s"] = "گوشی از اینترنت این کامپیوتر استفاده می‌کند. کابل را وصل نگه دار و برنامه را باز بگذار.",
        ["b_guide"] = "راهنمای USB", ["b_retry"] = "تلاش دوباره", ["b_start"] = "شروع اشتراک", ["b_restart"] = "اتصال مجدد",
        ["st0"] = "اتصال گوشی", ["st1"] = "تأیید USB Debugging", ["st2"] = "آماده‌سازی اپ", ["st3"] = "اتصال اینترنت",
        ["tl_phone"] = "گوشی", ["tl_none"] = "متصل نیست", ["tl_share"] = "اشتراک", ["tl_on"] = "در حال اجرا", ["tl_off"] = "متوقف",
        ["tl_sess"] = "مدت اتصال", ["tl_port"] = "درگاه رله",
        ["qa_open"] = "باز کردن اپ در گوشی", ["qa_reinst"] = "نصب مجدد اپ گوشی", ["qa_log"] = "گزارش فنی", ["qa_hide"] = "بستن گزارش",
        ["e_apk"] = "فایل اپ گوشی (tools\\app.apk) پیدا نشد. اپ را از فروشگاه روی گوشی نصب کن و دوباره تلاش کن.",
        ["e_restricted"] = "گوشی اجازهٔ نصب از طریق USB را نمی‌دهد. در گزینه‌های توسعه‌دهنده «Install via USB» را روشن کن (در شیائومی «USB debugging (Security settings)» هم)، بعد «تلاش دوباره» را بزن.",
        ["e_cancel"] = "نصب روی گوشی لغو شد. دوباره تلاش کن و پیام نصب را روی گوشی تأیید کن.",
        ["e_storage"] = "حافظهٔ گوشی پر است. کمی فضا آزاد کن و دوباره تلاش کن.",
        ["e_install"] = "نصب اپ روی گوشی انجام نشد. اپ را از فروشگاه روی گوشی نصب کن و دوباره تلاش کن. (جزئیات در گزارش فنی)",
        ["e_notinst"] = "اپ روی گوشی نصب نیست و نصب خودکار در تنظیمات خاموش است.",
        ["e_reverse"] = "ساخت مسیر USB ناموفق بود. کابل را بکش و دوباره وصل کن.",
        ["e_relay"] = "بخش اشتراک اینترنت اجرا نشد. شاید آنتی‌ویروس فایل tools\\gnirehtet.exe را مسدود کرده. آن را مجاز کن و برنامه را دوباره باز کن.",
        ["g_t"] = "راهنمای روشن کردن USB Debugging", ["g_s"] = "فقط یک بار لازم است.", ["g_status"] = "وضعیت گوشی",
        ["g_check"] = "بررسی دوباره",
        ["gt0"] = "همه گوشی‌ها", ["gt1"] = "Samsung", ["gt2"] = "Xiaomi / Redmi / Poco", ["gt3"] = "Huawei / Honor", ["gt4"] = "Oppo / Realme / OnePlus", ["gt5"] = "Vivo / iQOO", ["gt6"] = "گوشی دیده نمی‌شود؟",
        ["g0"] = "در گوشی وارد «تنظیمات» شو و به «درباره گوشی» (About phone) برو.\nهفت بار پشت سر هم روی «شماره بیلد» (Build number) بزن و در صورت نیاز رمز را وارد کن. پیام «شما اکنون توسعه‌دهنده هستید» می‌آید.\nبه تنظیمات برگرد و «گزینه‌های توسعه‌دهنده» (Developer options) را باز کن؛ معمولاً داخل «سیستم» یا «تنظیمات اضافی» یا پایین‌ترین بخش است.\nگزینهٔ «USB debugging» را روشن کن و تأیید بزن.\nگوشی را به کامپیوتر وصل کن. وقتی «Allow USB debugging?» آمد، تیک Always allow را بزن و Allow را بزن.\nنام گزینه‌ها در مدل‌های مختلف کمی فرق دارد. این کار فقط یک بار لازم است.",
        ["g1"] = "تنظیمات ← درباره گوشی ← اطلاعات نرم‌افزار (Software information).\nهفت بار روی «شماره بیلد» بزن و رمز را وارد کن.\nبرگرد: «گزینه‌های توسعه‌دهنده» پایین تنظیمات ساخته شده.\n«USB debugging» را روشن کن.\nگوشی را وصل کن و پیام Allow را تأیید کن.\nاگر در One UI جدید کار نکرد: تنظیمات ← امنیت و حریم خصوصی ← Auto Blocker را خاموش کن (یا «Block commands via USB cable»).",
        ["g2"] = "تنظیمات ← درباره گوشی.\nهفت بار روی «نسخه MIUI» (در HyperOS «نسخه OS») بزن.\nتنظیمات ← تنظیمات اضافی ← گزینه‌های توسعه‌دهنده.\nاین‌ها را روشن کن: USB debugging و Install via USB و (اگر هست) USB debugging (Security settings).\nگوشی را وصل کن و پیام Allow را تأیید کن.\nبدون «Install via USB» شیائومی نصب اپ را رد می‌کند. بعضی مدل‌ها برای روشن کردنش حساب Mi و اینترنت می‌خواهند.",
        ["g3"] = "تنظیمات ← درباره گوشی.\nهفت بار روی «شماره بیلد» بزن.\nتنظیمات ← سیستم و به‌روزرسانی ← گزینه‌های توسعه‌دهنده.\nUSB debugging را روشن کن (و اگر هست «Allow ADB debugging in charge only mode»).\nگوشی را وصل کن، حالت USB را روی «انتقال فایل» بگذار و Allow را تأیید کن.",
        ["g4"] = "تنظیمات ← درباره دستگاه (About device) ← نسخه (Version).\nهفت بار روی «شماره بیلد» بزن.\nتنظیمات ← تنظیمات اضافی ← گزینه‌های توسعه‌دهنده.\nUSB debugging را روشن کن.\nگوشی را وصل کن و پیام Allow را تأیید کن.\nاگر نصب اپ رد شد، در همان صفحه «Install via USB» یا «Disable permission monitoring» را روشن کن.",
        ["g5"] = "تنظیمات ← تنظیمات بیشتر (یا سیستم) ← درباره گوشی.\nهفت بار روی «نسخه نرم‌افزار» یا «شماره بیلد» بزن.\nبرگرد و «گزینه‌های توسعه‌دهنده» را باز کن.\nUSB debugging را روشن کن.\nگوشی را وصل کن و پیام Allow را تأیید کن.",
        ["g6"] = "کابل: بعضی کابل‌ها فقط شارژ می‌کنند. از کابل اصلی داده استفاده کن.\nپورت: مستقیم به کامپیوتر بزن، نه به هاب.\nصفحه: گوشی را باز نگه دار. نوار اعلان را بکش پایین و حالت USB را روی «انتقال فایل» (MTP) بگذار.\nپیام Allow نمی‌آید: در گزینه‌های توسعه‌دهنده «Revoke USB debugging authorizations» را بزن، USB debugging را خاموش و روشن کن و کابل را دوباره وصل کن.\nدرایور: ویندوز را آپدیت کن (Windows Update). اگر نشد، برنامهٔ رسمی سازندهٔ گوشی (مثل Samsung Smart Switch) را یک بار نصب کن تا درایور نصب شود.\nبرنامه‌های دیگر: Mi PC Suite و HiSuite و Smart Switch و Android Studio را ببند؛ ممکن است adb را اشغال کنند.\nباز هم نشد؟ گوشی و کامپیوتر را ری‌استارت کن.",
        ["s_t"] = "تنظیمات", ["s_s"] = "برنامه را به سلیقهٔ خودت تنظیم کن.",
        ["sg0"] = "عمومی", ["sg1"] = "شروع و نوار وظیفه", ["sg2"] = "اتصال", ["sg3"] = "نگهداری",
        ["s_lang"] = "زبان", ["s_lang_d"] = "حالت خودکار از زبان ویندوز پیروی می‌کند.",
        ["o_auto"] = "خودکار", ["o_en"] = "English", ["o_fa"] = "فارسی",
        ["s_theme"] = "پوسته", ["s_theme_d"] = "تیره، روشن یا مطابق ویندوز.",
        ["o_dark"] = "تیره", ["o_light"] = "روشن", ["o_sys"] = "سیستم",
        ["s_anim"] = "انیمیشن‌ها", ["s_anim_d"] = "حرکت نرم در برنامه. روی کامپیوتر ضعیف خاموش کن.",
        ["s_win"] = "اجرا با ویندوز", ["s_win_d"] = "برنامه با ورود به ویندوز خودکار باز شود.",
        ["s_min"] = "شروع به‌صورت کوچک‌شده", ["s_min_d"] = "بدون باز شدن پنجره در نوار وظیفه شروع شود.",
        ["s_tray"] = "با بستن، به نوار وظیفه برود", ["s_tray_d"] = "با بستن پنجره، اشتراک در پس‌زمینه ادامه دارد.",
        ["s_open"] = "باز کردن خودکار اپ گوشی", ["s_open_d"] = "وقتی گوشی آماده شد اپ روی آن باز شود.",
        ["s_inst"] = "نصب / به‌روزرسانی اپ گوشی", ["s_inst_d"] = "در صورت نیاز اپ همراه برنامه با کابل نصب شود.",
        ["s_relay"] = "شروع خودکار اشتراک", ["s_relay_d"] = "به‌محض آماده شدن گوشی اشتراک شروع شود.",
        ["s_adb"] = "ری‌استارت adb", ["s_adb_d"] = "بیشتر مشکلات شناسایی گوشی را حل می‌کند.",
        ["s_reinst"] = "نصب مجدد اپ گوشی", ["s_reinst_d"] = "اپ همراه برنامه دوباره نصب شود.",
        ["s_tools"] = "باز کردن پوشهٔ tools", ["s_tools_d"] = "محل adb و رله.",
        ["s_diag"] = "کپی اطلاعات فنی", ["s_diag_d"] = "اطلاعات فنی را برای پشتیبانی کپی کن.",
        ["s_reset"] = "بازنشانی تنظیمات", ["s_reset_d"] = "همهٔ تنظیمات به حالت اولیه برگردد.",
        ["s_reset_q"] = "همهٔ تنظیمات به حالت اولیه برگردد؟",
        ["b_run"] = "اجرا", ["b_open"] = "باز کردن", ["b_copy"] = "کپی", ["b_reset"] = "بازنشانی", ["copied"] = "در کلیپ‌بورد کپی شد",
        ["a_t"] = "درباره", ["a_desc"] = "«کابل اینترنت» اینترنت کامپیوتر را از طریق کابل USB به گوشی اندروید می‌رساند. بدون وای‌فای، بدون هات‌اسپات، بدون اینترنت سیم‌کارت.",
        ["a_ver"] = "نسخه", ["a_feat"] = "امکانات",
        ["a_f"] = "راه‌اندازی خودکار با یک کلیک\nکار با هر کابل USB داده\nنصب خودکار اپ روی گوشی\nراهنمای قدم‌به‌قدم USB Debugging برای هر برند\nوضعیت زنده، فارسی و انگلیسی، پوستهٔ تیره و روشن",
        ["a_how"] = "نحوهٔ کار",
        ["a_h"] = "این برنامه از طریق adb با گوشی ارتباط می‌گیرد.\nاپ گوشی یک VPN محلی می‌سازد و ترافیک را داخل کابل می‌فرستد.\nبخش اشتراک روی این کامپیوتر آن را به اینترنت می‌رساند.",
        ["a_priv"] = "حریم خصوصی",
        ["a_priv_t"] = "ما هیچ اطلاعاتی جمع نمی‌کنیم. بدون حساب کاربری، بدون آمار، بدون تبلیغ. ترافیک گوشی فقط از کابل USB به کامپیوتر خودت می‌رسد و ذخیره نمی‌شود.",
        ["a_oss"] = "اجزای متن‌باز",
        ["a_oss_t"] = "رلهٔ Gnirehtet از Genymobile (مجوز Apache 2.0)\nAndroid Platform Tools / adb از Google (مجوز Apache 2.0)",
        ["a_lic"] = "باز کردن مجوزها", ["a_sys"] = "سیستم", ["a_contact"] = "تماس", ["a_tg"] = "تلگرام", ["a_mail"] = "ایمیل", ["a_web"] = "وب‌سایت",
        ["tray_show"] = "نمایش", ["tray_exit"] = "خروج",
        ["running"] = "برنامه از قبل در حال اجراست.",
        ["phase_log"] = "وضعیت",
    };
}
