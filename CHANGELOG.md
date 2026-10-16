# Changelog

All notable changes to WuWaConfig are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.1.7] — Unreleased

### Fixed
- **Battle stats** — count every retained log; stop the merge skipping backups.
- **Profile** — stop rendering a clean session as a failed read.
- **Profile** — stage the SQLite rollback journal when pulling game databases.
- **Gacha** — stop blanking every portrait Kuro's guide has art for (the bundled-art fallback was being nulled by the remote-URL preference, but Coil has no HTTP fetcher in this build).
- **Site** — percent-encode the JetBrains Mono filenames in the precache.

### Added
- **Theme engine** — split into two orthogonal axes: `UiStyle` (structure: radii, strokes, blur) and `ColourPalette` (pigment: M3 slot colours). 6 styles × 7 palettes, all dark/light safe.
- **UI Redesign** — Modernized full app UI with premium dark theme: cyan/teal primary + warm amber secondary accents, refined spacing (16dp/24dp), softer corners, shared component library (SectionHeader, StatusChip, EmptyState, ErrorState, LoadingState), improved typography, reduced-motion support, and comprehensive previews.
- **Characters** — native Kuro login and a WWT-style character view.
- **Calculator** — runtime auto-sync of material data from the wiki.
- **Gacha** — Kuro guide featured-character banner strip; standard pool collection card.

---

## [1.1.6] — 2026-10-02

### Added
- **AppFunctions** — 9 read-only functions exposed to system agents (`listPresets`, `generateConfig`, `getDeviceProfile`, `recommendPreset`, `analyzeGameLog`, `getCachedLogAnalysis`, `getCachedBattleStats`, `getDeployHistory`, `getDeployOutcome`).
- **ADB** — wireless pairing; trust an unauthenticated loopback adbd; store ADB key material under a platform `AndroidKeyStore` key.
- **ADB** — `CryptoAdapter` interface + injectable `socketFactory` for testability.
- **Config** — load game knowledge from an external `game_profile.properties` asset (paths, log names, CVar lists, gacha economy) with compiled-in defaults for every field.
- **Config** — externalize the 8-preset tuning table, 67 GPU/chipset classifier patterns, 75-path `[Core.System]` inventory, tier budgets, grass-cull table, Kuro level names, and chipset vendor markers into `assets/config/tuning.json`.
- **Config** — detect the engine generation (UE4/UE5) instead of assuming UE4.
- **Config** — mark CVars the target platform can never use.
- **Config** — auto-check + "Force C# Environment" toggle for WuWa 3.6.
- **Site** — full pages, a real service worker, and PWA icons.

### Changed
- **Navigation** — migrate from string-based routes to type-safe `NavKey` route keys, and from `NavHost`/`NavController` to `NavDisplay`/`NavKey` (Navigation 3).
- **Deps** — migrate Coil 2.7.0 → 3.3.0, dropping the bundled HTTP image loader (no remote fetcher in this build by design).
- **Deps** — migrate to AGP 9.4.0 (Gradle 9.6.0, Kotlin 2.2.10); ktlint 12.1.0 → 14.2.0; core-ktx 1.18.0 → 1.19.0.
- **R8** — narrow three over-broad keep rules, verified in the shipped APK.
- **Config** — replace the hardcoded XOR decryption loop with a dynamic strategy fallback.
- **Config** — unify byte-size formatting on one 1024-based formatter.

### Fixed
- **Log parser** — capture effective CVar values from console-variable lines.
- **CVar DB** — refresh the database for game 3.7.0; stop the gate stripping live CVars.
- **Log** — read every rotated `Client.log` and validate Kuro's real log format.
- **Profile** — read the real `SdkLevelData` shape and the real DB table name; read the rotated log and the wording this game actually logs.
- **Deploy** — only report deploy success after the pipeline fully finishes.
- **Shizuku** — stop the UserService process from killing itself on bind; address the UserService as `context.packageName` instead of a literal; stop retrying a disconnected service.
- **Security** — correctness, memory, and privacy hardening pass (installer launch guard, locked `exported` flags, sanitizer hardening).
- **UI** — close edge-to-edge inset and IME gaps; surface cached-analysis age so stale analysis no longer drives generation; report real profile-read progress and cancellation; distinguish unavailable from zero on the profile screen; show the opacity value being dragged; reactive neon saturation; off-main diff/generate; editor & dialog scroll; state + dedup.
- **Gacha** — correct retry errors, drop dead receiver, count-aware totals, soft-pity curve; make legacy gacha cache backward compatible; report unknown gacha-data age as null.
- **Settings** — stop the "Check for updates" button from starving the version label.
- **Pity** — rewritten README section (4 panels, 11 pool types, soft-pity bar, named predictions).

### Removed
- **Refactor** — remove Auto-Tune Wizard; delete dead code the wiring audit turned up.

### Tests
- `SmartBrainTest` expanded to 71 tests (full signal/preset/warning coverage).
- New `CvarDatabaseTest` + `SmartBrainTest` P0 coverage for previously untested areas.
- 38 tests for `PityPrediction` math, `GachaHistoryStore` TTL, and count collapsing.
- `AdbProtocol` + `HashMonitor` coverage.
- Extract the chipset classifier and backup listing order so they are testable.
