using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using Microsoft.Win32;

namespace Peyvand;

/// <summary>Persian (Jalali) calendar. Same arithmetic as the Android app so both sides agree on the month.</summary>
static class Jalali
{
    static readonly int[] GDays = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };
    static readonly int[] JDays = { 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29 };

    public static int[] FromGregorian(int gy, int gm, int gd)
    {
        int gy2 = gy - 1600, gm2 = gm - 1, gd2 = gd - 1;
        int gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400;
        for (int i = 0; i < gm2; i++) gDayNo += GDays[i];
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) gDayNo += 1;
        gDayNo += gd2;
        int jDayNo = gDayNo - 79;
        int jNp = jDayNo / 12053;
        jDayNo %= 12053;
        int jy = 979 + 33 * jNp + 4 * (jDayNo / 1461);
        jDayNo %= 1461;
        if (jDayNo >= 366) { jy += (jDayNo - 1) / 365; jDayNo = (jDayNo - 1) % 365; }
        int k = 0;
        while (k < 11 && jDayNo >= JDays[k]) { jDayNo -= JDays[k]; k++; }
        return new[] { jy, k + 1, jDayNo + 1 };
    }

    public static string PeriodKey(DateTime d)
    {
        var j = FromGregorian(d.Year, d.Month, d.Day);
        return j[0].ToString("0000", CultureInfo.InvariantCulture) + "-" + j[1].ToString("00", CultureInfo.InvariantCulture);
    }

    public static int DaysUntilRenewal(DateTime d)
    {
        string start = PeriodKey(d);
        for (int i = 1; i <= 32; i++) if (PeriodKey(d.AddDays(i)) != start) return i;
        return 30;
    }

    static readonly string[] MonthsFa = { "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند" };

    public static string MonthLabel(string period, bool fa)
    {
        var p = period.Split('-');
        if (p.Length != 2 || !int.TryParse(p[1], out int m)) return period;
        return fa && m >= 1 && m <= 12 ? MonthsFa[m - 1] + " " + p[0] : p[0] + "/" + p[1];
    }
}

/// <summary>Very small JSON reader/writer (objects, arrays, strings, numbers, booleans) for the phone link and update file.</summary>
static class MiniJson
{
    public static object? Parse(string s)
    {
        int i = 0;
        try { var v = Value(s, ref i); return v; } catch { return null; }
    }

    public static Dictionary<string, object?>? ParseObject(string s) => Parse(s) as Dictionary<string, object?>;

    static void Ws(string s, ref int i) { while (i < s.Length && char.IsWhiteSpace(s[i])) i++; }

