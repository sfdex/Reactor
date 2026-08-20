# Galaxy S26 Ultra Zygisk Spoof Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Magisk-installable Zygisk module that makes only `com.ruanmei.ithome` processes on Android 15 see the selected mainland-China Galaxy S26 Ultra product identity.

**Architecture:** A dependency-free core owns identity data, package matching, and property lookup. Android adapters rewrite `android.os.Build`, hook Android 15's `SystemProperties.native_get`, and use the official Zygisk PLT API for bionic property readers. A small Zygisk lifecycle class wires those adapters only into the selected package and a shell build creates both ARM ABI libraries and the flashable ZIP.

**Tech Stack:** C++17 without NDK libc++, JNI, Android bionic system-property API, Zygisk API v5, Android NDK r27+, ndk-build, POSIX shell, host-side Apple Clang tests.

## Global Constraints

- Runtime target is Android 15 / API 35 with Magisk 27.0 or newer and Zygisk enabled.
- Select only `com.ruanmei.ithome` and non-empty `com.ruanmei.ithome:*` child processes.
- Identity is exactly `samsung / samsung / SM-S9480 / s26ultra / s26ultrachn` for manufacturer, brand, model, device, and product.
- Do not spoof Android version, SDK, security patch, fingerprint, serial, IMEI, Android ID, CPU/GPU, memory, display, or hardware-backed identity.
- Do not add `system.prop`, `resetprop`, a companion daemon, SELinux rules, Magisk hiding, deny-list bypass, Play Integrity bypass, or anti-detection behavior.
- Build `arm64-v8a` and `armeabi-v7a`; do not use the NDK C++ STL.
- Any failed field write or hook must fail open and leave unrelated property reads unchanged.
- Native PLT coverage is limited to already-loaded ELF callers, as approved in the design.

---

## File Map

- `.gitignore`: exclude NDK intermediates, host test binaries, and packaged output.
- `README.md`: build, install, verify, disable, and known-limit instructions.
- `module/module.prop`: stable Magisk module metadata.
- `module/skip_mount`: state that no partition overlay is required.
- `module/jni/Android.mk`: build the injected library and link Android logging.
- `module/jni/Application.mk`: API 35, C++17, no STL, and the two requested ABIs.
- `module/jni/zygisk.hpp`: unmodified official 0BSD Zygisk API v5 header.
- `module/jni/include/s26spoof/identity.hpp`: single source of truth for identity strings and Build field names.
- `module/jni/include/s26spoof/core.hpp`, `module/jni/core.cpp`: process selection, property mapping, and bounded copy.
- `module/jni/include/s26spoof/maps.hpp`, `module/jni/maps.cpp`: parse and de-duplicate executable `/proc/self/maps` entries.
- `module/jni/include/s26spoof/property_hooks.hpp`, `module/jni/property_hooks.cpp`: Java and bionic property-hook behavior and original-function storage.
- `module/jni/include/s26spoof/build_fields.hpp`, `module/jni/build_fields.cpp`: exception-safe JNI Build field writes.
- `module/jni/include/s26spoof/lifecycle.hpp`, `module/jni/lifecycle.cpp`: testable per-process lifecycle decisions.
- `module/jni/module.cpp`: Zygisk lifecycle, hook installation, unload decision, and diagnostic log.
- `tests/native/test_support.hpp`: dependency-free assertion helpers.
- `tests/native/core_test.cpp`: identity, process matcher, aliases, and bounded copy tests.
- `tests/native/maps_test.cpp`: map parser and fixed-capacity de-duplication tests.
- `tests/native/property_hooks_test.cpp`: recognized override and unknown pass-through tests for native hook wrappers.
- `tests/native/build_fields_test.cpp`: fake-JNI success and fail-open behavior tests.
- `tests/native/lifecycle_test.cpp`: target/non-target hook and unload decisions.
- `scripts/test-host.sh`: compile and execute all host-native tests.
- `scripts/build.sh`: test, ndk-build, stage, validate, and ZIP the module.
- `scripts/verify-package.sh`: assert metadata, ABI names, exports, and prohibited-file absence.

---

### Task 1: Pure identity and selection core

