using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Windows.Forms;

namespace Peyvand;

static class AppBus
{
    public static Action<int>? Navigate;
    public static Action? LanguageChanged, ThemeChanged;
}

// ===================================================================================== HOME
sealed class HomePage : CanvasPage
{
    readonly Controller ctl;
    readonly RingItem ring = new();
    readonly System.Windows.Forms.Timer clock = new System.Windows.Forms.Timer { Interval = 1000 };
    bool showLog;
    ButtonItem? main;

    public HomePage(Controller c)
    {
        ctl = c;
        ctl.Changed += () => { Sync(); Invalidate(); };
        UpdateCheck.Changed += () => { if (IsHandleCreated) Rebuild(); };
        clock.Tick += (_, _) => { if (Visible) Invalidate(); };
        clock.Start();
    }

    void Sync()
    {
        ring.Mode = ctl.Mode;
        if (main != null) main.Enabled = ctl.Phase != Phase.Starting && ctl.Phase != Phase.Preparing;
    }

    string MainText() => Loc.T(ctl.Phase switch
    {
        Phase.NoDevice or Phase.Unauthorized or Phase.Offline => "b_guide",
        Phase.Failed => "b_retry",
        Phase.Ready => "b_start",
        Phase.Blocked => ctl.Block == BlockReason.Update ? "upd_b" : "b_sub",
        _ => "b_restart",
    });

    void MainClick()
    {
        switch (ctl.Phase)
        {
            case Phase.NoDevice: case Phase.Unauthorized: case Phase.Offline: AppBus.Navigate?.Invoke(2); break;
            case Phase.Ready: ctl.StartSharing(); break;
            case Phase.Blocked:
                if (ctl.Block == BlockReason.Update) Links.Open(UpdateCheck.Url.Length > 0 ? UpdateCheck.Url : Brand.PcDownloadUrl);
                else AppBus.Navigate?.Invoke(1);
                break;
            default: ctl.Retry(); break;
        }
    }