    static object? Value(string s, ref int i)
    {
        Ws(s, ref i);
        char c = s[i];
        if (c == '{')
        {
            var d = new Dictionary<string, object?>(); i++; Ws(s, ref i);
            if (s[i] == '}') { i++; return d; }
            while (true)
            {
                Ws(s, ref i); string k = Str(s, ref i); Ws(s, ref i);
                if (s[i++] != ':') throw new FormatException();
                d[k] = Value(s, ref i); Ws(s, ref i);
                if (s[i] == ',') { i++; continue; }
                if (s[i] == '}') { i++; return d; }
                throw new FormatException();
            }
        }
        if (c == '[')
        {
            var l = new List<object?>(); i++; Ws(s, ref i);
            if (s[i] == ']') { i++; return l; }
            while (true)
            {
                l.Add(Value(s, ref i)); Ws(s, ref i);
                if (s[i] == ',') { i++; continue; }
                if (s[i] == ']') { i++; return l; }
                throw new FormatException();
            }
        }
        if (c == '"') return Str(s, ref i);
        if (string.CompareOrdinal(s, i, "true", 0, 4) == 0) { i += 4; return true; }
        if (string.CompareOrdinal(s, i, "false", 0, 5) == 0) { i += 5; return false; }
        if (string.CompareOrdinal(s, i, "null", 0, 4) == 0) { i += 4; return null; }
        int st = i;
        while (i < s.Length && (char.IsDigit(s[i]) || s[i] == '-' || s[i] == '+' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++;
        return double.Parse(s.Substring(st, i - st), CultureInfo.InvariantCulture);
    }

    static string Str(string s, ref int i)
    {
        if (s[i] != '"') throw new FormatException();
        i++; var sb = new StringBuilder();
        while (s[i] != '"')
        {
            char c = s[i++];
            if (c != '\\') { sb.Append(c); continue; }
            char e = s[i++];
            switch (e)
            {
                case 'n': sb.Append('\n'); break;
                case 't': sb.Append('\t'); break;
                case 'r': sb.Append('\r'); break;
                case 'b': sb.Append('\b'); break;
                case 'f': sb.Append('\f'); break;
                case 'u': sb.Append((char)Convert.ToInt32(s.Substring(i, 4), 16)); i += 4; break;
                default: sb.Append(e); break;
            }
        }
        i++;
        return sb.ToString();
    }

    public static string Build(params (string Key, object Value)[] kv)
    {
        var sb = new StringBuilder("{");
        for (int n = 0; n < kv.Length; n++)
        {
            if (n > 0) sb.Append(',');
            sb.Append('"').Append(kv[n].Key).Append("\":");
            var v = kv[n].Value;
            if (v is bool b) sb.Append(b ? "true" : "false");
            else if (v is long || v is int) sb.Append(Convert.ToString(v, CultureInfo.InvariantCulture));
            else sb.Append('"').Append(Convert.ToString(v, CultureInfo.InvariantCulture)?.Replace("\\", "\\\\").Replace("\"", "\\\"")).Append('"');
        }
        return sb.Append('}').ToString();
    }

    public static long Long(Dictionary<string, object?> d, string k, long def = 0) =>
        d.TryGetValue(k, out var v) && v is double x ? (long)x : def;
    public static string Text(Dictionary<string, object?> d, string k, string def = "") =>
        d.TryGetValue(k, out var v) && v is string x ? x : def;
    public static bool Bool(Dictionary<string, object?> d, string k, bool def = false) =>
        d.TryGetValue(k, out var v) && v is bool x ? x : def;
}

/// <summary>
/// Free-allowance ledger of THIS computer for the current Persian month (one shared pool, whichever phone is plugged in).
/// The phone also carries its own counter and both sides always adopt the larger value, so neither reinstalling the phone
/// app, nor plugging another phone into this PC, nor using the same phone on another PC gives a fresh allowance.
/// Stored redundantly (AppData file, ProgramData file, registry), signed with a machine-bound key; the largest valid value wins.
/// A "high-water" clock stops going back to an earlier month by changing the Windows date.
/// </summary>
static class Ledger
{
    public const long FreeLimit = 300L * 1024 * 1024;
    const string PoolKey = "pool";
    const string ClockKey = "hw|clock";
    static readonly object Lock = new object();
    static Dictionary<string, long>? map;
    static DateTime lastClockSave = DateTime.MinValue;
    static DateTime lastSave = DateTime.MinValue;
    static bool dirty;

    static string AppDataFile => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Peyvand", "usage.ini");
    static string CommonFile => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "Peyvand", "usage.ini");
    const string RegPath = @"Software\Peyvand";

