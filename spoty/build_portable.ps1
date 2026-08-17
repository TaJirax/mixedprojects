$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Vendor = Join-Path $Root "vendor"
$Spotdl = Join-Path $Vendor "spotdl.exe"
$Ytdlp = Join-Path $Vendor "yt-dlp.exe"
$Deno = Join-Path $Vendor "deno.exe"
$Ffmpeg = Join-Path $Vendor "ffmpeg.exe"
$Ffprobe = Join-Path $Vendor "ffprobe.exe"
$Output = Join-Path $Root "release"
$Work = Join-Path $Root "build"
$PythonRoot = python -c "import sys; print(sys.base_prefix)"
$PythonDlls = Join-Path $PythonRoot "DLLs"
$PythonLib = Join-Path $PythonRoot "Lib"
$PythonTcl = Join-Path $PythonRoot "tcl"

$SpotdlVersion = "4.5.2"
$YtjsVersion = "18.0.0"

# Extra PyInstaller arguments that only exist when their file does.
$ExtraArgs = @()

# vendor/ is deliberately not in the repository: it is 200 MB of other
# projects' release binaries. Fetching what is missing is what makes a fresh
# clone — and a CI runner — able to build without a manual setup step.
New-Item -ItemType Directory -Force -Path $Vendor | Out-Null

function Get-Tool {
    param([string]$Url, [string]$Destination)
    if (Test-Path -LiteralPath $Destination) { return }
    Write-Host "  fetching $(Split-Path -Leaf $Destination)"
    Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
}

Get-Tool "https://github.com/spotDL/spotify-downloader/releases/download/v$SpotdlVersion/spotdl-$SpotdlVersion-win32.exe" $Spotdl
Get-Tool "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe" $Ytdlp

# The desktop's second YouTube engine. Built from source here rather than
# downloaded, because it is our own wrapper around YoutubeExplode; the .NET SDK
# is the only thing needed and CI installs it. A clone without the SDK still
# builds a working app, one engine shorter, which is why this warns instead of
# failing.
$YoutubeExplode = Join-Path $Vendor "blueknight-youtube.exe"
if (-not (Test-Path -LiteralPath $YoutubeExplode)) {
    if (Get-Command dotnet -ErrorAction SilentlyContinue) {
        Write-Host "  building the YoutubeExplode engine"
        dotnet publish (Join-Path $Root "dotnet/BlueKnightYoutube/BlueKnightYoutube.csproj") `
            -c Release -r win-x64 `
            --property:PublishDir="$(Join-Path $Work 'dotnet')/"
        if ($LASTEXITCODE -ne 0) { throw "the YoutubeExplode engine failed to build" }
        $Published = Join-Path $Work "dotnet/blueknight-youtube.exe"
        if ((Get-Item $Published).Length -lt 20MB) {
            throw "the engine published framework-dependent: it will not run without .NET installed"
        }
        Copy-Item $Published $YoutubeExplode -Force
    } else {
        Write-Warning "no .NET SDK: building without the YoutubeExplode fallback engine"
    }
}
if (Test-Path -LiteralPath $YoutubeExplode) {
    $ExtraArgs += @("--add-binary", "$YoutubeExplode;tools")
}

