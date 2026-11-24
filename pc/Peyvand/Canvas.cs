using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace Peyvand;

abstract class Item
{
    public Rectangle B;
    public bool Hover, Down, Enabled = true;
    public Action? Click;
    public float HoverT;
    /// <summary>When set and returning false the item is neither drawn nor clickable.</summary>
    public Func<bool>? ShowIf;
    public bool Shown => ShowIf == null || ShowIf();
    public virtual bool Interactive => Click != null && Enabled && Shown;
    public abstract void Paint(Graphics g);
    public virtual void Activate(Point p) => Click?.Invoke();
    public virtual bool Update(float dt)
    {
        float target = Hover && Interactive ? 1f : 0f;
        if (!Settings.Animations) { bool ch = HoverT != target; HoverT = target; return ch; }
        if (Math.Abs(HoverT - target) < 0.002f) { bool ch = HoverT != target; HoverT = target; return ch; }
        HoverT += (target - HoverT) * Math.Min(1f, dt * 14f);
        return true;
    }
}

sealed class CardItem : Item
{
    public Color? Fill; public bool Tint; public float Radius = 18; public bool Outline = true;
    public override void Paint(Graphics g)
    {
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        float rad = Ui.S(Radius);
        using (var br = new SolidBrush(Fill ?? Pal.Surface)) Ui.FillRound(g, br, r, rad);
        if (Tint) using (var br = Ui.Brand(r, 30f, Pal.Dark ? 34 : 26)) Ui.FillRound(g, br, r, rad);
        if (Outline) using (var pen = new Pen(Pal.Border)) Ui.StrokeRound(g, pen, r, rad);
    }
}

sealed class TextItem : Item
{
    public string Text = ""; public Func<string>? Provider; public Font F = Ui.Font(10);
    public Color Color = Pal.Text; public Func<Color>? ColorProvider;
    public Align Alignment = Align.Start; public bool VMid, Wrap = true, Ellipsis, ForceLtr;
    public override void Paint(Graphics g)
    {
        string s = Provider != null ? Provider() : Text;
        Ui.Text(g, s, F, ColorProvider != null ? ColorProvider() : Color, B, Alignment, VMid, Wrap, Ellipsis, ForceLtr ? false : (bool?)null);
    }
}

sealed class IconItem : Item
{
    public Ic Icon; public Color Color = Pal.Text; public Func<Color>? ColorProvider;
    public override void Paint(Graphics g) => Icons.Draw(g, Icon, B, ColorProvider != null ? ColorProvider() : Color);
}

sealed class DividerItem : Item
{
    public override void Paint(Graphics g) { using var p = new Pen(Pal.Border); g.DrawLine(p, B.X, B.Y, B.Right, B.Y); }
}

enum BtnStyle { Primary, Secondary, Ghost, Danger }

sealed class ButtonItem : Item
{
    public string Text = ""; public Ic? Icon; public BtnStyle Style = BtnStyle.Secondary; public Func<string>? Provider;
    public override void Paint(Graphics g)
    {
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        float rad = r.Height / 2f;   // pill, same as the phone app
        int a = Enabled ? 255 : 90;
        Color fg;
        if (Style == BtnStyle.Primary)
        {
            using (var br = Ui.Brand(r, 20f, a)) Ui.FillRound(g, br, r, rad);
            if (HoverT > 0) using (var br = new SolidBrush(Color.FromArgb((int)(40 * HoverT), Color.White))) Ui.FillRound(g, br, r, rad);
            fg = Color.White;
        }
        else
        {
            Color baseC = Style == BtnStyle.Danger ? Pal.Red : Pal.Text;
            Color fill = Style == BtnStyle.Ghost ? Ui.Alpha(Pal.Surface2, (int)(255 * HoverT)) : Ui.Mix(Pal.Surface2, Ui.Mix(Pal.Surface2, Pal.Blue, 0.18f), HoverT);
            using (var br = new SolidBrush(fill)) Ui.FillRound(g, br, r, rad);
            if (Style != BtnStyle.Ghost) using (var pen = new Pen(Ui.Mix(Style == BtnStyle.Danger ? Ui.Alpha(Pal.Red, 120) : Pal.Border, Pal.Blue, HoverT * 0.7f))) Ui.StrokeRound(g, pen, r, rad);
            fg = Ui.Alpha(baseC, a);
        }
        string t = Provider != null ? Provider() : Text;
        var f = Ui.Font(10, FontStyle.Bold);
        int tw = Ui.TextW(t, f); int iw = Icon.HasValue ? Ui.S(18) : 0; int gap = Icon.HasValue && t.Length > 0 ? Ui.S(8) : 0;
        int total = tw + iw + gap;
        float x0 = B.X + (B.Width - total) / 2f;
        float iconX = Ui.Rtl ? x0 + tw + gap : x0; float textX = Ui.Rtl ? x0 : x0 + iw + gap;
        if (Icon.HasValue) Icons.Draw(g, Icon.Value, new RectangleF(iconX, B.Y + (B.Height - iw) / 2f, iw, iw), fg);
        Ui.Text(g, t, f, fg, new RectangleF(textX - 2, B.Y, tw + 8, B.Height), Align.Start, true, false);
    }
}

