using System.Diagnostics;

namespace CableInternet;

sealed class MainForm : Form
{
    // ---- look ----
    static readonly Color Bg = Color.FromArgb(244, 246, 250);
    static readonly Color Ink = Color.FromArgb(28, 34, 48);
    static readonly Color Muted = Color.FromArgb(104, 114, 134);
    static readonly Color Accent = Color.FromArgb(37, 99, 235);

    readonly Label _icon = new();
    readonly Label _title = new();
    readonly Label _sub = new();
    readonly Panel _card = new();
    readonly Label[] _steps = new Label[4];
    readonly Button _btnGuide = new();
    readonly Button _btnRetry = new();
    readonly TextBox _log = new();
    readonly LinkLabel _logLink = new();
    readonly System.Windows.Forms.Timer _timer = new() { Interval = 1500 };

    // ---- state ----
    readonly RelayManager _relay = new();
    bool _busy;
    string? _preparedSerial;
    string? _failedSerial;
    string _failMessage = "";
    int _noDeviceTicks;
    int _tick;
    bool _guideShown;
    UsbDebugGuide? _guide;
    string _statusLine = "";

    public MainForm()
    {
        AutoScaleDimensions = new SizeF(96f, 96f);
        AutoScaleMode = AutoScaleMode.Dpi;
        Text = "کابل اینترنت";
        RightToLeft = RightToLeft.Yes;
        RightToLeftLayout = false;
        Font = new Font("Segoe UI", 10f);
        BackColor = Bg;
        ForeColor = Ink;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(480, 640);
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

        BuildUi();

        Shown += async (_, _) => await InitAsync();
        FormClosing += OnClosing;
    }

    // ------------------------------------------------------------------ UI

    void BuildUi()
    {
        const int W = 432, X = 24;
        var header = new Label
        {
            Text = "کابل اینترنت",
            Font = new Font("Segoe UI", 20f, FontStyle.Bold),
            AutoSize = false,
            Location = new Point(X, 18),
            Size = new Size(W, 42),
        };
        var tagline = new Label
        {
            Text = "اینترنت کامپیوتر را با کابل USB به گوشی بده",
            ForeColor = Muted,
            AutoSize = false,
            Location = new Point(X, 60),
            Size = new Size(W, 24),
        };

        _card.Location = new Point(X, 98);
        _card.Size = new Size(W, 170);
        _card.BorderStyle = BorderStyle.None;
        _icon.Font = new Font("Segoe UI Emoji", 26f);
        _icon.AutoSize = false;
        _icon.TextAlign = ContentAlignment.MiddleCenter;
        _icon.Location = new Point(W - 16 - 64, 18);
        _icon.Size = new Size(64, 64);
        _title.Font = new Font("Segoe UI", 14f, FontStyle.Bold);
        _title.AutoSize = false;
        _title.Location = new Point(16, 22);
        _title.Size = new Size(W - 16 - 64 - 28, 56);
        _title.TextAlign = ContentAlignment.MiddleLeft; // mirrored by RightToLeft: shows on the right
        _sub.AutoSize = false;
        _sub.Location = new Point(16, 88);
        _sub.Size = new Size(W - 32, 74);
        _sub.ForeColor = Color.FromArgb(55, 65, 85);
        _card.Controls.AddRange(new Control[] { _icon, _title, _sub });

        var stepNames = new[]
        {
            "اتصال گوشی با کابل",
            "تأیید USB Debugging روی گوشی",
            "نصب و آماده‌سازی اپ روی گوشی",
            "اتصال اینترنت",
        };
        int y = 288;
        for (int i = 0; i < _steps.Length; i++)
        {
            _steps[i] = new Label
            {
                AutoSize = false,
                Location = new Point(X, y),
                Size = new Size(W, 28),
                TextAlign = ContentAlignment.MiddleLeft,
                Tag = stepNames[i],
            };
            Controls.Add(_steps[i]);
            y += 30;
        }

        _btnGuide.Text = "راهنمای روشن کردن USB Debugging";
        _btnGuide.Location = new Point(X, 420);
        _btnGuide.Size = new Size(W, 46);
        StyleButton(_btnGuide, primary: false);
        _btnGuide.Click += (_, _) => OpenGuide();

        _btnRetry.Text = "تلاش دوباره / اتصال مجدد";
        _btnRetry.Location = new Point(X, 474);
        _btnRetry.Size = new Size(W, 42);
        StyleButton(_btnRetry, primary: false);
        _btnRetry.Click += (_, _) => Retry();

        var about = new LinkLabel
        {
            Text = "درباره",
            AutoSize = false,
            Location = new Point(X, 534),
            Size = new Size(80, 24),
            TextAlign = ContentAlignment.MiddleRight,
            LinkColor = Muted,
        };
        about.LinkClicked += (_, _) => ShowAbout();

        _logLink.Text = "نمایش گزارش فنی";
        _logLink.AutoSize = false;
        _logLink.TextAlign = ContentAlignment.MiddleLeft;
        _logLink.LinkColor = Muted;
        _logLink.Location = new Point(X + W - 160, 534);
        _logLink.Size = new Size(160, 24);
        _logLink.LinkClicked += (_, _) =>
        {
            _log.Visible = !_log.Visible;
            _logLink.Text = _log.Visible ? "پنهان کردن گزارش" : "نمایش گزارش فنی";
        };

        _log.Multiline = true;
        _log.ReadOnly = true;
        _log.ScrollBars = ScrollBars.Vertical;
        _log.Location = new Point(X, 562);
        _log.Size = new Size(W, 64);
        _log.Font = new Font("Consolas", 8.5f);
        _log.RightToLeft = RightToLeft.No;
        _log.Visible = false;

        Controls.AddRange(new Control[] { header, tagline, _card, _btnGuide, _btnRetry, about, _logLink, _log });
        foreach (var l in _steps) l.Font = new Font("Segoe UI", 10.5f);
        SetSteps(0);
        SetStatus("⏳", Color.FromArgb(230, 240, 255), "در حال شروع…", "لطفاً چند لحظه صبر کن");
    }