    static string? machineKey;
    static string MachineKey()
    {
        if (machineKey != null) return machineKey;
        string id = "";
        try { using var k = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Cryptography"); id = k?.GetValue("MachineGuid") as string ?? ""; } catch { }
        if (id.Length == 0) id = Environment.MachineName;
        return machineKey = id;
    }

    static string Mac(string body)
    {
        using var h = new HMACSHA256(SHA256.Create().ComputeHash(Encoding.UTF8.GetBytes(MachineKey() + "|peyvand-ledger-v2")));
        return BitConverter.ToString(h.ComputeHash(Encoding.UTF8.GetBytes(body))).Replace("-", "");
    }

    /// <summary>Parses signed text; returns false when the signature is wrong (edited by hand or copied from another PC).</summary>
    static bool Parse(string text, Dictionary<string, long> into)
    {
        try
        {
            var body = new StringBuilder(); string? mac = null;
            var rows = new List<string>();
            foreach (var l0 in text.Split('\n'))
            {
                var l = l0.TrimEnd('\r');
                if (l.Length == 0) continue;
                if (l.StartsWith("mac=")) { mac = l.Substring(4); continue; }
                body.Append(l).Append('\n'); rows.Add(l);
            }
            if (mac == null || !string.Equals(mac, Mac(body.ToString()), StringComparison.Ordinal)) return false;
            foreach (var l in rows)
            {
                var p = l.Split('|');
                if (p.Length == 3 && long.TryParse(p[2], out var v))
                {
                    string key = p[0] + "|" + p[1];
                    if (!into.TryGetValue(key, out var cur) || v > cur) into[key] = v;
                }
            }
            return true;
        }
        catch { return false; }
    }

    static void Load()
    {
        if (map != null) return;
        map = new Dictionary<string, long>();
        foreach (var path in new[] { AppDataFile, CommonFile })
        {
            try { if (File.Exists(path)) Parse(File.ReadAllText(path), map); } catch { }
        }
        try
        {
            using var k = Registry.CurrentUser.OpenSubKey(RegPath);
            if (k?.GetValue("ledger") is string t) Parse(t, map);
        }
        catch { }
    }

    static string Serialize()
    {
        var body = new StringBuilder();
        foreach (var kv in map!) { var k = kv.Key.Split('|'); body.Append(k[0]).Append('|').Append(k[1]).Append('|').Append(kv.Value).Append('\n'); }
        return body + "mac=" + Mac(body.ToString()) + "\n";
    }

    static void Save()
    {
        dirty = false; lastSave = DateTime.UtcNow;
        string text = Serialize();
        foreach (var path in new[] { AppDataFile, CommonFile })
        {
            try { Directory.CreateDirectory(Path.GetDirectoryName(path)!); File.WriteAllText(path, text); } catch { }
        }
        try { using var k = Registry.CurrentUser.CreateSubKey(RegPath); k?.SetValue("ledger", text); } catch { }
    }

    /// <summary>Current time that can never go backwards (protects the monthly renewal from clock tricks).</summary>
    public static DateTime Now()
    {
        lock (Lock)
        {
            Load();
            var now = DateTime.Now;
            long hw = map!.TryGetValue(ClockKey, out var v) ? v : 0;
            if (now.Ticks > hw)
            {
                map[ClockKey] = now.Ticks;
                if ((now - lastClockSave).TotalMinutes >= 1) { lastClockSave = now; Save(); }
                return now;
            }
            try { return new DateTime(hw); } catch { return now; }
        }
    }

    public static long Get(string period)
    {
        lock (Lock) { Load(); return map!.TryGetValue(PoolKey + "|" + period, out var v) ? v : 0; }
    }

    /// <summary>Keeps the larger value (usage only ever grows inside a month).</summary>
    public static long Raise(string period, long used)
    {
        lock (Lock)
        {
            Load();
            string key = PoolKey + "|" + period;
            map!.TryGetValue(key, out var cur);
            if (used > cur)
            {
                map[key] = used; cur = used;
                // live counter: write to disk at most every 3 s (and always when the allowance is used up)
                if (used >= FreeLimit || (DateTime.UtcNow - lastSave).TotalSeconds >= 3) Save(); else dirty = true;
            }
            return cur;
        }
    }

    public static bool Has(string period)
    {
        lock (Lock) { Load(); return map!.ContainsKey(PoolKey + "|" + period); }
    }

    /// <summary>Overwrites the stored value (the computer's record is the authority and may also be corrected).</summary>
    public static void Set(string period, long used)
    {
        lock (Lock) { Load(); map![PoolKey + "|" + period] = used; Save(); }
    }

    /// <summary>Writes any pending value (call when the phone disconnects or the app closes).</summary>
    public static void Flush() { lock (Lock) { if (map != null && dirty) Save(); } }
}

/// <summary>Live state received from the phone app over the side channel (abstract socket -> adb reverse -> 127.0.0.1:31417).</summary>
static class Usage
{
    public const int Port = 31417;
    public static string Serial = "";
    /// <summary>The free allowance is a ONE-TIME trial (same as the phone app); the ledger key is per Bazaar account.</summary>
    public const string TrialPeriod = "onetime";
    public static string Period = TrialPeriod;
    public static long Used;
    public static long Limit = Ledger.FreeLimit;
    public static bool Pro;
    public static bool UpdateOnly;
    public static bool PhoneLinked;
    public static DateTime LastStatus = DateTime.MinValue;
    public static string LastBye = "";
    public static event Action? Changed;
    /// <summary>Raised on the UI thread once per phone link when the plan (Pro or Free) becomes known or changes. Argument: true = Pro.</summary>
    public static event Action<bool>? PlanDetected;
    static int announced = -1;   // -1 none, 0 free, 1 pro