**Files:**
- Create: `.gitignore`
- Create: `module/jni/include/s26spoof/identity.hpp`
- Create: `module/jni/include/s26spoof/core.hpp`
- Create: `module/jni/core.cpp`
- Create: `tests/native/test_support.hpp`
- Create: `tests/native/core_test.cpp`
- Create: `scripts/test-host.sh`

**Interfaces:**
- Produces: `bool s26spoof::is_target_process(const char *name) noexcept`
- Produces: `const char *s26spoof::find_property_override(const char *key) noexcept`
- Produces: `int s26spoof::copy_property_value(const char *value, char *destination, size_t capacity) noexcept`
- Produces: `s26spoof::kBuildFields`, an array of five `{field_name, value}` entries.

- [ ] **Step 1: Write the failing core tests**

Create a single test executable that asserts these inputs before production files exist:

```cpp
EXPECT_TRUE(is_target_process("com.ruanmei.ithome"));
EXPECT_TRUE(is_target_process("com.ruanmei.ithome:web"));
EXPECT_FALSE(is_target_process(nullptr));
EXPECT_FALSE(is_target_process(""));
EXPECT_FALSE(is_target_process("com.ruanmei.ithome:"));
EXPECT_FALSE(is_target_process("com.ruanmei.ithome2"));
EXPECT_FALSE(is_target_process("system_server"));

EXPECT_STREQ(find_property_override("ro.product.model"), "SM-S9480");
EXPECT_STREQ(find_property_override("ro.product.vendor.brand"), "samsung");
EXPECT_STREQ(find_property_override("ro.product.system_ext.name"), "s26ultrachn");
EXPECT_NULL(find_property_override("ro.build.version.release"));
EXPECT_NULL(find_property_override("ro.build.fingerprint"));
EXPECT_NULL(find_property_override("ro.serialno"));

char output[92] = {};
EXPECT_EQ(copy_property_value("SM-S9480", output, sizeof(output)), 8);
EXPECT_STREQ(output, "SM-S9480");
EXPECT_EQ(copy_property_value("abcd", output, 3), 2);
EXPECT_STREQ(output, "ab");
```

Loop over all six accepted property prefixes and all five identity suffixes so every approved alias is asserted. Also assert that `kBuildFields` contains exactly five entries and no field named `VERSION`, `FINGERPRINT`, or `SERIAL`.

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./scripts/test-host.sh core
```

Expected: non-zero exit because `s26spoof/core.hpp` or the referenced symbols do not exist.

- [ ] **Step 3: Implement the minimal pure core**

Define the identity once:

```cpp
namespace s26spoof {
struct BuildField { const char *name; const char *value; };
inline constexpr char kPackageName[] = "com.ruanmei.ithome";
inline constexpr BuildField kBuildFields[] = {
    {"MANUFACTURER", "samsung"},
    {"BRAND", "samsung"},
    {"MODEL", "SM-S9480"},
    {"DEVICE", "s26ultra"},
    {"PRODUCT", "s26ultrachn"},
};
}
```

Implement exact package equality plus a non-empty colon suffix. Implement property lookup from the six exact prefixes and five exact suffix/value pairs; reject null keys and trailing or extra characters. Implement bounded copy so invalid inputs return `-1`, capacity zero writes nothing, and any positive capacity always receives a terminator.

Make `scripts/test-host.sh core` compile with:

```bash
xcrun clang++ -std=c++17 -Wall -Wextra -Werror \
  -Imodule/jni/include tests/native/core_test.cpp module/jni/core.cpp \
  -o .test-bin/core_test
