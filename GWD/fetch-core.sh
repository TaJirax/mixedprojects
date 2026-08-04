#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$ROOT/app/libs"
AAR="$ROOT/app/libs/libv2ray.aar"
URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.7.31/libv2ray.aar"
if [ ! -f "$AAR" ] || [ "$(stat -c%s "$AAR" 2>/dev/null || echo 0)" -lt 1000000 ]; then
  echo "Downloading libv2ray.aar..."
  curl -L --fail -o "$AAR" "$URL"
fi
echo "OK: $AAR"
