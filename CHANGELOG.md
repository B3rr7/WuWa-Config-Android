# Changelog

All notable changes to WuWaConfig are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.1.8] — 2026-11-24

### Added
- **Launcher icon** — the neon 3D squircle replaces the flat mark: per-density adaptive
  foregrounds on the 72dp safe square, a background layer sampled from the art's own rim
  (`#390F86`), and a hand-drawn monochrome layer (card + `>_`) for themed icons. The site/PWA
  rasters are regenerated from the same master, and `tools/gen_icon_assets.py` rebuilds all of
  them.
- **Appearance** — the UI style engine gains surface and edge *modes* (`SurfaceMode`,
  `EdgeMode`) instead of radius-only skins, plus a `Spacing` scale: 3 structural skins × 13
  colour palettes, all dark/light safe. Glassmorphism now draws a frosted interior, a diagonal
  sheen and a specular rim rather than a translucent rectangle.
- **Profile** — a dedicated extraction console (`ProfileDataLoader`) driven by the real read
  progress: extraction dial, four data-source nodes (UID, level, device, config) and a progress
  rail that resolves into success or a broken failure state.
- **SmartBrain** — the log analysis animation is rebuilt as a scan overlay: a responsive
  waveform while `client.log` is pulled and parsed, then the verdict with the extracted
  `KEY=value` signals and the score gauge.
- **Pity** — one shared counter per banner family (Kuro's carry-over rule), a chronological
  50/50 walk, curated Featured/Permanent/Novice banner cards with 5★/4★ pity, the featured
  resonator and signature weapon learned from Kuro's guide, recent ★5 badges and per-pull
  history with a Newest First / Since Last Appearance toggle.
- **Device detection** — report the SoC the game log never names, and resolve a GPU tier from a
  generation-only Adreno token, so a moto g(60) no longer prints `GPU tier: unknown` /
  `SoC: unknown` in the SmartBrain report.
- **CVar database** — 33 CVars recovered from the 3.8.1 device log, including 26
  `r.Kuro.PlanarReflection.*` flags that appear in no build's string table (5,405 active).
  No entries removed: 1,014 names absent from the 3.7.0 binary reappeared in 3.8.1, so
  binary absence is not evidence of removal. See `apk_extract_381/CHANGES_370_to_381.md`.
- **UI** — shared component library (SectionHeader, StatusChip, EmptyState, ErrorState,
  LoadingState), a 16dp/24dp spacing rhythm, softer corners, reduced-motion support, a Stop
  button for the battle-stats read, and previews on every screen.

### Changed
- **Theme** — `LIQUID_GLASS` is now `GLASSMORPHISM`; the old storage key stays aliased so an
  existing install keeps its style instead of resetting to the default.
- **Theme** — the top bar follows the active surface style instead of a fixed opaque fill, and
  under Glassmorphism it re-draws the wallpaper inside itself so rows scrolling underneath
  cannot ghost through the title.
- **Characters** — avatar art loads from Kuro's guide through Coil with a real failure state,
  and the carousel selection follows swipes.
- **Device profiles** — Snapdragon 7-series codes map to `Android_Adreno6xx` (SM7150/7250/7350
  drive Adreno 618/620/642L) instead of blanket `Android_Adreno7xx`; 7+ Gen 2 and 7 Gen 3 keep
  7xx.
- **Data sources** — calculator/material data comes only from this repo; Kuro's guide is used
  only by My Character and the Pity Tracker.
- **Navigation** — faster, deeper screen transitions (200ms enter / 140ms exit, front-loaded
  easing, `scaleIn` from 0.94).
- **Site** — the web root moved to `site/` and is published by the Pages workflow; README
  translations added for Indonesian and Vietnamese.

### Fixed
- **OTA** — self-update downloaded the debug-signed sibling APK because asset selection matched
  the package-archive content type first; it now prefers the exact
  `WuWaConfig-v<version>-release.apk` name and the content-type fallback skips debug assets.
- **Release build** — `assembleRelease` stopped compiling when previews were added to the
  screens: `@Preview` lives in `ui-tooling-preview`, which was only on the debug classpath, so
  every release since 2026-10-16 failed and OTA had no artifact to serve.
- **UI** — the INI draft moved out of `rememberSaveable` (a generated INI blew the Binder
  transaction limit on rotation), ConfigGen's generate path no longer lets a failure kill the
  process, the skill-level slider can reach 1 again, thin gacha slices no longer draw backwards
  over their neighbour, and system back returns from the INI grid to the editor.
- **UI** — invisible button labels, blank avatars and a false terms claim.
- **Site** — repair homepage 404s and FAQ search corruption, re-sync the FPS-lab presets, and
  stop the service worker freezing the screenshots.

### Removed
- **Theme** — neumorphism, plus the Bento Grid, Aurora Fluid, Midnight OLED *style* and
  Minimalist skins. Midnight OLED remains a colour palette.

---

## [1.1.7] — 2026-10-12

### Fixed
- **Battle stats** — count every retained log; stop the merge skipping backups.
- **Profile** — stop rendering a clean session as a failed read.
- **Profile** — stage the SQLite rollback journal when pulling game databases.
- **Gacha** — stop blanking every portrait Kuro's guide has art for (the bundled-art fallback was being nulled by the remote-URL preference, but Coil has no HTTP fetcher in this build).
- **Site** — percent-encode the JetBrains Mono filenames in the precache.

### Added
- **Theme engine** — split into two orthogonal axes: `UiStyle` (structure: radii, strokes, blur) and `ColourPalette` (pigment: M3 slot colours). 6 styles × 7 palettes, all dark/light safe.
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