sealed class ToggleItem : Item
{
    public bool Value; public Action<bool>? Changed; float t = -1;
    public ToggleItem() { Click = () => { Value = !Value; Changed?.Invoke(Value); }; }
    public override bool Update(float dt)
    {
        bool r = base.Update(dt);
        float target = Value ? 1f : 0f;
        if (t < 0) t = target;
        if (!Settings.Animations) { if (t != target) { t = target; return true; } return r; }
        if (Math.Abs(t - target) > 0.002f) { t += (target - t) * Math.Min(1f, dt * 16f); return true; }
        t = target; return r;
    }
    public override void Paint(Graphics g)
    {
        if (t < 0) t = Value ? 1f : 0f;
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        using (var br = new SolidBrush(Pal.Surface2)) Ui.FillRound(g, br, r, r.Height / 2);
        using (var pen = new Pen(Pal.Border)) Ui.StrokeRound(g, pen, r, r.Height / 2);
        using (var br = Ui.Brand(r, 0f, (int)(255 * t))) Ui.FillRound(g, br, r, r.Height / 2);
        float k = r.Height - Ui.S(6); float travel = r.Width - k - Ui.S(6);
        float pos = Ui.Rtl ? 1 - t : t;
        using var kb = new SolidBrush(Color.White);
        g.FillEllipse(kb, r.X + Ui.S(3) + travel * pos, r.Y + Ui.S(3), k, k);
    }
}

sealed class SegItem : Item
{
    public string[] Options = new string[0]; public int Index; public Action<int>? Changed; float pos = -1;
    public SegItem() { Click = () => { }; }
    int Seg(Point p)
    {
        float w = (float)B.Width / Options.Length; int i = (int)((Ui.Rtl ? B.Right - p.X : p.X - B.X) / w);
        return Ui.Clamp(i, 0, Options.Length - 1);
    }
    public override void Activate(Point p) { int i = Seg(p); if (i != Index) { Index = i; Changed?.Invoke(i); } }
    public override bool Update(float dt)
    {
        bool r = base.Update(dt);
        if (pos < 0) pos = Index;
        if (!Settings.Animations) { bool ch = pos != Index; pos = Index; return ch || r; }
        if (Math.Abs(pos - Index) > 0.005f) { pos += (Index - pos) * Math.Min(1f, dt * 16f); return true; }
        pos = Index; return r;
    }
    public override void Paint(Graphics g)
    {
        if (pos < 0) pos = Index;
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        using (var br = new SolidBrush(Pal.Surface2)) Ui.FillRound(g, br, r, Ui.S(11));
        using (var pen = new Pen(Pal.Border)) Ui.StrokeRound(g, pen, r, Ui.S(11));
        float w = r.Width / Options.Length; float pad = Ui.S(3);
        float sx = Ui.Rtl ? r.Right - (pos + 1) * w : r.X + pos * w;
        var sel = new RectangleF(sx + pad, r.Y + pad, w - 2 * pad, r.Height - 2 * pad);
        using (var br = Ui.Brand(sel, 20f)) Ui.FillRound(g, br, sel, Ui.S(9));
        for (int i = 0; i < Options.Length; i++)
        {
            float x = Ui.Rtl ? r.Right - (i + 1) * w : r.X + i * w;
            bool on = i == Index;
            Ui.Text(g, Options[i], Ui.Font(9.5f, on ? FontStyle.Bold : FontStyle.Regular), on ? Color.White : Pal.Muted, new RectangleF(x, r.Y, w, r.Height), Align.Center, true, false);
        }
    }
}

