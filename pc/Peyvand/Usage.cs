using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;

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
/// Monthly usage per phone, kept on this computer (survives reinstalling the phone app).
/// File: %AppData%\Peyvand\usage.ini, signed so casual edits are ignored.
/// </summary>
static class Ledger
{
    public const long FreeLimit = 100L * 1024 * 1024;
    static readonly object Lock = new object();
    static Dictionary<string, long>? map;

    static string Dir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Peyvand");
    static string FilePath => Path.Combine(Dir, "usage.ini");

    static string Mac(string body)
    {
        using var h = new HMACSHA256(SHA256.Create().ComputeHash(Encoding.UTF8.GetBytes(Environment.MachineName + "|" + Environment.UserName + "|peyvand-ledger")));
        return BitConverter.ToString(h.ComputeHash(Encoding.UTF8.GetBytes(body))).Replace("-", "");
    }

    static void Load()
    {
        if (map != null) return;
        map = new Dictionary<string, long>();
        try
        {
            if (!File.Exists(FilePath)) return;
            var lines = File.ReadAllLines(FilePath);
            var body = new StringBuilder(); string? mac = null;
            foreach (var l in lines)
            {
                if (l.StartsWith("mac=")) { mac = l.Substring(4); continue; }
                body.Append(l).Append('\n');
            }
            if (mac == null || !string.Equals(mac, Mac(body.ToString()), StringComparison.Ordinal)) return;
            foreach (var l in lines)
            {
                var p = l.Split('|');
                if (p.Length == 3 && long.TryParse(p[2], out var v)) map[p[0] + "|" + p[1]] = v;
            }
        }
        catch { }
    }

    static void Save()
    {
        try
        {
            Directory.CreateDirectory(Dir);
            var body = new StringBuilder();
            foreach (var kv in map!) { var k = kv.Key.Split('|'); body.Append(k[0]).Append('|').Append(k[1]).Append('|').Append(kv.Value).Append('\n'); }
            File.WriteAllText(FilePath, body + "mac=" + Mac(body.ToString()) + "\n");
        }
        catch { }
    }

    public static long Get(string serial, string period)
    {
        lock (Lock) { Load(); return map!.TryGetValue(serial + "|" + period, out var v) ? v : 0; }
    }

    /// <summary>Keeps the larger value (usage only ever grows inside a month).</summary>
    public static long Raise(string serial, string period, long used)
    {
        lock (Lock)
        {
            Load();
            string key = serial + "|" + period;
            map!.TryGetValue(key, out var cur);
            if (used > cur) { map[key] = used; cur = used; Save(); }
            return cur;
        }
    }
}

/// <summary>Live state received from the phone app over the side channel (abstract socket -> adb reverse -> 127.0.0.1:31417).</summary>
static class Usage
{
    public const int Port = 31417;
    public static string Serial = "";
    public static string Period = Jalali.PeriodKey(DateTime.Now);
    public static long Used;
    public static long Limit = Ledger.FreeLimit;
    public static bool Pro;
    public static bool UpdateOnly;
    public static bool PhoneLinked;
    public static DateTime LastStatus = DateTime.MinValue;
    public static string LastBye = "";
    public static event Action? Changed;

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
        PhoneLinked = false;
        Raise();
    }

    static void Handle(string line, NetworkStream s)
    {
        var o = MiniJson.ParseObject(line);
        if (o == null) return;
        string t = MiniJson.Text(o, "t");
        if (t == "hello" || t == "status")
        {
            string serial = MiniJson.Text(o, "serial", Serial);
            if (serial.Length == 0) serial = "unknown";
            string phonePeriod = MiniJson.Text(o, "period");
            string pcPeriod = Jalali.PeriodKey(DateTime.Now);
            string period = string.CompareOrdinal(phonePeriod, pcPeriod) > 0 ? phonePeriod : pcPeriod;
            long used = MiniJson.Long(o, "used");
            bool pro = MiniJson.Bool(o, "pro");
            Serial = serial; Period = period; PhoneLinked = true; LastStatus = DateTime.UtcNow;
            Pro = pro; UpdateOnly = MiniJson.Bool(o, "only"); Limit = MiniJson.Long(o, "limit", Ledger.FreeLimit);
            if (pro) Settings.NoteProSeen();
            long ledger = phonePeriod == period ? Ledger.Raise(serial, period, used) : Ledger.Get(serial, period);
            Used = ledger;
            if (t == "hello")
            {
                var reply = Encoding.UTF8.GetBytes(MiniJson.Build(("t", "sync"), ("period", period), ("used", ledger)) + "\n");
                s.Write(reply, 0, reply.Length); s.Flush();
            }
            Raise();
        }
        else if (t == "bye")
        {
            LastBye = MiniJson.Text(o, "reason");
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