    static SynchronizationContext? ui;
    static TcpListener? listener;
    static volatile bool running;

    public static bool Exhausted => !Pro && Used >= Limit;
    public static bool ProRecent => Pro || (Settings.ProLastSeenUtc != 0 && (DateTime.UtcNow - new DateTime(Settings.ProLastSeenUtc, DateTimeKind.Utc)).TotalDays < 3);

    static void Raise() { var h = Changed; if (h == null) return; if (ui != null) ui.Post(_ => h(), null); else h(); }

    public static void Start()
    {
        if (running) return;
        ui = SynchronizationContext.Current;
        try
        {
            listener = new TcpListener(IPAddress.Loopback, Port);
            listener.Start();
            running = true;
            new Thread(AcceptLoop) { IsBackground = true, Name = "peyvand-ctl" }.Start();
        }
        catch { }
    }

    public static void Stop()
    {
        running = false;
        Ledger.Flush();
        try { listener?.Stop(); } catch { }
    }

    static void AcceptLoop()
    {
        while (running)
        {
            try
            {
                var c = listener!.AcceptTcpClient();
                new Thread(() => Serve(c)) { IsBackground = true }.Start();
            }
            catch { if (!running) return; Thread.Sleep(200); }
        }
    }

    static void Serve(TcpClient c)
    {
        try
        {
            using (c)
            using (var s = c.GetStream())
            using (var r = new StreamReader(s, Encoding.UTF8))
            {
                s.ReadTimeout = 45000;
                string? line;
                while ((line = r.ReadLine()) != null) Handle(line, s);
            }
        }
        catch { }
        PhoneLinked = false; announced = -1;
        Ledger.Flush();
        Raise();
    }

    static string HmacHex(string data)
    {
        using var h = new HMACSHA256(Encoding.UTF8.GetBytes(Brand.ControlSecret));
        var sb = new StringBuilder();
        foreach (var b in h.ComputeHash(Encoding.UTF8.GetBytes(data))) sb.Append(b.ToString("x2"));
        return sb.ToString();
    }

    static bool Verified(Dictionary<string, object?> o, string t)
    {
        long ts = MiniJson.Long(o, "ts");
        long now = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        if (Math.Abs(now - ts) > 600) return false;   // stale or replayed
        string body = string.Join("|", t, MiniJson.Text(o, "serial"), MiniJson.Bool(o, "pro") ? "true" : "false",
            MiniJson.Long(o, "used").ToString(CultureInfo.InvariantCulture), MiniJson.Text(o, "period"), ts.ToString(CultureInfo.InvariantCulture));
        return string.Equals(HmacHex(body), MiniJson.Text(o, "sig"), StringComparison.OrdinalIgnoreCase);
    }