sealed class ChipItem : Item
{
    public string Text = ""; public bool Selected;
    public override void Paint(Graphics g)
    {
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        if (Selected) using (var br = Ui.Brand(r, 20f)) Ui.FillRound(g, br, r, r.Height / 2);
        else
        {
            using (var br = new SolidBrush(Ui.Mix(Pal.Surface2, Pal.Blue, HoverT * 0.18f))) Ui.FillRound(g, br, r, r.Height / 2);
            using (var pen = new Pen(Pal.Border)) Ui.StrokeRound(g, pen, r, r.Height / 2);
        }
        Ui.Text(g, Text, Ui.Font(9.5f, Selected ? FontStyle.Bold : FontStyle.Regular), Selected ? Color.White : Pal.Text, r, Align.Center, true, false);
    }
}

sealed class NumberItem : Item
{
    public int N;
    public override void Paint(Graphics g)
    {
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        using (var br = Ui.Brand(r, 45f)) g.FillEllipse(br, r);
        Ui.Text(g, N.ToString(), Ui.Font(10, FontStyle.Bold), Color.White, r, Align.Center, true, false, false, false);
    }
}

sealed class TileItem : Item
{
    public Ic Icon; public string Title = ""; public Func<string> Value = () => ""; public Func<Color>? Accent;
    public override void Paint(Graphics g)
    {
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        using (var br = new SolidBrush(Pal.Surface)) Ui.FillRound(g, br, r, Ui.S(16));
        using (var pen = new Pen(Pal.Border)) Ui.StrokeRound(g, pen, r, Ui.S(16));
        Color ac = Accent != null ? Accent() : Pal.Blue;
        int pad = Ui.S(14), isz = Ui.S(34);
        float ix = Ui.Rtl ? r.Right - pad - isz : r.X + pad;
        var ir = new RectangleF(ix, r.Y + pad, isz, isz);
        using (var br = new SolidBrush(Ui.Alpha(ac, 40))) Ui.FillRound(g, br, ir, Ui.S(10));
        Icons.Draw(g, Icon, new RectangleF(ir.X + Ui.S(7), ir.Y + Ui.S(7), isz - Ui.S(14), isz - Ui.S(14)), ac);
        var tr = new RectangleF(r.X + pad, r.Y + pad + isz + Ui.S(8), r.Width - 2 * pad, Ui.S(18));
        Ui.Text(g, Title, Ui.Font(9), Pal.Muted, tr, Align.Start, false, false, true);
        var vr = new RectangleF(tr.X, tr.Bottom, tr.Width, Ui.S(24));
        Ui.Text(g, Value(), Ui.Font(11.5f, FontStyle.Bold), Pal.Text, vr, Align.Start, false, false, true);
    }
}