.test-bin/core_test
```

- [ ] **Step 4: Run the core test and verify GREEN**

Run `./scripts/test-host.sh core`.

Expected: `core_test: PASS` and exit 0 with no warnings.

- [ ] **Step 5: Commit the core**

```bash
git add .gitignore module/jni/include/s26spoof/identity.hpp module/jni/include/s26spoof/core.hpp module/jni/core.cpp tests/native scripts/test-host.sh
git commit -m "feat: add identity spoof core"
```

---

### Task 2: Native property wrappers and map parsing

**Files:**
- Create: `module/jni/include/s26spoof/maps.hpp`
- Create: `module/jni/maps.cpp`
- Create: `module/jni/include/s26spoof/property_hooks.hpp`
- Create: `module/jni/property_hooks.cpp`
- Create: `tests/native/maps_test.cpp`
- Create: `tests/native/property_hooks_test.cpp`
- Modify: `scripts/test-host.sh`

**Interfaces:**
- Consumes: `find_property_override()` and `copy_property_value()` from Task 1.
- Produces: `bool s26spoof::parse_map_identity(const char *line, MapIdentity *result) noexcept`
- Produces: `bool s26spoof::remember_unique_map(MapIdentity value, MapIdentity *values, size_t *count, size_t capacity) noexcept`
- Produces: `void s26spoof::set_original_property_get(PropertyGetFn function) noexcept`
- Produces: `void s26spoof::set_original_property_read_callback(PropertyReadCallbackFn function) noexcept`
- Produces: `int s26spoof::hooked_property_get(const char *key, char *value) noexcept`
- Produces: `void s26spoof::hooked_property_read_callback(const prop_info *info, PropertyReadCallback callback, void *cookie) noexcept`

- [ ] **Step 1: Write failing parser and hook tests**

Test Android-style map lines and rejection:

```cpp
MapIdentity parsed{};
EXPECT_TRUE(parse_map_identity(
    "7a1000-7b2000 r-xp 00000000 fd:01 1234 /system/lib64/libc.so", &parsed));
