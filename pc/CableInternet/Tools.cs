using System.Diagnostics;
using System.Net.NetworkInformation;
using System.Text;
using System.Text.RegularExpressions;

namespace CableInternet;

/// <summary>Locations of the bundled helper programs (they live in the "tools" folder next to the exe).</summary>
static class Paths
{
    public static readonly string BaseDir =
        Path.GetDirectoryName(Environment.ProcessPath) ?? AppContext.BaseDirectory;
    public static readonly string ToolsDir = Path.Combine(BaseDir, "tools");
    public static string Adb => Path.Combine(ToolsDir, "adb.exe");
    public static string Relay => Path.Combine(ToolsDir, "gnirehtet.exe");
    public static string Apk => Path.Combine(ToolsDir, "app.apk");
    public static string VersionFile => Path.Combine(ToolsDir, "app.version");
    public static bool ToolsPresent => File.Exists(Adb) && File.Exists(Relay);
}

static class Proc
{
    /// <summary>Runs a console program hidden and returns its exit code and combined output.</summary>
    public static async Task<(int Code, string Output)> RunAsync(string file, string args, int timeoutMs = 15000)
    {
        var psi = new ProcessStartInfo(file, args)
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
            WorkingDirectory = Paths.ToolsDir,
        };
        using var p = new Process { StartInfo = psi };
        var sb = new StringBuilder();
        void Add(string? line)
        {
            if (line == null) return;
            lock (sb) sb.AppendLine(line);
        }
        p.OutputDataReceived += (_, e) => Add(e.Data);
        p.ErrorDataReceived += (_, e) => Add(e.Data);
        try { p.Start(); }
        catch (Exception ex) { return (-1, ex.Message); }
        p.BeginOutputReadLine();
        p.BeginErrorReadLine();
        using var cts = new CancellationTokenSource(timeoutMs);
        try
        {
            await p.WaitForExitAsync(cts.Token);
        }
        catch (OperationCanceledException)
        {
            try { p.Kill(true); } catch { }
            return (-2, "timeout");
        }
        lock (sb) return (p.ExitCode, sb.ToString());
    }
}

enum DeviceState { None, Unauthorized, Offline, Ready }

sealed record DeviceInfo(DeviceState State, string? Serial);

static class Adb
{
    public const string PackageName = "app.usbnet";
    public const int RelayPort = 31416;