sealed class StepperItem : Item
{
    public string[] Labels = new string[0]; public Func<int, int> StateOf = _ => 0; // 0 pending 1 active 2 done 3 failed
    public override void Paint(Graphics g)
    {
        int n = Labels.Length; float w = (float)B.Width / n; int d = Ui.S(34);
        for (int i = 0; i < n; i++)
        {
            int idx = Ui.Rtl ? n - 1 - i : i;
            float cx = B.X + (idx + 0.5f) * w; float cy = B.Y + d / 2f + Ui.S(4);
            int st = StateOf(i);
            if (i < n - 1)
            {
                int nidx = Ui.Rtl ? n - 2 - i : i + 1; float nx = B.X + (nidx + 0.5f) * w;
                bool lit = st == 2;
                using var pen = new Pen(lit ? Pal.Green : Pal.Border, Ui.S(2));
                g.DrawLine(pen, cx + (nx > cx ? d / 2f + 4 : -d / 2f - 4), cy, nx + (nx > cx ? -d / 2f - 4 : d / 2f + 4), cy);
            }
            Color c = st == 2 ? Pal.Green : st == 3 ? Pal.Red : st == 1 ? Pal.Amber : Pal.Muted;
            var cr = new RectangleF(cx - d / 2f, cy - d / 2f, d, d);
            if (st == 1 && Settings.Animations)
            {
                float p = (float)((Anim.Time * 1.2) % 1.0); float ex = Ui.S(10) * p;
                using var pen = new Pen(Ui.Alpha(Pal.Amber, (int)(120 * (1 - p))), Ui.S(2));
                g.DrawEllipse(pen, cr.X - ex, cr.Y - ex, cr.Width + 2 * ex, cr.Height + 2 * ex);
            }
            using (var br = new SolidBrush(st == 0 ? Pal.Surface2 : Ui.Alpha(c, 45))) g.FillEllipse(br, cr);
            using (var pen = new Pen(st == 0 ? Pal.Border : c, Ui.S(2))) g.DrawEllipse(pen, cr);
            var ir = new RectangleF(cr.X + Ui.S(8), cr.Y + Ui.S(8), d - Ui.S(16), d - Ui.S(16));
            if (st == 2) Icons.Draw(g, Ic.Check, ir, c, Ui.S(2.2));
            else if (st == 3) Icons.Draw(g, Ic.Cross, ir, c, Ui.S(2.2));
            else Ui.Text(g, (i + 1).ToString(), Ui.Font(10, FontStyle.Bold), c, cr, Align.Center, true, false, false, false);
            var tr = new RectangleF(B.X + idx * w + 2, cy + d / 2f + Ui.S(8), w - 4, B.Bottom - cy - d / 2f - Ui.S(8));
            Ui.Text(g, Labels[i], Ui.Font(9, st == 1 || st == 2 ? FontStyle.Bold : FontStyle.Regular), st == 0 ? Pal.Muted : Pal.Text, tr, Align.Center);
        }
    }
    public override bool Update(float dt) => Settings.Animations;
}

enum RingMode { Idle, Search, Action, Ok, Error }

sealed class RingItem : Item
{
    public RingMode Mode = RingMode.Search; Color cur = Pal.Blue; float rot;
    Color Target => Mode == RingMode.Ok ? Pal.Green : Mode == RingMode.Error ? Pal.Red : Mode == RingMode.Action ? Pal.Amber : Mode == RingMode.Idle ? Pal.Muted : Pal.Blue;
    public override bool Update(float dt)
    {
        var t = Target; cur = Settings.Animations ? Ui.Mix(cur, t, Math.Min(1f, dt * 8f)) : t;
        rot = (rot + dt * 260f) % 360f;
        return Settings.Animations || cur != t;
    }
    public override void Paint(Graphics g)
    {
        float s = Math.Min(B.Width, B.Height); var r = new RectangleF(B.X + (B.Width - s) / 2, B.Y + (B.Height - s) / 2, s, s);
        float cx = r.X + s / 2, cy = r.Y + s / 2; float core = s * 0.30f;
        if (Settings.Animations && (Mode == RingMode.Ok || Mode == RingMode.Action))
            for (int i = 0; i < 2; i++)
            {
                float p = (float)((Anim.Time * 0.6 + i * 0.5) % 1.0); float rad = core + (s / 2 - core) * p;
                using var pen = new Pen(Ui.Alpha(cur, (int)(150 * (1 - p))), Ui.S(2));
                g.DrawEllipse(pen, cx - rad, cy - rad, rad * 2, rad * 2);
            }
        float rr = s * 0.42f;
        using (var pen = new Pen(Ui.Alpha(cur, 50), Ui.S(8))) g.DrawEllipse(pen, cx - rr, cy - rr, rr * 2, rr * 2);
        using (var pen = new Pen(cur, Ui.S(8)) { StartCap = LineCap.Round, EndCap = LineCap.Round })
        {
            if (Mode == RingMode.Search) g.DrawArc(pen, cx - rr, cy - rr, rr * 2, rr * 2, rot, 100);
            else if (Mode != RingMode.Idle) g.DrawEllipse(pen, cx - rr, cy - rr, rr * 2, rr * 2);
        }
        var cr = new RectangleF(cx - core, cy - core, core * 2, core * 2);
        using (var br = new SolidBrush(Ui.Alpha(cur, 38))) g.FillEllipse(br, cr);
        var ir = new RectangleF(cx - core * 0.6f, cy - core * 0.6f, core * 1.2f, core * 1.2f);
        if (Mode == RingMode.Ok) Icons.Draw(g, Ic.Check, ir, cur, Ui.S(3));
        else if (Mode == RingMode.Error) Icons.Draw(g, Ic.Warning, ir, cur, Ui.S(2.4));
        else Logo.Trident(g, ir, cur);
    }
}