EXPECT_TRUE(parsed.executable);
EXPECT_EQ(parsed.inode, static_cast<ino_t>(1234));
EXPECT_FALSE(parse_map_identity("not a maps line", &parsed));
```

Test fixed-capacity de-duplication by adding the same device/inode twice, then a distinct entry, and finally a third entry into a capacity-two array. Expected results are `true`, `false`, `true`, and `false`; the count remains two.

Install fake original property functions and assert:

```cpp
char value[92] = {};
EXPECT_EQ(hooked_property_get("ro.product.model", value), 8);
EXPECT_STREQ(value, "SM-S9480");
EXPECT_EQ(hooked_property_get("ro.build.version.release", value), 4);
EXPECT_STREQ(value, "15.0");
```

For the callback path, make the fake original emit both `ro.product.brand=realbrand` and an unrelated property. Assert that the user callback receives `samsung` for the first and the unchanged value for the second. Assert null callback and null original pointers return safely.

- [ ] **Step 2: Run the new tests and verify RED**

Run:

```bash
./scripts/test-host.sh maps
./scripts/test-host.sh property-hooks
```

Expected: both builds fail because the new headers or functions are missing.

- [ ] **Step 3: Implement map parsing and hook wrappers**

Use `sscanf` to read permissions, hex device major/minor, and decimal inode. Accept only successfully parsed entries with nonzero inode, and mark executable only when the permission string contains `x`. `remember_unique_map` compares both `dev_t` and `ino_t` and never writes past capacity.

Keep original function pointers in file-local storage. The property-get wrapper must return overrides without calling the original, and delegate unknown keys exactly once. The callback wrapper must use a stack context:

```cpp
struct CallbackContext {
    PropertyReadCallback callback;
    void *cookie;
};
```

Its adapter looks up the callback-supplied name, substitutes only a recognized value, and preserves the original serial. Do not log or allocate in either hook path.

- [ ] **Step 4: Run all available host tests and verify GREEN**

Run `./scripts/test-host.sh all`.

Expected: `core_test`, `maps_test`, and `property_hooks_test` each print `PASS`; exit 0 and no warnings.

- [ ] **Step 5: Commit native wrappers**

```bash
git add module/jni/include/s26spoof/maps.hpp module/jni/maps.cpp module/jni/include/s26spoof/property_hooks.hpp module/jni/property_hooks.cpp tests/native scripts/test-host.sh
git commit -m "feat: add native property hook wrappers"
```

---

### Task 3: Exception-safe JNI adapters

**Files:**
- Create: `module/jni/include/s26spoof/build_fields.hpp`
- Create: `module/jni/build_fields.cpp`
- Create: `tests/native/fake_jni.hpp`
- Create: `tests/native/build_fields_test.cpp`
- Create: `tests/native/java_property_hook_test.cpp`
- Modify: `module/jni/include/s26spoof/property_hooks.hpp`
- Modify: `module/jni/property_hooks.cpp`
- Modify: `scripts/test-host.sh`

**Interfaces:**
- Consumes: `kBuildFields` and `find_property_override()`.
- Produces: `BuildWriteResult s26spoof::write_build_fields(JNIEnv *environment) noexcept`, where `BuildWriteResult` contains `uint8_t attempted`, `uint8_t succeeded`, and `bool exception_cleared`.
- Produces: `using JavaPropertyGetFn = jstring (*)(JNIEnv *, jclass, jstring, jstring)`.
- Produces: `void s26spoof::set_original_java_property_get(JavaPropertyGetFn function) noexcept`.
- Produces: `jstring s26spoof::hooked_java_property_get(JNIEnv *, jclass, jstring key, jstring default_value) noexcept`.

- [ ] **Step 1: Write failing fake-JNI tests**

Build a `JNINativeInterface_` table that supplies only the JNI calls used by production: `FindClass`, `GetStaticFieldID`, `NewStringUTF`, `SetStaticObjectField`, `GetStringUTFChars`, `ReleaseStringUTFChars`, `ExceptionCheck`, `ExceptionClear`, and `DeleteLocalRef`.

Test a fully successful writer:

```cpp
const BuildWriteResult result = write_build_fields(fake.environment());
EXPECT_EQ(result.attempted, 5);
EXPECT_EQ(result.succeeded, 5);
EXPECT_FALSE(result.exception_cleared);
EXPECT_STREQ(fake.value_for("MODEL"), "SM-S9480");
EXPECT_STREQ(fake.value_for("PRODUCT"), "s26ultrachn");
```

Configure `GetStaticFieldID` to fail with a pending exception for `DEVICE`. Assert four fields still succeed, the exception is cleared, no pending exception remains, and later `PRODUCT` is still written. Pass a null environment and assert a zeroed result.

For the Java property hook, assert a recognized key returns a newly created spoof string without calling the fake original. Assert an unknown key delegates once and returns the fake original result. Assert failed UTF access clears the module-created exception and falls back to the original.

- [ ] **Step 2: Run the JNI tests and verify RED**

Run:

```bash
ANDROID_NDK_ROOT=/Users/dijkstra/Library/Android/sdk/ndk/28.2.13676358 ./scripts/test-host.sh jni
```

Expected: compile failure because `build_fields.hpp` and the Java hook functions are absent.

- [ ] **Step 3: Implement the JNI adapters**

Include JNI headers from `$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include` for host tests. In `write_build_fields`, find `android/os/Build` once, then process every entry independently. After every JNI operation, check `ExceptionCheck`; record and clear failures, delete any created local reference, and proceed to the next field.

The Java property hook must always release a successfully acquired UTF key. Recognized properties return `NewStringUTF(override)`. Unknown keys and any conversion failure delegate to the saved original pointer. If no original is available, return the supplied default value rather than throwing.

- [ ] **Step 4: Run every host test and verify GREEN**

Run:

```bash
ANDROID_NDK_ROOT=/Users/dijkstra/Library/Android/sdk/ndk/28.2.13676358 ./scripts/test-host.sh all
```

Expected: five test executables print `PASS`; exit 0 with no sanitizer, compiler, or JNI-fake warnings.

- [ ] **Step 5: Commit JNI adapters**

```bash
git add module/jni/include/s26spoof/build_fields.hpp module/jni/build_fields.cpp module/jni/include/s26spoof/property_hooks.hpp module/jni/property_hooks.cpp tests/native scripts/test-host.sh
git commit -m "feat: add JNI identity adapters"
```

---

### Task 4: Zygisk lifecycle and Android shared libraries

**Files:**
- Create: `module/jni/zygisk.hpp`
- Create: `module/jni/module.cpp`
- Create: `module/jni/Android.mk`
- Create: `module/jni/Application.mk`
- Create: `module/jni/include/s26spoof/lifecycle.hpp`
- Create: `module/jni/lifecycle.cpp`
- Create: `tests/native/lifecycle_test.cpp`
- Modify: `scripts/test-host.sh`

**Interfaces:**
- Consumes: every production interface from Tasks 1–3.
- Produces: `LifecycleActions lifecycle_actions_for_process(const char *)`, used directly by the Zygisk callback.
- Produces: exported `zygisk_module_entry` through `REGISTER_ZYGISK_MODULE(S26SpoofModule)`.
- Produces: `bool install_java_property_hook(zygisk::Api *, JNIEnv *)` and `bool install_native_property_hooks(zygisk::Api *)`, file-local helpers in `module.cpp`.

- [ ] **Step 1: Write the failing lifecycle behavior test**

The host test asserts that main and child target processes keep the library and enable Java hooks, native hooks, and Build writes. It also asserts that unrelated and malformed process names unload the library and enable none of those actions:

```cpp
const LifecycleActions target = lifecycle_actions_for_process("com.ruanmei.ithome");
EXPECT_TRUE(target.target);
EXPECT_FALSE(target.unload_library);
EXPECT_TRUE(target.install_java_hook);
EXPECT_TRUE(target.install_native_hooks);
EXPECT_TRUE(target.write_build_fields);

