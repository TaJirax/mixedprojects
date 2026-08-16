#!/usr/bin/env bash
# Build Blue Knight Downloader for Linux or macOS.
#
# The Windows counterpart is build_portable.ps1. This does the same job with
# two differences forced by the platform: the bundled tools are fetched here
# rather than committed (the Linux and macOS binaries are ~200 MB the Windows
# build already has in vendor/), and there is no Tcl/Tk to carry, because
# pywebview draws its dialogs with GTK/WebKit on Linux and Cocoa on macOS.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$ROOT/vendor"
OUTPUT="$ROOT/release"
WORK="$ROOT/build"

SPOTDL_VERSION="4.5.2"
YTJS_VERSION="18.0.0"
case "$(uname -s)" in
    Darwin) OS_TAG=mac ;;
    Linux)  OS_TAG=linux ;;
    *) echo "Use build_portable.ps1 on Windows." >&2; exit 1 ;;
esac
case "$(uname -m)" in
    arm64|aarch64) ARCH=arm64 ;;
    *) ARCH=x64 ;;
esac
echo "Building for $OS_TAG/$ARCH"

# --------------------------------------------------------------------------
# pywebview has no window to draw on a Linux box without the WebKit GTK
# bindings, and it fails at import rather than at startup. Say so now, with
# the command that fixes it, instead of shipping an executable that cannot open.
# --------------------------------------------------------------------------
if [ "$OS_TAG" = linux ]; then
    # 4.1 on current distributions, 4.0 on anything Ubuntu 22.04 vintage.
    # pywebview accepts either, so requiring 4.1 would fail a machine that works.
    if ! python3 - <<'PROBE' 2>/dev/null
import gi
for version in ("4.1", "4.0"):
    try:
        gi.require_version("WebKit2", version)
        break
    except ValueError:
        continue
else:
    raise SystemExit(1)
PROBE
    then
        echo "ERROR: pywebview needs the WebKit2 GTK bindings." >&2
        echo "  Debian/Ubuntu: sudo apt install python3-gi gir1.2-webkit2-4.1 libcairo2-dev" >&2
        echo "  Fedora:        sudo dnf install python3-gobject webkit2gtk4.1" >&2
        echo "  Arch:          sudo pacman -S python-gobject webkit2gtk-4.1" >&2
        exit 1
    fi
fi

fetch() {  # fetch <url> <destination>
    echo "  fetching $(basename "$2")"
    curl --fail --location --silent --show-error --retry 3 -o "$2" "$1"
}

mkdir -p "$VENDOR"

# --- spotDL, yt-dlp, Deno: one release binary each -------------------------
if [ "$OS_TAG" = mac ]; then
    SPOTDL_ASSET="spotdl-${SPOTDL_VERSION}-darwin"
    YTDLP_ASSET="yt-dlp_macos"
    DENO_ASSET="deno-$([ "$ARCH" = arm64 ] && echo aarch64 || echo x86_64)-apple-darwin.zip"
else
    SPOTDL_ASSET="spotdl-${SPOTDL_VERSION}-linux"
    YTDLP_ASSET="yt-dlp_linux$([ "$ARCH" = arm64 ] && echo _aarch64 || echo '')"
    DENO_ASSET="deno-$([ "$ARCH" = arm64 ] && echo aarch64 || echo x86_64)-unknown-linux-gnu.zip"
fi

[ -f "$VENDOR/spotdl" ] || fetch \
    "https://github.com/spotDL/spotify-downloader/releases/download/v${SPOTDL_VERSION}/${SPOTDL_ASSET}" \
    "$VENDOR/spotdl"
[ -f "$VENDOR/yt-dlp" ] || fetch \
    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/${YTDLP_ASSET}" \
    "$VENDOR/yt-dlp"
if [ ! -f "$VENDOR/deno" ]; then
    fetch "https://github.com/denoland/deno/releases/latest/download/${DENO_ASSET}" "$VENDOR/deno.zip"
    (cd "$VENDOR" && unzip -o -q deno.zip deno && rm -f deno.zip)
fi

# --- FFmpeg: no single project publishes every platform --------------------
if [ ! -f "$VENDOR/ffmpeg" ] || [ ! -f "$VENDOR/ffprobe" ]; then
    if [ "$OS_TAG" = mac ]; then
        fetch "https://evermeet.cx/ffmpeg/getrelease/ffmpeg/zip" "$VENDOR/ffmpeg.zip"
        fetch "https://evermeet.cx/ffmpeg/getrelease/ffprobe/zip" "$VENDOR/ffprobe.zip"
        (cd "$VENDOR" && unzip -o -q -j ffmpeg.zip ffmpeg && unzip -o -q -j ffprobe.zip ffprobe \
            && rm -f ffmpeg.zip ffprobe.zip)
    else
        SUFFIX=$([ "$ARCH" = arm64 ] && echo linuxarm64 || echo linux64)
        fetch "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-${SUFFIX}-gpl.tar.xz" \
              "$VENDOR/ffmpeg.tar.xz"
        tar -xJf "$VENDOR/ffmpeg.tar.xz" -C "$VENDOR" --strip-components=2 \
            --wildcards '*/bin/ffmpeg' '*/bin/ffprobe'
        rm -f "$VENDOR/ffmpeg.tar.xz"
    fi
fi

