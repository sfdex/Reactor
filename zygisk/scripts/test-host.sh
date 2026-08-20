#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR="$PROJECT_ROOT/.test-bin"
CXX=${CXX:-xcrun clang++}

mkdir -p "$BUILD_DIR"

run_core() {
  $CXX -std=c++17 -Wall -Wextra -Werror \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/core_test.cpp" \
    "$PROJECT_ROOT/module/jni/core.cpp" \
    -o "$BUILD_DIR/core_test"
  "$BUILD_DIR/core_test"
}

run_maps() {
  $CXX -std=c++17 -Wall -Wextra -Werror \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/maps_test.cpp" \
    "$PROJECT_ROOT/module/jni/maps.cpp" \
    -o "$BUILD_DIR/maps_test"
  "$BUILD_DIR/maps_test"
}

run_property_hooks() {
  require_ndk
  $CXX -std=c++17 -Wall -Wextra -Werror \
    $JNI_INCLUDE \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/property_hooks_test.cpp" \
    "$PROJECT_ROOT/module/jni/property_hooks.cpp" \
    "$PROJECT_ROOT/module/jni/core.cpp" \
    -o "$BUILD_DIR/property_hooks_test"
  "$BUILD_DIR/property_hooks_test"

  $CXX -std=c++17 -Wall -Wextra -Werror \
    $JNI_INCLUDE \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/native_hooks_test.cpp" \
    "$PROJECT_ROOT/module/jni/native_hooks.cpp" \
    "$PROJECT_ROOT/module/jni/property_hooks.cpp" \
    "$PROJECT_ROOT/module/jni/core.cpp" \
    -o "$BUILD_DIR/native_hooks_test"
  "$BUILD_DIR/native_hooks_test"
}

require_ndk() {
  if [ -n "${ANDROID_NDK_ROOT:-}" ] && [ -f "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include/jni.h" ]; then
    JNI_INCLUDE="-idirafter $ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include"
  elif [ -f "/opt/homebrew/opt/openjdk@17/include/jni.h" ]; then
    JNI_INCLUDE="-I/opt/homebrew/opt/openjdk@17/include -I/opt/homebrew/opt/openjdk@17/include/darwin"
  elif [ -n "${JAVA_HOME:-}" ] && [ -f "$JAVA_HOME/include/jni.h" ]; then
    JNI_INCLUDE="-I$JAVA_HOME/include"
    if [ -d "$JAVA_HOME/include/darwin" ]; then
      JNI_INCLUDE="$JNI_INCLUDE -I$JAVA_HOME/include/darwin"
    fi
  else
    echo "JNI headers not found (set ANDROID_NDK_ROOT or JAVA_HOME)" >&2
    exit 2
  fi
}

run_jni() {
  require_ndk
  $CXX -std=c++17 -Wall -Wextra -Werror \
    $JNI_INCLUDE \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/build_fields_test.cpp" \
    "$PROJECT_ROOT/module/jni/build_fields.cpp" \
    -o "$BUILD_DIR/build_fields_test"
  "$BUILD_DIR/build_fields_test"

  $CXX -std=c++17 -Wall -Wextra -Werror \
    $JNI_INCLUDE \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/java_property_hook_test.cpp" \
    "$PROJECT_ROOT/module/jni/property_hooks.cpp" \
    "$PROJECT_ROOT/module/jni/core.cpp" \
    -o "$BUILD_DIR/java_property_hook_test"
  "$BUILD_DIR/java_property_hook_test"
}

run_config_parser() {
  $CXX -std=c++17 -Wall -Wextra -Werror \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/config_parser_test.cpp" \
    "$PROJECT_ROOT/module/jni/config_parser.cpp" \
    -o "$BUILD_DIR/config_parser_test"
  "$BUILD_DIR/config_parser_test"
}

run_lifecycle() {
  $CXX -std=c++17 -Wall -Wextra -Werror \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/lifecycle_test.cpp" \
    "$PROJECT_ROOT/module/jni/lifecycle.cpp" \
    "$PROJECT_ROOT/module/jni/core.cpp" \
    -o "$BUILD_DIR/lifecycle_test"
  "$BUILD_DIR/lifecycle_test"
}

run_companion() {
  require_ndk
  $CXX -std=c++17 -Wall -Wextra -Werror \
    $JNI_INCLUDE \
    -I"$PROJECT_ROOT/module/jni" \
    -I"$PROJECT_ROOT/module/jni/include" \
    -I"$PROJECT_ROOT/tests/native" \
    "$PROJECT_ROOT/tests/native/companion_test.cpp" \
    "$PROJECT_ROOT/module/jni/companion.cpp" \
    "$PROJECT_ROOT/module/jni/config_parser.cpp" \
    -o "$BUILD_DIR/companion_test"
  "$BUILD_DIR/companion_test"
}


case "${1:-all}" in
  core) run_core ;;
  config-parser) run_config_parser ;;
  maps) run_maps ;;
  property-hooks) run_property_hooks ;;
  jni) run_jni ;;
  lifecycle) run_lifecycle ;;
  companion) run_companion ;;
  all)
    run_core
    run_config_parser
    run_maps
    run_property_hooks
    run_jni
    run_lifecycle
    run_companion
    ;;
  *)
    echo "unknown test suite: $1" >&2
    exit 2
    ;;
esac

