# Galaxy S26 Ultra Zygisk Spoof Design

## Context

Build a standalone Magisk Zygisk module for an Android 15 phone. The module changes the device identity visible inside IT之家 only. The target package is `com.ruanmei.ithome`, including its colon-suffixed child processes. Other applications and system processes must continue to see the phone's real identity.

The module uses two complementary mechanisms because either mechanism alone can miss an access path:

1. Rewrite the already initialized static fields in `android.os.Build` with JNI.
2. Hook Java and native system-property reads for product identity properties.

The module is a customization tool. It does not attempt to bypass integrity checks, hide Magisk, or spoof hardware-backed identity.

## Goals

- Run on Android 15 (API 35) with Magisk Zygisk enabled.
- Affect only `com.ruanmei.ithome` and processes whose names start with `com.ruanmei.ithome:`.
- Make the target processes see a consistent mainland-China Galaxy S26 Ultra identity.
- Cover ordinary `android.os.Build` reads, Java `SystemProperties` reads, and native property reads made through already loaded ELF callers.
- Produce a Magisk-installable ZIP with `arm64-v8a` and `armeabi-v7a` Zygisk libraries.
- Fail open: a failed hook or field update must leave the application running and preserve all unaffected reads.

## Non-goals

- Do not change Android version, SDK level, security patch level, build fingerprint, serial number, IMEI, Android ID, CPU/GPU, memory, display, or other hardware capabilities.
- Do not write global properties with `resetprop` or ship `system.prop`.
- Do not inject into `system_server` or any package other than IT之家.
- Do not implement Magisk hiding, deny-list bypasses, Play Integrity bypasses, or anti-detection behavior.
- Do not guarantee interception of direct property calls from native libraries loaded after the Zygisk specialization callbacks. Covering that path would require a riskier inline or dynamic-loader hook and is outside this module.

## Target Identity

The public mainland-China model number is `SM-S9480`. The module will use these exact values:

| Field | Value |
|---|---|
| `Build.MANUFACTURER` | `samsung` |
| `Build.BRAND` | `samsung` |
| `Build.MODEL` | `SM-S9480` |
| `Build.DEVICE` | `s26ultra` |
| `Build.PRODUCT` | `s26ultrachn` |

Samsung's public support material confirms the model number but does not publish the exact production values for `Build.DEVICE` and `Build.PRODUCT`. Those two stable project values are deliberately kept in one identity definition so a later verified device dump can replace them without changing hook code.

## Process Selection

The process matcher accepts exactly:

- `com.ruanmei.ithome`
- Any non-empty child-process name beginning with `com.ruanmei.ithome:`

It rejects similar prefixes such as `com.ruanmei.ithome2`, empty names, and all system processes. `preAppSpecialize` reads `nice_name` once and stores a per-process target flag.

For non-target processes, the module sets `DLCLOSE_MODULE_LIBRARY` and performs no field writes or hooks. For target processes, it keeps the library loaded because installed hook function pointers remain inside the module. The system-server specialization path always requests `DLCLOSE_MODULE_LIBRARY` and never installs hooks or writes fields.

## Components

### Identity definition

A dependency-free C++ header owns the five identity strings and property aliases. No Android or JNI code duplicates identity values.

### Process matcher

A pure C++ function matches the main package and colon-suffixed child processes. Keeping this independent from JNI makes exact-match behavior host-testable.

### Property override lookup

A pure C++ lookup function returns an override for recognized product identity keys and returns null for every other key. It covers:

- `ro.product.{manufacturer,brand,model,device,name}`
- `ro.product.system.{manufacturer,brand,model,device,name}`
- `ro.product.vendor.{manufacturer,brand,model,device,name}`
- `ro.product.product.{manufacturer,brand,model,device,name}`
- `ro.product.odm.{manufacturer,brand,model,device,name}`
- `ro.product.system_ext.{manufacturer,brand,model,device,name}`

The `name` property maps to `s26ultrachn`. Unknown, null, version, fingerprint, and hardware keys always pass through unchanged.

### Build field writer

After specialization, the writer finds `android/os/Build` and updates the five static `String` fields with `SetStaticObjectField`. Each field is handled independently. It deletes local references, preserves an exception that was already pending on entry, and clears only failures caused by its own lookups and writes so one unavailable field cannot abort the remaining updates or escape into the application.

### Java system-property hook

Before specialization, the module uses Zygisk's supported `hookJniNativeMethods` API to replace Android 15's `android.os.SystemProperties.native_get(String, String)` native method. The hook converts the requested key, returns a new Java string for recognized identity properties, and delegates all other reads to the saved original function.

If the method signature is unavailable or the original pointer is not returned, this layer is disabled and the other layers continue.

### Native system-property hooks

