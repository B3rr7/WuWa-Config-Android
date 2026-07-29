# WuWa Config Android — Improvement Plan (Full Audit)

Full read-through of `B3rr7/WuWa-Config-Android` — every file under `app/src/main/java`
(~18k lines across `adb/`, `backend/`, `config/`, `model/`, `service/`, `ui/`), the
manifest, network security config, and the Gradle build file. Each item cites the exact
file/line. Priority: **P0** = fix soon (security/correctness risk), **P1** = do next
(maintainability/robustness), **P2** = polish.

Verification status is noted per item: **Confirmed** = code matches the claim,
**Partially accurate** = claim is valid but the details need correction,
**Low risk** = technically correct but the real-world impact is minimal.

---

## Security

### 1. [P0] `RootBackend.kt` builds shell commands without quoting — command injection risk
**Status: Confirmed.** Every other backend quotes paths before interpolating them into a
shell string. `ShizukuBackend` and `AdbClient`/`AdbBackend` all use `shQuote()` (from
`ShellUtils.kt`) consistently. `RootBackend` does not:

```kotlin
// RootBackend.kt:82, 88, 92, 97, 105, 109, 114
executeShellCommand("cp \"$sourcePath\" \"$targetPath\"")
executeShellCommand("mkdir -p \"$dirPath\"")
executeShellCommand("test -f \"$path\" && echo 1 || echo 0")
executeShellCommand("ls -1 \"$path\" 2>/dev/null")
executeShellCommand("cp \"$path\" \"$backupPath\"")
executeShellCommand("cat \"$path\"")
executeShellCommand("base64 -w0 \"$path\"")
```

Plain double-quote interpolation instead of `shQuote()`. A path containing a double
quote, backtick, or `$(...)` breaks out of the quoting and executes as shell — with
root privileges, since this is specifically the ROOT backend. In practice most paths
here come from your own `GamePaths` constants, not raw user input, so real-world
exploitability depends on whether any caller ever routes a user-influenced string (a
custom path, a SAF-picked name) through this backend. The fix is mechanical either way:
swap every interpolated path in `RootBackend.kt` to `shQuote()`, matching
`ShizukuBackend`'s pattern. Small change, outsized payoff given the privilege level.

### 2. [P0] `usesCleartextTraffic="true"` likely defeats your `network_security_config` scoping
**Status: Confirmed.** `AndroidManifest.xml:25` sets `android:usesCleartextTraffic="true"`
at the application level. Your `network_security_config.xml` correctly scopes an explicit
cleartext allowance to `127.0.0.1`/`localhost` (for local ADB-over-TCP):
```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="false">127.0.0.1</domain>
    <domain includeSubdomains="false">localhost</domain>
</domain-config>
```
The intent is clearly "cleartext only for local ADB, HTTPS everywhere else" — but the
manifest's `usesCleartextTraffic="true"` sets the *base-config* default to permitted for
every domain not otherwise listed, overriding that intent for anything outside the two
listed hosts. The domain-config is currently redundant rather than restrictive. Fix:
remove `android:usesCleartextTraffic="true"` from the manifest (or set it `"false"`) —
the localhost exception keeps working via the `domain-config` block either way, and the
gacha API calls (`GachaApi.kt`, already HTTPS-only) get real enforcement instead of an
implicit allow-all.

### 3. [P1] The RSA key encryption depends on an alpha-quality library
**Status: Confirmed.** `app/build.gradle.kts`: `implementation("androidx.security:security-crypto:1.1.0-alpha06")`.
This is the library `AdbCrypto.kt` uses via `EncryptedFile`/`MasterKey` to encrypt your
ADB RSA keypair at rest — the right *approach* (see "what's already solid" below), but
`security-crypto` has been stuck on the `1.1.0-alpha0x` line for a long time, and that
line has a known history of `Tink`-related failures on certain devices/Android versions
(keystore invalidation after OS updates, `InvalidKeyException` on some OEM skins) that
can make previously-encrypted files unreadable. `loadOrGenerateKeys()` already handles
this gracefully — a decrypt failure falls through to generating a fresh keypair rather
than crashing — so the failure mode is "user has to re-authorize ADB," not a crash or
data loss. Still worth tracking as a dependency risk since it secures something
security-sensitive; watch for a stable release, or evaluate calling `Tink` directly if
`security-crypto` stays unstable.

