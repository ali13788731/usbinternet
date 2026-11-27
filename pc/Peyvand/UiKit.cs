using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace Peyvand;

enum Align { Start, Center, End }

static class Pal
{
    public static bool Dark = true;
    public static Color Bg, Surface, Surface2, Border, Text, Muted;
    public static readonly Color Blue = Color.FromArgb(0x0D, 0x94, 0x88);
    public static readonly Color Violet = Color.FromArgb(0x0E, 0xA5, 0xE9);
    public static readonly Color Green = Color.FromArgb(0x22, 0xC5, 0x5E);
    public static readonly Color Amber = Color.FromArgb(0xF5, 0x9E, 0x0B);
    public static readonly Color Red = Color.FromArgb(0xEF, 0x44, 0x44);
    public static readonly Color Cyan = Color.FromArgb(0x22, 0xD3, 0xEE);

    public static void Apply(bool dark)
    {
        Dark = dark;
        if (dark)
        {
            Bg = Color.FromArgb(0x0A, 0x11, 0x10); Surface = Color.FromArgb(0x11, 0x1B, 0x1A);
            Surface2 = Color.FromArgb(0x18, 0x27, 0x26); Border = Color.FromArgb(0x23, 0x38, 0x36);
            Text = Color.FromArgb(0xEA, 0xF5, 0xF3); Muted = Color.FromArgb(0x8D, 0xA8, 0xA3);
        }
        else
        {
            Bg = Color.FromArgb(0xF2, 0xF7, 0xF6); Surface = Color.FromArgb(0xFF, 0xFF, 0xFF);
            Surface2 = Color.FromArgb(0xE7, 0xF0, 0xEE); Border = Color.FromArgb(0xD8, 0xE6, 0xE3);
            Text = Color.FromArgb(0x0A, 0x1B, 0x19); Muted = Color.FromArgb(0x55, 0x72, 0x6D);
        }
    }
}

static class Ui
{
    public static float Scale = 1f;
    public static int S(double v) => (int)Math.Round(v * Scale);
    public static bool Rtl => Loc.IsRtl;

    public static void InitScale()
    {
        try { using var g = Graphics.FromHwnd(IntPtr.Zero); Scale = Math.Max(1f, g.DpiX / 96f); }
        catch { Scale = 1f; }
    }

    static readonly Dictionary<string, Font> fonts = new();
    static PrivateFontCollection? titr;
    static FontFamily? titrFamily;

    /// <summary>Loads the embedded B Titr font once (falls back to Segoe UI if anything goes wrong).</summary>
    static FontFamily? TitrFont()
    {
        if (titrFamily != null || titr != null) return titrFamily;
        try
        {
            titr = new PrivateFontCollection();
            var asm = System.Reflection.Assembly.GetExecutingAssembly();
            foreach (var name in new[] { "BTITRBD.ttf" })
            {
                using var st = asm.GetManifestResourceStream(name);
                if (st == null) continue;
                var data = new byte[st.Length]; st.Read(data, 0, data.Length);
                IntPtr mem = Marshal.AllocCoTaskMem(data.Length);   // must stay alive for the whole run
                Marshal.Copy(data, 0, mem, data.Length);
                titr.AddMemoryFont(mem, data.Length);
            }
            foreach (var fam in titr.Families) if (fam.Name.StartsWith("B Titr")) { titrFamily = fam; break; }
        }
        catch { titrFamily = null; }
        return titrFamily;
    }

    public static Font Font(double pt, FontStyle st = FontStyle.Regular, string family = "Segoe UI")
    {
        string key = family + pt + (int)st + (Rtl ? "fa" : "en");
        if (!fonts.TryGetValue(key, out var f))
        {
            // B Titr draws the digits 0-9 with Persian shapes, so it is used only in Persian; English uses Segoe UI (real Latin digits).
            var vf = family == "Segoe UI" && Rtl ? TitrFont() : null;
            if (vf != null)
            {
                var style = (st & FontStyle.Bold) != 0 ? FontStyle.Bold : FontStyle.Regular;
                if (!vf.IsStyleAvailable(style)) style = vf.IsStyleAvailable(FontStyle.Regular) ? FontStyle.Regular : FontStyle.Bold;
                f = new Font(vf, (float)(pt * 0.96), style, GraphicsUnit.Point);
            }
            else f = new Font(family, (float)pt, st, GraphicsUnit.Point);
            fonts[key] = f;
        }
        return f;
    }

