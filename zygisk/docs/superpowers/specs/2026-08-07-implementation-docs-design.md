# Bilingual Implementation Documentation Design

## Purpose

Add developer-facing documentation that explains how the Zygisk module works
and how to maintain or adapt it. The documentation targets developers who need
to change the target package, spoofed identity, property mappings, build fields,
or supported ABIs without first reverse-engineering the entire codebase.

This change is documentation-only. It does not alter module behavior, packaging,
or the released version.

## Deliverables

- `docs/IMPLEMENTATION.md`: the default English developer guide.
- `docs/IMPLEMENTATION.zh-CN.md`: an equivalent Simplified Chinese guide.
- `README.md`: a short implementation-documentation section linking both
  language versions.

The existing files under `docs/superpowers/` remain development records and are
not repurposed as public maintenance guides.

## Document Structure

Both language versions use the same section order and contain equivalent
technical detail:

1. Goals and scope boundaries.
2. Zygisk lifecycle and end-to-end data flow, including a Mermaid flowchart.
3. The three identity-coverage layers:
   - JNI writes to `android.os.Build`.
   - The `SystemProperties.native_get` JNI hook.
   - PLT hooks for the two supported bionic property APIs.
4. Property mappings and the single identity source of truth.
5. Fail-open behavior and JNI exception ownership.
6. Maintenance recipes:
   - Change the target package.
   - Change the spoofed identity.
   - Add or remove product-property aliases.
   - Adjust the rewritten Build fields.
   - Add or remove an ABI.
7. Build, host tests, package verification, runtime logs, and debugging.
8. Known limitations and possible extension points.

## Technical Content Requirements

The guides describe the implementation that exists in the current source tree,
not a hypothetical architecture. They reference the relevant source files,
including:

- `module/jni/module.cpp`
- `module/jni/build_fields.cpp`
- `module/jni/property_hooks.cpp`
- `module/jni/native_hooks.cpp`
- `module/jni/core.cpp`
- `module/jni/lifecycle.cpp`
- `module/jni/include/s26spoof/identity.hpp`

The lifecycle section explains target-process selection, non-target unloading,
the dedicated system-server unload path, pre-specialization hook installation,
and post-specialization Build-field writes.

The identity section documents the five current values and all six supported
product-property prefix groups. It explains why all consumers reference
`identity.hpp` instead of duplicating literals.

The safety section documents pre-published bionic delegates resolved with
`dlsym`, refusal to register native hooks when either delegate is missing,
pass-through behavior for unrelated properties, partial-commit handling,
preservation of pre-existing JNI exceptions, and independent hook-status logs.

The customization section gives concrete file-level instructions and calls out
which tests must be updated when a mapping or identity changes.

## Synchronization Rules

- English and Chinese documents keep identical section ordering.
- Field values, commands, code symbols, property keys, and file paths are the
  same in both versions.
- Neither translation omits maintenance procedures or safety constraints.
- README contains navigation only; it does not duplicate the full guide.

## Validation

Before committing the public guides:

- Verify every referenced source path exists.
- Verify build and test commands match the current scripts.
- Verify the documented diagnostic log fields match `module.cpp`.
- Compare the two tables of contents for one-to-one coverage.
- Scan for unfinished sections or placeholder markers.
- Run `git diff --check`.

## Acceptance Criteria

- Developers can identify why three interception layers are used.
- Developers can trace the specialization lifecycle from process selection to
  final Build-field writes.
- Developers can safely change the package name, identity values, property
  aliases, Build fields, or ABI list using the documented recipes.
- Fail-open behavior and the limits of PLT coverage are explicit.
- README links to both complete language versions.
- No production code or package metadata changes as part of this task.