    protected override void Build()
    {
        int y = 0; Header(Loc.T("home_t"), Loc.T("home_s"), ref y);
        if (UpdateCheck.Available)
        {
            Add(new CardItem { B = R(0, y, IW, 60), Radius = 16, Tint = true });
            Add(new TextItem { Text = string.Format(Loc.T("upd_t"), UpdateCheck.LatestName), F = Ui.Font(10.5f, FontStyle.Bold), Color = Pal.Text, VMid = true, Wrap = false, Ellipsis = true, B = R(20, y, IW - 190, 60) });
            Add(new ButtonItem { Text = Loc.T("upd_b"), Icon = Ic.Refresh, Style = BtnStyle.Primary, B = R(IW - 156, y + 11, 140, 38), Click = () => Links.Open(UpdateCheck.Url.Length > 0 ? UpdateCheck.Url : Brand.PcDownloadUrl) });
            y += 76;
        }
        const int heroH = 226;
        Add(new CardItem { B = R(0, y, IW, heroH), Tint = true, Radius = 22 });
        ring.B = R(24, y + 28, 170, 170); items.Add(ring);
        double tx = 214, tw = IW - 214 - 24;
        Add(new TextItem { Provider = () => ctl.Title, F = Ui.Font(17, FontStyle.Bold), Color = Pal.Text, B = R(tx, y + 30, tw, 58), VMid = true });
        Add(new TextItem { Provider = () => ctl.Sub, F = Ui.Font(10), Color = Pal.Muted, B = R(tx, y + 92, tw, 82) });
        main = Add(new ButtonItem { Style = BtnStyle.Primary, Icon = Ic.Play, B = R(tx, y + 176, 210, 40) });
        main.Provider = MainText; main.Click = MainClick;
        Sync();
        y += heroH + 16;

        Add(new CardItem { B = R(0, y, IW, 124) });
        Add(new StepperItem { Labels = new[] { Loc.T("st0"), Loc.T("st1"), Loc.T("st2"), Loc.T("st3") }, StateOf = ctl.StepState, B = R(16, y + 14, IW - 32, 98) });
        y += 140;

        // ---- usage (exact, updates every second while the phone is connected)
        Add(new CardItem { B = R(0, y, IW, 128) });
        Add(new TextItem { Text = Loc.T("us_t"), F = Ui.Font(10.5f, FontStyle.Bold), Color = Pal.Text, Wrap = false, Ellipsis = true, B = R(20, y + 14, IW / 2, 24) });
        Add(new TextItem { Provider = () => !Usage.PhoneLinked && Usage.Used == 0 ? Loc.T("us_nolink") : Usage.ProRecent && Usage.Pro ? Loc.T("us_plan_pro") : Loc.T("us_plan_free"), F = Ui.Font(9.5f, FontStyle.Bold), ColorProvider = () => Usage.Pro ? Pal.Green : Pal.Muted, Alignment = Align.End, Wrap = false, Ellipsis = true, B = R(IW / 2, y + 16, IW / 2 - 20, 22) });
        Add(new TextItem { Provider = () => Usage.Pro ? Fmt.Mb(Usage.Used) + "  ·  " + Loc.T("us_unl") : Fmt.Mb(Usage.Used) + " / " + Fmt.Mb(Usage.Limit), F = Ui.Font(18, FontStyle.Bold), ColorProvider = () => UsageColor(), Wrap = false, Ellipsis = true, ForceLtr = !Loc.IsRtl, B = R(20, y + 42, IW - 40, 36) });
        Add(new BarItem { Value = () => Usage.Pro ? 0f : (float)Usage.Used / Usage.Limit, Accent = () => UsageColor(), B = R(20, y + 84, IW - 40, 10) });
        Add(new TextItem { Provider = () => Usage.Pro ? Loc.T("us_exact") : Fmt.Mb(Math.Max(0, Usage.Limit - Usage.Used)) + " " + Loc.T("us_left") + "   ·   " + string.Format(Loc.T("us_renew"), Jalali.DaysUntilRenewal(Ledger.Now())), F = Ui.Font(9), Color = Pal.Muted, Wrap = false, Ellipsis = true, B = R(20, y + 100, IW - 40, 20) });
        y += 144;

        double twid = (IW - 3 * 12) / 4.0;
        var tiles = new (Ic ic, string key, Func<string> val)[]
        {
            (Ic.Phone, "tl_phone", () => ctl.PhoneText),
            (Ic.Globe, "tl_share", () => Loc.T(ctl.Relay.Running ? "tl_on" : "tl_off")),
            (Ic.Refresh, "tl_sess", () => ctl.SessionText()),
            (Ic.Usb, "tl_port", () => Adb.RelayPort.ToString()),
        };
        for (int i = 0; i < 4; i++)
        {
            int idx = i; var tl = tiles[i];
            Add(new TileItem { Icon = tl.ic, Title = Loc.T(tl.key), Value = tl.val, Accent = () => idx == 1 && ctl.Relay.Running ? Pal.Green : idx == 2 && ctl.Phase == Phase.Connected ? Pal.Green : Pal.Blue, B = R(i * (twid + 12), y, twid, 112) });
        }
        y += 128;

        double bw = (IW - 24) / 3.0;
        Add(new ButtonItem { Text = Loc.T("qa_open"), Icon = Ic.Phone, B = R(0, y, bw, 44), Click = () => ctl.OpenApp() });
        Add(new ButtonItem { Text = Loc.T("qa_reinst"), Icon = Ic.Refresh, B = R(bw + 12, y, bw, 44), Click = () => ctl.Reinstall() });
        Add(new ButtonItem { Provider = () => Loc.T(showLog ? "qa_hide" : "qa_log"), Icon = Ic.Info, B = R(2 * (bw + 12), y, bw, 44), Click = () => { showLog = !showLog; Rebuild(); } });
        y += 60;
        if (showLog) { Add(new LogItem { Provider = () => ctl.Relay.LogText + "\n" + ctl.Phase + " " + (ctl.Serial ?? ""), B = R(0, y, IW, 160) }); y += 176; }
        contentH = y;
    }

