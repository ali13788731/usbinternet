using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace CableInternet;

enum Phase { ToolsMissing, Starting, NoDevice, Unauthorized, Offline, Preparing, Failed, Ready, WaitingPhone, Connected }

/// <summary>The connection state machine (adb polling, app install, adb reverse, relay). No UI code.</summary>
sealed class Controller : IDisposable
{
    public Phase Phase = Phase.Starting;
    public string? Serial, Brand, Model, FailKey;
    public int NoDeviceTicks;
    public DateTime? ConnectedSince;
    public readonly RelayManager Relay = new();
    public event Action? Changed;
    public event Action? GuideSuggested;

    readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 1500 };
    bool busy, guideShown, relayAllowed = true, disposed;
    string? preparedSerial, failedSerial;
    int tick;

    public Controller() { timer.Tick += async (_, _) => await PollAsync(); }

    public async void Start()
    {
        if (!Paths.ToolsPresent) { Set(Phase.ToolsMissing); return; }
        await Adb.StartServerAsync();
        if (disposed) return;
        timer.Start();
        await PollAsync();
    }

    void Set(Phase p)
    {
        bool ch = Phase != p;
        Phase = p;
        if (p == Phase.Connected) ConnectedSince ??= DateTime.Now; else ConnectedSince = null;
        if (ch) Changed?.Invoke();
    }

    public string Title => Loc.T(Phase switch
    {
        Phase.ToolsMissing => "ph_tools_t", Phase.Starting => "ph_start_t",
        Phase.NoDevice => NoDeviceTicks >= 6 ? "ph_nodev2_t" : "ph_nodev_t",
        Phase.Unauthorized => "ph_auth_t", Phase.Offline => "ph_off_t", Phase.Preparing => "ph_prep_t",
        Phase.Failed => "ph_fail_t", Phase.Ready => "ph_ready_t", Phase.WaitingPhone => "ph_wait_t", _ => "ph_conn_t",
    });

    public string Sub => Phase == Phase.Failed ? Loc.T(FailKey ?? "e_install") : Loc.T(Phase switch
    {
        Phase.ToolsMissing => "ph_tools_s", Phase.Starting => "ph_start_s",
        Phase.NoDevice => NoDeviceTicks >= 6 ? "ph_nodev2_s" : "ph_nodev_s",
        Phase.Unauthorized => "ph_auth_s", Phase.Offline => "ph_off_s", Phase.Preparing => "ph_prep_s",
        Phase.Ready => "ph_ready_s", Phase.WaitingPhone => "ph_wait_s", _ => "ph_conn_s",
    });

    public RingMode Mode => Phase switch
    {
        Phase.Connected => RingMode.Ok, Phase.Failed or Phase.ToolsMissing => RingMode.Error,
        Phase.Unauthorized or Phase.Offline or Phase.WaitingPhone or Phase.Ready => RingMode.Action,
        _ => RingMode.Search,
    };

    public System.Drawing.Color Accent => Mode == RingMode.Ok ? Pal.Green : Mode == RingMode.Error ? Pal.Red : Mode == RingMode.Action ? Pal.Amber : Pal.Blue;

    /// <summary>0 pending, 1 active, 2 done, 3 failed — for the four steps.</summary>
    public int StepState(int i)
    {
        int done = Phase switch
        {
            Phase.NoDevice or Phase.Starting or Phase.ToolsMissing => 0,
            Phase.Unauthorized or Phase.Offline => 1,
            Phase.Preparing or Phase.Failed => 2,
            Phase.Ready or Phase.WaitingPhone => 3,
            _ => 4,
        };
        if (Phase == Phase.Failed && i == 2) return 3;
        if (Phase == Phase.ToolsMissing && i == 0) return 3;
        return i < done ? 2 : i == done ? 1 : 0;
    }

    public string SessionText()
    {
        if (ConnectedSince == null) return "—";
        var t = DateTime.Now - ConnectedSince.Value;
        return string.Format("{0:00}:{1:00}:{2:00}", (int)t.TotalHours, t.Minutes, t.Seconds);
    }

    public string PhoneText => Model != null ? ((Brand ?? "") + " " + Model).Trim() : Serial ?? Loc.T("tl_none");

    async Task PollAsync()
    {
        if (busy || disposed) return;
        busy = true; tick++;
        try
        {
            var dev = await Adb.GetDeviceAsync();
            switch (dev.State)
            {
                case DeviceState.None:
                    Reset(); NoDeviceTicks++; Set(Phase.NoDevice); if (NoDeviceTicks == 6) Changed?.Invoke();
                    if (NoDeviceTicks == 4 && !guideShown) { guideShown = true; GuideSuggested?.Invoke(); }
                    break;
                case DeviceState.Unauthorized: Reset(); NoDeviceTicks = 0; Serial = dev.Serial; Set(Phase.Unauthorized); break;
                case DeviceState.Offline: Reset(); Set(Phase.Offline); break;
                default: NoDeviceTicks = 0; await HandleReadyAsync(dev.Serial!); break;
            }
        }
        catch (Exception ex) { Relay.AddLog("poll error: " + ex.Message); }
        finally { busy = false; }
    }

    void Reset() { preparedSerial = null; failedSerial = null; Model = null; Brand = null; }

    async Task HandleReadyAsync(string serial)
    {
        Serial = serial;
        if (failedSerial == serial) { Set(Phase.Failed); return; }
        if (preparedSerial != serial) { await PrepareAsync(serial); return; }
        if (!relayAllowed && !Settings.AutoRelay) { Set(Phase.Ready); return; }
        if (!Relay.Running) Relay.Start();
        if (tick % 5 == 0 && !RelayManager.PhoneConnected() && !await Adb.HasReverseAsync(serial)) await Adb.ReverseAsync(serial);
        Set(RelayManager.PhoneConnected() ? Phase.Connected : Phase.WaitingPhone);
    }

    async Task PrepareAsync(string serial)
    {
        Set(Phase.Preparing);
        try
        {
            Brand = await Adb.GetPropAsync(serial, "ro.product.brand");
            Model = await Adb.GetPropAsync(serial, "ro.product.model");
            if (Brand.Length > 0) Brand = char.ToUpper(Brand[0]) + Brand.Substring(1);
        }
        catch { }
        int bundled = Adb.BundledVersion();
        int? installed = await Adb.InstalledVersionAsync(serial);
        if (installed == null && !Settings.AutoInstall) { Fail(serial, "e_notinst"); return; }
        if (Settings.AutoInstall && (installed == null || installed < bundled))
        {
            var err = await Adb.InstallAppAsync(serial);
            if (err != null) { Relay.AddLog("install: " + err); Fail(serial, ErrKey(err)); return; }
        }
        if (!await Adb.ReverseAsync(serial)) { Fail(serial, "e_reverse"); return; }
        relayAllowed = Settings.AutoRelay;
        if (Settings.AutoRelay && !Relay.Start()) { Fail(serial, "e_relay"); return; }
        if (Settings.AutoOpenApp) await Adb.LaunchAppAsync(serial);
        preparedSerial = serial; failedSerial = null;
        Set(Settings.AutoRelay ? Phase.WaitingPhone : Phase.Ready);
    }

    static string ErrKey(string err)
    {
        if (err == "MISSING_APK") return "e_apk";
        if (err.Contains("USER_RESTRICTED")) return "e_restricted";
        if (err.Contains("INSTALL_CANCELED") || err.Contains("cancel")) return "e_cancel";
        if (err.Contains("INSUFFICIENT_STORAGE")) return "e_storage";
        return "e_install";
    }

    void Fail(string serial, string key) { failedSerial = serial; FailKey = key; Set(Phase.Failed); Changed?.Invoke(); }

    // ---- user actions ----
    public void Retry() { failedSerial = null; preparedSerial = null; NoDeviceTicks = 0; Relay.Stop(); relayAllowed = true; Set(Phase.Starting); _ = PollAsync(); }
    public void StartSharing() { relayAllowed = true; if (!Relay.Running) Relay.Start(); if (Serial != null) _ = Adb.ReverseAsync(Serial); Set(Phase.WaitingPhone); }
    public async void OpenApp() { if (Serial != null) await Adb.LaunchAppAsync(Serial); }
    public async void Reinstall()
    {
        if (Serial == null) return;
        Set(Phase.Preparing);
        var err = await Adb.InstallAppAsync(Serial);
        if (err != null) { Relay.AddLog("install: " + err); Fail(Serial, ErrKey(err)); return; }
        preparedSerial = null; failedSerial = null;
    }
    public async void RestartAdb()
    {
        Relay.Stop(); Adb.KillServer(); Reset(); Set(Phase.Starting);
        await Adb.StartServerAsync();
        await PollAsync();
    }
    public void Check() { _ = PollAsync(); }

    public string Diagnostics()
    {
        var sb = new StringBuilder();
        sb.AppendLine("Cable Internet " + Application.ProductVersion.Split('+')[0]);
        sb.AppendLine("Windows: " + WindowsName());
        sb.AppendLine(".NET Framework: " + Environment.Version);
        sb.AppendLine("Phase: " + Phase + "  Serial: " + (Serial ?? "-") + "  Phone: " + PhoneText);
        sb.AppendLine("Relay running: " + Relay.Running + "  Phone connected to relay: " + RelayManager.PhoneConnected());
        sb.AppendLine("Tools present: " + Paths.ToolsPresent + "  APK: " + File.Exists(Paths.Apk) + "  APK version: " + Adb.BundledVersion());
        sb.AppendLine("--- log ---"); sb.AppendLine(Relay.LogText);
        return sb.ToString();
    }

    public static string WindowsName()
    {
        try
        {
            using var k = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows NT\CurrentVersion");
            return (k?.GetValue("ProductName") ?? "Windows") + " " + (k?.GetValue("DisplayVersion") ?? "") + " (build " + (k?.GetValue("CurrentBuild") ?? "?") + ")";
        }
        catch { return "Windows"; }
    }

    public void Dispose()
    {
        disposed = true; timer.Stop(); Relay.Stop(); Adb.KillServer();
    }
}
