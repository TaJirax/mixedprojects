#!/usr/bin/env bash
# Build the FFmpeg and FFprobe executables the Android app runs.
#
# yt-dlp merges and transcodes by launching ffmpeg with a path, so the app
# needs real executables, not a library binding. Nothing publishes those for
# Android any more — ffmpeg-kit retired and its Maven artifacts were withdrawn
# — so they are compiled here from the official release tarball rather than
# taken from a third party's prebuilt release.
#
# The results are installed as jniLibs. Android only extracts and marks
# executable the files in that folder that are named lib*.so, which is why two
# programs end up with library names.
set -euo pipefail

FFMPEG_VERSION="${FFMPEG_VERSION:-7.1.1}"
API="${API:-24}"                      # Android 7.0, matching the app's minSdk
ABI="${ABI:-arm64-v8a}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_DIR="$HERE/app/src/main/jniLibs/$ABI"

# FFmpeg's configure does not quote the paths it builds its probes from, so a
# single space anywhere in the build tree breaks it with errors as unhelpful
# as "ambiguous redirect". The checkout is allowed to live wherever it likes;
# the build tree is put somewhere without spaces regardless.
WORK="${WORK:-$HOME/.blueknight-ffmpeg-build/$ABI}"
case "$WORK" in
    *\ *) echo "ERROR: the build path must not contain spaces: $WORK" >&2; exit 1 ;;
esac

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}}"
NDK_ROOT="${NDK_ROOT:-}"
if [ -z "$NDK_ROOT" ]; then
    # Newest installed NDK wins; any of 26+ has the clang this needs.
    NDK_ROOT="$SDK_ROOT/ndk/$(ls "$SDK_ROOT/ndk" | sort -V | tail -1)"
fi

case "$(uname -s)" in
    Linux)  HOST_TAG=linux-x86_64 ;;
    Darwin) HOST_TAG=darwin-x86_64 ;;
    *)      HOST_TAG=windows-x86_64 ;;
esac
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
[ -d "$TOOLCHAIN" ] || { echo "No NDK toolchain at $TOOLCHAIN" >&2; exit 1; }

# On Windows the two halves of this build disagree about what a path looks
# like: the shell cannot exec a backslashed C:\... path, and the NDK's clang is
# a native binary that cannot read the /c/... form the shell prefers. The mixed
# form, C:/..., is the one both accept, so every path handed across that line
# is normalised to it. cygpath is absent on real Unix, where nothing is needed.
if command -v cygpath >/dev/null 2>&1; then
    winpath() { cygpath -m "$1"; }
else
    winpath() { printf '%s' "$1"; }
fi
TOOLCHAIN="$(winpath "$TOOLCHAIN")"
WORK="$(winpath "$WORK")"

case "$ABI" in
    arm64-v8a)   ARCH=aarch64; TRIPLE=aarch64-linux-android ;;
    armeabi-v7a) ARCH=arm;     TRIPLE=armv7a-linux-androideabi ;;
    x86)         ARCH=x86;     TRIPLE=i686-linux-android ;;
    x86_64)      ARCH=x86_64;  TRIPLE=x86_64-linux-android ;;
    *) echo "Unsupported ABI $ABI" >&2; exit 1 ;;
esac

# MSYS rewrites anything that looks like a Unix path when it crosses into a
# native Windows .exe, which mangles every -I and --sysroot flag configure
# passes to clang. This is the documented off switch.
export MSYS2_ARG_CONV_EXCL="*"
export MSYS_NO_PATHCONV=1

# configure compiles and runs a probe out of TMPDIR. On Windows that variable
# holds a backslashed native path, which the shell then eats as escapes and
# the probe fails with "Unable to create and execute files". A POSIX path
# inside the build tree avoids that, and avoids a noexec system temp too.
export TMPDIR="$WORK/tmp"
mkdir -p "$TMPDIR"

MAKE=make
command -v make >/dev/null 2>&1 || MAKE=mingw32-make
command -v "$MAKE" >/dev/null 2>&1 || { echo "Neither make nor mingw32-make found" >&2; exit 1; }

# The NDK names its two kinds of tool differently on Windows: the binutils
# replacements are real .exe binaries, while the per-API compiler entry points
# are .cmd wrappers that add the target and API level before calling clang.
EXE_SUFFIX=""
CC_SUFFIX=""
if [ "$HOST_TAG" = windows-x86_64 ]; then
    EXE_SUFFIX=".exe"
    CC_SUFFIX=".cmd"