    static Color UsageColor() => Usage.Pro ? Pal.Green : Usage.Used >= Usage.Limit ? Pal.Red : Usage.Used >= Usage.Limit * 0.8 ? Pal.Amber : Pal.Blue;

    protected override void Dispose(bool disposing) { if (disposing) clock.Dispose(); base.Dispose(disposing); }
}

static class Fmt
{
    /// <summary>Exact size, two decimals in MB so the live counter visibly moves. Persian unit names in Persian.</summary>
    public static string Mb(long b)
    {
        bool fa = Loc.IsRtl;
        if (b < 1024 * 1024) return (b / 1024.0).ToString("0.0", System.Globalization.CultureInfo.InvariantCulture) + " " + (fa ? "کیلوبایت" : "KB");
        return (b / 1048576.0).ToString("0.00", System.Globalization.CultureInfo.InvariantCulture) + " " + (fa ? "مگابایت" : "MB");
    }
}

static class Links
{
    public static void Open(string url) { if (url.Length == 0) return; try { Process.Start(new ProcessStartInfo(url.Contains("@") && !url.StartsWith("http") ? "mailto:" + url : url) { UseShellExecute = true }); } catch { } }
}

// ===================================================================================== GUIDE
sealed class GuidePage : CanvasPage
{
    readonly Controller ctl; int tab;
    public GuidePage(Controller c) { ctl = c; ctl.Changed += () => Invalidate(); }

    protected override void Build()
    {
        int y = 0; Header(Loc.T("g_t"), Loc.T("g_s"), ref y);
        Add(new CardItem { B = R(0, y, IW, 62), Radius = 16 });
        Add(new IconItem { Icon = Ic.Phone, ColorProvider = () => ctl.Accent, B = R(18, y + 19, 24, 24) });
        Add(new TextItem { Provider = () => Loc.T("g_status") + ":  " + ctl.Title, F = Ui.Font(10.5f, FontStyle.Bold), ColorProvider = () => Pal.Text, VMid = true, Wrap = false, Ellipsis = true, B = R(54, y, IW - 54 - 170, 62) });
        Add(new ButtonItem { Text = Loc.T("g_check"), Icon = Ic.Refresh, B = R(IW - 156, y + 12, 140, 38), Click = () => ctl.Check() });
        y += 78;

        double x = 0, rowY = y; var f = Ui.Font(9.5f);
        for (int i = 0; i < 8; i++)
        {
            int idx = i; string label = Loc.T(i == 0 ? "gtT" : "gt" + (i - 1));
            double w = Ui.TextW(label, f) / Ui.Scale + 36;
            if (x + w > IW) { x = 0; rowY += 46; }
            Add(new ChipItem { Text = label, Selected = tab == i, B = R(x, rowY, w, 36), Click = () => { tab = idx; Rebuild(); } });
            x += w + 10;
        }
        y = (int)rowY + 56;

        var lines = Loc.Lines(tab == 0 ? "gT" : "g" + (tab - 1)); var tf = Ui.Font(10.5f);
        for (int i = 0; i < lines.Length; i++)
        {
            int th = Ui.TextH(lines[i], tf, Ui.S(IW - 72)); double h = Math.Max(56, th / Ui.Scale + 26);
            Add(new CardItem { B = R(0, y, IW, h), Radius = 16 });
            Add(new NumberItem { N = i + 1, B = R(16, y + 14, 28, 28) });
            Add(new TextItem { Text = lines[i], F = tf, Color = Pal.Text, B = RP(58, y + 13, IW - 76, th) });
            y += (int)h + 10;
        }
        contentH = y + 8;
    }
}

// ===================================================================================== SETTINGS
sealed class SettingsPage : CanvasPage
{
    readonly Controller ctl; int y, groupTop, row;
    public SettingsPage(Controller c) { ctl = c; }

