#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ZIP_PATH=${1:-}

if [ -z "$ZIP_PATH" ] || [ ! -f "$ZIP_PATH" ]; then
  echo "module ZIP not found: ${ZIP_PATH:-<missing argument>}" >&2
  exit 1
fi

if [ -z "${ANDROID_NDK_ROOT:-}" ]; then
  echo "ANDROID_NDK_ROOT is required" >&2
  exit 2
fi

READELF="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
if [ ! -x "$READELF" ]; then
  echo "llvm-readelf not found under ANDROID_NDK_ROOT" >&2
  exit 2
fi

ZIP_PATH=$(CDPATH= cd -- "$(dirname -- "$ZIP_PATH")" && pwd)/$(basename -- "$ZIP_PATH")
ENTRIES=$(unzip -Z1 "$ZIP_PATH")

require_entry() {
  if ! printf '%s\n' "$ENTRIES" | grep -Fqx "$1"; then
    echo "missing ZIP entry: $1" >&2
    exit 1
  fi
}

require_metadata() {
  if ! printf '%s\n' "$MODULE_METADATA" | grep -Fqx "$1"; then
    echo "missing module metadata: $1" >&2
    exit 1
  fi
}

require_entry "module.prop"
require_entry "skip_mount"
require_entry "customize.sh"
require_entry "zygisk/arm64-v8a.so"
require_entry "zygisk/armeabi-v7a.so"

if printf '%s\n' "$ENTRIES" | grep -Eq '(^|/)system\.prop$|^system/'; then
  echo "package contains a prohibited global system override" >&2
  exit 1
fi

SO_ENTRIES=$(printf '%s\n' "$ENTRIES" | grep -E '\.so$' || true)
EXPECTED_SO_ENTRIES=$(printf '%s\n' \
  "zygisk/arm64-v8a.so" \
  "zygisk/armeabi-v7a.so" | LC_ALL=C sort)
if [ "$(printf '%s\n' "$SO_ENTRIES" | LC_ALL=C sort)" != "$EXPECTED_SO_ENTRIES" ]; then
  echo "package contains unexpected or missing shared libraries" >&2
  exit 1
fi

MODULE_METADATA=$(unzip -p "$ZIP_PATH" module.prop)
require_metadata "id=reactor_zygisk"
require_metadata "name=转基因反应堆 (Reactor)"
require_metadata "version=v1.0.0"
require_metadata "versionCode=1"
require_metadata "author=sfdex"

VERIFY_TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/s26spoof-verify.XXXXXX")
trap 'rm -rf "$VERIFY_TMP_DIR"' EXIT HUP INT TERM

for ABI in arm64-v8a armeabi-v7a; do
  unzip -p "$ZIP_PATH" "zygisk/$ABI.so" > "$VERIFY_TMP_DIR/$ABI.so"
  if ! "$READELF" -Ws "$VERIFY_TMP_DIR/$ABI.so" | awk '
    $4 == "FUNC" && $5 == "GLOBAL" && $6 == "DEFAULT" &&
    $7 != "UND" && $8 == "zygisk_module_entry" { found = 1 }
    END { exit(found ? 0 : 1) }
  '; then
    echo "missing defined GLOBAL DEFAULT zygisk_module_entry export in $ABI" >&2
    exit 1
  fi
  if "$READELF" -S "$VERIFY_TMP_DIR/$ABI.so" | grep -Fq '.note.gnu.build-id'; then
    echo "non-reproducible GNU build ID section found in $ABI" >&2
    exit 1
  fi
done

echo "package verification: PASS"