Before specialization, the module parses `/proc/self/maps`, de-duplicates mapped executable files by device and inode, and registers Zygisk PLT hooks for:

- `__system_property_get`
- `__system_property_read_callback`

`__system_property_get` performs a bounded copy of the mapped override into the caller buffer and returns the written byte count. Its override values are shorter than `PROP_VALUE_MAX` and are always null-terminated.

The read-callback hook delegates to the original reader with a wrapper callback. The wrapper substitutes only recognized name/value pairs before invoking the caller's original callback. Unknown keys preserve the original callback arguments exactly.

Before registering either native hook, the module resolves both bionic implementations with `dlsym` and publishes them as fail-open delegates. If either delegate is unavailable, no native hook is registered. If `pltHookCommit` fails after partially applying changes, any active wrapper still delegates unrelated reads to the pre-published originals. The module logs each hook status separately and continues with the JNI field writer and any Java hook that succeeded.

## Lifecycle and Data Flow

1. Zygisk loads the native module in a newly forked application process and calls `onLoad`.
2. `preAppSpecialize` reads `nice_name` and invokes the pure process matcher.
3. A non-target process requests module unload and stops.
4. A target process installs the Java property hook and the native PLT property hooks. It never requests module unload.
5. After Android applies the target application's sandbox, `postAppSpecialize` updates the five `android.os.Build` fields.
6. A single diagnostic log line reports which layers installed and the final five field values. Individual failures are logged without sensitive data.
7. IT之家 starts. Static Build reads use the rewritten fields; matching property reads use the hook mapping; all unrelated reads call their original implementations.

The system-server callback is separate from this flow and immediately requests module unload.

## Error Handling and Safety

- Null JNI strings, failed UTF conversion, missing classes or fields, pending exceptions, null buffers, and missing original hook pointers are checked explicitly.
- The module never returns fabricated data for a key absent from the override table.
- JNI exceptions caused by the module are described only in debug builds, then cleared before returning to Android.
- Hook functions do not allocate through the C++ STL and avoid recursive property or logging calls.
- Hook status logging uses Android logging only outside the property-hook call path.
- No companion daemon, root IPC, SELinux rule, boot script, or writable configuration is required.
- Removing or disabling the module and rebooting restores the original behavior because no persistent device property is changed.

## Build and Packaging

- Use the official 0BSD Zygisk API v5 header and its documented module lifecycle.
- Require Magisk 27.0 or newer, matching Zygisk API v5.
- Compile with Android NDK r27 or newer and API level 35.
- Build `arm64-v8a` and `armeabi-v7a` without the NDK C++ STL, following the official Zygisk sample guidance.
- Package `module.prop`, `skip_mount`, and the two ABI libraries as `zygisk/arm64-v8a.so` and `zygisk/armeabi-v7a.so`.
- Normalize archive timestamps from `SOURCE_DATE_EPOCH` (or the Git commit time) so clean builds are byte-for-byte reproducible.
- Disable the path-sensitive GNU linker build ID; package verification rejects either ABI if that note reappears.
- Do not include `system.prop` or a mounted `system` directory.

## Testing

Host-side tests run before Android compilation and cover:

- Exact main-process match.
- Valid child-process matches.
- Rejection of near-prefix, empty, null, and unrelated process names.
- Every direct and partition-qualified property alias.
- Pass-through of unknown, Android-version, fingerprint, serial, and hardware properties.
- Correct bounded-copy byte count and null termination.
- Identity constants, including `SM-S9480`, and the absence of version overrides.
- System-server unload behavior, pre-existing JNI exception preservation, and native-hook failure/partial-success states.

Build verification covers:

- Clean host-test execution.
- Successful NDK compilation for both requested ABIs.
- Expected exported Zygisk entry point in both shared libraries.
- ZIP contents, module metadata, absence of `system.prop`, and correct ABI filenames.
- Matching SHA-256 hashes from two clean builds.

On-device verification requires an Android 15 phone with Magisk/Zygisk and IT之家 installed. After flashing and rebooting:

- Confirm a target-process log reports all five rewritten fields and hook installation status.
- Confirm launching and browsing IT之家 does not crash.
- Confirm a non-target application still reports the real device identity.
- Confirm disabling the module and rebooting restores IT之家的 original view.

No device is currently connected, so on-device results cannot be claimed as part of host-only completion.

## Acceptance Criteria

- The installable ZIP builds reproducibly from the repository.
- Host tests and both ABI builds pass.
- Only IT之家 main and child processes are selected.
- Target-process logs on Android 15 show `samsung / samsung / SM-S9480 / s26ultra / s26ultrachn` after specialization.
- Unrelated properties and non-target processes are unchanged.
- Any individual spoofing-layer failure is non-fatal and visible in a concise diagnostic log.