    void Group(string titleKey, int rows)
    {
        y += AddText(Loc.T(titleKey), 11, FontStyle.Bold, Pal.Blue, 4, y, IW) + 8;
        Add(new CardItem { B = R(0, y, IW, rows * 68 + 4), Radius = 18 });
        groupTop = y + 2; row = 0; y += rows * 68 + 4 + 22;
    }

    int RowY() { int ry = groupTop + row * 68; row++; return ry; }

    void RowText(int ry, string title, string desc, double ctlW)
    {
        double w = IW - 40 - ctlW - 16;
        Add(new TextItem { Text = title, F = Ui.Font(10.5f, FontStyle.Bold), Color = Pal.Text, Wrap = false, Ellipsis = true, B = R(20, ry + 12, w, 22) });
        Add(new TextItem { Text = desc, F = Ui.Font(9), Color = Pal.Muted, Wrap = false, Ellipsis = true, B = R(20, ry + 36, w, 20) });
        if (row > 0 && ry > groupTop) Add(new DividerItem { B = R(20, ry, IW - 40, 1) });
    }

    void Toggle(string tk, bool value, Action<bool> set)
    {
        int ry = RowY(); RowText(ry, Loc.T(tk), Loc.T(tk + "_d"), 52);
        Add(new ToggleItem { Value = value, B = R(IW - 20 - 48, ry + 20, 48, 26), Changed = v => { set(v); Settings.Save(); } });
    }

    void Gated(string tk, bool value, Action<bool> set)
    {
        int ry = RowY(); RowText(ry, Loc.T(tk) + "  ★ Pro", Loc.T(tk + "_d"), 52);
        Add(new ToggleItem { Value = value, B = R(IW - 20 - 48, ry + 20, 48, 26), Changed = v =>
        {
            if (v && !Usage.ProRecent)
            {
                MessageBox.Show(Loc.T("pro_only"), Loc.T("app"), MessageBoxButtons.OK, MessageBoxIcon.Information, MessageBoxDefaultButton.Button1, Opts());
                Rebuild(); return;
            }
            set(v); Settings.Save();
        } });
    }

    void Seg(string tk, string[] opts, int idx, Action<int> set)
    {
        int ry = RowY(); RowText(ry, Loc.T(tk), Loc.T(tk + "_d"), 290);
        Add(new SegItem { Options = opts, Index = idx, B = R(IW - 20 - 280, ry + 15, 280, 38), Changed = i => { set(i); Settings.Save(); } });
    }

    void Btn(string tk, string btnKey, Ic icon, Action act, BtnStyle st = BtnStyle.Secondary)
    {
        int ry = RowY(); RowText(ry, Loc.T(tk), Loc.T(tk + "_d"), 150);
        Add(new ButtonItem { Text = Loc.T(btnKey), Icon = icon, Style = st, B = R(IW - 20 - 140, ry + 15, 140, 38), Click = act });
    }

