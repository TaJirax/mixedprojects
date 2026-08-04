$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Ffmpeg = Join-Path $env:LOCALAPPDATA "SpotifyDownloader\ffmpeg\bin\ffmpeg.exe"
$Spotdl = Join-Path $Root "vendor\spotdl.exe"
$Ytdlp = Join-Path $Root "vendor\yt-dlp.exe"
$Output = Join-Path $Root "release"
$Work = Join-Path $Root "build"
$PythonRoot = python -c "import sys; print(sys.base_prefix)"
$PythonDlls = Join-Path $PythonRoot "DLLs"
$PythonLib = Join-Path $PythonRoot "Lib"
$PythonTcl = Join-Path $PythonRoot "tcl"

if (-not (Test-Path -LiteralPath $Ffmpeg)) { throw "FFmpeg not found: $Ffmpeg" }
if (-not (Test-Path -LiteralPath $Spotdl)) { throw "spotDL not found: $Spotdl" }
if (-not (Test-Path -LiteralPath $Ytdlp)) { throw "yt-dlp not found: $Ytdlp" }

python -m PyInstaller --noconfirm --clean --windowed --onefile `
    --name SpotifyDownloader `
    --icon (Join-Path $Root "assets\icon.ico") `
    --distpath $Output `
    --workpath $Work `
    --specpath $Work `
    --collect-all webview `
    --runtime-hook (Join-Path $Root "pyi_tk_runtime_hook.py") `
    --add-data "$(Join-Path $Root 'web');web" `
    --add-data "$(Join-Path $Root 'assets');assets" `
    --add-data "$(Join-Path $PythonLib 'tkinter');tkinter" `
    --add-data "$(Join-Path $PythonTcl 'tcl8.6');_tcl_data" `
    --add-data "$(Join-Path $PythonTcl 'tk8.6');_tk_data" `
    --add-binary "$(Join-Path $PythonDlls '_tkinter.pyd');." `
    --add-binary "$(Join-Path $PythonDlls 'tcl86t.dll');." `
    --add-binary "$(Join-Path $PythonDlls 'tk86t.dll');." `
    --add-binary "$Spotdl;tools" `
    --add-binary "$Ytdlp;tools" `
    --add-binary "$Ffmpeg;tools" `
    (Join-Path $Root "spotify_downloader.py")
if ($LASTEXITCODE -ne 0) { throw "PyInstaller failed with exit code $LASTEXITCODE" }

$Dist = $Output
Copy-Item (Join-Path $Root "README.md") $Dist -Force
Copy-Item (Join-Path $Root "THIRD_PARTY_NOTICES.txt") $Dist -Force
Copy-Item (Join-Path $Root "GPL-3.0.txt") $Dist -Force

Write-Host "Standalone executable created at $(Join-Path $Dist 'SpotifyDownloader.exe')"