sealed class LogItem : Item
{
    public Func<string> Provider = () => "";
    public override void Paint(Graphics g)
    {
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        using (var br = new SolidBrush(Pal.Dark ? Color.FromArgb(0x07, 0x0D, 0x0C) : Color.FromArgb(0xE0, 0xEE, 0xEB))) Ui.FillRound(g, br, r, Ui.S(12));
        var f = Ui.Font(8.5f, FontStyle.Regular, "Consolas");
        int lh = Ui.S(15); int max = Math.Max(1, (B.Height - Ui.S(16)) / lh);
        var lines = Provider().Split('\n'); int start = Math.Max(0, lines.Length - max);
        for (int i = start; i < lines.Length; i++)
            Ui.Text(g, lines[i].TrimEnd('\r'), f, Pal.Muted, new RectangleF(B.X + Ui.S(10), B.Y + Ui.S(8) + (i - start) * lh, B.Width - Ui.S(20), lh), Align.Start, false, false, true, false);
    }
}

sealed class BarItem : Item
{
    public Func<float> Value = () => 0f; public Func<Color>? Accent;
    public override void Paint(Graphics g)
    {
        var r = new RectangleF(B.X, B.Y, B.Width - 1, B.Height - 1);
        float rad = r.Height / 2f;
        using (var br = new SolidBrush(Pal.Surface2)) Ui.FillRound(g, br, r, rad);
        float v = Ui.Clamp01(Value());
        float w = Math.Max(v > 0 ? r.Height : 0, r.Width * v);
        if (w <= 0) return;
        var fr = new RectangleF(Ui.Rtl ? r.Right - w : r.X, r.Y, w, r.Height);
        using (var br = new SolidBrush(Accent != null ? Accent() : Pal.Blue)) Ui.FillRound(g, br, fr, rad);
    }
}

/// <summary>A scrolling page of custom-drawn items (no child controls, no flicker).</summary>
class CanvasPage : Control
{
    protected readonly List<Item> items = new();
    protected int contentH;           // design units
    int scroll; Item? hot, down; float ent = 1f;
    protected int Pad => Ui.S(28);
    protected double IW => Width / Ui.Scale - 56;

    public CanvasPage()
    {
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
        TabStop = false;
    }

    protected virtual void Build() { }
    public void Rebuild() { items.Clear(); hot = null; down = null; if (Width > 50) Build(); ClampScroll(); Invalidate(); }
    public void PlayEntrance() { ent = Settings.Animations ? 0f : 1f; scroll = 0; Invalidate(); }
    protected override void OnHandleCreated(EventArgs e) { base.OnHandleCreated(e); Anim.Tick += OnTick; }
    protected override void OnHandleDestroyed(EventArgs e) { Anim.Tick -= OnTick; base.OnHandleDestroyed(e); }
    protected override void OnSizeChanged(EventArgs e) { base.OnSizeChanged(e); if (Width > 50) Rebuild(); }
    protected override void OnVisibleChanged(EventArgs e) { base.OnVisibleChanged(e); if (Visible && items.Count == 0 && Width > 50) Rebuild(); }
    protected override void OnMouseEnter(EventArgs e) { base.OnMouseEnter(e); Focus(); }