    protected override void Build()
    {
        y = 0; Header(Loc.T("s_t"), Loc.T("s_s"), ref y);
        Group("sg0", 3);
        Seg("s_lang", new[] { Loc.T("o_auto"), "English", "فارسی" }, Settings.Language == "en" ? 1 : Settings.Language == "fa" ? 2 : 0,
            i => { Settings.Language = i == 1 ? "en" : i == 2 ? "fa" : "auto"; Loc.Apply(Settings.Language); AppBus.LanguageChanged?.Invoke(); });
        Seg("s_theme", new[] { Loc.T("o_dark"), Loc.T("o_light"), Loc.T("o_sys") }, Settings.Theme == "light" ? 1 : Settings.Theme == "system" ? 2 : 0,
            i => { Settings.Theme = i == 1 ? "light" : i == 2 ? "system" : "dark"; AppBus.ThemeChanged?.Invoke(); });
        Toggle("s_anim", Settings.Animations, v => Settings.Animations = v);

        Group("sg1", 3);
        Gated("s_win", Settings.StartWithWindows, v => { Settings.StartWithWindows = v; Settings.ApplyStartup(); });
        Toggle("s_min", Settings.StartMinimized, v => Settings.StartMinimized = v);
        Gated("s_tray", Settings.MinimizeToTray, v => Settings.MinimizeToTray = v);

        Group("sg2", 3);
        Toggle("s_open", Settings.AutoOpenApp, v => Settings.AutoOpenApp = v);
        Toggle("s_inst", Settings.AutoInstall, v => Settings.AutoInstall = v);
        Toggle("s_relay", Settings.AutoRelay, v => Settings.AutoRelay = v);

        Group("sg3", 5);
        Btn("s_adb", "b_run", Ic.Refresh, () => ctl.RestartAdb());
        Btn("s_reinst", "b_run", Ic.Phone, () => ctl.Reinstall());
        Btn("s_tools", "b_open", Ic.Folder, () => { try { Process.Start("explorer.exe", "\"" + Paths.ToolsDir + "\""); } catch { } });
        Btn("s_diag", "b_copy", Ic.Copy, () => { try { Clipboard.SetText(ctl.Diagnostics()); MessageBox.Show(Loc.T("copied"), Loc.T("app"), MessageBoxButtons.OK, MessageBoxIcon.Information, MessageBoxDefaultButton.Button1, Opts()); } catch { } });
        Btn("s_reset", "b_reset", Ic.Power, () =>
        {
            if (MessageBox.Show(Loc.T("s_reset_q"), Loc.T("app"), MessageBoxButtons.YesNo, MessageBoxIcon.Question, MessageBoxDefaultButton.Button2, Opts()) != DialogResult.Yes) return;
            Settings.Reset(); Loc.Apply(Settings.Language); AppBus.ThemeChanged?.Invoke(); AppBus.LanguageChanged?.Invoke();
        }, BtnStyle.Danger);
        contentH = y;
    }

    public static MessageBoxOptions Opts() => Loc.IsRtl ? MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign : 0;
}

// ===================================================================================== ABOUT
sealed class LogoItem : Item
{
    public override void Paint(Graphics g)
    {
        float s = Math.Min(B.Width, B.Height); float cx = B.X + B.Width / 2f, cy = B.Y + B.Height / 2f; float core = s * 0.34f;
        if (Settings.Animations)
            for (int i = 0; i < 2; i++)
            {
                float p = (float)((Anim.Time * 0.4 + i * 0.5) % 1.0); float r = core + (s / 2 - core) * p;
                using var pen = new Pen(Ui.Alpha(Pal.Violet, (int)(110 * (1 - p))), Ui.S(2));
                g.DrawEllipse(pen, cx - r, cy - r, r * 2, r * 2);
            }
        Logo.Mark(g, new RectangleF(cx - core, cy - core, core * 2, core * 2));
    }
    public override bool Update(float dt) => Settings.Animations;
}

