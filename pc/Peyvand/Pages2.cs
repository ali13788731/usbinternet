using System;
using System.Drawing;
using Microsoft.Win32;

namespace Peyvand;

// ===================================================================================== SUBSCRIPTION
sealed class SubscriptionPage : CanvasPage
{
    readonly Controller ctl;
    public SubscriptionPage(Controller c)
    {
        ctl = c;
        Usage.Changed += () => { if (Visible) Invalidate(); };
    }

    static bool Known => Usage.PhoneLinked || Usage.ProRecent;
    static bool IsPro => Usage.PhoneLinked ? Usage.Pro : Usage.ProRecent;

    protected override void Build()
    {
        int y = 0; Header(Loc.T("sb_t"), Loc.T("sb_s"), ref y);

        Add(new CardItem { B = R(0, y, IW, 110), Tint = true, Radius = 22 });
        Add(new IconItem { Icon = Ic.Shield, ColorProvider = () => !Known ? Pal.Muted : IsPro ? Pal.Green : Pal.Blue, B = R(24, y + 30, 48, 48) });
        Add(new TextItem { Provider = () => !Known ? Loc.T("sb_unknown") : IsPro ? Loc.T("sb_pro") : Loc.T("sb_free"), F = Ui.Font(16, FontStyle.Bold), Color = Pal.Text, VMid = true, Wrap = false, Ellipsis = true, B = R(90, y + 16, IW - 114, 40) });
        Add(new TextItem { Provider = () => !Known ? "" : IsPro ? Loc.T("sb_pro_d") : Loc.T("sb_free_d"), F = Ui.Font(10), Color = Pal.Muted, B = R(90, y + 58, IW - 114, 40) });
        y += 128;

        // how to subscribe
        y += AddText(Loc.T("sb_how"), 11, FontStyle.Bold, Pal.Blue, 4, y, IW) + 8;
        var lines = Loc.Lines("sb_how_l"); var tf = Ui.Font(10.5f);
        for (int i = 0; i < lines.Length; i++)
        {
            int th = Ui.TextH(lines[i], tf, Ui.S(IW - 72)); double h = Math.Max(52, th / Ui.Scale + 24);
            Add(new CardItem { B = R(0, y, IW, h), Radius = 16 });
            Add(new NumberItem { N = i + 1, B = R(16, y + 12, 28, 28) });
            Add(new TextItem { Text = lines[i], F = tf, Color = Pal.Text, B = RP(58, y + 12, IW - 76, th) });
            y += (int)h + 8;
        }
        y += 14;

        // comparison table
        y += AddText(Loc.T("cmp_title"), 11, FontStyle.Bold, Pal.Blue, 4, y, IW) + 8;
        string[][] rows =
        {
            new[] { "cmp_data", "cmp_data_f", "cmp_data_p" }, new[] { "cmp_live", "✓", "✓" }, new[] { "cmp_pc", "✓", "✓" },
            new[] { "cmp_routing", "—", "✓" }, new[] { "cmp_timer", "—", "✓" }, new[] { "cmp_pcauto", "—", "✓" },
            new[] { "cmp_report", "—", "✓" }, new[] { "cmp_support", "—", "✓" },
        };
        double rowH = 44, tableH = 48 + rows.Length * rowH + 8;
        Add(new CardItem { B = R(0, y, IW, tableH), Radius = 18 });
        double c1 = IW - 2 * 150 - 20;
        Add(new TextItem { Text = Loc.T("cmp_free"), F = Ui.Font(9.5f, FontStyle.Bold), Color = Pal.Muted, Alignment = Align.Center, VMid = true, Wrap = false, B = R(20 + c1, y, 150, 44) });
        Add(new TextItem { Text = Loc.T("cmp_pro"), F = Ui.Font(9.5f, FontStyle.Bold), Color = Pal.Violet, Alignment = Align.Center, VMid = true, Wrap = false, B = R(20 + c1 + 150, y, 150, 44) });
        for (int i = 0; i < rows.Length; i++)
        {
            double ry = y + 44 + i * rowH;
            Add(new DividerItem { B = R(20, ry, IW - 40, 1) });
            Add(new TextItem { Text = Loc.T(rows[i][0]), F = Ui.Font(10), Color = Pal.Text, VMid = true, Wrap = false, Ellipsis = true, B = R(20, ry, c1 - 10, rowH) });
            string f = rows[i][1].StartsWith("cmp_") ? Loc.T(rows[i][1]) : rows[i][1];
            string p = rows[i][2].StartsWith("cmp_") ? Loc.T(rows[i][2]) : rows[i][2];
            Add(new TextItem { Text = f, F = Ui.Font(10, FontStyle.Bold), Color = f == "—" ? Pal.Muted : Pal.Text, Alignment = Align.Center, VMid = true, Wrap = false, B = R(20 + c1, ry, 150, rowH) });
            Add(new TextItem { Text = p, F = Ui.Font(10, FontStyle.Bold), Color = Pal.Green, Alignment = Align.Center, VMid = true, Wrap = false, B = R(20 + c1 + 150, ry, 150, rowH) });
        }
        y += (int)tableH + 12;
        contentH = y;
    }
}

