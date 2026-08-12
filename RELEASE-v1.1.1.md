## v1.1.1 — HashMonitor Default-On & ModifyCount Fix

### Key Changes
- **HashMonitor now enabled by default (opt-out)** — the "Enable HashMonitor" setting ships ON for all users (was OFF in v1.1.0). The feature still self-guards via `HashMonitor`: it only performs MD5/config-hash work when enabled *and* a backend (ADB/Shizuku/Root/SAF) is live, so idle installs incur no overhead.
- **ModifyCount fix** — `HashMonitor.ModifyCount` is now incremented by 1 (capped at 8) **only on deploys**, not on sync or game-touched writes.
- **Version bump** — `versionName` 1.1.0 → 1.1.1, `versionCode` 11 → 12.

### Downloads
- Debug APK: `app/build/outputs/apk/debug/WuWaConfig-debug.apk`
- Release APK: `app/build/outputs/apk/release/WuWaConfig-v1.1.1-release.apk`

---

## v1.1.0 — Shizuku UserService Migration & Major Refactor

### Overview
Version 1.1.0 is a significant maintenance release focused on stability, architecture cleanup, mobile web experience, and anti-detection hardening. The Shizuku backend has been migrated from reflection-based IPC to the modern Binder-based UserService API, 6 screen-scoped ViewModels have been extracted from the monolithic MainViewModel, the Engine.ini generator has been refactored into modular section builders, and file I/O has been hardened to reduce detection surface.

### Key Changes

#### Shizuku Backend (UserService Migration)
- **Replaced reflection-based `Shizuku.newProcess()`** with Binder-based `ShellUserService` (extends `android.app.Service`)
- Uses Shizuku's `UserService` API (requires Shizuku v10+) for stable IPC
- Eliminates fragile reflection on private methods that could break with Shizuku library updates
- 60s timeout, 3x retry, script-file fallback for commands >4096 chars

#### Screen-Scoped ViewModels
Extracted 6 ViewModels from the 1477-line `MainViewModel`:
- `IniEditorViewModel` — INI editor state, syncConfigHashes, pushSingleFile
- `ConfigGenViewModel` — analysis, presets, generate, deploy, auto-tune
- `SettingsViewModel` — theme, backgrounds, backup dir
- `GachaViewModel` — fetch history, predictions, polling
- `ProfileViewModel` — read player profile, cache
- `DeployHistoryViewModel` — list, compare, delete

#### Engine.ini Refactor
- `buildAndroidEngineIni` split into `EngineIniContext` + 25 per-section builders
- Improves maintainability and testability of INI generation logic

#### GachaApi Improvements
- Dynamic standard pool derivation from type "1" records (no hardcoded sets)
- Eliminates stale standard pool data when new banners are added

#### LogParser Improvements
- `decodeLogBytes` return type changed from `Boolean` to `DecodeResult` enum (`DECRYPTED`/`PLAINTEXT`)
- More expressive return type for log decoding logic

#### Anti-Detection Hardening (File I/O)
- **Replaced chunked shell-based file reading** with single `backend.copyFile()` calls
  - Removed complex `dd | base64 | gzip` shell pipelines in `readRemoteLogTextChunked`, `readBattleStats`, and `readFullFileText`
  - Single `cp` command + local file read instead of multiple shell invocations
  - Eliminates predictable shell command patterns (multiple `dd`/`base64` commands per file read)
- **Added `copyFile()` method** to all 4 backends (`AdbBackend`, `ShizukuBackend`, `RootBackend`, `SafBackend`)
  - Reduces IPC/binder transactions (fewer separate command responses)
  - Single bulk file operation instead of chunked transfers
- **Removed `execCommandToFile`** from `ShellUserService` and `ShizukuBackend`
  - Simplified Binder transaction surface (removed `TRANSACTION_EXEC_COMMAND_TO_FILE`)
  - Reduces unique transaction codes that anti-cheat could flag
- **Added jittered delays** (50-150ms) between file pushes
  - `applyCustomConfigs()` and `applyFiles()` in `ConfigManager`
  - Breaks burst-pattern detection from rapid sequential file writes
- **Simplified ShizukuBackend IPC interface**
  - Removed `execCommandToFile` from `IShellService` interface and `ShellServiceProxy`
  - Fewer Binder transaction codes reduces detection surface

#### Bug Fixes
- **OffsetMapping crash in IniEditorScreen** — custom clamped `OffsetMapping` for Compose BOM 2024.10.00 validation
- **ViewModelProvider ClassCastException** in `reloadDeviceFileForReview` — replaced with direct `ConfigManager` instance
- **syncConfigHashes uncaught exception** in `IniEditorViewModel` — added try-catch wrapper
- **ShellUserService manifest compatibility** — extends `Service` instead of `Binder`

#### Mobile Web Optimizations
- Disabled cursor glow, bit trails, matrix chars, and ripple effects on touch devices
- Full-width buttons on small screens
- Reduced padding and font sizes for mobile
- Removed app icon from landing page hero

#### Tech Stack Updates
- AGP 8.2.2 → 8.4.2
- Kotlin 1.9.22 → 1.9.24
- Compose BOM 2024.02 → 2024.10.00
- Added Gradle version catalog (`libs.versions.toml`)

### Files Changed
- 45+ files changed (including commit 0666594 — anti-detection hardening)
- 6 new ViewModel files
- 1 new Service file (ShellUserService)
- 3 new test files (ConfigGeneratorTest, GachaApiTest, LogParserTest)
- 1 new Gradle version catalog (libs.versions.toml)
- 1 new lint config (app/lint.xml)

### Testing
- ktlintCheck: PASS
- testDebugUnitTest: PASS (all existing + new tests)
- assembleDebug: PASS (24.5 MB APK)
- assembleRelease: PASS (4.8 MB APK)

### Downloads
- Debug APK: `app/build/outputs/apk/debug/WuWaConfig-debug.apk`
- Release APK: `app/build/outputs/apk/release/WuWaConfig-v1.1.0-release.apk`

### Upgrade Notes
- Shizuku users: ensure Shizuku v10+ is installed for UserService support
- Users enabling/disabling anti-detection features should verify compatibility with their Shizuku version
- No breaking changes to user-facing features
- All existing presets, settings, and configurations are preserved
