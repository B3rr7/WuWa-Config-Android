# Code Audit — Findings To Fix

Branch `ui-redesign` @ `06e1a2a`. Audit date: 2026-10-29.
Scope: whole app module (`app/src/main`), build config, tests, lint.

**Baseline at time of audit** (run under `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`):

| Check | Result |
|---|---|
| `testDebugUnitTest` | 1042 tests, 0 failures, 0 errors, 0 skipped |
| `ktlintCheck` | PASS |
| `lint` | PASS — 0 errors/fatals, 71 warnings |
| `assembleDebug` | PASS — `WuWaConfig-debug.apk`, 33.6 MB |

Everything below is a real, reproducible observation on that baseline; none of it was a
build failure. Order is by risk, not by effort.

**Status 2026-10-29:** F-1, F-3, F-4, F-5, F-8, F-10 fixed; F-6 and F-13 fixed in part
(see each section for what remains). F-2, F-7, F-9, F-11, F-12 are open.

| ID | Severity | Status | Area | One line |
|---|---|---|---|---|
| F-1 | P1 | **fixed** | `ui/components/GachaAvatar.kt` | Coil network art shipped; KDocs + `http://` acceptance contradicted intent |
| F-2 | P1 | **open** | tests | 32 files have no test reference, incl. all backends and every ViewModel |
| F-3 | P2 | **fixed** | `ui/theme` + `Components.kt` | Four enum values were unreachable — dead theme branches |
| F-4 | P2 | **fixed** | `adb/AdbCrypto.kt` | Raw NUL byte in source broke grep/git-diff tooling |
| F-5 | P2 | **fixed** | `ui/screens/BattleStatsScreen.kt` | Locale-dependent number formatting |
| F-6 | P2 | **partial** | `ui/components/RemoteImage.kt` | Second HTTP image stack: hardened, still not consolidated onto Coil |
| F-7 | P2 | **open** | `ui/screens/IniEditorScreen.kt`, `ReviewTuneScreen.kt` | Palette bypass via hardcoded colours |
| F-8 | P2 | **fixed** | `AGENTS.md` | Counts, theme list, `service/` and the Coil claim were stale |
| F-9 | P2 | **open** | manifest | `MANAGE_EXTERNAL_STORAGE` is a Play policy decision with no written rationale |
| F-10 | P3 | **fixed** | `ui/DeployHistoryViewModel.kt` | Unstructured coroutine scope in `onCleared()` |
| F-11 | P3 | **open** | repo-wide | 129 raw `RoundedCornerShape(n.dp)` vs 20 token readers |
| F-12 | P3 | **open** | repo-wide | 5 files > 1,200 lines; two functions > 180 lines |
| F-13 | P3 | **partial** | repo-wide | 80 lint warnings → 71; mechanical remainder listed below |

---

## F-1 — Coil network art: behaviour is right, the stated invariant is wrong

**Evidence**

- `app/build.gradle.kts:282` — `implementation(libs.coil.network.okhttp)`
- `ui/components/GachaAvatar.kt:160` — `isCoilLoadable` returns true for `https://` **and `http://`**
- `ui/components/GachaAvatar.kt:120-158` — KDoc still says *"`coil-network` is not a
  dependency"* and *"if `coil-network` is ever added, [isCoilLoadable] is the only thing
  that has to change"*
- `app/src/main/res/xml/network_security_config.xml` — no cleartext exemption, so an
  `http://` model can only ever fail to load

**Why it matters.** The app's only privacy property that is enforced by code is "Coil is
never handed a remote URL". Commit `1eb78a0` intentionally reversed that for Kuro guide
avatars, which is fine — but the comment that a reviewer reads to verify the property now
asserts the opposite of the binary. `http://` in the predicate adds a path that cannot
work and that the network config forbids.

**Fix (done).** `http://` removed from `isCoilLoadable`; both KDocs rewritten to state that
`coil-network` is present and that `LocalOnlyImage` guards *user input* rather than all
network art.

**Verify.** `GachaAvatarPortraitTest` still passes. A dedicated `http://`-returns-false
case is not added yet — that is part of the open F-2 test work.

---

## F-2 — The untested surface is the destructive one

**Evidence.** 32 main files are never referenced by any test:

