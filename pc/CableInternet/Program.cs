using System.Threading;

namespace CableInternet;

static class Program
{
    [STAThread]
    static void Main()
    {
        using var mutex = new Mutex(true, "CableInternet.PC.SingleInstance", out bool first);
        if (!first)
        {
            MessageBox.Show("برنامه از قبل در حال اجراست.", "کابل اینترنت",
                MessageBoxButtons.OK, MessageBoxIcon.Information,
                MessageBoxDefaultButton.Button1, MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign);
            return;
        }

        ApplicationConfiguration.Initialize();
        Application.ThreadException += (_, e) => ShowError(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => ShowError(e.ExceptionObject as Exception);
        Application.Run(new MainForm());
    }

    static void ShowError(Exception? ex)
    {
        MessageBox.Show("خطای غیرمنتظره:\n" + ex?.Message, "کابل اینترنت",
            MessageBoxButtons.OK, MessageBoxIcon.Error,
            MessageBoxDefaultButton.Button1, MessageBoxOptions.RtlReading | MessageBoxOptions.RightAlign);
    }
}
