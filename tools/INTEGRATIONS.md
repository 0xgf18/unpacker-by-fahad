# DPT-UNPACKER External Tool Integrations

Integration strategy: **vendor as external tools**. Discovery is graceful -- missing
interpreter/compiler degrades to a hint instead of aborting the run (same contract as the
existing apktool launcher in `lsp/ExternalTool.kt`).

## Detection boosters

| Tool | Role | Vendor location | Status |
|------|------|-----------------|--------|
| rednaga/APKiD | YARA-based packer/obfuscator/compiler identification (PEiD for Android) | `tools/apkid` (cloned) | Runner `detection/ApkIdRunner.kt` wired into `--analyze`; needs `python` + `yara-python` (build needs MSVC on Windows, fine on Termux/linux) |
| RePairip (ispointer/RePairip) | PairipProtect deprotection: merges APKS bundles, instruments for runtime dictionary capture, then statically restores translated dex methods | `tools/RePairip.jar` (vendored) | `--mode pairip`; runner `tools/RePairipTool.kt`; full flow needs a rooted device for pairip.json capture, offline `-t pairip.json` translation is desktop-only |
| ashishb/android-security-awesome | Fingerprint-table feed for `detection/Profiler.kt` (packer/cruncher markers) | reference only | read |

## Unpack / dump / restore backends

| Tool | Vendor path | Used by strategy | Notes |
|------|-------------|------------------|-------|
| KeyDive (AbhiTheModder) | `tools/keydive` | `frida` | frida-based in-memory dex/asset dumping; pairs with frida-dexdump |
| Auto-Il2cppDumper | `tools/auto_il2cpp` | `il2cpp` | Unity global-metadata + libil2cpp.so reconstruction |
| Il2CppInspector | `tools/il2cpp_inspector` | `il2cpp` | playback of il2cppdumper sessions |
| pyxamstore | `tools/pyxamstore` | `xamarin` | Xamarin assemblies/manifest extraction |
| hermes_rs | `tools/hermes_rs` | `hermes` | Hermes bytecode to JS |
| blutter-termux | `tools/blutter` | `flutter` | Dart AOT snapshot -> source level mapping |
| dabzcy/dex2c, dex2c/d2c | `tools/dex2c` | `native` | JNI/`jbytea` native method reconstruction from code-gen output |
| APKEditor | `tools/apkeditor` | rebuild/split | universal dex/arsc editor + repack; java -jar |
| androguard | `tools/androguard` | analysis | Python graph-based APK analysis |

## Deobfuscation / junk removal

| Tool | Vendor path | Role |
|------|-------------|------|
| Ciphey | `tools/ciphey` | auto-decrypt protected strings, configs |
| Mariana-Trench | `tools/mariana_trench` | inter-procedural taint -> locate real code paths for weight reduction |
| dexmod/dexterity | `tools/dexmod` | dex mutation / instruction-level rewrite for junk-strip |
| dexpatcher-tool | `tools/dexpatcher` | patch-and-strip round trips |
| banjo | `tools/banjo` | smali assembly/disassembly for post-unpack cleanups |
| smalisp / understand-smali | `tools/smalisp` | smali scripting + re-obfuscation wake |

## Signing / metadata repair

| Tool | Vendor path | Role |
|------|-------------|------|
| ManifestEditor | `tools/manifest_editor` | manifest attribute repair (application disabled, etc.) |
| ARSCLib | `tools/arsc_lib` | resources.arsc parse/retarget for missing meta |
| ApkSignatureKiller (variants) | `tools/apk_signature_killer` | signature verification strip before resign |
| sigtool | `tools/sigtool` | certificate/key extraction + resign |
| sigmatcher | `tools/sigmatcher` | match SIG from v2/v3 blocks |

## Runtime / device support (Android RE harness)

| Tool | Vendor path | Role |
|------|-------------|------|
| adb-enhanced (ashishb) | `tools/adb_enhanced` | unified adb actions (REPL-ish) for payload dumps |
| drozer (ashishb) | `tools/drozer` | runtime app probing / intent fuzzing on device |
| android-ssl-bypass (ashishb) | `tools/android_ssl_bypass` | SSL-pinning bypass for dump agents grabbing HTTPS-hijacked dex |
| android-malware (ashishb) | corpus only | packed/obfuscated sample test-set for Profiler + unpackers |
| frida-trick | `tools/frida_trick` | hook recipes for runtime dump |
| IDAFrida | `tools/ida_frida` | frida-java server bridge for dynamic flows |
| jni_helper | `tools/jni_helper` | JNI export hooks for encrypted loaders |
| radare2 / r2droid | `tools/r2droid` | native disassembly for dex2c/etc. |

## Vendoring steps (per tool)

1. `git clone --depth 1 <url> "$DPT_UNTRIALS/tools/<dir>"` (DPT_UNTRIALS = DPT-UNPACKER root).
2. Add a `detection/ApkIdRunner.kt`-style launcher OR a `ExternalTool`-style jar ref
   (locate via `$DPT_<NAME>_CMD`, PATH, then `tools/<dir>` next to distribution).
3. Register the strategy name in `Profiler.dispenseStrategies()` and match it in
   `strategyLabel()` / `profileDispatch()`.
4. Keep builds graceful: `scan()`/`find()` return null when the backend is missing.