```
MainActivity.kt                    ui/MainViewModel.kt            ui/SettingsViewModel.kt
adb/AdbKeyVault.kt                 ui/LogInsightsViewModel.kt     ui/ThemeViewModel.kt
backend/AdbBackend.kt              ui/IniEditorViewModel.kt       ui/ProfileViewModel.kt
backend/RootBackend.kt             ui/GachaViewModel.kt           ui/BackupViewModel.kt
backend/SafBackend.kt              ui/CharacterViewModel.kt       ui/CalculatorViewModel.kt
backend/ShizukuBackend.kt          ui/DeployHistoryViewModel.kt   ui/DeviceOps.kt
service/AdbConnectionService.kt    ui/anim/AnimTheme.kt           ui/anim/GeoLoading.kt
service/ShellUserService.kt        ui/anim/LoadAndReadAnimation.kt
model/ConfigHashInfo.kt            model/LogEntry.kt
util/RomBackgroundSettings.kt      ui/components/Components.kt (BackgroundSettings)
ui/theme/{Color,Motion,PaletteColors}.kt
ui/screens/{HomeScreen,PityScreen,GachaStatsSection,ReviewTuneScreen}.kt
```

The pattern is systematic: **the four backends, both services, the ADB key vault, and all
11 ViewModels have no direct test.** Yet these are exactly the paths that write to the
game's install directory and to `AndroidKeyStore`.

**Why it matters.** `ShellUtils`/`PushFilePlan` proves a mocked-shell backend test is
practical (`backend/PushFilePlanTest`, `backend/ShellUtilsTest` pass today), so
`AdbBackend`/`ShizukuBackend` retry, staging and run-as fallbacks are testable and are not
tested. A regression there destroys a user's config rather than throwing.

**Fix (incremental, no device needed).**

1. `AdbKeyVault`: round-trip `encrypt`/`decrypt`, `isVaultFormat` header rejection,
   truncation handling. Pure JVM once the Keystore call is behind an interface.
2. `AdbBackend.pushFile`: fake `AdbClient` returning scripted failures — assert the
   staging path is used, that `mv` happens only after the MD5 matches, and that a failed
   decode never touches the target.
3. `DeployHistoryViewModel` / `ConfigManager` deploy-verify coordination: fake backend,
   assert `Result.failure` propagates and the busy flag clears.
4. `UpdateManager` cert check is partly covered; extend to the debug/release suffix case.

---

## F-3 — Four theme enum values cannot be reached

**Evidence**

- `ui/theme/ThemeEnums.kt:41-215` — the 7 `UiStyle` values set only
  `TONAL`, `GLASS`, `OUTLINED`, `FLAT` and `NONE`, `HAIRLINE`, `SPECULAR`.
- `ui/components/Components.kt:354-380` — `surfaceFillFor` implements `RELIEF` and `SUNKEN`.
- `ui/components/Components.kt:394-450` — `surfaceEdge` implements `HARD` and `BEVEL`.
  `HARD` differs from `HAIRLINE` only by ignoring `glowWidth`.
- `EdgeMode.BEVEL` builds
  `Brush.linearGradient(..., end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))`
  — infinite gradient geometry, unreachable today, a latent NaN source if re-enabled.

**Why it matters.** `ThemeTokens.kt:1-80` documents RELIEF/SUNKEN/BEVEL as first-class
design modes, and `PaletteColors.kt:647,714` tunes palettes for them. A future session
will reasonably assume these are live. Commit `cc1f327` removed the styles that used them
and left the machinery.

**Fix (done, option a).** Deleted `SurfaceMode.RELIEF`/`SUNKEN` and
`EdgeMode.BEVEL`/`HARD`, plus their branches in `surfaceFillFor`/`surfaceEdge`. The two
`PaletteColors` comments that justified themselves by those modes were reworded. Both
`when` expressions are now exhaustive over the surviving values.

**Verify (done).** `ThemeEngineTest.every surface and edge mode is reachable from a style`
fails if a mode is added without a style that selects it. Suite: 1042 passing.

---

## F-4 — Literal NUL byte in `AdbCrypto.kt`

**Evidence.** `adb/AdbCrypto.kt:316`:

```kotlin
return "$b64 wuwaconfig@android<0x00>".toByteArray()   // <0x00> is a raw NUL in the file
```

`file(1)` reports `data`, not text. `grep -r` prints **"binary file matches"** and no
lines; `git diff` will refuse to show the file.

**Why it matters.** The bytes are correct at runtime (ADB requires the terminating NUL),
so no test fails. The cost is that the one security-sensitive file in `adb/` is invisible
to code review, search and diff tooling.

