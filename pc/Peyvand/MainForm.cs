using System;
using System.Drawing;
using System.Windows.Forms;

namespace Peyvand;

sealed class MainForm : Form
{
    readonly Controller ctl = new Controller();
    readonly Sidebar side = new Sidebar();
    readonly CanvasPage[] pages;
    readonly NotifyIcon tray = new NotifyIcon();
    readonly ToolStripMenuItem miShow = new ToolStripMenuItem(), miExit = new ToolStripMenuItem();
    bool exiting, hideFirst;
    int cur = -1;

    public MainForm(bool startHidden)
    {
        hideFirst = startHidden;
        Icon = Logo.CreateIcon(64);
        ClientSize = new Size(Ui.S(1000), Ui.S(660));
        MinimumSize = new Size(Ui.S(900), Ui.S(620));
        StartPosition = FormStartPosition.CenterScreen;
        DoubleBuffered = true;

        pages = new CanvasPage[] { new HomePage(ctl), new SubscriptionPage(ctl), new GuidePage(ctl), new RequirementsPage(ctl), new SettingsPage(ctl), new AboutPage(ctl) };
        foreach (var p in pages) { p.Dock = DockStyle.Fill; p.Visible = false; Controls.Add(p); }
        side.Width = Ui.S(236);
        side.StatusColor = () => ctl.Accent;
        side.StatusText = () => ctl.Title;
        Controls.Add(side);

        side.Selected += i => ShowPage(i);
        AppBus.Navigate = i => { side.Index = i; ShowPage(i); };
        AppBus.LanguageChanged = ApplyLanguage;
        AppBus.ThemeChanged = ApplyTheme;
        ctl.Changed += () => side.Invalidate();
        ctl.GuideSuggested += () => { if (cur == 0) AppBus.Navigate?.Invoke(2); };
        Usage.PlanDetected += OnPlanDetected;
        ctl.Notify += text => { try { tray.ShowBalloonTip(6000, Loc.T("app"), text, ToolTipIcon.Warning); System.Media.SystemSounds.Exclamation.Play(); } catch { } };

        var menu = new ContextMenuStrip();
        miShow.Click += (_, _) => RestoreFromTray();
        miExit.Click += (_, _) => { exiting = true; Close(); };
        menu.Items.Add(miShow); menu.Items.Add(miExit);
        tray.ContextMenuStrip = menu; tray.Icon = Icon; tray.Visible = true;
        tray.DoubleClick += (_, _) => RestoreFromTray();

        ApplyTheme();
        ApplyLanguage();
        ShowPage(0);
    }

    protected override void SetVisibleCore(bool value)
    {
        if (hideFirst) { hideFirst = false; if (!IsHandleCreated) CreateHandle(); value = false; }
        base.SetVisibleCore(value);
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        Ui.DarkTitleBar(Handle, Pal.Dark);
        ctl.Start();
    }

    void ShowPage(int i)
    {
        if (i == cur) return;
        cur = i; side.Index = i;
        for (int k = 0; k < pages.Length; k++) pages[k].Visible = k == i;
        pages[i].Rebuild(); pages[i].PlayEntrance(); pages[i].Focus();
        side.Invalidate();
    }

    void ApplyLanguage()
    {
        Text = Loc.T("app");
        side.Dock = Ui.Rtl ? DockStyle.Right : DockStyle.Left;
        miShow.Text = Loc.T("tray_show"); miExit.Text = Loc.T("tray_exit"); tray.Text = Loc.T("app");
        foreach (var p in pages) p.Rebuild();
        side.Invalidate();
    }

    void ApplyTheme()
    {
        Pal.Apply(Settings.IsDark());
        BackColor = Pal.Bg;
        if (IsHandleCreated) Ui.DarkTitleBar(Handle, Pal.Dark);
        foreach (var p in pages) { p.BackColor = Pal.Bg; p.Rebuild(); }
        side.Invalidate();
    }

    /// <summary>The phone just linked: announce the plan and, for Pro, switch the Pro desktop features on (once).</summary>
    void OnPlanDetected(bool pro)
    {
        try
        {
            if (pro && !Settings.ProWelcomed)
            {
                Settings.ProWelcomed = true; Settings.StartWithWindows = true; Settings.MinimizeToTray = true;
                Settings.Save(); Settings.ApplyStartup();
            }
            string msg = pro ? Loc.T("pl_pro_n") : string.Format(Loc.T("pl_free_n"), Fmt.Mb(Math.Max(0, Usage.Limit - Usage.Used)));
            tray.ShowBalloonTip(6000, Loc.T("app"), msg, ToolTipIcon.Info);
            System.Media.SystemSounds.Asterisk.Play();
        }
        catch { }
    }

    void RestoreFromTray()
    {
        Show(); if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
        Anim.Paused = false; Activate();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (!exiting && Settings.MinimizeToTray && Usage.ProRecent && e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true; Hide(); Anim.Paused = true; return;
        }
        base.OnFormClosing(e);
        if (!e.Cancel) { tray.Visible = false; tray.Dispose(); ctl.Dispose(); }
    }
}