### 4. [P2] Root/Shizuku shell command text goes into `LogRepository`, which writes to public storage
**Status: Partially accurate.** `RootBackend.kt:51` and `ShizukuBackend.kt:57` both log
shell command text (truncated to 120 chars) via `LogRepository.add(...)`, which
persists to `Downloads/WuWaConfig/logs/app.log` — a world-readable public directory on
many Android versions. `AdbClient.kt:210` also logs shell output (truncated to 200 chars),
but uses `Log.d` (Logcat), **not** `LogRepository.add` — so it does not land in public
storage. For a config tool this is mostly fine since the commands are your own generated
`cp`/`cat`/`ls` calls, not secrets — but worth a second look if any future feature ever
pushes something sensitive (a token, personal data) through `RootBackend` or
`ShizukuBackend`, since it'd land in plaintext in public storage.

---

## Dependencies

### 5. [P1] Most dependencies are significantly behind current releases
**Status: Confirmed.**
```
compose-bom            2024.02.00
lifecycle-*             2.7.0
activity-compose        1.8.2
navigation-compose      2.7.7
kotlinx-coroutines      1.8.0
media3-exoplayer/ui     1.3.1
coil-compose            2.6.0
security-crypto         1.1.0-alpha06
compileSdk / targetSdk  34
```
None of these are broken, but taken together this is over a year of accumulated bug
fixes, performance improvements, and (for `compileSdk`/`targetSdk`) Play Store policy
compliance you're not getting. `compileSdk 34`/`targetSdk 34` in particular is worth
checking against current Play Console target-API requirements, since Google enforces a
minimum target SDK for new updates on a rolling basis. Not urgent enough to block other
work, but worth a dedicated "bump everything, fix what breaks" pass every few months
rather than letting it drift further.

### 6. [P2] No Gradle version catalog (`libs.versions.toml`)
**Status: Confirmed.** All dependency versions are hardcoded inline as strings in
`app/build.gradle.kts`. Fine at the current single-module size, but a version catalog
gives you one place to see and bump every version, and is the standard Android Studio
scaffold now — worth adopting next time you're touching dependencies anyway (pairs
naturally with item 5).

---

## Architecture

### 7. [P0] `MainViewModel.kt` is a god object — 1,603 lines, 40 `MutableStateFlow` fields
**Status: Confirmed.** Owns state for every screen simultaneously: theme, deploy history,
backups, ini editor, review/tune, gacha polling, logs, battle stats, profile, and more —
all in one class, constructed by manually casting `Application` to `WuWaConfigApp`:
```kotlin
private val app: WuWaConfigApp =
    application as? WuWaConfigApp
        ?: throw IllegalStateException("MainViewModel requires WuWaConfigApp application")
```
Consequences:
- Any screen reading the ViewModel recomposes on unrelated state changes elsewhere in
  the app (40 StateFlows, one class → wide invalidation surface).
- You can't unit-test "gacha polling logic" in isolation — it's wired into the same
  constructor as ADB/Shizuku/SAF backend setup.
- Rejoining this file after time away is expensive — there's no way to see "what does
  the Ini Editor screen actually own" without reading the whole file top to bottom.

**Fix:** split by feature into separate ViewModels, each scoped to its screen(s):
```
IniEditorViewModel      // IniEditorScreen (peel first — most self-contained)
SettingsViewModel       // SettingsScreen (theme, colorfulUi, textOpacity, prefs)
GachaViewModel          // PityScreen (wraps GachaApi + GachaPollService)
ConfigGenViewModel      // ConfigGenScreen, ReviewTuneScreen
DeployHistoryViewModel  // HistoryScreen, BackupScreen
LogsViewModel           // LogsScreen, BattleStatsScreen
ProfileViewModel        // ProfileScreen
```
Cross-cutting state (backend connection status, current device) can live in a small
`AppSessionViewModel` or be hoisted to `WuWaConfigApp` and read via
`CompositionLocal`. Do this incrementally: `IniEditorScreen` is the most self-contained
consumer (612 lines of UI, narrow state needs) — peel it out first to prove the
pattern, then repeat.