    /// <summary>
    /// Starts the adb server. Output is NOT redirected on purpose: the background server process
    /// would keep redirected pipes open forever and the call would never finish.
    /// </summary>
    public static Task StartServerAsync() => Task.Run(() =>
    {
        try
        {
            var psi = new ProcessStartInfo(Paths.Adb, "start-server")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                WorkingDirectory = Paths.ToolsDir,
            };
            using var p = Process.Start(psi);
            p?.WaitForExit(15000);
        }
        catch { }
    });

    /// <summary>Stops the adb server so no background process keeps files in the install folder locked.</summary>
    public static void KillServer()
    {
        try
        {
            var psi = new ProcessStartInfo(Paths.Adb, "kill-server")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                WorkingDirectory = Paths.ToolsDir,
            };
            using var p = Process.Start(psi);
            p?.WaitForExit(3000);
        }
        catch { }
    }

    public static async Task<DeviceInfo> GetDeviceAsync()
    {
        var (_, output) = await Proc.RunAsync(Paths.Adb, "devices", 8000);
        string? ready = null, unauth = null, offline = null;
        foreach (var raw in output.Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0 || line.StartsWith("*") || line.StartsWith("List of")) continue;
            var parts = line.Split(new[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length < 2) continue;
            var serial = parts[0];
            switch (parts[1])
            {
                case "device": ready ??= serial; break;
                case "unauthorized": unauth ??= serial; break;
                case "offline": offline ??= serial; break;
            }
        }
        if (ready != null) return new DeviceInfo(DeviceState.Ready, ready);
        if (unauth != null) return new DeviceInfo(DeviceState.Unauthorized, unauth);
        if (offline != null) return new DeviceInfo(DeviceState.Offline, offline);
        return new DeviceInfo(DeviceState.None, null);
    }

    static Task<(int Code, string Output)> Run(string serial, string args, int timeoutMs = 15000) =>
        Proc.RunAsync(Paths.Adb, $"-s {serial} {args}", timeoutMs);

    public static async Task<int?> InstalledVersionAsync(string serial)
    {
        var (_, o) = await Run(serial, $"shell pm list packages {PackageName}");
        bool installed = o.Split('\n').Any(l => l.Trim() == $"package:{PackageName}");
        if (!installed) return null;
        var (_, d) = await Run(serial, $"shell dumpsys package {PackageName}");
        var m = Regex.Match(d, @"versionCode=(\d+)");
        return m.Success ? int.Parse(m.Groups[1].Value) : 0;
    }

    public static int BundledVersion()
    {
        try
        {
            if (File.Exists(Paths.VersionFile) &&
                int.TryParse(File.ReadAllText(Paths.VersionFile).Trim(), out var v)) return v;
        }
        catch { }
        return 1;
    }

    /// <summary>Installs the bundled APK. Returns null on success, otherwise an error code/message.</summary>
    public static async Task<string?> InstallAppAsync(string serial)
    {
        if (!File.Exists(Paths.Apk)) return "MISSING_APK";
        var (_, o) = await Run(serial, $"install -r -t \"{Paths.Apk}\"", 180000);
        if (o.Contains("Success")) return null;
        if (o.Contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || o.Contains("INSTALL_FAILED_VERSION_DOWNGRADE"))
        {
            await Run(serial, $"uninstall {PackageName}", 30000);
            (_, o) = await Run(serial, $"install -t \"{Paths.Apk}\"", 180000);
            if (o.Contains("Success")) return null;
        }
        return string.IsNullOrWhiteSpace(o) ? "UNKNOWN" : o.Trim();
    }

    public static async Task<bool> ReverseAsync(string serial)
    {
        var (code, _) = await Run(serial, $"reverse localabstract:gnirehtet tcp:{RelayPort}");
        return code == 0;
    }

    public static async Task<bool> HasReverseAsync(string serial)
    {
        var (_, o) = await Run(serial, "reverse --list", 8000);
        return o.Contains("gnirehtet");
    }

    public static Task LaunchAppAsync(string serial) =>
        Run(serial, $"shell am start -n {PackageName}/.MainActivity");
}

/// <summary>Runs the gnirehtet relay (the part that shares this computer's internet).</summary>
sealed class RelayManager : IDisposable
{
    Process? _p;
    readonly Queue<string> _log = new();

    public bool Running => _p is { HasExited: false };

    public string LogText
    {
        get { lock (_log) return string.Join(Environment.NewLine, _log); }
    }

    public void AddLog(string line)
    {
        lock (_log)
        {
            _log.Enqueue(line);
            while (_log.Count > 300) _log.Dequeue();
        }
    }

    public bool Start()
    {
        if (Running) return true;
        // Clean up a relay left over from an earlier run or crash; it would hold the port.
        foreach (var old in Process.GetProcessesByName("gnirehtet"))
        {
            try { old.Kill(true); old.WaitForExit(2000); } catch { }
        }
        try
        {
            var psi = new ProcessStartInfo(Paths.Relay, "relay")
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                WorkingDirectory = Paths.ToolsDir,
            };
            _p = new Process { StartInfo = psi, EnableRaisingEvents = true };
            _p.OutputDataReceived += (_, e) => { if (e.Data != null) AddLog(e.Data); };
            _p.ErrorDataReceived += (_, e) => { if (e.Data != null) AddLog(e.Data); };
            _p.Start();
            _p.BeginOutputReadLine();
            _p.BeginErrorReadLine();
            return true;
        }
        catch (Exception ex)
        {
            AddLog("relay start failed: " + ex.Message);
            return false;
        }
    }

    public void Stop()
    {
        try { if (_p is { HasExited: false }) _p.Kill(true); } catch { }
        _p = null;
    }

    /// <summary>True while the phone app holds a connection to the relay (through adb reverse).</summary>
    public static bool PhoneConnected()
    {
        try
        {
            return IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpConnections()
                .Any(c => c.State == TcpState.Established && c.LocalEndPoint.Port == Adb.RelayPort);
        }
        catch { return false; }
    }

    public void Dispose() => Stop();
}
