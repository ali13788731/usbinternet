namespace Peyvand;

/// <summary>Owner settings. Rows with an empty value are hidden or disabled.</summary>
static class Brand
{
    /// <summary>Shared secret that signs the messages of the phone app. Must equal CONTROL_SECRET of the Android build.</summary>
    public const string ControlSecret = "pv-ctl-7Gq2xK9mWd4RtLz8";

    /// <summary>Shown once, on the splash screen only.</summary>
    public const string Designer = "Mr.PC";
    public const string Telegram = "";   // e.g. "https://t.me/yourchannel"
    public const string Email = "";      // e.g. "support@example.com"
    public const string Website = "";    // e.g. "https://example.com"

    /// <summary>Same update.json as the phone app (leave empty to disable the update check).</summary>
    public const string UpdateUrl = "";
    /// <summary>Direct link to the Windows installer, shown in the update message.</summary>
    public const string PcDownloadUrl = "";

    // Prerequisite downloads (official pages)
    public const string UrlDotNet = "https://dotnet.microsoft.com/download/dotnet-framework/net48";
    public const string UrlVcRedist = "https://aka.ms/vs/17/release/vc_redist.x64.exe";
    public const string UrlUsbDriver = "https://developer.android.com/studio/run/win-usb";
    public const string UrlBazaar = "https://cafebazaar.ir/install";
}
