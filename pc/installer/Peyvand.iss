; Inno Setup script - builds a one-click installer for the Windows app.
; Needs no administrator rights: it installs for the current user only.

#define AppName "Peyvand"
#define AppVersion "1.0.0"

[Setup]
AppId={{9D2E7A14-3C58-4F0E-8B7A-51C6E2D4F7B3}
AppName={#AppName}
AppVersion={#AppVersion}
DefaultDirName={autopf}\Peyvand
DefaultGroupName={#AppName}
PrivilegesRequired=lowest
OutputBaseFilename=Peyvand-Setup
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern
UninstallDisplayIcon={app}\Peyvand.exe
DisableProgramGroupPage=yes

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; Flags: checkedonce

[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\Peyvand.exe"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\Peyvand.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Peyvand.exe"; Description: "Run {#AppName}"; Flags: nowait postinstall skipifsilent
