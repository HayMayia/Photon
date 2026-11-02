[Setup]
AppId={{7A41EEAF-951D-4DE1-9365-82D2177F9B65}
AppName=Photon
AppVersion=0.2.0
AppPublisher=Photon contributors
AppPublisherURL=https://github.com/HayMayia/Photon
DefaultDirName={localappdata}\Programs\Photon
DefaultGroupName=Photon
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\..\..\artifacts
OutputBaseFilename=Photon-Windows-x64-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
LicenseFile=..\..\..\LICENSE
UninstallDisplayIcon={app}\Photon.exe
[Files]
Source: "..\..\..\artifacts\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{group}\Photon"; Filename: "{app}\Photon.exe"
[Run]
Filename: "{app}\Photon.exe"; Description: "Open Photon"; Flags: nowait postinstall skipifsilent