    protected Rectangle R(double x, double y, double w, double h)
    {
        var r = new Rectangle(Pad + Ui.S(x), Ui.S(y), Ui.S(w), Ui.S(h));
        return Ui.Rtl ? new Rectangle(Width - r.Right, r.Y, r.Width, r.Height) : r;
    }
    protected Rectangle RP(double x, double y, double w, int hPx)
    {
        var r = new Rectangle(Pad + Ui.S(x), Ui.S(y), Ui.S(w), hPx);
        return Ui.Rtl ? new Rectangle(Width - r.Right, r.Y, r.Width, r.Height) : r;
    }

    protected T Add<T>(T it) where T : Item { items.Add(it); return it; }

    protected int AddText(string s, double size, FontStyle st, Color c, double x, double y, double w, Align a = Align.Start, bool wrap = true)
    {
        var f = Ui.Font(size, st); int h = Ui.TextH(s, f, Ui.S(w));
        items.Add(new TextItem { Text = s, F = f, Color = c, Alignment = a, Wrap = wrap, B = RP(x, y, w, h) });
        return (int)Math.Ceiling(h / Ui.Scale);
    }

    protected void Header(string title, string sub, ref int y)
    {
        y = 26;
        y += AddText(title, 22, FontStyle.Bold, Pal.Text, 0, y, IW) + 2;
        y += AddText(sub, 10.5f, FontStyle.Regular, Pal.Muted, 0, y, IW) + 22;
    }

    void ClampScroll() { int max = Math.Max(0, Ui.S(contentH + 28) - Height); scroll = Ui.Clamp(scroll, 0, max); }

    void OnTick(float dt)
    {
        if (!Visible || !IsHandleCreated || Width < 50) return;
        if (ent < 1f) { ent = Math.Min(1f, ent + dt / 0.45f); Invalidate(); return; }
        foreach (var it in items)
            if (it.Update(dt)) { var r = it.B; r.Offset(0, -scroll); r.Inflate(Ui.S(14), Ui.S(14)); Invalidate(r); }
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics; Ui.Quality(g); g.Clear(Pal.Bg);
        var clip = e.ClipRectangle; clip.Offset(0, scroll);
        foreach (var it in items)
        {
            if (!it.B.IntersectsWith(clip) || !it.Shown) continue;
            float off = 0;
            if (ent < 1f) { float d = Ui.Clamp01((ent * 1.6f) - (float)it.B.Y / Math.Max(1, Height) * 0.5f); off = (1 - Ui.EaseOut(d)) * Ui.S(22); }
            g.TranslateTransform(0, -scroll + off);
            it.Paint(g);
            g.ResetTransform();
        }
        int total = Ui.S(contentH + 28);
        if (total > Height)
        {
            float th = Math.Max(Ui.S(30), (float)Height * Height / total); float ty = (float)scroll / (total - Height) * (Height - th);
            float x = Ui.Rtl ? Ui.S(3) : Width - Ui.S(7);
            using var br = new SolidBrush(Ui.Alpha(Pal.Muted, 90));
            Ui.FillRound(g, br, new RectangleF(x, ty, Ui.S(4), th), Ui.S(2));
        }
    }