// ===================================================================================== REQUIREMENTS
sealed class RequirementsPage : CanvasPage
{
    readonly Controller ctl;
    public RequirementsPage(Controller c) { ctl = c; ctl.Changed += () => { if (Visible) Invalidate(); }; }

    static bool? NetOk()
    {
        try
        {
            using var k = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full");
            return k?.GetValue("Release") is int r ? r >= 528040 : (bool?)false;
        }
        catch { return null; }
    }

    static bool? VcOk()
    {
        try
        {
            using var k = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64");
            return k?.GetValue("Installed") is int i && i == 1;
        }
        catch { return null; }
    }

    bool? UsbOk() => ctl.Phase == Phase.NoDevice ? (ctl.NoDeviceTicks >= 6 ? false : (bool?)null)
        : ctl.Phase == Phase.Starting || ctl.Phase == Phase.ToolsMissing ? null : true;

    protected override void Build()
    {
        int y = 0; Header(Loc.T("rq_t"), Loc.T("rq_s"), ref y);
        var rows = new (string t, string d, Func<bool?> ok, string url)[]
        {
            ("rq_win", "rq_win_d", () => Environment.OSVersion.Version.Major >= 10 && Environment.Is64BitOperatingSystem, ""),
            ("rq_net", "rq_net_d", NetOk, Brand.UrlDotNet),
            ("rq_vc", "rq_vc_d", VcOk, Brand.UrlVcRedist),
            ("rq_tools", "rq_tools_d", () => Paths.ToolsPresent, Brand.PcDownloadUrl),
            ("rq_usb", "rq_usb_d", UsbOk, Brand.UrlUsbDriver),
            ("rq_cable", "rq_cable_d", () => null, ""),
            ("rq_phone", "rq_phone_d", () => null, Brand.UrlBazaar),
        };
        foreach (var r in rows)
        {
            var ok = r.ok(); var rr = r;
            Add(new CardItem { B = R(0, y, IW, 72), Radius = 16 });
            Color col = ok == true ? Pal.Green : ok == false ? Pal.Red : Pal.Muted;
            Add(new IconItem { Icon = ok == true ? Ic.Check : ok == false ? Ic.Warning : Ic.Info, Color = col, B = R(18, y + 24, 24, 24) });
            double btnW = r.url.Length > 0 ? 130 : 0;
            double statusW = 90;
            Add(new TextItem { Text = Loc.T(r.t), F = Ui.Font(10.5f, FontStyle.Bold), Color = Pal.Text, Wrap = false, Ellipsis = true, B = R(58, y + 12, IW - 58 - statusW - btnW - 40, 24) });
            Add(new TextItem { Text = Loc.T(r.d), F = Ui.Font(9), Color = Pal.Muted, Wrap = false, Ellipsis = true, B = R(58, y + 38, IW - 58 - statusW - btnW - 40, 22) });
            Add(new TextItem { Text = ok == true ? Loc.T("rq_ok") : ok == false ? Loc.T("rq_missing") : "", F = Ui.Font(9.5f, FontStyle.Bold), Color = col, Alignment = Align.End, VMid = true, Wrap = false, B = R(IW - 20 - btnW - statusW - (btnW > 0 ? 10 : 0), y, statusW, 72) });
            if (r.url.Length > 0)
                Add(new ButtonItem { Text = Loc.T("rq_get"), Icon = Ic.Refresh, B = R(IW - 20 - btnW, y + 17, btnW, 38), Click = () => Links.Open(rr.url) });
            y += 82;
        }
        contentH = y;
    }
}