    /// <summary>Persian digits and decimal mark when the interface language is Persian (not for the technical log).</summary>
    public static string Digits(string s, Font? f = null)
    {
        if (!Rtl || string.IsNullOrEmpty(s) || (f != null && f.FontFamily.Name == "Consolas")) return s;
        bool latin = false;
        foreach (char c in s) if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) { latin = true; break; }
        var sb = new System.Text.StringBuilder(s.Length);
        for (int i = 0; i < s.Length; i++)
        {
            char c = s[i];
            if (c >= '0' && c <= '9') sb.Append((char)('\u06F0' + (c - '0')));
            else if (c == '.' && !latin && i > 0 && i < s.Length - 1 && char.IsDigit(s[i - 1]) && char.IsDigit(s[i + 1])) sb.Append('\u066B');
            else sb.Append(c);
        }
        return sb.ToString();
    }

    public static int Clamp(int v, int lo, int hi) => v < lo ? lo : v > hi ? hi : v;
    public static float Clamp01(float v) => v < 0 ? 0 : v > 1 ? 1 : v;
    public static Color Alpha(Color c, int a) => Color.FromArgb(Clamp(a, 0, 255), c);
    public static Color Mix(Color a, Color b, float t)
    {
        t = Clamp01(t);
        return Color.FromArgb((int)(a.A + (b.A - a.A) * t), (int)(a.R + (b.R - a.R) * t), (int)(a.G + (b.G - a.G) * t), (int)(a.B + (b.B - a.B) * t));
    }
    public static float EaseOut(float t) { t = Clamp01(t); return 1 - (1 - t) * (1 - t) * (1 - t); }
    public static float EaseBack(float t) { t = Clamp01(t); float c = 1.70158f; float u = t - 1; return 1 + (c + 1) * u * u * u + c * u * u; }

    public static Rectangle Mirror(Rectangle r, int totalW) => Rtl ? new Rectangle(totalW - r.Right, r.Y, r.Width, r.Height) : r;

    public static GraphicsPath Round(RectangleF r, float rad)
    {
        var p = new GraphicsPath();
        float d = Math.Min(rad * 2, Math.Min(r.Width, r.Height));
        if (d <= 0.5f) { p.AddRectangle(r); return p; }
        p.AddArc(r.X, r.Y, d, d, 180, 90);
        p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }
    public static void FillRound(Graphics g, Brush b, RectangleF r, float rad) { using var p = Round(r, rad); g.FillPath(b, p); }
    public static void StrokeRound(Graphics g, Pen pen, RectangleF r, float rad) { using var p = Round(r, rad); g.DrawPath(pen, p); }

    public static LinearGradientBrush Brand(RectangleF r, float angle = 45f, int alpha = 255)
    {
        if (r.Width < 1) r.Width = 1;
        if (r.Height < 1) r.Height = 1;
        return new LinearGradientBrush(r, Alpha(Pal.Blue, alpha), Alpha(Pal.Violet, alpha), angle);
    }

    public static StringFormat Fmt(Align a, bool vmid, bool wrap, bool ellipsis, bool rtl)
    {
        var sf = new StringFormat(StringFormatFlags.NoClip);
        if (rtl) sf.FormatFlags |= StringFormatFlags.DirectionRightToLeft;
        if (!wrap) sf.FormatFlags |= StringFormatFlags.NoWrap;
        sf.Alignment = a == Align.Start ? StringAlignment.Near : a == Align.Center ? StringAlignment.Center : StringAlignment.Far;
        sf.LineAlignment = vmid ? StringAlignment.Center : StringAlignment.Near;
        sf.Trimming = ellipsis ? StringTrimming.EllipsisCharacter : StringTrimming.None;
        return sf;
    }

    public static void Text(Graphics g, string s, Font f, Color c, RectangleF r, Align a = Align.Start, bool vmid = false, bool wrap = true, bool ellipsis = false, bool? rtl = null)
    {
        if (string.IsNullOrEmpty(s)) return;
        s = Digits(s, f);
        using var sf = Fmt(a, vmid, wrap, ellipsis, rtl ?? Rtl);
        using var br = new SolidBrush(c);
        g.DrawString(s, f, br, r, sf);
    }

    static readonly Bitmap mb = new Bitmap(1, 1);
    static readonly Graphics mg = Graphics.FromImage(mb);

    public static int TextH(string s, Font f, int widthPx)
    {
        if (string.IsNullOrEmpty(s)) return 0;
        s = Digits(s, f);
        using var sf = Fmt(Align.Start, false, true, false, Rtl);
        return (int)Math.Ceiling(mg.MeasureString(s, f, Math.Max(1, widthPx), sf).Height) + 2;
    }
    public static int TextW(string s, Font f)
    {
        if (string.IsNullOrEmpty(s)) return 0;
        s = Digits(s, f);
        using var sf = Fmt(Align.Start, false, false, false, Rtl);
        return (int)Math.Ceiling(mg.MeasureString(s, f, 4000, sf).Width);
    }

    public static void Quality(Graphics g)
    {
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;
    }

    [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int val, int size);
    public static void DarkTitleBar(IntPtr hwnd, bool dark)
    {
        try { int v = dark ? 1 : 0; DwmSetWindowAttribute(hwnd, 20, ref v, 4); DwmSetWindowAttribute(hwnd, 19, ref v, 4); } catch { }
    }
}

