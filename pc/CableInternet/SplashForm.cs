using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Windows.Forms;

namespace CableInternet;

/// <summary>Animated start screen: logo, name, and the "Designed by Mr.PC" line typed out below the logo.</summary>
sealed class SplashForm : Form
{
    readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 16 };
    readonly System.Diagnostics.Stopwatch sw = System.Diagnostics.Stopwatch.StartNew();
    const double Total = 3.2;

    public SplashForm()
    {
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer, true);
        FormBorderStyle = FormBorderStyle.None; ShowInTaskbar = false; StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(Ui.S(560), Ui.S(380)); BackColor = Color.FromArgb(0x0B, 0x10, 0x20); Opacity = 0;
        TopMost = true;
        try { Icon = Logo.CreateIcon(); } catch { }
        using (var p = Ui.Round(new RectangleF(0, 0, Width, Height), Ui.S(26))) Region = new Region(p);
        timer.Tick += (_, _) =>
        {
            double t = sw.Elapsed.TotalSeconds;
            Opacity = t < 0.35 ? t / 0.35 : t > Total - 0.35 ? Math.Max(0, (Total - t) / 0.35) : 1;
            if (t >= Total) { timer.Stop(); Close(); return; }
            Invalidate();
        };
        timer.Start();
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics; double t = sw.Elapsed.TotalSeconds;
        g.SmoothingMode = SmoothingMode.AntiAlias; g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
        var full = new RectangleF(0, 0, Width, Height);
        using (var br = new LinearGradientBrush(full, Color.FromArgb(0x0B, 0x10, 0x20), Color.FromArgb(0x1B, 0x14, 0x45), 60f)) g.FillRectangle(br, full);
        float cx = Width / 2f, cy = Height * 0.36f;
        // glow rings
        for (int i = 0; i < 3; i++)
        {
            float p = (float)((t * 0.55 + i / 3.0) % 1.0); float rad = Ui.S(60) + Ui.S(110) * p;
            using var pen = new Pen(Color.FromArgb((int)(70 * (1 - p)), Pal.Blue), Ui.S(2));
            g.DrawEllipse(pen, cx - rad, cy - rad, rad * 2, rad * 2);
        }
        using (var path = new GraphicsPath())
        {
            float gr = Ui.S(120); path.AddEllipse(cx - gr, cy - gr, gr * 2, gr * 2);
            using var pg = new PathGradientBrush(path) { CenterColor = Color.FromArgb(70, Pal.Violet), SurroundColors = new[] { Color.FromArgb(0, Pal.Violet) } };
            g.FillPath(pg, path);
        }
        // logo
        float s = Ui.EaseBack((float)(t / 0.8)); int a = (int)(255 * Ui.Clamp01((float)(t / 0.5)));
        float size = Ui.S(104) * (0.55f + 0.45f * s);
        Logo.Mark(g, new RectangleF(cx - size / 2, cy - size / 2, size, size), a);
        // name
        float na = Ui.Clamp01((float)((t - 0.7) / 0.6)); float ny = cy + Ui.S(78) + (1 - Ui.EaseOut(na)) * Ui.S(10);
        using (var sf = new StringFormat { Alignment = StringAlignment.Center, FormatFlags = Loc.IsRtl ? StringFormatFlags.DirectionRightToLeft : 0 })
        {
            using (var br = new SolidBrush(Color.FromArgb((int)(255 * na), 0xEE, 0xF2, 0xFF)))
                g.DrawString(Loc.T("app"), Ui.Font(24, FontStyle.Bold), br, new RectangleF(0, ny, Width, Ui.S(44)), sf);
            // designed by — typed out under the logo
            string full2 = Loc.T("designed"); double ts = (t - 1.35) / 1.1; int n = (int)Math.Max(0, Math.Min(full2.Length, Math.Floor(ts * full2.Length)));
            if (n > 0)
            {
                string part = full2.Substring(0, n); float dy = ny + Ui.S(48);
                float shimmer = (float)(((t - 2.5) * 0.9) % 1.5);
                var rect = new RectangleF(0, dy, Width, Ui.S(28));
                using var br2 = new LinearGradientBrush(new RectangleF(cx - Ui.S(130), dy, Ui.S(260), Ui.S(28)), Pal.Cyan, Pal.Violet, 0f);
                if (t > 2.5) { var cb = new ColorBlend(4) { Positions = new[] { 0f, 0.4f, 0.6f, 1f }, Colors = new[] { Pal.Blue, Color.White, Color.White, Pal.Violet } }; br2.InterpolationColors = cb; br2.TranslateTransform((shimmer - 0.5f) * Ui.S(120), 0); }
                g.DrawString(part + (n < full2.Length && ((int)(t * 8) % 2 == 0) ? "▏" : ""), Ui.Font(12, FontStyle.Regular), br2, rect, sf);
            }
        }
        // progress + version
        float prog = Ui.Clamp01((float)(t / Total)); float bw = Ui.S(160), bx = cx - bw / 2, by = Height - Ui.S(54);
        using (var br = new SolidBrush(Color.FromArgb(40, 255, 255, 255))) Ui.FillRound(g, br, new RectangleF(bx, by, bw, Ui.S(4)), Ui.S(2));
        using (var br = Ui.Brand(new RectangleF(bx, by, Math.Max(2, bw * prog), Ui.S(4)), 0f)) Ui.FillRound(g, br, new RectangleF(bx, by, Math.Max(2, bw * prog), Ui.S(4)), Ui.S(2));
        using (var sf = new StringFormat { Alignment = StringAlignment.Center })
        using (var br = new SolidBrush(Color.FromArgb(130, 0x93, 0xA0, 0xC4)))
            g.DrawString("v" + Application.ProductVersion.Split('+')[0], Ui.Font(8.5f), br, new RectangleF(0, Height - Ui.S(38), Width, Ui.S(20)), sf);
        using (var pen = new Pen(Color.FromArgb(60, Pal.Blue))) Ui.StrokeRound(g, pen, new RectangleF(0, 0, Width - 1, Height - 1), Ui.S(26));
    }

    protected override void Dispose(bool disposing) { if (disposing) timer.Dispose(); base.Dispose(disposing); }
}
