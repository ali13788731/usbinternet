namespace CableInternet;

/// <summary>Step-by-step help for turning on USB debugging, with a tab for each popular phone brand.</summary>
sealed class UsbDebugGuide : Form
{
    readonly Label _live = new();
    readonly System.Windows.Forms.Timer _timer = new() { Interval = 1000 };
    readonly Func<string> _status;

    static readonly (string Tab, string Text)[] Pages =
    {
        ("همه گوشی‌ها",
@"این کار فقط یک بار لازم است.

۱. در گوشی وارد «تنظیمات» (Settings) شو.
۲. به «درباره گوشی» (About phone) برو.
۳. هفت بار پشت سر هم روی «شماره بیلد» (Build number) بزن. اگر رمز یا الگو خواست وارد کن. پیام «شما اکنون توسعه‌دهنده هستید» می‌آید.
۴. به تنظیمات برگرد و «گزینه‌های توسعه‌دهنده» (Developer options) را باز کن. معمولاً داخل «سیستم» (System) یا «تنظیمات اضافی» (Additional settings) یا پایین‌ترین بخش تنظیمات است.
۵. گزینه‌ی «USB debugging» را روشن کن و تأیید بزن.
۶. گوشی را با کابل به کامپیوتر وصل کن. روی گوشی پیام «Allow USB debugging?» می‌آید. تیک «Always allow» را بزن و روی Allow بزن.

نام گزینه‌ها در هر مدل گوشی ممکن است کمی فرق کند. برند گوشی خودت را از تب‌های بالا هم ببین."),

        ("Samsung",
@"۱. تنظیمات ← درباره گوشی (About phone) ← اطلاعات نرم‌افزار (Software information).
۲. هفت بار روی «شماره بیلد» (Build number) بزن و رمز گوشی را وارد کن.
۳. به تنظیمات برگرد؛ پایین‌ترین بخش «گزینه‌های توسعه‌دهنده» (Developer options) ساخته شده.
۴. «USB debugging» را روشن کن.
۵. گوشی را وصل کن و پیام Allow را تأیید کن (تیک Always allow).

اگر گوشی پیام Allow نداد یا بعد از وصل شدن خطا داد، در نسخه‌های جدید One UI ویژگی «Auto Blocker» دستورهای USB را مسدود می‌کند:
تنظیمات ← امنیت و حریم خصوصی (Security and privacy) ← Auto Blocker ← خاموش یا گزینه‌ی «Block commands via USB cable» را خاموش کن."),

        ("Xiaomi / Redmi / Poco",
@"۱. تنظیمات ← درباره گوشی (About phone).
۲. هفت بار روی «نسخه MIUI» یا «نسخه HyperOS» (MIUI / OS version) بزن.
۳. تنظیمات ← تنظیمات اضافی (Additional settings) ← گزینه‌های توسعه‌دهنده (Developer options).
۴. این سه گزینه را روشن کن (هر کدام که در گوشی تو هست):
   • USB debugging
   • Install via USB (نصب از طریق USB)
   • USB debugging (Security settings) (دیباگ USB، تنظیمات امنیتی)
۵. گوشی را وصل کن و پیام Allow را تأیید کن.

نکته: در شیائومی بدون روشن بودن «Install via USB»، نصب خودکار اپ روی گوشی رد می‌شود. بعضی مدل‌ها برای روشن کردن آن به حساب Mi و اینترنت نیاز دارند."),

        ("Huawei / Honor",
@"۱. تنظیمات ← درباره گوشی (About phone).
۲. هفت بار روی «شماره بیلد» (Build number) بزن.
۳. تنظیمات ← سیستم و به‌روزرسانی (System & updates) ← گزینه‌های توسعه‌دهنده (Developer options).
۴. «USB debugging» را روشن کن.
۵. اگر گزینه‌ی «Allow ADB debugging in charge only mode» هست، آن را هم روشن کن.
۶. گوشی را وصل کن. حالت USB را روی «انتقال فایل» (Transfer files) بگذار و پیام Allow را تأیید کن."),

        ("Oppo / Realme / OnePlus",
@"۱. تنظیمات ← درباره دستگاه (About device) ← نسخه (Version).
۲. هفت بار روی «شماره بیلد» (Build number) بزن.
۳. تنظیمات ← تنظیمات اضافی (Additional settings) ← گزینه‌های توسعه‌دهنده (Developer options).
۴. «USB debugging» را روشن کن.
۵. گوشی را وصل کن و پیام Allow را تأیید کن.

اگر نصب اپ روی گوشی رد شد، در همان بخش گزینه‌هایی مثل «Install via USB» یا «Disable permission monitoring» را روشن کن."),

        ("Vivo / iQOO",
@"۱. تنظیمات ← تنظیمات بیشتر (More settings) یا سیستم ← درباره گوشی (About phone).
۲. هفت بار روی «نسخه نرم‌افزار» (Software version) یا «شماره بیلد» بزن.
۳. به تنظیمات برگرد ← گزینه‌های توسعه‌دهنده (Developer options).
۴. «USB debugging» را روشن کن.
۵. گوشی را وصل کن و پیام Allow را تأیید کن."),

        ("گوشی دیده نمی‌شود؟",
@"این موارد را به ترتیب بررسی کن:

• کابل: بعضی کابل‌ها فقط شارژ می‌کنند. از کابل اصلی گوشی استفاده کن.
• پورت: گوشی را مستقیم به پورت USB پشت یا کنار کیس بزن، نه به هاب.
• قفل صفحه: صفحه‌ی گوشی را روشن و بازشده نگه دار.
• حالت USB: وقتی کابل وصل شد، از نوار اعلان گوشی حالت را روی «انتقال فایل» (File transfer / MTP) بگذار.
• پیام Allow پیدا نمی‌شود: در گزینه‌های توسعه‌دهنده «Revoke USB debugging authorizations» را بزن، USB debugging را خاموش و دوباره روشن کن، کابل را دربیاور و دوباره بزن.
• درایور: ویندوز را به‌روز کن (Windows Update) تا درایور خودکار نصب شود. اگر نشد، برنامه‌ی رسمی برند گوشی (مثل Samsung Smart Switch) را نصب کن تا درایورش نصب شود. بعد برنامه‌ی خودش را ببند.
• برنامه‌های دیگر: برنامه‌هایی مثل Mi PC Suite، HiSuite، Smart Switch و Android Studio ممکن است adb را اشغال کنند. آن‌ها را ببند.
• اگر باز هم دیده نشد، گوشی و کامپیوتر را یک بار ری‌استارت کن."),
    };

    public UsbDebugGuide(Func<string> status)
    {
        _status = status;
        AutoScaleDimensions = new SizeF(96f, 96f);
        AutoScaleMode = AutoScaleMode.Dpi;
        Text = "راهنمای روشن کردن USB Debugging";
        RightToLeft = RightToLeft.Yes;
        RightToLeftLayout = false;
        Font = new Font("Segoe UI", 10f);
        StartPosition = FormStartPosition.CenterParent;
        ClientSize = new Size(560, 600);
        MinimumSize = new Size(480, 480);
        BackColor = Color.White;

        var tabs = new TabControl
        {
            Dock = DockStyle.Fill,
            RightToLeft = RightToLeft.Yes,
            RightToLeftLayout = false,
            Multiline = true,
            Padding = new Point(10, 4),
        };
        foreach (var (tab, text) in Pages)
        {
            var page = new TabPage(tab) { BackColor = Color.White, RightToLeft = RightToLeft.Yes };
            var box = new RichTextBox
            {
                Dock = DockStyle.Fill,
                ReadOnly = true,
                BorderStyle = BorderStyle.None,
                BackColor = Color.White,
                Font = new Font("Segoe UI", 11f),
                RightToLeft = RightToLeft.Yes,
                Text = text.Replace("\r\n", "\n"),
                TabStop = false,
            };
            page.Padding = new Padding(12);
            page.Controls.Add(box);
            tabs.TabPages.Add(page);
        }

        var bottom = new Panel { Dock = DockStyle.Bottom, Height = 64, BackColor = Color.FromArgb(245, 247, 250) };
        _live.AutoSize = false;
        _live.Dock = DockStyle.Fill;
        _live.TextAlign = ContentAlignment.MiddleLeft;
        _live.Padding = new Padding(14, 0, 14, 0);
        _live.Font = new Font("Segoe UI", 10.5f, FontStyle.Bold);
        var close = new Button
        {
            Text = "بستن",
            Dock = DockStyle.Left,
            Width = 90,
            FlatStyle = FlatStyle.System,
        };
        close.Click += (_, _) => Close();
        bottom.Controls.Add(_live);
        bottom.Controls.Add(close);

        Controls.Add(tabs);
        Controls.Add(bottom);

        _timer.Tick += (_, _) => _live.Text = _status();
        _timer.Start();
        _live.Text = _status();
        FormClosed += (_, _) => _timer.Dispose();
    }
}
