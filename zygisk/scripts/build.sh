#!/bin/sh
set -eu

export LC_ALL=C
export TZ=UTC

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEFAULT_NDK=/Users/dijkstra/Library/Android/sdk/ndk/28.2.13676358
ZY_NDK_ROOT=${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-$DEFAULT_NDK}}
STAGE_DIR="$PROJECT_ROOT/.build/module-stage"
OUTPUT_DIR="$PROJECT_ROOT/out"
OUTPUT_ZIP="$OUTPUT_DIR/Reactor-v1.0.0.zip"

if [ ! -x "$ZY_NDK_ROOT/ndk-build" ]; then
  echo "ndk-build not found: $ZY_NDK_ROOT/ndk-build" >&2
  exit 2
fi

NDK_REVISION=$(sed -n 's/^Pkg.Revision[[:space:]]*=[[:space:]]*//p' \
  "$ZY_NDK_ROOT/source.properties" | sed -n '1p')
NDK_MAJOR=${NDK_REVISION%%.*}
if [ -z "$NDK_MAJOR" ] || [ "$NDK_MAJOR" -lt 27 ]; then
  echo "Android NDK r27 or newer is required; found ${NDK_REVISION:-unknown}" >&2
  exit 2
fi

export ANDROID_NDK_ROOT=$ZY_NDK_ROOT

BUILD_EPOCH=${SOURCE_DATE_EPOCH:-}
if [ -z "$BUILD_EPOCH" ]; then
  BUILD_EPOCH=$(git -C "$PROJECT_ROOT" log -1 --format=%ct 2>/dev/null || true)
fi
if [ -z "$BUILD_EPOCH" ]; then
  BUILD_EPOCH=315532800
fi
case "$BUILD_EPOCH" in
  *[!0-9]*)
    echo "SOURCE_DATE_EPOCH must be a non-negative integer" >&2
    exit 2
    ;;
esac

if ARCHIVE_TIMESTAMP=$(date -u -r "$BUILD_EPOCH" +%Y%m%d%H%M.%S 2>/dev/null); then
  :
else
  ARCHIVE_TIMESTAMP=$(date -u -d "@$BUILD_EPOCH" +%Y%m%d%H%M.%S)
fi

"$PROJECT_ROOT/scripts/test-host.sh" all
"$ZY_NDK_ROOT/ndk-build" -C "$PROJECT_ROOT/module" -j1

ARM64_LIBRARY="$PROJECT_ROOT/module/libs/arm64-v8a/libs26spoof.so"
if [ ! -f "$ARM64_LIBRARY" ]; then
  echo "Zygisk arm64-v8a library was not produced" >&2
  exit 1
fi

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR/zygisk" "$OUTPUT_DIR"
cp "$PROJECT_ROOT/module/module.prop" "$STAGE_DIR/module.prop"
cp "$PROJECT_ROOT/module/skip_mount" "$STAGE_DIR/skip_mount"
if [ -f "$PROJECT_ROOT/module/customize.sh" ]; then
  cp "$PROJECT_ROOT/module/customize.sh" "$STAGE_DIR/customize.sh"
fi
if [ -f "$PROJECT_ROOT/module/reactor.apk" ]; then
  cp "$PROJECT_ROOT/module/reactor.apk" "$STAGE_DIR/reactor.apk"
fi
cp "$ARM64_LIBRARY" "$STAGE_DIR/zygisk/arm64-v8a.so"
find "$STAGE_DIR" -type f -exec touch -t "$ARCHIVE_TIMESTAMP" {} +

rm -f "$OUTPUT_ZIP"
(
  cd "$STAGE_DIR"
  find . -type f | sed 's|^\./||' | LC_ALL=C sort | \
    zip -X -q "$OUTPUT_ZIP" -@
)

"$PROJECT_ROOT/scripts/verify-package.sh" "$OUTPUT_ZIP"
echo "module ZIP: $OUTPUT_ZIP"