**Fix (done).** Written as `"\u0000"` — identical runtime bytes, normal text file.

**Verify (done).** `file` now reports `Unicode text, UTF-8 text` and `grep -n` prints line
316. Nothing asserts the terminator yet — `AdbClientTest` stubs `getAdbFormattedPublicKey`
(line 105) — so a device reconnect is still the only end-to-end check; that assertion
belongs to the open F-2 work.

---

## F-5 — Locale-dependent formatting in battle stats

**Evidence.** `ui/screens/BattleStatsScreen.kt:327,328,332,333` — `String.format(...)`
with no `Locale` (lint `DefaultLocale` ×4).

**Why it matters.** On a comma-decimal locale the performance figures render
inconsistently with the rest of the screen and can produce `1,234` meaning either 1234 or
1.234, a real ambiguity in a number the user is reading to judge a build.

**Fix (done).** All four calls now `String.format(Locale.US, ...)`, matching the
`SimpleDateFormat(..., Locale.US)` usage already in `HashMonitor`. Lint `DefaultLocale`
is clear.

**Verify.** Compiles and the existing `StatRatioTest`/`BattleStatsTest` maths still pass;
a formatting assertion is still worth adding under F-2.

---

## F-6 — A second, weaker image-loading stack

**Evidence.** `ui/components/RemoteImage.kt:27-46`:
raw `HttpURLConnection`, no `responseCode` check before `BitmapFactory.decodeStream`,
no `disconnect()`, no disk cache, and one process-wide `LruCache` sized
`maxMemory / 6`. Eight call sites in `ui/screens/MyCharacterScreen.kt` (551, 719, 798,
877, 914, 977, 1030, 1170).

**Why it matters.** Coil (with `coil-network-okhttp`) is already in the binary and is what
`GachaAvatar`/`CalculatorScreen` use. Character-screen icons therefore (a) re-download on
every cold start, (b) may decode an HTML/error body into a bitmap, (c) reserve ~1/6 of the
heap for a cache Coil could manage better.

**Fix (partial).** `fetchBitmap` now checks `responseCode == HTTP_OK` before decoding and
`disconnect()`s in a `finally`. Still open: consolidating the eight `MyCharacterScreen`
call sites onto Coil so icons get its disk cache instead of re-downloading per cold start.

**Verify.** Compiles; behaviour change is confined to failed/error responses. The
consolidation needs an on-device pass on `MyCharacterScreen`.

---

## F-7 — Two screens cannot be themed

**Evidence.** `Color(0x...)` literals outside `ui/theme/`: `IniEditorScreen.kt` **17**,
`ReviewTuneScreen.kt` **8**, `GachaAvatar.kt` **8**, `Components.kt` **3**. Total theme
palette literals for comparison: 777 in `PaletteColors.kt`.

**Why it matters.** Rule 5 of the theme engine ("the palette supplies the accent layer")
holds everywhere except the editor. Under `ACID_BRUT`/`PAPER_INK` those two screens keep
the old neon accents, which is exactly the failure the theme rules were written to avoid.

**Fix.** Route syntax highlighting and diff colours through `PaletteColors.accents` (or a
small `EditorColors` token holder) so a palette change reaches them.

**Verify.** `ThemeEngineTest` / `SaturationParityTest` pass; add a source-level guard test
that forbids `Color(0x` outside `ui/theme` (the suite already has precedent for
source-scanning assertions).

---

## F-8 — `AGENTS.md` describes a different tree

**Evidence** (re-derived, not copied):

| Claim in `AGENTS.md` | Actual |
|---|---|
| 128 main files / 42,881 lines | **131 / 45,248** |
| 68 `*Test.kt` / 1,033 `@Test` | **70 / 1,041** |
| 12 `UiStyle` incl. NEUMORPHIC_SOFT, SKEUOMORPHIC, NEO_BRUTALISM, RETRO_FUTURISM | **7** (6 of the 12 named still exist; `AURORA_FLUID` removed and `GLASSMORPHISM` added) |
| `ColorPalette` 13 | 13 ✔ |
| "`coil-network` is deliberately absent" | **present**, `build.gradle.kts:282` |
| Architecture map lists no `service/` | `service/AdbConnectionService.kt`, `ShellUserService.kt` |
| Keys "encrypted at rest (`EncryptedFile` + `AndroidKeyStore`)" | now `AdbKeyVault` (AES-GCM); `EncryptedFile` is migration-read only |

