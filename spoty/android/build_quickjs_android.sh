#!/usr/bin/env bash
# Build the JavaScript engine yt-dlp needs on Android.
#
# YouTube signs its media URLs with a JavaScript challenge. yt-dlp solves it
# with an external engine, defaulting to Deno — which publishes no Android
# build, so on the phone no engine was passed at all, yt-dlp fell back to its
# own interpreter, and the unsigned URLs came back HTTP 403 Forbidden.
#
# yt-dlp names four supported engines: deno, node, quickjs, bun. QuickJS is the
# only one that is a small C interpreter with no runtime of its own to ship, so
# it is the one that cross-compiles cleanly for arm64 and adds ~1 MB rather
# than ~50 MB to the APK.
set -euo pipefail

QUICKJS_VERSION="${QUICKJS_VERSION:-v0.10.1}"
API="${API:-24}"
ABI="${ABI:-arm64-v8a}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_DIR="$HERE/app/src/main/jniLibs/$ABI"

# Same rule as the FFmpeg build: the checkout may live anywhere, the build tree
# must not contain a space.
WORK="${WORK:-$HOME/.blueknight-quickjs-build}"
case "$WORK" in
    *\ *) echo "ERROR: the build path must not contain spaces: $WORK" >&2; exit 1 ;;
esac

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}}"
NDK_ROOT="${NDK_ROOT:-}"
if [ -z "$NDK_ROOT" ]; then
    NDK_ROOT="$SDK_ROOT/ndk/$(ls "$SDK_ROOT/ndk" | sort -V | tail -1)"
fi

if command -v cygpath >/dev/null 2>&1; then
    winpath() { cygpath -m "$1"; }
else
    winpath() { printf '%s' "$1"; }
fi
NDK_ROOT="$(winpath "$NDK_ROOT")"
WORK="$(winpath "$WORK")"

TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake"
[ -f "$TOOLCHAIN_FILE" ] || { echo "No CMake toolchain at $TOOLCHAIN_FILE" >&2; exit 1; }

command -v cmake >/dev/null 2>&1 || { echo "cmake is required" >&2; exit 1; }

mkdir -p "$WORK" "$JNI_DIR"
cd "$WORK"

SRC="$WORK/quickjs-${QUICKJS_VERSION}"
if [ ! -d "$SRC" ]; then
    echo "==> downloading QuickJS $QUICKJS_VERSION"
    # quickjs-ng rather than the original: it is actively maintained and ships
    # a CMake build, which is what makes this a short script instead of a long one.
    curl --fail --location --retry 3 -o quickjs.tar.gz \
        "https://github.com/quickjs-ng/quickjs/archive/refs/tags/${QUICKJS_VERSION}.tar.gz"
    tar -xzf quickjs.tar.gz
    mv "quickjs-${QUICKJS_VERSION#v}" "$SRC"
    rm -f quickjs.tar.gz
fi

echo "==> configuring for $ABI (API $API)"
cmake -S "$SRC" -B "$WORK/build" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF

echo "==> compiling"
# The CLI target is qjs_exe; the target simply called "qjs" is the static
# library, and building that produces libqjs.a and no interpreter at all.
cmake --build "$WORK/build" --target qjs_exe --config Release -j"$(nproc 2>/dev/null || echo 4)"

# An archive is not an interpreter. Insist on a real executable image, or the
# APK ships a file yt-dlp cannot run and YouTube fails exactly as before.
BINARY=""
while IFS= read -r found; do
    case "$found" in *.a|*.so) continue ;; esac
    if head -c 4 "$found" | grep -q "ELF"; then BINARY="$found"; break; fi
done <<EOF
$(find "$WORK/build" -name "qjs" -type f)
EOF
[ -n "$BINARY" ] || { echo "no qjs executable was produced" >&2; exit 1; }

# lib*.so is the only shape Android will unpack and leave executable.
install -m 0755 "$BINARY" "$JNI_DIR/libquickjs.so"

# CMake's Release build still carries debug_info here, which is most of the
# file and none of the behaviour. This is going in an APK, so strip it.
case "$(uname -s)" in
    Linux)  HOST_TAG=linux-x86_64 ;;
    Darwin) HOST_TAG=darwin-x86_64 ;;
    *)      HOST_TAG=windows-x86_64 ;;
esac
STRIP="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG/bin/llvm-strip"
[ "$HOST_TAG" = windows-x86_64 ] && STRIP="$STRIP.exe"
[ -x "$STRIP" ] && "$STRIP" "$JNI_DIR/libquickjs.so"

echo
echo "QuickJS -> $JNI_DIR/libquickjs.so ($(du -h "$JNI_DIR/libquickjs.so" | cut -f1))"
file "$JNI_DIR/libquickjs.so" 2>/dev/null || true
