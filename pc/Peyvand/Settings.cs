using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using Microsoft.Win32;

namespace Peyvand;

/// <summary>User settings, stored in %AppData%\Peyvand\settings.ini.</summary>
static class Settings
{
    public static string Language = "auto";   // auto | en | fa
    public static string Theme = "dark";      // dark | light | system
    public static bool Animations = true;
    public static bool StartWithWindows;
    public static bool StartMinimized;
    public static bool MinimizeToTray;
    public static bool AutoOpenApp = true;
    public static bool AutoInstall = true;
    public static bool AutoRelay = true;
    /// <summary>UTC ticks when the phone last reported an active Pro subscription (cached for 3 days).</summary>
    public static long ProLastSeenUtc;
    /// <summary>True once the Pro desktop features were switched on automatically after the first Pro detection.</summary>
    public static bool ProWelcomed;

    static string Dir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Peyvand");
    static string FilePath => Path.Combine(Dir, "settings.ini");

    public static void Reset()
    {
        Language = "auto"; Theme = "dark"; Animations = true; StartWithWindows = false; StartMinimized = false;
        MinimizeToTray = false; AutoOpenApp = true; AutoInstall = true; AutoRelay = true;
        Save();
        ApplyStartup();
    }

    public static void Load()
    {
        try
        {
            if (!File.Exists(FilePath)) return;
            var map = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            foreach (var line in File.ReadAllLines(FilePath))
            {
                int i = line.IndexOf('=');
                if (i <= 0) continue;
                map[line.Substring(0, i).Trim()] = line.Substring(i + 1).Trim();
            }
            // Files written by older builds may hold a stale language: without the marker, go back to "follow Windows".
            Language = map.ContainsKey("langver") ? Get(map, "language", Language) : "auto";
            Theme = Get(map, "theme", Theme);
            Animations = GetB(map, "animations", Animations);
            StartWithWindows = GetB(map, "startWithWindows", StartWithWindows);
            StartMinimized = GetB(map, "startMinimized", StartMinimized);
            MinimizeToTray = GetB(map, "minimizeToTray", MinimizeToTray);
            AutoOpenApp = GetB(map, "autoOpenApp", AutoOpenApp);
            AutoInstall = GetB(map, "autoInstall", AutoInstall);
            AutoRelay = GetB(map, "autoRelay", AutoRelay);
            ProWelcomed = GetB(map, "proWelcomed", ProWelcomed);
            if (map.TryGetValue("proSeen", out var ps) && long.TryParse(ps, out var pl)) ProLastSeenUtc = pl;
        }
        catch { }
    }

    public static void Save()
    {
        try
        {
            Directory.CreateDirectory(Dir);
            File.WriteAllLines(FilePath, new[]
            {
                "langver=2",
                "language=" + Language,
                "theme=" + Theme,
                "animations=" + Animations,
                "startWithWindows=" + StartWithWindows,
                "startMinimized=" + StartMinimized,
                "minimizeToTray=" + MinimizeToTray,
                "autoOpenApp=" + AutoOpenApp,
                "autoInstall=" + AutoInstall,
                "autoRelay=" + AutoRelay,
                "proSeen=" + ProLastSeenUtc,
                "proWelcomed=" + ProWelcomed,
            });
        }
        catch { }
    }

    public static void NoteProSeen() { ProLastSeenUtc = DateTime.UtcNow.Ticks; Save(); }

    static string Get(Dictionary<string, string> m, string k, string d) => m.TryGetValue(k, out var v) && v.Length > 0 ? v : d;
    static bool GetB(Dictionary<string, string> m, string k, bool d) => m.TryGetValue(k, out var v) && bool.TryParse(v, out var b) ? b : d;

    /// <summary>True when the dark palette should be used.</summary>
    public static bool IsDark()
    {
        if (Theme == "dark") return true;
        if (Theme == "light") return false;
        try
        {
            using var k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            var v = k?.GetValue("AppsUseLightTheme");
            if (v is int i) return i == 0;
        }
        catch { }
        return true;
    }

    const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

    public static void ApplyStartup()
    {
        try
        {
            using var k = Registry.CurrentUser.OpenSubKey(RunKey, true);
            if (k == null) return;
            if (StartWithWindows && Usage.ProRecent)
                k.SetValue("CableNet", "\"" + System.Windows.Forms.Application.ExecutablePath + "\" --tray");
            else
                k.DeleteValue("CableNet", false);
        }
        catch { }
    }
}