chmod +x "$VENDOR/spotdl" "$VENDOR/yt-dlp" "$VENDOR/deno" "$VENDOR/ffmpeg" "$VENDOR/ffprobe"

# A downloaded file of plausible size can still be a mirror's error page. The
# Windows build makes FFmpeg prove it launches before bundling; so does this.
FFMPEG_BANNER="$("$VENDOR/ffmpeg" -version | head -n 1)"
echo "Bundling $FFMPEG_BANNER"

# gallery-dl imports each site's extractor by name at runtime, so a normal
# import scan finds none of them and the frozen app would be able to do yt-dlp
# only. --collect-all is what puts the extractors (and requests) inside it.
PYI_ARGS=(
    --noconfirm --clean --windowed --onefile
    --name SpotifyDownloader
    --distpath "$OUTPUT" --workpath "$WORK" --specpath "$WORK"
    --collect-all webview
    --collect-all gallery_dl
    --collect-all streamlink
    --collect-all requests
    --collect-all certifi
    --collect-all PIL
    --collect-all pikepdf
    --hidden-import img2pdf
    --add-data "$ROOT/web:web"
    --add-data "$ROOT/assets:assets"
    --add-binary "$VENDOR/spotdl:tools"
    --add-binary "$VENDOR/yt-dlp:tools"
    --add-binary "$VENDOR/deno:tools"
    --add-binary "$VENDOR/ffmpeg:tools"
    --add-binary "$VENDOR/ffprobe:tools"
)

# The desktop's second YouTube engine, built from source rather than fetched:
# it is our own wrapper around YoutubeExplode and the .NET SDK is all it needs.
# A clone without the SDK still produces a working app, one engine shorter.
YOUTUBE_EXPLODE="$VENDOR/blueknight-youtube"
if [ ! -f "$YOUTUBE_EXPLODE" ]; then
    if command -v dotnet >/dev/null 2>&1; then
        echo "  building the YoutubeExplode engine"
        case "$OS_TAG:$ARCH" in
            mac:arm64)   RID=osx-arm64 ;;
            mac:*)       RID=osx-x64 ;;
            linux:arm64) RID=linux-arm64 ;;
            *)           RID=linux-x64 ;;
        esac
        dotnet publish "$ROOT/dotnet/BlueKnightYoutube/BlueKnightYoutube.csproj"             -c Release -r "$RID" --property:PublishDir="$WORK/dotnet/"
        _size=$(wc -c < "$WORK/dotnet/blueknight-youtube")
        if [ "$_size" -lt 20000000 ]; then
            echo "the engine published at $_size bytes: that is a framework-dependent build, not a self-contained one" >&2
            exit 1
        fi
        cp "$WORK/dotnet/blueknight-youtube" "$YOUTUBE_EXPLODE"
        chmod +x "$YOUTUBE_EXPLODE"
    else
        echo "  no .NET SDK: building without the YoutubeExplode fallback engine" >&2
    fi
fi
[ -f "$YOUTUBE_EXPLODE" ] && PYI_ARGS+=(--add-binary "$YOUTUBE_EXPLODE:tools")

# The YouTube.js engine: our two scripts plus the library, bundled as one file
# so nothing is fetched at run time. Deno already ships for yt-dlp's challenge
# solver, so this engine costs a script rather than another runtime.
YTJS_BUNDLE="$ROOT/engines/youtubejs/youtubei.bundle.mjs"
if [ ! -f "$YTJS_BUNDLE" ]; then
    echo "  fetching the YouTube.js library"
    curl --fail --location --retry 3 -o "$YTJS_BUNDLE"         "https://esm.sh/youtubei.js@$YTJS_VERSION/denonext/youtubei.bundle.mjs"
fi
PYI_ARGS+=(--add-data "$ROOT/engines/youtubejs:tools")
if [ "$OS_TAG" = mac ]; then
    # .icns is the only icon format a bundle accepts; skip rather than fail
    # the build when only the Windows .ico has been checked in.
    [ -f "$ROOT/assets/icon.icns" ] && PYI_ARGS+=(--icon "$ROOT/assets/icon.icns")
    PYI_ARGS+=(--osx-bundle-identifier net.blueknight.downloader)
fi

python3 -m PyInstaller "${PYI_ARGS[@]}" "$ROOT/spotify_downloader.py"

cp "$ROOT/README.md" "$ROOT/THIRD_PARTY_NOTICES.txt" "$ROOT/GPL-3.0.txt" "$OUTPUT/"

cd "$OUTPUT"
if [ "$OS_TAG" = mac ]; then
    # Ship the .app, not the bare Unix executable beside it: double-clicking
    # the latter opens a terminal, and Gatekeeper treats it differently.
    PACKAGE="BlueKnightDownloader-macos-${ARCH}.zip"
    ditto -c -k --sequesterRsrc --keepParent SpotifyDownloader.app "$PACKAGE"
    zip -q "$PACKAGE" README.md THIRD_PARTY_NOTICES.txt GPL-3.0.txt
    echo "Application bundle created at $OUTPUT/SpotifyDownloader.app"
else
    PACKAGE="BlueKnightDownloader-linux-${ARCH}.tar.gz"
    tar -czf "$PACKAGE" SpotifyDownloader README.md THIRD_PARTY_NOTICES.txt GPL-3.0.txt
    echo "Standalone executable created at $OUTPUT/SpotifyDownloader"
fi
echo "Release package created at $OUTPUT/$PACKAGE"