    static void Handle(string line, NetworkStream s)
    {
        var o = MiniJson.ParseObject(line);
        if (o == null) return;
        string t = MiniJson.Text(o, "t");
        if (t == "hello" || t == "status")
        {
            // Only messages signed by the real phone app are trusted (a script on this PC cannot claim "Pro").
            if (!Verified(o, t)) return;
            string serial = MiniJson.Text(o, "serial", Serial);
            if (serial.Length == 0) serial = "unknown";
            // One-time trial: the ledger key is the phone's anonymous Bazaar-account tag (so another account on this PC is not penalised).
            string acct = MiniJson.Text(o, "acct");
            if (acct.Length > 24 || !System.Text.RegularExpressions.Regex.IsMatch(acct, "^[0-9a-fA-F]*$")) acct = "";
            string period = TrialPeriod + (acct.Length > 0 ? "-" + acct : "");
            long used = MiniJson.Long(o, "used");
            bool pro = MiniJson.Bool(o, "pro");
            Serial = serial; Period = period; PhoneLinked = true; LastStatus = DateTime.UtcNow;
            Pro = pro; UpdateOnly = MiniJson.Bool(o, "only"); Limit = Ledger.FreeLimit;   // the limit is decided here, not by the phone
            if (pro) Settings.NoteProSeen();
            // The computer's record has PRIORITY: if it already knows this account, the phone must take that value
            // (the phone adopts it from the "sync" reply). Only an unknown account starts from the phone's number.
            // Status messages sent before the phone adopted the sync are ignored for the counter.
            bool phoneSynced = MiniJson.Bool(o, "synced");
            long pool;
            if (t == "hello") pool = Ledger.Has(period) ? Ledger.Get(period) : Ledger.Raise(period, used);
            else if (phoneSynced) pool = Ledger.Raise(period, used);
            else pool = Ledger.Get(period);
            if (t == "hello" && !Ledger.Has(period)) Ledger.Set(period, pool);
            Used = pool;
            if (t == "hello" || (t == "status" && !phoneSynced))
            {
                var reply = Encoding.UTF8.GetBytes(MiniJson.Build(("t", "sync"), ("period", TrialPeriod), ("used", pool)) + "\n");
                s.Write(reply, 0, reply.Length); s.Flush();
            }
            int plan = pro ? 1 : 0;
            bool announce = announced != plan;
            announced = plan;
            Raise();
            if (announce) { var ph = PlanDetected; if (ph != null) { if (ui != null) ui.Post(_ => ph(pro), null); else ph(pro); } }
        }
        else if (t == "bye")
        {
            LastBye = MiniJson.Text(o, "reason");
            Ledger.Flush();
            Raise();
        }
    }
}

/// <summary>Optional update check for the Windows app (same update.json as the phone app, "pc_*" fields).</summary>
static class UpdateCheck
{
    public const int VersionCode = 1;
    public static int LatestCode, MinCode;
    public static string LatestName = "", Url = "";
    public static string[] NotesFa = new string[0], NotesEn = new string[0];
    public static bool Available => LatestCode > VersionCode;
    public static bool Required => MinCode > VersionCode;
    public static event Action? Changed;

    public static void CheckAsync()
    {
        if (Brand.UpdateUrl.Length == 0) return;
        var ctx = SynchronizationContext.Current;
        new Thread(() =>
        {
            try
            {
                var req = (HttpWebRequest)WebRequest.Create(Brand.UpdateUrl);
                req.Timeout = 8000; req.ReadWriteTimeout = 8000; req.UserAgent = "Peyvand";
                using var resp = req.GetResponse(); using var rd = new StreamReader(resp.GetResponseStream()!, Encoding.UTF8);
                var o = MiniJson.ParseObject(rd.ReadToEnd());
                if (o == null) return;
                LatestCode = (int)MiniJson.Long(o, "pc_versionCode");
                MinCode = (int)Math.Max(MiniJson.Long(o, "pc_minVersionCode"), MiniJson.Bool(o, "pc_force") ? LatestCode : 0);
                LatestName = MiniJson.Text(o, "pc_versionName", LatestCode.ToString());
                Url = MiniJson.Text(o, "pc_url", Brand.PcDownloadUrl);
                if (o.TryGetValue("pc_changelog", out var cl) && cl is Dictionary<string, object?> d)
                {
                    NotesFa = Lines(d, "fa"); NotesEn = Lines(d, "en");
                }
                if (ctx != null) ctx.Post(_ => Changed?.Invoke(), null); else Changed?.Invoke();
            }
            catch { }
        }) { IsBackground = true }.Start();
    }

    static string[] Lines(Dictionary<string, object?> d, string k)
    {
        var l = new List<string>();
        if (d.TryGetValue(k, out var v) && v is List<object?> a) foreach (var x in a) if (x is string s && s.Length > 0) l.Add(s);
        return l.ToArray();
    }
}