const LifecycleActions other = lifecycle_actions_for_process("com.example.other");
EXPECT_FALSE(other.target);
EXPECT_TRUE(other.unload_library);
EXPECT_FALSE(other.install_java_hook);
EXPECT_FALSE(other.install_native_hooks);
EXPECT_FALSE(other.write_build_fields);
```

- [ ] **Step 2: Run the contract test and verify RED**

Run `./scripts/test-host.sh lifecycle`, then run NDK build before adding the build files.

Expected: the host compile fails because `lifecycle.cpp` is missing, and ndk-build fails because the application project/build rules are missing.

- [ ] **Step 3: Add the official header, lifecycle, and NDK rules**

Vendor the canonical Zygisk API v5 header unchanged from `topjohnwu/zygisk-module-sample`, preserving its 0BSD license.

Implement `LifecycleActions` as a pure value derived from `is_target_process`, then make the Zygisk class follow those flags. Use C headers and the system `libstdc++` runtime support library with `APP_STL := none`; the latter supplies the `__cxa_guard_*` functions used by the official header's function-local static objects without adding NDK libc++.

Implement `S26SpoofModule` with this lifecycle:

```cpp
void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
    target_ = matches_nice_name(args == nullptr ? nullptr : args->nice_name);
    if (!target_) {
        api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        return;
    }
    java_hooked_ = install_java_property_hook(api_, environment_);
    native_hooked_ = install_native_property_hooks(api_);
}

