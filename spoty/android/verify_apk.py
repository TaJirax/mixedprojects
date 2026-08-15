#!/usr/bin/env python3
"""Verify that an Android release contains a complete, architecture-correct payload."""

from pathlib import Path
import io
import struct
import sys
import zipfile


ABIS = {
    "arm64-v8a": 183,   # EM_AARCH64
    "armeabi-v7a": 40,  # EM_ARM
    "x86": 3,           # EM_386
    "x86_64": 62,       # EM_X86_64
}


def fail(message):
    raise SystemExit(f"APK verification failed: {message}")


if len(sys.argv) != 3:
    raise SystemExit("usage: verify_apk.py APK (universal|ABI)")

apk = Path(sys.argv[1])
target = sys.argv[2]
if target == "universal":
    expected_abis = set(ABIS)
elif target in ABIS:
    expected_abis = {target}
else:
    fail(f"unknown target {target!r}")

with zipfile.ZipFile(apk) as archive:
    names = set(archive.namelist())
    actual_abis = {
        name.split("/", 2)[1]
        for name in names
        if name.startswith("lib/") and name.count("/") >= 2
    }
    if actual_abis != expected_abis:
        fail(f"expected ABI directories {sorted(expected_abis)}, found {sorted(actual_abis)}")

    common = {
        "assets/chaquopy/app.imy",
        "assets/chaquopy/bootstrap.imy",
        "assets/chaquopy/requirements-common.imy",
        "assets/chaquopy/stdlib-common.imy",
        "assets/web/index.html",
    }
    missing = common - names

    # Pure-Python requirements are stored once, while compiled wheels are
    # stored in a separate archive for every ABI. Checking only that the IMY
    # files exist would miss a dependency-resolution or packaging regression.
    with zipfile.ZipFile(io.BytesIO(
            archive.read("assets/chaquopy/requirements-common.imy"))) as requirements:
        common_names = set(requirements.namelist())
    common_packages = {
        "yt_dlp/", "gallery_dl/", "streamlink/", "reportlab/", "docx/",
        "openpyxl/", "pptx/", "odf/", "pypdf/", "ebooklib/", "ytmusicapi/",
        "mutagen/", "requests/", "certifi/",
    }
    missing_packages = {
        package for package in common_packages
        if not any(name.startswith(package) for name in common_names)
    }

    for abi in expected_abis:
        required = {
            f"lib/{abi}/libchaquopy_java.so",
            f"lib/{abi}/libffmpeg.so",
            f"lib/{abi}/libffprobe.so",
            f"lib/{abi}/libpython3.11.so",
            f"assets/chaquopy/bootstrap-native/{abi}/java/chaquopy.so",
            f"assets/chaquopy/requirements-{abi}.imy",
            f"assets/chaquopy/stdlib-{abi}.imy",
        }
        missing.update(required - names)

        requirements_name = f"assets/chaquopy/requirements-{abi}.imy"
        native_names = set()
        if requirements_name in names:
            with zipfile.ZipFile(io.BytesIO(archive.read(requirements_name))) as requirements:
                native_names = set(requirements.namelist())
        native_packages = {"_brotli.so", "PIL/_imaging.so", "lxml/etree.so",
                           "Crypto/Cipher/_raw_aes.so"}
        missing_packages.update(
            f"{abi}:{package}" for package in native_packages
            if package not in native_names and package not in common_names
        )

        for binary in (f"lib/{abi}/libffmpeg.so", f"lib/{abi}/libffprobe.so",
                       f"lib/{abi}/libpython3.11.so"):
            if binary not in names:
                continue
            header = archive.read(binary)[:20]
            if header[:4] != b"\x7fELF" or len(header) < 20:
                fail(f"{binary} is not an ELF binary")
            byte_order = "<" if header[5] == 1 else ">"
            machine = struct.unpack(f"{byte_order}H", header[18:20])[0]
            if machine != ABIS[abi]:
                fail(f"{binary} has ELF machine {machine}, expected {ABIS[abi]}")

    if missing:
        fail("missing entries: " + ", ".join(sorted(missing)))
    if missing_packages:
        fail("missing Python packages: " + ", ".join(sorted(missing_packages)))

print(f"Verified {apk.name}: {', '.join(sorted(expected_abis))}")