    static void StyleButton(Button b, bool primary)
    {
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = primary ? 0 : 1;
        b.FlatAppearance.BorderColor = Color.FromArgb(200, 208, 222);
        b.BackColor = primary ? Accent : Color.White;
        b.ForeColor = primary ? Color.White : Ink;
        b.Font = new Font("Segoe UI", 10.5f, primary ? FontStyle.Bold : FontStyle.Regular);
        b.Cursor = Cursors.Hand;
    }

    void SetStatus(string icon, Color tint, string title, string sub)
    {
        _card.BackColor = tint;
        _icon.Text = icon;
        _icon.BackColor = tint;
        _title.Text = title;
        _title.BackColor = tint;
        _sub.Text = sub;
        _sub.BackColor = tint;
        _statusLine = title;
    }

    /// <summary>done = number of finished steps (0-4); the next step is shown as in progress.</summary>
    void SetSteps(int done, bool waiting = true)
    {
        for (int i = 0; i < _steps.Length; i++)
        {
            string name = (string)_steps[i].Tag!;
            if (i < done)
            {
                _steps[i].Text = "✔  " + name;
                _steps[i].ForeColor = Color.FromArgb(22, 128, 61);
            }
            else if (i == done && waiting)
            {
                _steps[i].Text = "●  " + name;
                _steps[i].ForeColor = Color.FromArgb(180, 110, 0);
            }
            else
            {
                _steps[i].Text = "○  " + name;
                _steps[i].ForeColor = Muted;
            }
        }
    }

    void Highlight(bool guide) => StyleButton(_btnGuide, primary: guide);

    static readonly Color Gray = Color.FromArgb(236, 240, 246);
    static readonly Color Amber = Color.FromArgb(255, 244, 214);
    static readonly Color Red = Color.FromArgb(253, 232, 232);
    static readonly Color Green = Color.FromArgb(220, 246, 230);
    static readonly Color Blue = Color.FromArgb(226, 238, 255);

    // ------------------------------------------------------------------ logic

    async Task InitAsync()
    {
        if (!Paths.ToolsPresent)
        {
            SetStatus("⚠", Red, "فایل‌های برنامه ناقص است",
                "پوشه‌ی tools کنار برنامه پیدا نشد. برنامه را دوباره نصب یا از حالت فشرده کامل خارج کن.");
            SetSteps(0, false);
            return;
        }
        await Adb.StartServerAsync();
        _timer.Tick += async (_, _) => await PollAsync();
        _timer.Start();
        await PollAsync();
    }

