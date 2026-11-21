param([Parameter(Mandatory=$true)][string]$Root)
$ErrorActionPreference = 'Stop'
$fixture = Join-Path $env:RUNNER_TEMP 'Photon-native-video-only.mp4'
& "$Root/tools/ffmpeg.exe" -nostdin -hide_banner -loglevel error -y -f lavfi -i 'color=c=black:s=160x90:r=10:d=20' -an -c:v mpeg4 $fixture
if ($LASTEXITCODE -ne 0) { throw 'Native test video generation failed' }
$nativePath = Get-ChildItem $Root -Recurse -Filter libvlc.dll | Where-Object { $_.DirectoryName -notmatch 'win-x86' } | Select-Object -First 1 -ExpandProperty DirectoryName
Add-Type -Path "$Root/LibVLCSharp.dll"
$references = @("$Root/LibVLCSharp.dll") + @(Get-ChildItem "$PSHOME/ref/*.dll" | ForEach-Object FullName)
Add-Type -Path "$PSScriptRoot/NativeMediaCheck.cs" -ReferencedAssemblies $references
[NativeMediaCheck]::Run($Root, $nativePath, $fixture)
# Exit this child host to release the installed DLLs before uninstallation.