    Item? HitTest(Point p)
    {
        p.Offset(0, scroll);
        for (int i = items.Count - 1; i >= 0; i--) if (items[i].Interactive && items[i].B.Contains(p)) return items[i];
        return null;
    }
    protected override void OnMouseMove(MouseEventArgs e)
    {
        base.OnMouseMove(e);
        var h = HitTest(e.Location);
        if (h != hot) { if (hot != null) hot.Hover = false; hot = h; if (hot != null) hot.Hover = true; Cursor = hot != null ? Cursors.Hand : Cursors.Default; }
    }
    protected override void OnMouseLeave(EventArgs e) { base.OnMouseLeave(e); if (hot != null) hot.Hover = false; hot = null; Cursor = Cursors.Default; }
    protected override void OnMouseDown(MouseEventArgs e) { base.OnMouseDown(e); down = hot; if (down != null) down.Down = true; }
    protected override void OnMouseUp(MouseEventArgs e)
    {
        base.OnMouseUp(e);
        var d = down; down = null;
        if (d != null) { d.Down = false; if (d == HitTest(e.Location)) { var p = e.Location; p.Offset(0, scroll); d.Activate(p); Invalidate(); } }
    }
    protected override void OnMouseWheel(MouseEventArgs e)
    {
        base.OnMouseWheel(e);
        scroll -= e.Delta / 120 * Ui.S(70); ClampScroll(); Invalidate();
    }
}

/// <summary>Left (or right in RTL) navigation menu.</summary>
sealed class Sidebar : Control
{
    public int Index; public event Action<int>? Selected; public Func<Color>? StatusColor; public Func<string>? StatusText;
    float selY; int hover = -1; float hoverT;
    public const int Count = 6;
    static readonly Ic[] Icons_ = { Ic.Home, Ic.Shield, Ic.Guide, Ic.Usb, Ic.Settings, Ic.Info };
    int ItemH => Ui.S(46); int Top0 => Ui.S(112); int Gap => Ui.S(6);