**Why it matters.** The file's own warning — *"do not trust the counts without
re-deriving them"* — applies to its prose too. A future session following the theme
section will look for four styles that no longer exist.

**Fix (done).** Counts, `UiStyle` inventory (7, with their surface/edge pairs), the Coil
trap, `AdbKeyVault` encryption, a new `service/` entry, and the 26-combination device note
are all updated. The historical "went stale twice" paragraph is kept as history.

---

## F-9 — `MANAGE_EXTERNAL_STORAGE` with no written decision

**Evidence.** `AndroidManifest.xml:26` (`MANAGE_EXTERNAL_STORAGE`), `:27`
(`REQUEST_INSTALL_PACKAGES`); lint `ScopedStorage` warning. `LogRepository.init()` already
moved logs to app-scoped storage *specifically* to stop depending on this grant.

**Why it matters.** Play restricts All-Files-Access to file managers/backup apps; the app
ships outside Play today, but the permission now buys less than it used to (logs are
app-scoped, deploy goes through ADB/Shizuku/SAF/Root).

**Fix.** Either document the remaining justification (backups in `Downloads/WuWaConfig/`,
plus `publicBaseDir()`) in the manifest comment, or gate it behind the features that still
need it.

---

## F-10 — Unstructured teardown coroutine

**Evidence.** `ui/DeployHistoryViewModel.kt:288-296` — `onCleared()` launches
`CoroutineScope(Dispatchers.IO)` because `viewModelScope` is already cancelled and
`AdbBackend.disconnect()` blocks in `socket.close()`.

**Why it matters.** The comment justifies *why* a scope is needed, not *which* scope. A
rapid Activity recreate can start a new connect while the previous teardown is still
running, with no ordering between them.

**Fix (done).** `WuWaConfigApp.backendTeardownScope` is an app-scoped `SupervisorJob` on
`Dispatchers.IO`, and the teardown takes `DeviceOps.mutex` — so it waits for an in-flight
device operation and a later `connect()` waits for it.

---

## F-11 — Shape tokens only half-adopted

**Evidence.** 129 `RoundedCornerShape(<number>.dp)` call sites in `ui/` vs 20 readers of
`LocalThemeShapeTokens`. `GlassCard` itself is token-correct
(`Components.kt:1172+`), so the headline surfaces are fine; the drift is in chips, badges,
dots and dialogs.

**Why it matters.** The `UiStyle` contract is "structure changes everywhere". A style with
a 24dp card and 10dp chips is the intended layering; one where the card moves and the chips
don't is a half-applied theme.

**Fix.** Sweep the non-decorative call sites (dialogs, banners, list rows) onto tokens;
leave 4dp status dots alone.

---

## F-12 — Largest units

**Evidence** (function span heuristic):

| Unit | Lines |
|---|---:|
| `ui/components/Components.kt` | 1,928 |
| `config/ConfigGenerator.kt` | 1,569 |
| `config/LogParser.kt` | 1,327 |
| `ui/screens/MyCharacterScreen.kt` | 1,266 |
| `ui/screens/HomeScreen.kt` | 1,243 |
| `BuildAndroidScalabilityIni` | 182 |
| `TermsScreen` | 219 |
| `BrainSim.update` | 222 |

`ConfigGenerator`'s per-section builders are already the reward of a previous split and
are individually small and single-purpose — do not re-flatten them.

**Fix.** Only `TermsScreen` (219 lines of what is largely prose) and
`BuildAndroidScalabilityIni` are worth touching for readability. `Components.kt` should
keep splitting by concern (`Components.kt` already contains `GlassCard`, `GlitchText`,
`LogViewer`, `GlassDialog`, `background` machinery).

---

## F-13 — Mechanical lint backlog (80 items, 0 errors)

| Rule | Count | Note |
|---|---:|---|
| `UseKtx` | 25 | `androidx.core` extensions |
| `AutoboxingStateCreation` | 16 | `mutableStateOf(0)` → `mutableIntStateOf` in Calculator/ConfigGen/GeoLoading |
| `GradleDependency` | 10 | newer versions available; verify with `verification-metadata.xml` before bumping |
| `ObsoleteSdkInt` | 6 | dead branches (`AdbConnectionService:114`, `MainActivity:297`, `SettingsViewModel:221`, `UpdateManager:417`, `RomBackgroundSettings:104`) |
| `UseOfNonLambdaOffsetOverload` | 5 | drag/scroll handlers |
| `ModifierParameter` | 3 | `Components.kt:1778,1840`, `GeoLoading.kt:293` |
| `InlinedApi` | 2 | `util/SensitiveClipboard.kt:26` — add `@RequiresApi(33)` |
| `FrequentlyChangingValue` | 2 | `MyCharacterScreen.kt:459,488` read inside composition |
| `MonochromeLauncherIcon` | 2 | themed-icon support |
| `UnusedResources` | 1 | `R.color.ic_launcher_background` |