# The YouTube.js engine: our two scripts plus the library as one bundled file,
# so nothing is fetched at run time. Deno already ships for yt-dlp's challenge
# solver, so this engine costs a script rather than another runtime.
$YtjsDir = Join-Path $Root "engines/youtubejs"
$YtjsBundle = Join-Path $YtjsDir "youtubei.bundle.mjs"
if (-not (Test-Path -LiteralPath $YtjsBundle)) {
    Write-Host "  fetching the YouTube.js library"
    Invoke-WebRequest -UseBasicParsing -OutFile $YtjsBundle `
        -Uri "https://esm.sh/youtubei.js@$YtjsVersion/denonext/youtubei.bundle.mjs"
}
$ExtraArgs += @("--add-data", "$YtjsDir;tools")

# The NewPipe engine and a runtime for it. Skipped without a JDK, like the
# .NET engine is without its SDK: a clone still builds, one engine shorter.
$NewPipeJar = Join-Path $Vendor "blueknight-newpipe.jar"
if (-not (Test-Path -LiteralPath $NewPipeJar) -and (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "  building the NewPipe engine"
    Push-Location (Join-Path $Root "jvm/newpipe")
    & ./gradlew.bat --no-daemon -q shadowJar
    Pop-Location
    $Built = Join-Path $Root "jvm/newpipe/build/libs/blueknight-newpipe.jar"
    if (Test-Path -LiteralPath $Built) { Copy-Item $Built $NewPipeJar -Force }
}
if (Test-Path -LiteralPath $NewPipeJar) {
    $ExtraArgs += @("--add-data", "$NewPipeJar;tools")
    $Jre = Join-Path $Vendor "jre"
    if (-not (Test-Path -LiteralPath $Jre) -and (Get-Command jlink -ErrorAction SilentlyContinue)) {
        Write-Host "  building a Java runtime for it"
        jlink --add-modules java.base,java.net.http,java.naming,jdk.crypto.ec `
              --strip-debug --no-header-files --no-man-pages --compress=2 `
              --output $Jre
    }
    if (Test-Path -LiteralPath $Jre) { $ExtraArgs += @("--add-data", "$Jre;tools/jre") }
}

if (-not (Test-Path -LiteralPath $Deno)) {
    $DenoZip = Join-Path $Vendor "deno.zip"
    Get-Tool "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip" $DenoZip
    Expand-Archive -LiteralPath $DenoZip -DestinationPath $Vendor -Force
    Remove-Item -LiteralPath $DenoZip -Force
}

if (-not (Test-Path -LiteralPath $Ffmpeg) -or -not (Test-Path -LiteralPath $Ffprobe)) {
    # An existing local install is preferred over a fresh 130 MB download.
    $LocalFfmpeg = Join-Path $env:LOCALAPPDATA "SpotifyDownloader\ffmpeg\bin"
    if (Test-Path -LiteralPath (Join-Path $LocalFfmpeg "ffmpeg.exe")) {
        Copy-Item (Join-Path $LocalFfmpeg "ffmpeg.exe") $Ffmpeg -Force
        Copy-Item (Join-Path $LocalFfmpeg "ffprobe.exe") $Ffprobe -Force
    } else {
        $FfmpegZip = Join-Path $Vendor "ffmpeg.zip"
        Get-Tool "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip" $FfmpegZip
        $Stage = Join-Path $Vendor "ffmpeg-stage"
        Expand-Archive -LiteralPath $FfmpegZip -DestinationPath $Stage -Force
        foreach ($name in @("ffmpeg.exe", "ffprobe.exe")) {
            $found = Get-ChildItem -Path $Stage -Filter $name -Recurse | Select-Object -First 1
            if (-not $found) { throw "The FFmpeg archive is missing $name" }
            Copy-Item $found.FullName (Join-Path $Vendor $name) -Force
        }
        Remove-Item -LiteralPath $FfmpegZip -Force
        Remove-Item -LiteralPath $Stage -Recurse -Force
    }
}

foreach ($tool in @($Ffmpeg, $Ffprobe, $Spotdl, $Ytdlp, $Deno)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "Missing after fetch: $tool" }
}

$FfmpegInfo = @(& $Ffmpeg -version 2>&1)
if ($LASTEXITCODE -ne 0 -or $FfmpegInfo.Count -eq 0) {
    throw "FFmpeg did not pass its pre-build launch check: $Ffmpeg"
}
Write-Host "Bundling $($FfmpegInfo[0])"

# gallery-dl imports each site's extractor by name at runtime, so a normal import
# scan finds none of them and the frozen app would be able to do yt-dlp only.
# --collect-all is what puts the extractors (and requests) inside the exe.
python -m PyInstaller --noconfirm --clean --windowed --onefile `
    --name SpotifyDownloader `
    --icon (Join-Path $Root "assets\icon.ico") `
    --distpath $Output `
    --workpath $Work `
    --specpath $Work `
    --collect-all webview `
    --collect-all gallery_dl `
    --collect-all streamlink `
    --collect-all requests `
    --collect-all certifi `
    --collect-all PIL `
    --collect-all pikepdf `
    --hidden-import img2pdf `
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
    --add-binary "$Deno;tools" `
    --add-binary "$Ffmpeg;tools" `
    --add-binary "$Ffprobe;tools" `
    @ExtraArgs `
    (Join-Path $Root "spotify_downloader.py")
if ($LASTEXITCODE -ne 0) { throw "PyInstaller failed with exit code $LASTEXITCODE" }

$Dist = $Output
Copy-Item (Join-Path $Root "README.md") $Dist -Force
Copy-Item (Join-Path $Root "THIRD_PARTY_NOTICES.txt") $Dist -Force
Copy-Item (Join-Path $Root "GPL-3.0.txt") $Dist -Force

$Executable = Join-Path $Dist "SpotifyDownloader.exe"
$Package = Join-Path $Dist "BlueKnightDownloader-windows-x64.zip"
$PackageItems = @(
    $Executable,
    (Join-Path $Dist "README.md"),
    (Join-Path $Dist "THIRD_PARTY_NOTICES.txt"),
    (Join-Path $Dist "GPL-3.0.txt")
)
Compress-Archive -LiteralPath $PackageItems -DestinationPath $Package -CompressionLevel Optimal -Force

Write-Host "Standalone executable created at $Executable"
Write-Host "Release package created at $Package"