/// <summary>One shared animation clock (about 60 fps).</summary>
static class Anim
{
    public static double Time;
    public static bool Paused;
    public static event Action<float>? Tick;
    static readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 16 };
    static readonly Stopwatch sw = Stopwatch.StartNew();
    static double last;

    public static void Start()
    {
        last = sw.Elapsed.TotalSeconds;
        timer.Tick += (_, _) =>
        {
            double now = sw.Elapsed.TotalSeconds;
            float dt = (float)Math.Min(0.1, now - last);
            last = now;
            Time = now;
            if (!Paused) Tick?.Invoke(dt);
        };
        timer.Start();
    }
}

static class Logo
{
    public static void Trident(Graphics g, RectangleF r, Color c)
    {
        float u = Math.Min(r.Width, r.Height) / 100f;
        PointF P(float x, float y) => new PointF(r.X + x * u, r.Y + y * u);
        using var br = new SolidBrush(c);
        using var pen = new Pen(c, 6f * u) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round };
        g.DrawLine(pen, P(50, 80), P(50, 24));
        g.FillPolygon(br, new[] { P(50, 12), P(41, 27), P(59, 27) });
        g.FillEllipse(br, r.X + 41 * u, r.Y + 73 * u, 18 * u, 18 * u);
        g.DrawLines(pen, new[] { P(50, 60), P(30, 48), P(30, 40) });
        g.FillEllipse(br, r.X + 23.5f * u, r.Y + 27.5f * u, 13 * u, 13 * u);
        g.DrawLines(pen, new[] { P(50, 48), P(70, 38), P(70, 32) });
        g.FillRectangle(br, r.X + 63.5f * u, r.Y + 20 * u, 13 * u, 13 * u);
    }

    public static void Mark(Graphics g, RectangleF r, int alpha = 255)
    {
        using (var br = Ui.Brand(r, 45f, alpha)) Ui.FillRound(g, br, r, r.Width * 0.26f);
        Trident(g, r, Color.FromArgb(alpha, Color.White));
    }

    [DllImport("user32.dll")] static extern bool DestroyIcon(IntPtr h);
    public static Icon CreateIcon(int size = 64)
    {
        using var bmp = new Bitmap(size, size);
        using (var g = Graphics.FromImage(bmp)) { g.SmoothingMode = SmoothingMode.AntiAlias; Mark(g, new RectangleF(1, 1, size - 2, size - 2)); }
        IntPtr h = bmp.GetHicon();
        var ic = (Icon)Icon.FromHandle(h).Clone();
        DestroyIcon(h);
        return ic;
    }
}

enum Ic { Home, Guide, Settings, Info, Phone, Check, Cross, Warning, Refresh, Folder, Copy, Globe, Power, Shield, Usb, Play }

