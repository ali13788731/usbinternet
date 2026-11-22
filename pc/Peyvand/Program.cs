using System;
using System.Threading;
using System.Windows.Forms;

namespace Peyvand;

static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        Settings.Load();
        Loc.Apply(Settings.Language);
        using var mutex = new Mutex(true, "Peyvand.PC.SingleInstance", out bool first);
        if (!first)
        {
            MessageBox.Show(Loc.T("running"), Loc.T("app"), MessageBoxButtons.OK, MessageBoxIcon.Information,
                MessageBoxDefaultButton.Button1, SettingsPage.Opts());
            return;
        }

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.ThreadException += (_, e) => ShowError(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => ShowError(e.ExceptionObject as Exception);

        Ui.InitScale();
        Pal.Apply(Settings.IsDark());
        Anim.Start();

        bool tray = Array.IndexOf(args, "--tray") >= 0 || Settings.StartMinimized;
        if (!tray) using (var splash = new SplashForm()) splash.ShowDialog();
        Application.Run(new MainForm(tray));
    }

    static void ShowError(Exception? ex)
    {
        MessageBox.Show(ex?.Message ?? "Error", Loc.T("app"), MessageBoxButtons.OK, MessageBoxIcon.Error,
            MessageBoxDefaultButton.Button1, SettingsPage.Opts());
    }
}