    async Task PollAsync()
    {
        if (_busy) return;
        _busy = true;
        _tick++;
        try
        {
            var dev = await Adb.GetDeviceAsync();
            switch (dev.State)
            {
                case DeviceState.None:
                    ResetDevice();
                    ShowNoDevice();
                    break;
                case DeviceState.Unauthorized:
                    ResetDevice();
                    _noDeviceTicks = 0;
                    SetSteps(1);
                    Highlight(true);
                    SetStatus("📱", Amber, "روی گوشی تأیید کن",
                        "یک پیام «Allow USB debugging?» روی صفحه‌ی گوشی آمده. تیک «Always allow» را بزن و روی Allow بزن.\n" +
                        "اگر پیامی نمی‌بینی، صفحه‌ی گوشی را روشن کن یا نوار اعلان را بکش پایین.");
                    break;
                case DeviceState.Offline:
                    ResetDevice();
                    SetSteps(1);
                    Highlight(true);
                    SetStatus("🔌", Amber, "گوشی آماده نیست",
                        "کابل را از گوشی دربیاور و دوباره وصل کن. اگر درست نشد، USB Debugging را یک بار خاموش و روشن کن.");
                    break;
                case DeviceState.Ready:
                    _noDeviceTicks = 0;
                    await HandleReadyAsync(dev.Serial!);
                    break;
            }
        }
        catch (Exception ex)
        {
            _relay.AddLog("poll error: " + ex.Message);
        }
        finally
        {
            _log.Text = _relay.LogText;
            _busy = false;
        }
    }

    void ResetDevice()
    {
        _preparedSerial = null;
        _failedSerial = null;
    }

    void ShowNoDevice()
    {
        _noDeviceTicks++;
        SetSteps(0);
        Highlight(true);
        if (_noDeviceTicks < 6)
        {
            SetStatus("🔌", Blue, "گوشی را با کابل USB وصل کن",
                "بعد از وصل کردن چند ثانیه صبر کن. اگر USB Debugging روشن نیست، دکمه‌ی راهنما را بزن.");
        }
        else
        {
            SetStatus("🔍", Amber, "گوشی پیدا نشد",
                "• از کابلی استفاده کن که داده هم منتقل می‌کند\n" +
                "• صفحه‌ی گوشی را باز کن و حالت USB را روی «انتقال فایل» بگذار\n" +
                "• USB Debugging باید روشن باشد (دکمه‌ی راهنما)");
        }
        if (_noDeviceTicks == 4 && !_guideShown) OpenGuide();
    }

    async Task HandleReadyAsync(string serial)
    {
        if (_failedSerial == serial)
        {
            ShowFailure();
            return;
        }

        if (_preparedSerial != serial)
        {
            await PrepareAsync(serial);
            return;
        }

        // Keep the relay alive and the tunnel registered.
        if (!_relay.Running) _relay.Start();
        if (_tick % 5 == 0 && !RelayManager.PhoneConnected() && !await Adb.HasReverseAsync(serial))
            await Adb.ReverseAsync(serial);

        if (RelayManager.PhoneConnected())
        {
            SetSteps(4, false);
            Highlight(false);
            SetStatus("✅", Green, "متصل است",
                "گوشی از اینترنت این کامپیوتر استفاده می‌کند.\nتا وقتی می‌خواهی اینترنت داشته باشی، کابل را وصل و این برنامه را باز بگذار.");
        }
        else
        {
            SetSteps(3);
            Highlight(false);
            SetStatus("👉", Blue, "حالا روی گوشی دکمه‌ی اتصال را بزن",
                "اپ «Cable Internet» روی گوشی باز شده. دکمه‌ی بزرگ اتصال را بزن و درخواست VPN را تأیید کن.\n" +
                "اگر اپ باز نیست، دکمه‌ی «تلاش دوباره» را بزن.");
        }
    }