    public Sidebar()
    {
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
        selY = -1;
    }
    protected override void OnHandleCreated(EventArgs e) { base.OnHandleCreated(e); Anim.Tick += OnTick; }
    protected override void OnHandleDestroyed(EventArgs e) { Anim.Tick -= OnTick; base.OnHandleDestroyed(e); }
    int TargetY(int i) => Top0 + i * (ItemH + Gap);
    int lastHover = -2;
    void OnTick(float dt)
    {
        if (!Visible) return;
        float t = TargetY(Index); bool mv;
        if (selY < 0 || !Settings.Animations) { mv = selY != t; selY = t; }
        else { float d = t - selY; mv = Math.Abs(d) > 0.3f; selY = mv ? selY + d * Math.Min(1f, dt * 14f) : t; }
        if (mv || hover != lastHover) { lastHover = hover; Invalidate(); }
    }
    protected override void OnMouseMove(MouseEventArgs e)
    {
        base.OnMouseMove(e);
        int i = (e.Y - Top0) / (ItemH + Gap); bool inside = e.Y >= Top0 && i >= 0 && i < Count && (e.Y - Top0) % (ItemH + Gap) < ItemH;
        hover = inside ? i : -1; Cursor = inside ? Cursors.Hand : Cursors.Default;
    }
    protected override void OnMouseLeave(EventArgs e) { base.OnMouseLeave(e); hover = -1; Cursor = Cursors.Default; }
    protected override void OnMouseUp(MouseEventArgs e)
    {
        base.OnMouseUp(e);
        if (hover >= 0 && hover != Index) { Index = hover; Selected?.Invoke(Index); }
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics; Ui.Quality(g);
        g.Clear(Pal.Dark ? Color.FromArgb(0x0C, 0x16, 0x15) : Color.FromArgb(0xFA, 0xFD, 0xFC));
        using (var pen = new Pen(Pal.Border)) { int bx = Ui.Rtl ? 0 : Width - 1; g.DrawLine(pen, bx, 0, bx, Height); }
        int pad = Ui.S(18); int lg = Ui.S(42);
        float lx = Ui.Rtl ? Width - pad - lg : pad;
        Logo.Mark(g, new RectangleF(lx, Ui.S(28), lg, lg));
        float tx = Ui.Rtl ? pad : pad + lg + Ui.S(12); float tw = Width - pad * 2 - lg - Ui.S(12);
        Ui.Text(g, Loc.T("app"), Ui.Font(12.5f, FontStyle.Bold), Pal.Text, new RectangleF(tx, Ui.S(28), tw, Ui.S(24)), Align.Start, false, false, true);
        Ui.Text(g, "PC", Ui.Font(9), Pal.Muted, new RectangleF(tx, Ui.S(52), tw, Ui.S(18)), Align.Start, false, false, true, false);

        float w = Width - pad * 2 + 1;
        if (selY >= 0)
        {
            var pr = new RectangleF(pad - Ui.S(4), selY, w + Ui.S(8) - 1, ItemH);
            using (var br = Ui.Brand(pr, 0f, Pal.Dark ? 60 : 45)) Ui.FillRound(g, br, pr, Ui.S(13));
            float bx = Ui.Rtl ? pr.Right - Ui.S(4) : pr.X;
            using (var br = Ui.Brand(new RectangleF(bx, pr.Y + Ui.S(10), Ui.S(4), pr.Height - Ui.S(20)), 90f)) Ui.FillRound(g, br, new RectangleF(bx, pr.Y + Ui.S(10), Ui.S(4), pr.Height - Ui.S(20)), Ui.S(2));
        }
        for (int i = 0; i < Count; i++)
        {
            float y = TargetY(i); bool on = i == Index;
            if (hover == i && !on) using (var br = new SolidBrush(Pal.Surface2)) Ui.FillRound(g, br, new RectangleF(pad - Ui.S(4), y, w + Ui.S(8) - 1, ItemH), Ui.S(13));
            Color c = on ? Pal.Text : Pal.Muted; int isz = Ui.S(22);
            float ix = Ui.Rtl ? Width - pad - isz - Ui.S(8) : pad + Ui.S(8);
            Icons.Draw(g, Icons_[i], new RectangleF(ix, y + (ItemH - isz) / 2f, isz, isz), on ? Pal.Blue : c);
            float nx = Ui.Rtl ? pad : pad + isz + Ui.S(18); float nw = Width - pad * 2 - isz - Ui.S(18) - Ui.S(8);
            Ui.Text(g, Loc.T("nav" + i), Ui.Font(10.5f, on ? FontStyle.Bold : FontStyle.Regular), c, new RectangleF(nx, y, nw, ItemH), Align.Start, true, false, true);
        }

        float fy = Height - Ui.S(112);
        var sc = StatusColor != null ? StatusColor() : Pal.Muted;
        var sr = new RectangleF(pad - Ui.S(4), fy, w + Ui.S(8) - 1, Ui.S(34));
        using (var br = new SolidBrush(Ui.Alpha(sc, 30))) Ui.FillRound(g, br, sr, Ui.S(10));
        // Status chip: [dot][gap][text] laid out from the start edge (right in Persian, left in English) with explicit,
        // non-overlapping rectangles. The text gets a generous clear gap from the dot (GDI+ RTL text overhangs a little).
        float dot = Ui.S(8), edge = Ui.S(14), gap = Ui.S(14), endPad = Ui.S(10);
        float dx = Ui.Rtl ? sr.Right - edge - dot : sr.X + edge;
        using (var br = new SolidBrush(sc)) g.FillEllipse(br, dx, sr.Y + (sr.Height - dot) / 2f, dot, dot);
        RectangleF tr;
        if (Ui.Rtl) { float right = dx - gap; tr = new RectangleF(sr.X + endPad, sr.Y, Math.Max(10, right - (sr.X + endPad)), sr.Height); }
        else { float left = dx + dot + gap; tr = new RectangleF(left, sr.Y, Math.Max(10, sr.Right - endPad - left), sr.Height); }
        Ui.Text(g, StatusText != null ? StatusText() : "", Ui.Font(9), Pal.Text, tr, Align.Start, true, false, true);
        Ui.Text(g, "v" + Application.ProductVersion.Split('+')[0], Ui.Font(8), Ui.Alpha(Pal.Muted, 160), new RectangleF(pad, Height - Ui.S(40), Width - pad * 2, Ui.S(18)), Align.Center, false, false, false, false);
    }
}