sealed class AboutPage : CanvasPage
{
    readonly Controller ctl; string adbVersion = "…";
    public AboutPage(Controller c) { ctl = c; }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        if (Paths.ToolsPresent) LoadAdbVersion();
    }
    async void LoadAdbVersion() { try { adbVersion = await Adb.VersionAsync(); Rebuild(); } catch { } }

    int Section(string title, string body, int y, bool bullets = false)
    {
        var lines = bullets ? body.Split('\n') : new[] { body }; var f = Ui.Font(10); int total = 0;
        var hs = new List<int>(); foreach (var l in lines) { int h = Ui.TextH(l, f, Ui.S(IW - 70)); hs.Add(h); total += h + 6; }
        double cardH = 62 + total / Ui.Scale + 12;
        Add(new CardItem { B = R(0, y, IW, cardH), Radius = 18 });
        Add(new TextItem { Text = title, F = Ui.Font(11.5f, FontStyle.Bold), Color = Pal.Text, B = R(20, y + 18, IW - 40, 26) });
        double yy = y + 52;
        for (int i = 0; i < lines.Length; i++)
        {
            if (bullets) Add(new TextItem { Text = "●", F = Ui.Font(6), Color = Pal.Blue, B = RP(20, (int)yy + 6, 14, 14) });
            Add(new TextItem { Text = lines[i], F = f, Color = Pal.Muted, B = RP(bullets ? 44 : 20, (int)yy, bullets ? IW - 64 : IW - 40, hs[i]) });
            yy += hs[i] / Ui.Scale + 6;
        }
        return y + (int)cardH + 14;
    }

    void Link(string labelKey, string url, ref double x, int y)
    {
        if (string.IsNullOrEmpty(url)) return;
        Add(new ButtonItem { Text = Loc.T(labelKey), Icon = Ic.Globe, B = R(x, y, 150, 40), Click = () => { try { Links.Open(url); } catch { } } });
        x += 160;
    }

    protected override void Build()
    {
        int y = 0; Header(Loc.T("a_t"), Loc.T("tagline"), ref y);
        const int hero = 224;
        Add(new CardItem { B = R(0, y, IW, hero), Tint = true, Radius = 22 });
        Add(new LogoItem { B = R(IW / 2 - 90, y + 6, 180, 150) });
        Add(new TextItem { Text = Loc.T("app"), F = Ui.Font(20, FontStyle.Bold), Color = Pal.Text, Alignment = Align.Center, B = R(0, y + 148, IW, 36) });
        Add(new TextItem { Text = Loc.T("a_ver") + " " + Application.ProductVersion.Split('+')[0], F = Ui.Font(9.5f), Color = Pal.Muted, Alignment = Align.Center, B = R(0, y + 190, IW, 22) });
        y += hero + 16;
        y = Section(Loc.T("a_t"), Loc.T("a_desc"), y);
        y = Section(Loc.T("a_feat"), Loc.T("a_f"), y, true);
        y = Section(Loc.T("a_how"), Loc.T("a_h"), y, true);
        y = Section(Loc.T("a_priv"), Loc.T("a_priv_t"), y);
        y = Section(Loc.T("a_oss"), Loc.T("a_oss_t"), y, true);
        Add(new ButtonItem { Text = Loc.T("a_lic"), Icon = Ic.Folder, B = R(0, y - 4, 190, 40), Click = () => { try { var d = Path.Combine(Paths.ToolsDir, "licenses"); Process.Start("explorer.exe", "\"" + (Directory.Exists(d) ? d : Paths.ToolsDir) + "\""); } catch { } } });
        Add(new ButtonItem { Text = Loc.T("a_upd"), Icon = Ic.Refresh, B = R(400, y - 4, 190, 40), Click = () => { UpdateCheck.CheckAsync(); if (!UpdateCheck.Available) MessageBox.Show(Loc.T("a_upd_none"), Loc.T("app"), MessageBoxButtons.OK, MessageBoxIcon.Information, MessageBoxDefaultButton.Button1, SettingsPage.Opts()); } });
        Add(new ButtonItem { Text = Loc.T("s_diag"), Icon = Ic.Copy, B = R(200, y - 4, 190, 40), Click = () => { try { Clipboard.SetText(ctl.Diagnostics()); } catch { } } });
        y += 52;
        string sys = "Windows: " + Controller.WindowsName() + "\n.NET Framework: " + Environment.Version + "\nadb: " + adbVersion;
        y = Section(Loc.T("a_sys"), sys, y);
        if (Brand.Telegram.Length + Brand.Email.Length + Brand.Website.Length > 0)
        {
            Add(new CardItem { B = R(0, y, IW, 118), Radius = 18 });
            Add(new TextItem { Text = Loc.T("a_contact"), F = Ui.Font(11.5f, FontStyle.Bold), Color = Pal.Text, B = R(20, y + 18, IW - 40, 26) });
            double x = 20; Link("a_tg", Brand.Telegram, ref x, y + 56); Link("a_mail", Brand.Email, ref x, y + 56); Link("a_web", Brand.Website, ref x, y + 56);
            y += 132;
        }
        contentH = y;
    }
}