    async Task PrepareAsync(string serial)
    {
        SetSteps(2);
        Highlight(false);
        SetStatus("⏳", Blue, "در حال آماده‌سازی گوشی…",
            "اپ روی گوشی نصب می‌شود. اگر گوشی پیامی نشان داد (مثلاً درخواست نصب)، آن را تأیید کن.");

        int bundled = Adb.BundledVersion();
        int? installed = await Adb.InstalledVersionAsync(serial);
        if (installed == null || installed < bundled)
        {
            var err = await Adb.InstallAppAsync(serial);
            if (err != null)
            {
                _relay.AddLog("install: " + err);
                Fail(serial, ExplainInstallError(err));
                return;
            }
        }

        if (!await Adb.ReverseAsync(serial))
        {
            Fail(serial, "ساخت مسیر USB ناموفق بود. کابل را دربیاور و دوباره وصل کن.");
            return;
        }

        if (!_relay.Start())
        {
            Fail(serial, "اجرای بخش اشتراک اینترنت ممکن نشد. احتمالاً آنتی‌ویروس فایل tools\\gnirehtet.exe را مسدود یا حذف کرده است. آن را مجاز کن و برنامه را دوباره باز کن.");
            return;
        }

        await Adb.LaunchAppAsync(serial);
        _preparedSerial = serial;
        _failedSerial = null;
    }

    static string ExplainInstallError(string err)
    {
        if (err == "MISSING_APK")
            return "فایل نصبی اپ گوشی (tools\\app.apk) پیدا نشد. اپ را از کافه‌بازار روی گوشی نصب کن و دوباره تلاش کن.";
        if (err.Contains("USER_RESTRICTED") || err.Contains("INSTALL_FAILED_USER_RESTRICTED"))
            return "گوشی اجازه‌ی نصب از طریق USB را نمی‌دهد. در گزینه‌های توسعه‌دهنده گزینه‌ی «Install via USB» (و در شیائومی «USB debugging (Security settings)») را روشن کن، بعد «تلاش دوباره» را بزن.";
        if (err.Contains("INSTALL_CANCELED") || err.Contains("cancel"))
            return "نصب روی گوشی لغو شد. دوباره تلاش کن و این بار پیام نصب روی گوشی را تأیید کن.";
        if (err.Contains("INSUFFICIENT_STORAGE"))
            return "حافظه‌ی گوشی پر است. کمی فضا آزاد کن و دوباره تلاش کن.";
        return "نصب اپ روی گوشی انجام نشد. اپ را از کافه‌بازار روی گوشی نصب کن و دوباره تلاش کن.\n(جزئیات در گزارش فنی)";
    }

    void Fail(string serial, string message)
    {
        _failedSerial = serial;
        _failMessage = message;
        ShowFailure();
    }

    void ShowFailure()
    {
        SetSteps(2, false);
        Highlight(false);
        SetStatus("⚠", Red, "مشکلی پیش آمد", _failMessage);
    }

    void Retry()
    {
        _failedSerial = null;
        _preparedSerial = null;
        _noDeviceTicks = 0;
        _relay.Stop();
    }

    void OpenGuide()
    {
        _guideShown = true;
        if (_guide is { IsDisposed: false })
        {
            _guide.Activate();
            return;
        }
        _guide = new UsbDebugGuide(() => "وضعیت: " + _statusLine);
        _guide.Show(this);
    }

    void ShowAbout()
    {
        MessageBox.Show(
            "کابل اینترنت — نسخه‌ی " + Application.ProductVersion.Split('+')[0] + "\n\n" +
            "اینترنت کامپیوتر را از طریق کابل USB به گوشی اندروید می‌رساند.\n\n" +
            "این برنامه از نرم‌افزارهای متن‌باز زیر استفاده می‌کند:\n" +
            "• Gnirehtet © Genymobile — Apache License 2.0\n" +
            "• Android Platform Tools (adb) © Google — Apache License 2.0\n\n" +
            "متن مجوزها در پوشه‌ی tools\\licenses کنار برنامه موجود است.",
            "درباره", MessageBoxButtons.OK, MessageBoxIcon.Information,
            MessageBoxDefaultButton.Button1, MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign);
    }

    void OnClosing(object? sender, FormClosingEventArgs e)
    {
        _timer.Stop();
        _relay.Stop();
        Adb.KillServer();
    }
}
