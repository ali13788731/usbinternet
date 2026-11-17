; Inno Setup script - builds a one-click installer for the Windows app.
; Needs no administrator rights: it installs for the current user only.

#define AppName "Cable Internet"
#define AppVersion "1.0.0"

[Setup]
AppId={{6B1B3D6E-5D2C-4B0B-9C57-3F3C4E8A1A01}
AppName={#AppName}
AppVersion={#AppVersion}
DefaultDirName={autopf}\CableInternet
DefaultGroupName={#AppName}
PrivilegesRequired=lowest
OutputBaseFilename=CableInternet-Setup
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern
UninstallDisplayIcon={app}\CableInternet.exe
DisableProgramGroupPage=yes

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; Flags: checkedonce

[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\CableInternet.exe"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\CableInternet.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\CableInternet.exe"; Description: "Run {#AppName}"; Flags: nowait postinstall skipifsilent