static class Icons
{
    public static void Draw(Graphics g, Ic k, RectangleF r, Color c, float sw = 0)
    {
        float u = Math.Min(r.Width, r.Height) / 24f;
        PointF P(float x, float y) => new PointF(r.X + x * u, r.Y + y * u);
        using var pen = new Pen(c, sw > 0 ? sw : Math.Max(1.4f, 2f * u)) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round };
        using var br = new SolidBrush(c);
        switch (k)
        {
            case Ic.Home:
                g.DrawLines(pen, new[] { P(3, 11), P(12, 3), P(21, 11) });
                g.DrawLines(pen, new[] { P(5.5f, 9.5f), P(5.5f, 20), P(18.5f, 20), P(18.5f, 9.5f) });
                g.DrawLines(pen, new[] { P(10, 20), P(10, 14), P(14, 14), P(14, 20) });
                break;
            case Ic.Guide:
                for (int i = 0; i < 3; i++)
                {
                    float y = 6 + i * 6;
                    g.DrawLine(pen, P(10, y), P(21, y));
                    g.FillEllipse(br, r.X + 3 * u, r.Y + (y - 1.8f) * u, 3.6f * u, 3.6f * u);
                }
                break;
            case Ic.Settings:
                float[] ys = { 6.5f, 12, 17.5f }; float[] xs = { 8, 16, 10 };
                for (int i = 0; i < 3; i++)
                {
                    g.DrawLine(pen, P(3.5f, ys[i]), P(20.5f, ys[i]));
                    g.FillEllipse(br, r.X + (xs[i] - 3) * u, r.Y + (ys[i] - 3) * u, 6 * u, 6 * u);
                }
                break;
            case Ic.Info:
                g.DrawEllipse(pen, r.X + 3 * u, r.Y + 3 * u, 18 * u, 18 * u);
                g.DrawLine(pen, P(12, 11), P(12, 17));
                g.FillEllipse(br, r.X + 10.8f * u, r.Y + 6.6f * u, 2.4f * u, 2.4f * u);
                break;
            case Ic.Phone:
                Ui.StrokeRound(g, pen, new RectangleF(r.X + 6.5f * u, r.Y + 2 * u, 11 * u, 20 * u), 2.5f * u);
                g.DrawLine(pen, P(10.5f, 18.5f), P(13.5f, 18.5f));
                break;
            case Ic.Check:
                g.DrawLines(pen, new[] { P(4, 12.5f), P(9.5f, 18), P(20, 6) });
                break;
            case Ic.Cross:
                g.DrawLine(pen, P(6, 6), P(18, 18)); g.DrawLine(pen, P(18, 6), P(6, 18));
                break;
            case Ic.Warning:
                g.DrawPolygon(pen, new[] { P(12, 3), P(22, 20), P(2, 20) });
                g.DrawLine(pen, P(12, 9.5f), P(12, 14));
                g.FillEllipse(br, r.X + 10.9f * u, r.Y + 15.6f * u, 2.2f * u, 2.2f * u);
                break;
            case Ic.Refresh:
                g.DrawArc(pen, r.X + 4 * u, r.Y + 4 * u, 16 * u, 16 * u, 40, 280);
                g.DrawLines(pen, new[] { P(18.2f, 2.8f), P(18.2f, 7.4f), P(13.6f, 7.4f) });
                break;
            case Ic.Folder:
                g.DrawPolygon(pen, new[] { P(3, 6), P(9, 6), P(11.5f, 8.5f), P(21, 8.5f), P(21, 19), P(3, 19) });
                break;
            case Ic.Copy:
                Ui.StrokeRound(g, pen, new RectangleF(r.X + 9 * u, r.Y + 9 * u, 11 * u, 11 * u), 2 * u);
                g.DrawLines(pen, new[] { P(5.5f, 15), P(4, 15), P(4, 4), P(15, 4), P(15, 5.5f) });
                break;
            case Ic.Globe:
                g.DrawEllipse(pen, r.X + 3 * u, r.Y + 3 * u, 18 * u, 18 * u);
                g.DrawEllipse(pen, r.X + 8 * u, r.Y + 3 * u, 8 * u, 18 * u);
                g.DrawLine(pen, P(3, 12), P(21, 12));
                break;
            case Ic.Power:
                g.DrawArc(pen, r.X + 5 * u, r.Y + 5 * u, 14 * u, 14 * u, -60, 300);
                g.DrawLine(pen, P(12, 3), P(12, 11));
                break;
            case Ic.Shield:
                g.DrawPolygon(pen, new[] { P(12, 2.5f), P(20, 5.5f), P(20, 12), P(12, 21.5f), P(4, 12), P(4, 5.5f) });
                g.DrawLines(pen, new[] { P(8.5f, 12), P(11, 14.5f), P(15.5f, 9.5f) });
                break;
            case Ic.Usb:
                Logo.Trident(g, r, c);
                break;
            case Ic.Play:
                g.FillPolygon(br, new[] { P(7, 4), P(20, 12), P(7, 20) });
                break;
        }
    }
}