`InlinedApi` and `FrequentlyChangingValue` are the only two with behavioural consequences.

**Fixed in this pass:** all 16 `AutoboxingStateCreation` (`mutableIntStateOf` /
`mutableFloatStateOf` across Calculator, ConfigGen, Components, GachaStatsSection,
GeoLoading, ReviewTune) and both `InlinedApi` items in `util/SensitiveClipboard.kt`.

**Correction to the original advice:** `@RequiresApi(33)` would have been *wrong* for
`SensitiveClipboard`. `ClipDescription.EXTRA_IS_SENSITIVE` is a compile-time `String` key,
so `extras.putBoolean(...)` works on every API level and is simply unread below 33;
suppressing `InlinedApi` keeps the flag on 26-32, where clipboard history is most likely
on. `@RequiresApi(33)` would have removed it there.

**Still open:** 25 `UseKtx`, 14 `GradleDependency` + 7 `NewerVersionAvailable`, 6
`ObsoleteSdkInt`, 5 `UseOfNonLambdaOffsetOverload`, 3 `ModifierParameter`, 2
`FrequentlyChangingValue` (`MyCharacterScreen.kt`), 2 `MonochromeLauncherIcon`, 1
`ScopedStorage`, plus `OldTargetApi`/`DataExtractionRules`/`IconLocation`/`UnusedResources`.
Total lint is now **71 warnings, 0 errors**.

---

## Verified non-issues (do not re-audit)

- **Account data left in `cacheDir`.** `ProfileExtractor.pullDb` copies
  `LocalStorage.db`/`DeviceStorage.db` into cache, but `readProfile` deletes both at
  `ProfileExtractor.kt:541-542`; the journal is deleted *before* the fresh DB is written,
  which is the correct order.
- **Signing material.** `keystore.properties` and `release.jks` are gitignored and have
  never been committed (`git log --all -- <paths>` is empty). `assembleRelease` still
  fails closed without all four keys.
- **Hash sync atomicity.** `HashMonitor.refreshConfigHashes` aborts instead of persisting
  an empty hash, stages through a unique `.new`, `mv`s, then verifies by read-back.
- **Cancellation.** Every `catch (Exception)` on a suspend path rethrows
  `CancellationException` (checked in `ConfigManager`, `ProfileExtractor`, `HashMonitor`,
  `ShizukuBackend`, `CvarDatabase`).
- **Shizuku bootstrap.** `isUserServiceProcess()` guard runs before any app init;
  the process-name suffix lives in exactly one constant.
- **Nav saved state.** `DestinationsTest` pins every route's serial name, so renames
  cannot silently break a restored back stack.
- **UI state collection.** 109 `collectAsStateWithLifecycle`, 0 bare `collectAsState`.
- **Backdrop coverage.** 17 of the 18 files in `ui/screens/` wrap `GradientBackground`;
  the exception is `GachaStatsSection.kt`, which is a section hosted inside `PityScreen`,
  not a destination — theme rule 3 holds.

---

## Remaining order of work

1. **F-2** — start with `AdbKeyVault` + `AdbBackend.pushFile`; highest blast radius. This
   is now the only P1 left.
2. **F-7** — route editor/diff colours through the palette; 25 literals, two files.
3. **F-6** — finish the consolidation onto Coil (needs an on-device `MyCharacterScreen`
   check).
4. **F-9** — document or gate `MANAGE_EXTERNAL_STORAGE`; a decision, not a refactor.
5. **F-11, F-12, F-13** — hygiene, only alongside other UI work.

Done in this pass: F-1, F-3, F-4, F-5, F-8, F-10, and the behavioural half of F-13
(`AutoboxingStateCreation`, `InlinedApi`).

Re-run the full gate after any of it:
`ktlintCheck` → `lint` → `testDebugUnitTest` → `assembleDebug`, under
`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (JDK 25 breaks 146 Mockito tests for
environmental reasons).