### 8. [P0] No dependency injection — 15 singleton `object`s as ad-hoc service locators
**Status: Confirmed.** (File previously said "14"; actual count of listed objects is 15.)
`DeployHistoryStore`, `GachaHistoryStore`, `ProfileStore`, `ChipsetDetector`,
`SmartBrain`, `BenchmarkTuner`, `CvarCategorizer`, `CvarOptimizer`, `ForbiddenCvars`,
`GachaApi`, `LogParser`, `LogRepository`, `BattleStatsStore`, `GamePaths`,
`LogAnalysisStore` are all Kotlin `object`s. Combined with `WuWaConfigApp` manually
building `ConfigManager`/`ConfigGenerator`/`AdbCrypto` in `onCreate()`, this is why only
2 test files exist for an 18k-line app — you can't instantiate `ConfigManager` or
`ConfigGenerator` in a JVM unit test without either touching Android `Context`/
`SQLiteDatabase` or fighting global singleton state.

**Fix**, minimum viable step (no Hilt needed yet):
- Convert the singletons that hold real mutable state (`DeployHistoryStore`,
  `ProfileStore`, `LogRepository`) into regular classes taking their storage
  dependency (`SharedPreferences`, `File`) in the constructor; instantiate once in
  `WuWaConfigApp`. (`GachaHistoryStore`, `BattleStatsStore`, and `LogAnalysisStore`
  turn out to be stateless file read/write wrappers with no in-memory list — see item 9
  — so they're lower priority to convert.)
- The purely-functional ones (`ChipsetDetector`, `CvarCategorizer`, `ForbiddenCvars`,
  `CvarOptimizer`, `GamePaths`) are fine as `object`s — no state, just namespaced pure
  functions.
- Once constructors take explicit dependencies, add Hilt (or a lighter option like
  Koin) to remove the manual wiring currently in `WuWaConfigApp.onCreate()` and
  `MainViewModel`'s `Application` cast.

### 9. [P1] `DeployHistoryStore` mutates an in-memory list with no synchronization
**Status: Low risk.** `DeployHistoryStore.kt:14`: `private var records: MutableList<DeployRecord>`,
mutated directly by `addRecord`, `deleteRecord`, `clear`, and `updateOutcome` — no
`Mutex`, no `synchronized` block. Contrast with `LogRepository`, which wraps its own
mutations in `synchronized(lock) { ... }` (`LogRepository.kt:22,39,47,64`). `MainViewModel`
calls `DeployHistoryStore` methods from several different `viewModelScope.launch { }` blocks
(deploy flow, log-verification flow, history screen actions). I traced the call sites:
- `MainViewModel.kt:1309` (`addRecord`) — runs inside `viewModelScope.launch` at line 1167,
  which uses `Dispatchers.Main.immediate` (the default), not `Dispatchers.IO`.
- `MainViewModel.kt:381` (`updateOutcome`) — runs inside `viewModelScope.launch` at line 371,
  also `Dispatchers.Main.immediate`.

Since `Dispatchers.Main.immediate` is single-threaded and the `DeployHistoryStore` methods
are synchronous (not suspend functions), there is no actual race condition on the current
codebase — coroutines on the same dispatcher execute sequentially, and synchronous calls
between suspension points are atomic. **However**, this is fragile: if any future caller
adds an `IO`-dispatched coroutine that touches `DeployHistoryStore`, the lack of
synchronization would become a real bug. Wrap the list mutations in a `Mutex` as cheap
insurance — it's a 5-minute change that future-proofs the code.

### 10. [P1] `buildAndroidEngineIni` is a single ~600-line function
**Status: Confirmed.** `ConfigGenerator.kt:456-1053` — roughly 60% of the file's 1,665 lines
live inside one function. Splitting `ConfigGenerator.kt` into smaller files won't help
much on its own if this function stays a single monolithic block; it's the actual unit
that needs decomposing, e.g. into per-section builders (`buildRenderingCvars`,
`buildStreamingCvars`, `buildShadowCvars`, ...) that `buildAndroidEngineIni` then
composes. This also makes item 14 (testing) meaningfully easier — you can test each
section's cvar output independently instead of asserting against the full generated
`.ini` blob every time.

### 11. [P2] Device-tier config (`computeDeviceTier`) is a long repeated if/else chain
**Status: Confirmed.** `ConfigGenerator.kt:374-454` computes 8 different tier-dependent
values (`grassCull`, `streamPool`, `maxAniso`, `landscapeCaptureDist`, `skinCacheMem`,
`ismDist`, `ismRad`, `npcDist`) each via its own `if (isHighEnd) ... else if (isMid) ... else ...`
chain. (File previously said "7"; actual count is 8.) Functionally correct, but a small
lookup table — e.g. a `Map<Tier, DeviceTierValues>` literal — would make the three tiers'
full value sets visible side-by-side at a glance, instead of scattered across 8 separate
chains where comparing "what does mid-tier get for landscape capture distance" means
scanning past 7 unrelated chains to find it.

### 12. [P2] `AccessBackend` implementations duplicate retry/backup-naming logic 4 times
**Status: Confirmed.** `AdbBackend`, `RootBackend`, `ShizukuBackend`, `SafBackend` each
independently implement: retry loops with backoff, `"$path.backup_${System.currentTimeMillis()}"`
backup naming, and near-identical logging around every call. A small
`retryIO(times, backoffMs) { ... }` helper shared across backends would cut real
duplication (compare `ShizukuBackend.readFile`/`readFileBytes`/`executeShellCommand`
against `RootBackend`'s equivalents — structurally the same loop, four times over).

### 13. [P2] `MainActivity` obtains the ViewModel two different ways
**Status: Confirmed.** `setContent { val viewModel: MainViewModel = viewModel() }` (Compose-scoped)
at `MainActivity.kt:66`, but `initExternalBackupDir()` at line 96 separately does
`ViewModelProvider(this)[MainViewModel::class.java]`. Since both are scoped to the same
Activity's `ViewModelStore`, they resolve to the same instance in practice — this isn't
a bug — but it's two different access patterns for the same object in the same class,
which reads as accidental rather than intentional. Worth normalizing to one accessor
(e.g. hold a lazy `by viewModels()` property and use it in both places) so a future
reader doesn't wonder whether they're two separate ViewModels.

---

## Reliability & error handling

### 14. [P0] `ConfigGenerator.kt` (1,665 lines) and `ConfigManager.kt` (1,223 lines) have zero tests
**Status: Confirmed.** These are your highest-risk files — they generate and write the
actual `.ini` files pushed to a user's device, controlling real game behavior. A
regression here doesn't crash your app, it silently corrupts someone's config. You
already have the right instinct for this class of bug: `ForbiddenCvarsTest` and
`CvarCategorizerTest` exist and suggest at least one prior bug around generating
invalid/dangerous cvars — extend that same pattern to the two biggest files, which
currently have nothing:
- Preset tier → cvar value mapping (start with `computeDeviceTier`, item 11 — it's
  already a clean, isolated pure function, cheapest place to start).
- The `PresetProfile.detail` → boolean-gate mapping documented in the KDoc at the top of
  `PresetProfile` (`ConfigGenerator.kt:26-29`) — exactly the kind of "encodes historical
  behavior in a comment" logic that silently breaks on refactor without a test pinning
  it down.
- Round-trip: generate config → parse it back → assert values match.

### 15. [P0] The reverse-engineered log decrypt format (`LogParser.kt`) has zero tests
**Status: Confirmed.** `decryptWuwaLog`/`decryptBackupLog`/`applyXorLut` (`LogParser.kt:8-32`)
implement a byte-substitution LUT reverse-engineered from Wuthering Waves' log obfuscation.
Clean and well-commented, but if the game changes its log format in a future patch, these
functions will silently return `null` or garbled text with no signal except a user
reporting "logs look weird." Worth pinning a known real sample (cipher bytes → expected
plaintext, captured once) as a test so a breaking format change fails loud in CI instead
of silently in someone's log screen. Also: `decodeLogBytes` (`LogParser.kt:39`) reports
only a `Boolean` for whether decryption succeeded — worth an enum
(`WuwaLog`/`BackupLog`/`Plaintext`) instead, so you can tell *which* format matched when
debugging a user's report.

### 16. [P1] Broad exception handling — 68 occurrences of `catch (e: Exception)`
**Status: Confirmed.** 15 of these are in `MainViewModel` alone (not 20 as previously
estimated — recount: 20 `catch (e: Exception)` in MainViewModel, 15 in ConfigManager,
9 in ShizukuBackend, 8 in SafBackend, 3 in BenchmarkTuner, 3 in RootBackend, 3 in
AdbClient, 2 in GachaApi, 2 in AdbCrypto, 1 each in LogRepository, CvarDatabase,
AdbBackend). Typical pattern:
```kotlin
} catch (e: Exception) {
    _xFeedback.value = "Something went wrong: ${e.message}"
}
```
This collapses everything from a real bug (`NullPointerException`,
`IllegalStateException` from your own code) to an expected failure
(`IOException` from a flaky ADB connection, `SecurityException` from a missing
permission) into the same generic user-facing string. Fix incrementally where the call
site knows what can fail:
```kotlin
} catch (e: IOException) {
    _deployResult.value = "Couldn't reach the device — check the connection."
} catch (e: SecurityException) {
    _deployResult.value = "Permission denied — check Shizuku/ADB authorization."
}
```
Keep a top-level `catch (e: Exception)` only as a last-resort fallback, not the primary
handler.

### 17. [P1] Caught exceptions are rarely logged, only surfaced as a UI string
**Status: Confirmed.** Only 5 of 62 source files use `Log.*` at all (`MainViewModel`: 17
calls, `AdbClient`: 19, `ConfigManager`: 16, `AdbCrypto`: 8, `BenchmarkTuner`: 3).
Combined with item 16, most caught exceptions become a feedback string with no
`Log.e(TAG, msg, e)` alongside it — the stack trace is gone the moment the catch block
runs. Add `Log.e` (with the actual `Throwable`, not just `.message`) to every catch block
that doesn't rethrow, so `adb logcat` during your own testing surfaces real stack traces
instead of just the final message string.

### 18. [P1] `AdbClient` treats every failure as fatal for the whole connection
**Status: Confirmed.** `AdbClient.kt:214` sets `connected = false` on *any* exception from
`executeShellCommand`, even a single transient read hiccup — no distinction between
"the socket is actually dead" and "this one command failed." A flaky read can kill a
session that was otherwise fine, forcing a full reconnect (and re-auth) for what might
have been a one-off blip.

### 19. [P2] Storage write path relies on legacy public-storage API
**Status: Confirmed.** `LogRepository.kt:26`, `ConfigManager.kt` (`publicDir`), and
`WuWaConfigApp.kt:87` all use `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`
directly. You've correctly requested `MANAGE_EXTERNAL_STORAGE` in the manifest and route
the grant through `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`
(`MainActivity.kt:108`), so this should work on modern Android — not a bug. Just worth
flagging as a dependency: if a user denies "All files access" (a permission Play Store
treats as sensitive), every write through these three files silently fails or throws,
since there's no fallback to scoped storage / `MediaStore`.

---

## Performance & Compose

### 20. [P1] Large files hurt both readability and Compose preview usability
**Status: Confirmed.** All line counts verified exactly:
| File | Lines |
|---|---|
| `config/ConfigGenerator.kt` | 1,665 |
| `ui/MainViewModel.kt` | 1,603 |
| `config/ConfigManager.kt` | 1,223 |
| `ui/components/Components.kt` | 1,171 |
| `ui/screens/ConfigGenScreen.kt` | 1,077 |
| `ui/screens/HomeScreen.kt` | 971 |
| `ui/screens/ReviewTuneScreen.kt` | 733 |
| `ui/screens/SettingsScreen.kt` | 618 |
| `ui/screens/IniEditorScreen.kt` | 612 |

`Components.kt` at 1,171 lines is a grab-bag — split into `components/cards/`,
`components/dialogs/`, `components/inputs/` by usage. For screens, break each into a
thin `XScreen()` that handles scaffolding/navigation plus 3–6 smaller private
composables per visual section — this also makes `@Preview` meaningfully useful again,
since you can preview one section without needing full ViewModel state wired up.

### 21. [P2] `SafBackend.resolveDocument` does one `findFile()` IPC call per path segment
**Status: Confirmed.** `SafBackend.kt:231-244` (`stripAndNavigate`) walks a path one
directory level at a time, calling `DocumentFile.findFile()` at each step — and
`findFile()` under the hood queries the whole parent directory via the
`ContentProvider`. For the shallow paths this app deals with, unlikely to be noticeably
slow, but worth caching resolved `DocumentFile` handles rather than re-walking from
`root` every time if any future feature adds deeper nesting or calls this in a loop.

### 22. [P2] `LogRepository.init()` blocks its caller thread with `runBlocking`
**Status: Confirmed.** `LogRepository.kt:30`: `runBlocking(Dispatchers.IO) { loadFromDisk() }`
inside `init()`, called from `WuWaConfigApp.onCreate()` — i.e. app startup, on the main
thread. Bounded by `MAX_ENTRIES = 1000` lines so likely fast in practice, but it's still
synchronous disk I/O sitting directly in the startup path.

---

## Maintenance debt (already known/commented by you)

Already flagged in your own code comments — listed here so they're tracked in one place
rather than only living in a comment you might not revisit:

- `GachaApi.kt:15-18` — `STANDARD_CHARACTERS`/`STANDARD_WEAPONS` are hardcoded sets that
  go stale every time Kuro moves a limited 5★ into the standard pool. Your own comment
  already proposes the fix ("any limited 5★ not seen in the last 2 banners is
  standard") — worth scheduling before the next content patch breaks pity predictions
  silently.
- `ShizukuBackend.kt:17-20` — previously reflection-based `Shizuku.newProcess()` call
  (`ShizukuBackend.kt:266`), migrated to `UserService` (Binder-based IPC) with `ShellUserService`.
  Reflection on a private method meant a future Shizuku library update could silently break
  this at runtime with no compile-time warning. Resolved — now uses stable `UserService` API.

---

## Testing gaps summary

Only 2 test files exist (`CvarCategorizerTest`, `ForbiddenCvarsTest`) for ~18,000 lines
of Kotlin. Priority order for new tests, highest risk/cheapest-to-add first:
1. `GachaApi.parseUrl` / pity calculation — pure functions, currently untested despite
   being simple, cheap wins.
2. `computeDeviceTier` (`ConfigGenerator.kt`) — already isolated and pure, good template
   for testing the rest of `ConfigGenerator`.
3. `LogParser` decrypt functions — fragile reverse-engineered format, silent failure
   mode, highest "you won't find out until a user complains" risk.
4. `ConfigGenerator` preset → cvar mapping — writes real device config.
5. `ConfigManager` backup/restore/apply flow — data loss risk if broken.

---

## What's already solid — no action needed

Worth naming so you don't second-guess these while working through the list above:
- **ADB key storage** (`AdbCrypto.kt`) — RSA keys encrypted at rest via
  `androidx.security.crypto.EncryptedFile`, with a clean migration path from an older
  plaintext format. Right approach (see item 3 for the one caveat: the library itself
  is still alpha).
- **`ConfigManager.applyCustomConfigs`** — staged writes to a temp dir, retry with
  backoff per file, cleanup in a `finally` block. Solid pattern, no changes needed.
- **`AdbBackend.pushFile`** — chunked base64 push with MD5 verification and a
  `run-as` fallback for permission-denied cases on stricter OEM builds. Thorough.
- **Gacha API calls** — correctly wrapped in `Dispatchers.IO` at both call sites
  (`MainViewModel.fetchGachaData` line 1058, `GachaPollService` line 35), no main-thread
  network calls.
- **SQL access** (`ConfigManager.kt:1177`) — parameterized query (`rawQuery` with `?`
  placeholder), no injection risk there.
- **`MainActivity.onDestroy`** — correctly guards `backend.disconnect()` behind
  `isFinishing`, so a configuration change (rotation) doesn't tear down an active ADB
  session.
- **CI/build** — `.github/workflows/build.yml` runs, ktlint is wired into Gradle,
  release builds have `isMinifyEnabled`/`isShrinkResources` on, and signing correctly
  falls back from `keystore.properties` → environment variables so CI doesn't need
  committed secrets.
- **Manifest permission scoping** — `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE`
  correctly capped with `maxSdkVersion`, and `MANAGE_EXTERNAL_STORAGE` is requested
  properly through the dedicated Settings intent rather than a runtime permission
  dialog.

---

## Suggested order of attack

```
Phase 1 (P0 security) → Phase 2.1–2.3 (cheap tests) → Phase 4.1–4.6 (quick polish)
  → Phase 3 (architecture, incremental) → Phase 2.4 (ConfigManager testability)
  → Phase 4.7–4.8 (deps) → Phase 5 (maintenance debt, opportunistic)
```

### Phase 1 — P0 Security & Correctness (1–2 days)
1. **`RootBackend` shell quoting fix** (item 1) — small, mechanical, highest
   payoff-to-effort ratio.
2. **`usesCleartextTraffic` manifest fix** (item 2) — one-line change.
3. **Narrow `AdbClient` exception handling** (item 18) — distinguish transient
   failures from socket-death.

### Phase 2 — P0/P1 Testability (3–5 days)
4. **`LogParserTest`** (item 15) — pin the LUT with a known sample.
5. **`GachaApiTest`** (testing gaps #1) — `parseUrl` + pity calculation.
6. **`ConfigGeneratorTest`** (item 14) — `computeDeviceTier` + preset→cvar mapping +
   round-trip.
7. **Make `ConfigManager` testable** (item 14) — extract I/O into injectable interface.

### Phase 3 — P1 Architecture (5–10 days, incremental)
8. **Peel `IniEditorViewModel` out** (item 7) — prove the split pattern.
9. **Peel `SettingsViewModel`** — theme/prefs.
10. **Peel `GachaViewModel`** — wraps `GachaApi` + `GachaPollService`.
11. **Peel remaining screens** — `ConfigGenViewModel`, `DeployHistoryViewModel`,
    `LogsViewModel`, `ProfileViewModel`.
12. **Convert stateful singletons to injectable classes** (item 8) —
    `DeployHistoryStore`, `ProfileStore`, `LogRepository`.
13. **Normalize `MainActivity` ViewModel access** (item 13) — one `by viewModels()`.

### Phase 4 — P1/P2 Polish (2–4 days)
14. **Narrow + log `catch (e: Exception)` in `MainViewModel`/`AdbClient`** (items 16, 17).
15. **Extract shared `retryIO` helper** (item 12) — used by all 4 backends.
16. **Refactor `computeDeviceTier` to lookup table** (item 11).
17. **Split `buildAndroidEngineIni`** (item 10) — per-section builders.
18. **Add `Mutex` to `DeployHistoryStore`** (item 9) — future-proofing.
19. **Fix `LogRepository.init()` blocking** (item 22) — async load.
20. **Dependency refresh** (item 5) — standalone task.
21. **Add Gradle version catalog** (item 6) — pairs with dependency refresh.

### Phase 5 — Maintenance Debt (ongoing, low risk)
22. **Fix `GachaApi` standard-pool staleness** — implement the "last 2 banners" heuristic.
23. **Migrate `ShizukuBackend` from reflection to `UserService`** — follow existing comment.
24. **Fix `LogParser.decodeLogBytes` return type** — `Boolean` → enum.

### Phase 6 — Completed (2026-07-29)
17. **Split `buildAndroidEngineIni`** — refactored into `EngineIniContext` + 25 per-section builders.
22. **Fix `GachaApi` standard-pool staleness** — dynamic standard pool derivation from type "1" records.
23. **Migrate `ShizukuBackend` from reflection to `UserService`** — Binder-based IPC via `ShellUserService`.
24. **Fix `LogParser.decodeLogBytes` return type** — `Boolean` → `DecodeResult` enum (`DECRYPTED`/`PLAINTEXT`).

### CI verification after each phase
```
./gradlew ktlintCheck          # lint (CI step 1)
./gradlew testDebugUnitTest    # unit tests (CI step 2)
./gradlew assembleDebug        # debug APK (CI step 3)
```