void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
    if (!target_) return;
    const auto result = write_build_fields(environment_);
    __android_log_print(ANDROID_LOG_INFO, "S26Spoof",
        "target=1 java=%d native=%d fields=%u/%u model=%s product=%s",
        java_hooked_, native_hooked_, result.succeeded, result.attempted,
        "SM-S9480", "s26ultrachn");
}
```

`matches_nice_name` must acquire and release `nice_name` safely and clear only exceptions caused by the module. Install the Android 15 JNI hook with method name `native_get` and signature `(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;`; store the returned original pointer only when non-null.

For native PLT hooks, read `/proc/self/maps`, keep at most 512 unique executable device/inode pairs, register both approved symbols for each pair, and call `pltHookCommit` once. Store original pointers through the setters from Task 2. A parse/open/commit failure returns false without aborting the process.

Set `APP_PLATFORM := android-35`, `APP_ABI := arm64-v8a armeabi-v7a`, `APP_STL := none`, hidden default visibility, section garbage collection, and link `-llog`.

- [ ] **Step 4: Run lifecycle and cross-compilation verification**

Run:

```bash
ANDROID_NDK_ROOT=/Users/dijkstra/Library/Android/sdk/ndk/28.2.13676358 ./scripts/test-host.sh all
/Users/dijkstra/Library/Android/sdk/ndk/28.2.13676358/ndk-build -C module -j2
```

Expected: all host tests pass and ndk-build creates:

```text
module/libs/arm64-v8a/libs26spoof.so
module/libs/armeabi-v7a/libs26spoof.so
```

- [ ] **Step 5: Commit the Zygisk library**

```bash
git add docs/superpowers/plans/2026-08-07-s26-ultra-zygisk.md module/jni tests/native/lifecycle_test.cpp scripts/test-host.sh
git commit -m "feat: wire Zygisk identity hooks"
```

---

### Task 5: Magisk packaging, documentation, and full verification

**Files:**
- Create: `module/module.prop`
- Create: `module/skip_mount`
- Create: `scripts/verify-package.sh`
- Create: `scripts/build.sh`
- Create: `README.md`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: `module/libs/<abi>/libs26spoof.so` from Task 4.
- Produces: `out/s26spoof-v1.0.0.zip` containing `module.prop`, `skip_mount`, `zygisk/arm64-v8a.so`, and `zygisk/armeabi-v7a.so`.

- [ ] **Step 1: Write the failing package verifier**

Make `scripts/verify-package.sh <zip>` extract only a listing and assert all four required entries exist. It must reject `system.prop`, a `system/` tree, missing ABI files, unexpected library names, and metadata that does not contain:

```properties
id=s26ultra_ithome
name=Galaxy S26 Ultra for IT之家
version=v1.0.0
versionCode=1
author=Codex
description=Spoofs Galaxy S26 Ultra product identity only inside com.ruanmei.ithome on Android 15.
```

For each staged `.so`, use the matching NDK `llvm-readelf -Ws` and assert a global `zygisk_module_entry` export.

- [ ] **Step 2: Run package verification and verify RED**

Run:

```bash
ANDROID_NDK_ROOT=/Users/dijkstra/Library/Android/sdk/ndk/28.2.13676358 ./scripts/verify-package.sh out/s26spoof-v1.0.0.zip
```

Expected: non-zero exit because the ZIP does not exist.

- [ ] **Step 3: Implement deterministic staging and documentation**

`scripts/build.sh` must:

1. Require or discover an NDK r27+ path and verify `ndk-build` exists.
2. Run `scripts/test-host.sh all`.
3. remove only the repository-local `.build/module-stage` directory and recreate it.
4. run `ndk-build -C module -j2`.
5. copy module metadata and rename the libraries to their Magisk ABI filenames.
6. create `out/s26spoof-v1.0.0.zip` from inside staging with sorted input paths.
7. call `scripts/verify-package.sh` on the resulting ZIP.

The README must document the Android 15 and Magisk 27+ requirements, enable-Zygisk/install/reboot steps, target package, exact spoofed fields, `adb logcat -s S26Spoof` verification, removal by disabling the module and rebooting, and the approved late-loaded-native-library limitation. It must explicitly state that Android version and integrity status are not changed.

- [ ] **Step 4: Run full fresh verification**

Run:

```bash
ANDROID_NDK_ROOT=/Users/dijkstra/Library/Android/sdk/ndk/28.2.13676358 ./scripts/build.sh
git diff --check
git status --short
```

Expected: all host tests pass; both Android ABIs build; package verification passes; `git diff --check` has no output; status contains only the intended Task 5 source changes plus ignored local build output.

Inspect `unzip -l out/s26spoof-v1.0.0.zip` and confirm there is no `system.prop` or `system/` entry.

- [ ] **Step 5: Commit the installable module sources**

```bash
git add .gitignore README.md module/module.prop module/skip_mount scripts/build.sh scripts/verify-package.sh
git commit -m "build: package installable Zygisk module"
```

---

## Device Verification Handoff

Host completion does not claim that the hooks ran on a real phone because no device is connected. When an Android 15 test phone is connected, install `out/s26spoof-v1.0.0.zip` in Magisk, reboot, launch IT之家, and collect:

```bash
adb logcat -c
adb shell am force-stop com.ruanmei.ithome
adb shell monkey -p com.ruanmei.ithome 1
adb logcat -d -s S26Spoof
```

The expected target log contains `java=1`, `native=1`, `fields=5/5`, `model=SM-S9480`, and `product=s26ultrachn`. Then verify a non-target device-info application still shows the real phone. If either hook flag is zero, preserve the log and diagnose that layer without changing the fail-open behavior.