fi

mkdir -p "$WORK" "$JNI_DIR"
cd "$WORK"

SRC="$WORK/ffmpeg-$FFMPEG_VERSION"
if [ ! -d "$SRC" ]; then
    # ffmpeg.org serves this from one slow host. The project's own GitHub
    # mirror carries the identical tagged tree an order of magnitude faster,
    # so it is tried first and the canonical release is the fallback.
    echo "==> downloading FFmpeg $FFMPEG_VERSION"
    if curl --fail --location --retry 3 --connect-timeout 20 -o "ffmpeg.tar.gz" \
            "https://github.com/FFmpeg/FFmpeg/archive/refs/tags/n$FFMPEG_VERSION.tar.gz"; then
        tar -xzf "ffmpeg.tar.gz"
        mv "FFmpeg-n$FFMPEG_VERSION" "$SRC"
        rm -f "ffmpeg.tar.gz"
    else
        curl --fail --location --retry 3 -o "ffmpeg-$FFMPEG_VERSION.tar.xz" \
            "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz"
        tar -xJf "ffmpeg-$FFMPEG_VERSION.tar.xz"
    fi
fi

# ---------------------------------------------------------------------------
# LAME, because FFmpeg has no MP3 encoder of its own.
#
# MP3 320 is the app's headline format on every other platform, and both the
# converter and yt-dlp's audio extraction ask ffmpeg for libmp3lame by name.
# Without this, the Android build could decode MP3 and never write one.
# ---------------------------------------------------------------------------
DEPS="$WORK/deps"
CC_BIN="$TOOLCHAIN/bin/${TRIPLE}${API}-clang${CC_SUFFIX}"
if [ ! -f "$DEPS/lib/libmp3lame.a" ]; then
    echo "==> building LAME"
    cd "$WORK"
    if [ ! -d "lame-3.100" ]; then
        curl --fail --location --retry 3 -o lame.tar.gz \
            "https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz"
        tar -xzf lame.tar.gz && rm -f lame.tar.gz
    fi
    cd "lame-3.100"
    # LAME 3.100 ships a decl of this symbol that bionic also defines, and the
    # duplicate stops the build before it starts.
    sed -i '/lame_init_old/d' include/libmp3lame.sym 2>/dev/null || true

    # configure is run only to generate config.h. Its Makefiles are not used:
    # they recurse into a sub-make named "make", and libtool then executes an
    # unquoted path out of "C:/Program Files", neither of which survives this
    # machine. The encoder is twenty C files with no generated sources, so
    # compiling and archiving them directly is both shorter and more reliable
    # than making autotools portable.
    ./configure --host="$TRIPLE" --prefix="$DEPS" \
        --disable-shared --enable-static --disable-frontend --disable-decoder \
        CC="$CC_BIN" AR="$TOOLCHAIN/bin/llvm-ar${EXE_SUFFIX}" \
        RANLIB="$TOOLCHAIN/bin/llvm-ranlib${EXE_SUFFIX}" \
        CFLAGS="-O2 -fPIC"
    [ -f config.h ] || { echo "LAME configure produced no config.h" >&2; exit 1; }

    mkdir -p "$DEPS/lib" "$DEPS/include/lame" obj
    # x86 configure enables LAME's SSE intrinsics, whose implementation lives
    # in vector/. Omitting it creates an archive successfully but leaves an
    # unresolved init_xrpow_core_sse, so FFmpeg rejects libmp3lame at configure.
    for source in libmp3lame/*.c libmp3lame/vector/*.c; do
        "$CC_BIN" -O2 -fPIC -DHAVE_CONFIG_H \
            -I. -Iinclude -Ilibmp3lame -Ilibmp3lame/vector \
            -c "$source" -o "obj/$(basename "${source%.c}").o"
    done
    "$TOOLCHAIN/bin/llvm-ar${EXE_SUFFIX}" rcs "$DEPS/lib/libmp3lame.a" obj/*.o
    # FFmpeg looks for <lame/lame.h>, not <lame.h>.
    cp include/lame.h "$DEPS/include/lame/lame.h"
fi
[ -f "$DEPS/lib/libmp3lame.a" ] || { echo "LAME did not build" >&2; exit 1; }

cd "$SRC"

echo "==> configuring for $ABI (API $API) with $(basename "$NDK_ROOT")"
# The NEON sources are .S files that #include assembler macros, so they have to
# go through the C preprocessor. clang decides that from the file extension
# alone, and on a case-insensitive filesystem the .S arrives as .s — which is
# how a Windows build ends up reporting "unrecognized instruction mnemonic:
# endfunc". Naming the preprocessor explicitly makes the case irrelevant, and
# keeps the hand-written ARM SIMD that a phone actually needs for transcoding.
# --disable-everything would save size but costs features the app advertises:
# the media converter offers H.264/H.265/VP9, MP3/FLAC/WAV/M4A/OGG/AAC, and
# yt-dlp needs the demuxers for whatever a site happens to serve. This is a
# normal GPL build minus the parts that cannot work on Android anyway.
./configure \
    --prefix="$WORK/out" \
    --target-os=android \
    --arch="$ARCH" \
    --enable-cross-compile \
    --cc="$TOOLCHAIN/bin/${TRIPLE}${API}-clang${CC_SUFFIX}" \
    --cxx="$TOOLCHAIN/bin/${TRIPLE}${API}-clang++${CC_SUFFIX}" \
    --as="$TOOLCHAIN/bin/${TRIPLE}${API}-clang${CC_SUFFIX} -x assembler-with-cpp" \
    --ar="$TOOLCHAIN/bin/llvm-ar${EXE_SUFFIX}" \
    --nm="$TOOLCHAIN/bin/llvm-nm${EXE_SUFFIX}" \
    --ranlib="$TOOLCHAIN/bin/llvm-ranlib${EXE_SUFFIX}" \
    --strip="$TOOLCHAIN/bin/llvm-strip${EXE_SUFFIX}" \
    --sysroot="$TOOLCHAIN/sysroot" \
    --pkg-config=false \
    --disable-shared --enable-static --enable-pic \
    --enable-gpl --enable-version3 \
    --enable-small \
    --disable-doc --disable-htmlpages --disable-manpages --disable-podpages --disable-txtpages \
    --disable-ffplay \
    --disable-debug \
    --disable-symver \
    --disable-vulkan \
    --disable-libxcb --disable-xlib --disable-sdl2 \
    --enable-jni --enable-mediacodec \
    --enable-libmp3lame \
    --extra-cflags="-O2 -fPIC -I$DEPS/include" \
    --extra-ldflags="-L$DEPS/lib" \
    --extra-ldexeflags="-pie"

# Archiving a library means naming ~875 object files on one command line, which
# is about 35 KB — past the 32 KB a Windows process may be created with. The
# arguments come back truncated mid-path ("libavfil: No such file"). Handing ar
# a response file instead is the documented way out, and $(file >) writes it
# without ever building that command line. GNU Make 4.0+ only, which is what
# the NDK-era toolchains ship.
if [ "$HOST_TAG" = windows-x86_64 ] && ! grep -q "objs.rsp" ffbuild/library.mak; then
    echo "==> teaching ar to use a response file"
    python - <<'PATCH'
from pathlib import Path
mak = Path("ffbuild/library.mak")
text = mak.read_text()
text = text.replace(
    "\t$(AR) $(ARFLAGS) $(AR_O) $^\n",
    "\t$(file >$@.objs.rsp,$^)\n\t$(AR) $(ARFLAGS) $(AR_O) @$@.objs.rsp\n")
mak.write_text(text)
PATCH
fi

echo "==> compiling (this is the slow part)"
"$MAKE" -j"$(nproc 2>/dev/null || echo 4)"

# Android's packager only extracts and chmod +x the lib*.so files in jniLibs.
# The names are a packaging requirement; these are ELF executables, not
# shared libraries, and the app runs them by path.
install -m 0755 "ffmpeg" "$JNI_DIR/libffmpeg.so"
install -m 0755 "ffprobe" "$JNI_DIR/libffprobe.so"

echo
echo "FFmpeg  -> $JNI_DIR/libffmpeg.so   ($(du -h "$JNI_DIR/libffmpeg.so" | cut -f1))"
echo "FFprobe -> $JNI_DIR/libffprobe.so  ($(du -h "$JNI_DIR/libffprobe.so" | cut -f1))"
file "$JNI_DIR/libffmpeg.so" 2>/dev/null || true
