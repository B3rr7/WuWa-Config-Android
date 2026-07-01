# WuWaConfig — App Documentation

## Overview

WuWaConfig is an Android tool that optimizes and deploys configuration files for **Wuthering Waves** (global version, `com.kurogame.wutheringwaves.global`). It generates tuned `.ini` files (Engine.ini, DeviceProfiles.ini, GameUserSettings.ini, Scalability.ini, Hardware.ini) and pushes them to the game's config directory via one of four access backends.

- **Package**: `com.wuwaconfig.app`
- **Min SDK**: 26 / **Target SDK**: 34
- **Language**: Kotlin 1.9.22, Compose BOM 2024.02
- **No DI** — global `WuWaConfigApp.instance` accessed from composables

### Architecture at a Glance

```
┌─────────────┐     ┌───────────────┐     ┌────────────────┐     ┌──────────────┐     ┌────────────┐
│  Compose UI  │────▶│  MainViewModel │────▶│  ConfigManager  │────▶│ AccessBackend │────▶│   Device    │
│  (10 screens)│◀────│  (~1070 lines) │◀────│  (per-backend)  │◀────│ ADB/Shizuku/  │◀────│ (game dir)  │
└─────────────┘     └───────────────┘     └────────────────┘     │ Root/SAF      │     └────────────┘
       │                     │                      │              └──────────────┘
       │                     │                      │
       ▼                     ▼                      ▼
  LogRepository       ConfigGenerator          CvarDatabase /
 (StateFlow logs)   + SmartBrain + LogParser   ForbiddenCvars
```

Screens dispatch actions to `MainViewModel`, which owns all app state and delegates device I/O to `ConfigManager`. `ConfigManager` is recreated per active `AccessBackend` and never talks to the device directly — it always goes through the backend's `executeShellCommand` / `pushFile` / `readFile` interface. Config *generation* (`ConfigGenerator`, `SmartBrain`, `LogParser`, `CvarDatabase`) is backend-agnostic and only produces/consumes text.

---

## Table of Contents

1. [Navigation & Screens](#navigation--screens)
2. [Access Backends](#access-backends)
3. [ADB Wire Protocol](#adb-wire-protocol-adb)
4. [Config System](#config-system)
   - [ConfigManager](#configmanager-configmanagerkt)
   - [ConfigGenerator & Presets](#configgenerator-configgeneratorkt)
   - [SmartBrain Scoring](#smartbrain-smartbrainkt)
   - [CvarDatabase](#cvardatabase-cvardatabasekt)
   - [ForbiddenCvars](#forbiddencvars-forbiddencvarskt)
   - [LogParser](#logparser-logparserkt)
 5. [Config Generation & Deploy — End-to-End Flow](#config-generation--deploy--end-to-end-flow)
 6. [Key Flows](#key-flows)
 7. [CvarOptimizer](#cvaroptimizer-cvaroptimizerkt)
 8. [Deploy History System](#deploy-history-system)
 9. [Data Model](#data-model-model)
 10. [Services](#services)
 11. [Dependencies](#dependencies-buildgradlekts)
 12. [State Management](#state-management)
 13. [File Storage](#file-storage)
 14. [Preset Profiles Quick Reference](#preset-profiles-quick-reference)
 15. [Color System](#color-system)
 16. [Component Library](#component-library-uicomponentscomponentskt)
 17. [Game Paths](#game-paths-gamepathskt)

---

## Navigation & Screens

Routes defined in `MainActivity.kt:117-196`. 10 screens via NavHost:

### Setup Screen (`SetupScreen.kt`)
Shows only on first launch (`isSetupDone` flag). Displays the game config directory path and allows user to proceed to Home.

### Terms Screen (`TermsScreen.kt`)
Gated before any other screen. Shows a disclaimer about terms of use. On accept: writes `terms_accepted` to prefs, requests storage permissions (`MANAGE_EXTERNAL_STORAGE` on Android 11+), initializes the backup download dir, and dismisses.

### Home Screen (`HomeScreen.kt`)
Main dashboard. Sections:
- **Backend status card** — shows current access method (ADB/Shizuku/Root/SAF), connection state, toggle button to cycle methods
- **Custom Config** — file picker (`OpenMultipleDocuments`) for `.ini` files → review screen showing matched files + skipped → if <5 files, Backup Scope dialog ("All 5 INIs" or "Only Overwritten") → Apply calls `viewModel.applyCustomFiles()` → success popup with green checkmark and file list
- **Actions** — "Delete Config Files" button, "Collect Client.log" button
- **Recent Log** — `MiniLogViewer` showing last 5 log entries; clickable to navigate to LogsScreen
- **Deploy History** — clickable card (if deploy history enabled) showing latest deploy + comparison status; navigates to HistoryScreen
- **Navigation buttons** — Config Generator, Backups, Pity Tracker, Profile, Battle Stats, App Log

### Config Gen Screen (`ConfigGenScreen.kt`)
Full generator UI (~823 lines). Features:
- **SmartBrain recommendation** — auto-selects preset based on analyzed Client.log
- **Preset selector** — Potato / Performance / Balanced / High / Ultra (hidden when Advanced per-device tuning enabled)
- **Advanced per-device tuning toggle** — when enabled, uses `CvarOptimizer` to compute profile fields per device instead of fixed presets
- **Toggle options** — FPS (30-120), unlock 120fps, unlock ultra, VSync, Cooling, Vulkan, HZB occlusion, Fog removal, CA, disable SSR/bloom/outline/radial blur/auto-exposure
- **File toggles** — generate Engine.ini, DeviceProfiles.ini, GameUserSettings.ini, Scalability.ini, Hardware.ini
- **Game mode** — Overworld / Domain toggle
- **Chipset override** — manual GPU selection dropdown
- **Import from log** — load existing CVars from Client.log and merge into generated config
- **Benchmark Tuner** — Progressive preset stepping (ultra→high→balanced→perf→potato) based on logcat FPS data
- **Review & Deploy** — shows generated INI text, allows editing `cvarOverrides`, then deploys via `viewModel.deployGeneratedConfigs()`
- **Verification report** — after deploy, shows accepted/rejected CVars with colored tags (redundant/unknown/monitored)

### Backup Screen (`BackupScreen.kt`)
- Lists local backups sorted by timestamp
- Create backup — dialog shows checkboxes for all 5 INI files (all checked by default). Only checked files are read from device and saved.
- Restore backup — "Restore" opens a dialog with the backup's files as checkboxes (all checked by default). Only checked files are pushed to device.
- Delete backup — removes JSON manifest + public `.ini` folder
- Auto-backup before custom config apply (configurable in settings) — when `<5 files` uploaded, Backup Scope dialog asks: back up all 5 or only the files being overwritten

### Pity Screen / Gacha Tracker (`PityScreen.kt`)
- Shows Convince URL extracted from Client.log
- Fetches gacha history from Kuro's gacha API
- Displays pulls per pool, 5★/4★ counts, pity predictions (soft/hard pity estimates)
- History cache (12h TTL, `GachaHistoryStore`)

### Profile Screen (`ProfileScreen.kt`)
Reads player profile from device databases (`LocalStorage.db`, `DeviceStorage.db`) and `.sav` files via SQLite queries and `strings` extraction:
- UID, server, player level
- Tower floor, weekly rogue score, battle pass status
- Game version, language, login device ID
- Config setting counts per INI file

### Battle Stats Screen (`BattleStatsScreen.kt`)
Parses Client.log for Chinese-language combat telemetry:
- Battles fought, echoes collected, dodges (forward/back/counter), deaths, role switches, teleports, staggers, stamina used, echo skills, month cards

### Settings Screen (`SettingsScreen.kt`)
- Access method chooser (ADB/Shizuku/Root/SAF)
- Manual ADB IP:port input
- Theme switcher (Light/Dark/System)
- Background customization — pick image or video for gradient background, opacity slider
- Backup auto-backup toggle
- Generation & History — Deploy History toggle (baseline + outcome comparison)
- About section with links

### History Screen (`HistoryScreen.kt`)
Lists past deploys with baseline metrics and optional comparison outcome. Features:
- Per-deploy summary (preset, CVar counts, redundant/unknown/monitored tags)
- **Compare Now** button — pulls fresh Client.log and computes FPS/thermal/OOM/drop Δ vs baseline
- Color-coded deltas (green = improvement, red = regression)
- Accessible from HomeScreen deploy history card

### Logs Screen (`LogsScreen.kt`)
Full-screen log viewer. Features:
- Search filter (free text)
- Level filter chips (All/INFO/SUCCESS/WARNING/ERROR)
- Auto-scroll to bottom
- Entry count display
- Save log to file

---

## Access Backends

All implement `AccessBackend` interface (`AccessBackend.kt:15-27`):

| Method | File | How it connects | Preferred use |
|--------|------|-----------------|---------------|
| **ADB** | `AdbBackend.kt` | Custom wire protocol (no external binary) over TCP socket. RSA key auth. Port scanning (37000-44000). | Wireless debugging |
| **Shizuku** | `ShizukuBackend.kt` | Uses `rikka.shizuku.Shizuku.newProcess()` reflection API. 15s command timeout. | System-level access without root |
| **Root** | `RootBackend.kt` | `ProcessBuilder("su", "-c", ...)` with 10s timeout. | Legacy rooted devices |
| **SAF** | `SafBackend.kt` | Storage Access Framework tree URI. No shell access — reads/writes via ContentResolver. | No root/debugging needed |

Cycle order: ADB → Shizuku → Root → SAF → ADB.

### Backend operations:
- `connect()` / `disconnect()`
- `executeShellCommand(command)` — shell access (not available in SAF)
- `pushFile(sourcePath, targetPath)` — file upload via base64 chunks + `printf >`/`>>` + `base64 -d` + MD5/size verification
- `ensureDirectoryExists(dirPath)`
- `fileExists(path)`
- `listDirectory(path)`
- `backupFile(path)`
- `readFile(path)`

---

## ADB Wire Protocol (`adb/`)

Custom implementation — **no external ADB binary**:

### AdbCrypto (`AdbCrypto.kt`)
- 2048-bit RSA key pair generation + persistence in `app files dir/adbkey` / `adbkey.pub`
- SSH-format public key encoding (`ssh-rsa` + exponent + modulus, base64)
- SHA1withRSA signature for AUTH_TOKEN challenges

### AdbProtocol (`AdbProtocol.kt`)
Binary ADB protocol messages (24-byte header + payload):
- Commands: CNXN (connect), AUTH (auth), OPEN (open stream), OKAY (ack), WRTE (write), CLSE (close)
- Little-endian framing: cmd(4) + arg0(4) + arg1(4) + dataLength(4) + crc32(4) + magic(4)
- CRC32 not enforced (Android 11+ daemon may send 0)

### AdbClient (`AdbClient.kt`)
- TCP socket to `127.0.0.1:<port>` or device IP
- Auth flow: send CNXN → receive CNXN (pre-auth) or AUTH challenge → sign token → send public key on retry → CNXN
- Shell execution via OPEN(streamId, "shell:cmd") → read WRTE responses → OKAY ack → CLSE close
- `drainTrailingWrite()` — handles ADB pipe buffer race (WRTE after CLSE)
- Auto-retry with regenerated keys on rejection

### PortScanner (`PortScanner.kt`)
- Scans 37000-44000 range (batch of 50, 300ms connect timeout)
- Also checks well-known 5555 and last-successful port
- 20s total wall clock timeout
- ADB-specific detection: connects, sends CNXN, checks for CNXN/AUTH response
- IP auto-detection from network interfaces (private range only)

### ADB Connection Sequence
1. `PortScanner` scans localhost + device IP on 37000-44000 + 5555
2. `AdbClient` connects TCP socket
3. ADB protocol auth: CNXN → AUTH token → RSA signature → public key → CNXN
4. Keep-alive via `AdbConnectionService` foreground notification
5. On rejection: regenerate RSA keys and retry

---

## Config System

### ConfigManager (`ConfigManager.kt`)
Created per backend (`WuWaConfigApp.switchTo()` recreates it). Core operations:

- **`applyCustomConfigs()`** — writes provided INI strings to staging dir → pushes to device via backend → handles retries → MD5 verify → returns success/failure
- **`createBackup(name, type, selectedFiles?)`** — reads current device config (optionally filtered by `selectedFiles`) → saves as JSON + `.ini` files to `Downloads/WuWaConfig/Backups/{name}/`
- **`restoreBackup(backup, onProgress, selectedFiles?)`** — reads backup files (optionally filtered) → pushes to device. Returns failure if empty set.
- **`readClientLogContent()`** — reads `Client.log` from device with chunked pull (head/mid/tail for large files, or full base64 for small)
- **`verifyDeployedCvars()`** — parses deployed `Client.log` → matches generated CVars against game-recognized CVars → returns `VerificationReport`
- **`readProfile()`** — pulls `LocalStorage.db` + `DeviceStorage.db` → SQLite queries for UID, server, tower, etc.
- **`readBattleStats()`** — multichunk pull of Client.log → parse battle telemetry
- **`refreshConfigHashes()`** — computes MD5 of each deployed file → writes `KuroConfigMonitor.hash` with incrementing `ModifyCount`
- **`deleteConfigFiles()`** — removes all 5 INI files from device

**File push mechanism** (Shizuku/ADB):
1. Base64-encode file content
2. Split into chunks (`MAX_ARG_STRLEN=4096` - overhead)
3. First chunk: `printf '%s' <chunk> > /data/local/tmp/wuwaconfig_{ts}_{rand}.b64`
4. Subsequent chunks: `printf '%s' <chunk> >> /data/local/tmp/...`
5. `base64 -d` → target path, `rm -f` temp file
6. Verify via `md5sum` or `wc -c`
7. Retry once (`PUSH_RETRY_COUNT=1`) on failure

**Client.log reading:**
1. Check file size via `wc -c`
2. If large (>500KB): pull head/mid/tail chunks via `head`/`dd`/`tail` → base64 → decode
3. If small: pull full file → base64 → decode
4. XOR decrypt (strip 3-byte header → apply LUT → detect UTF encoding)
5. Parse structured fields with regex

### ConfigGenerator (`ConfigGenerator.kt`)
Mutable singleton object. Must call `reset()` before generation:

- Generates `Engine.ini` (~300 lines) with 18 sections: Character, Scalability, Anti-Aliasing, Post Processing, Shadow, Texture Streaming, Mobile Rendering, VRS, Effects/Particles, Water/Reflection, Screen-Space Effects, Environment, NPC & World, Advanced LOD, Animation, Frame & Display, Pipeline/RHI, Thermal/Stability
- Generates `DeviceProfiles.ini` with chipset detection (40+ SoC profiles from Adreno 830 down to Mali-G57)
- Generates `GameUserSettings.ini` with `[ScalabilityGroups]` and `[/Script/Engine.GameUserSettings]` sections
- Optionally generates `Scalability.ini` and `Hardware.ini`
- `mergeWithLogCvars()` — imports CVars from Client.log that aren't already in the chosen preset
- `deduplicateIniText()` — removes duplicate CVar keys (last occurrence wins)
- `cvarOverrides` — user-specified key=value overrides applied after generation
- `allowRestrictedCvars` — controls SmartBrain penalty for forbidden CVars

#### Presets (5 tiers)

| Preset | Screen% | Shadow | ShadowRes | SSR | MipBias | Streaming | ViewDist | FoliageLOD | Detail | LOD Bias | GrassCull |
|--------|---------|--------|-----------|-----|---------|-----------|----------|------------|--------|----------|-----------|
| Potato | 50 | 0 | 128 | 0 | 3 | 0.3 | 0.3 | 0.4 | 0 | 5 | 1500 |
| Performance | 60 | 0 | 256 | 0 | 3 | 0.5 | 0.5 | 0.6 | 0 | 3 | 4500 |
| Balanced | 80 | 2 | 1024 | 1 | 0 | 2.0 | 1.5 | 2.0 | 1 | 0 | 15000 |
| High | 100 | 4 | 2048 | 2 | 0 | 3.0 | 2.0 | 2.5 | 2 | 0 | 20000 |
| Ultra | 100 | 5 | 2048 | 4 | -1 | 4.0 | 3.0 | 3.0 | 2 | -1 | 30000 |

#### Key CVar derivation rules

- **Render scale** → `p.screen` directly (e.g. potato=50, balanced=80, ultra=100)
- **Shadow quality** → `p.shadow`: 0-1→1, 2-3→2, 4-5→3 (sg.ShadowQuality)
- **Texture quality** → `p.detail`: 0→1, 1→2, 2→3 (sg.TextureQuality)
- **Post-process quality** → `p.detail`: 0→1, 1→2, 2→3
- **Effects quality** → `p.detail`: 0→0, 1→1, 2→2
- **Foliage quality** → `p.detail`: 0→0, 1→1, 2→2
- **Niagara quality** → `p.detail`: 0-1→1, 2→2
- **Device tier** → computed from GPU model string (Adreno 7xx/8xx = high-end, Mali-G7xx/8xx/9xx = high-end, Adreno 6xx = mid, etc.)
- **Texture pool size** → high-end=800, mid=500, low=380
- **Grass cull distance** → high-end=2000, mid=1200 (or 600 if thermal), low=800
- **ISM draw distance** → high-end=14000, mid=10000, low=7000
- **NPC disappear distance** → high-end=15000, mid=10000, low=7000

### SmartBrain (`SmartBrain.kt`)
Scores device capability from `LogInfo` starting at **50** (0-100 range) and recommends a preset.

**Scoring algorithm:**

```
base = 50

── GPU Tier ──────────────────────────────────
flagship  (Adreno 8xx, Dimensity 93+, Tensor G3+)   → +30
high      (Adreno 75x-80x, Dimensity 85+, Mali-G7+) → +20
mid_high  (Adreno 7xx, Dimensity 73+, Xclipse)      → +10
mid       (Adreno 6xx, Mali-G6xx)                   →  ±0
mid_low   (Adreno 5xx, Mali-G5xx)                   → -10
low/unknown                                        → -20

── RAM ────────────────────────────────────────
≥8GB   → +8
6-8GB  → +5
4-6GB  →  ±0
<4GB   → -15

── Vulkan ─────────────────────────────────────
available → +8

── FPS Analysis ───────────────────────────────
actual ≥ 95% of cap     → +5
drop 10-20%             →  -6
drop 20-30%             → -12
drop >30%               → -18
actual < 30fps          → -15
actual 30-45fps         →  -8

── Thermal Events (from Client.log) ──────────
≥5 events               → -20
≥3 events               → -12
≥1 event                →  -5

── GPU OOM (out of memory) ───────────────────
≥3 OOMs                 → -30
≥2 OOMs                 → -20
≥1 OOM                  → -12

── Frame Drops ────────────────────────────────
≥15 drops               → -10
≥5 drops                →  -5

── Other Flags ────────────────────────────────
Low memory device       → -15
Forbidden CVars (×5 each, if restricted) → -5 each
Network errors ≥10      → -10
Network errors ≥5       →  -5

── Render Scale Analysis ─────────────────────
≥125%                   →  +3
≥110%                   →  +5
<70%                    → -10

── Resolution ────────────────────────────────
4K (≥2160p)             → -10
  + mid/low GPU         →  -8 extra
QHD+ (≥1440p)           →  -4
  + mid_low/low GPU     →  -6 extra
  + low RAM (<6GB)      →  -5 extra

── Texture Errors (VRAM pressure) ───────────
textureErrors penalty   → min(textureErrors, 20)

── Active CVar Analysis ──────────────────────
>5 unknown CVars          →  -5
>10 monitored CVars       →  +3
>20 CVars differ from defaults  →  +5 (already optimized)
<5 differ from defaults   →  -8 (room to improve)
High shadows + low GPU    →  -6
High shadows + <6GB RAM  →  -4
High textures + <6GB RAM  →  -5
High textures + tex errors → -4
Render scale >100%        →  -min(overScale, 8)
High FPS target + thermal →  -6
FSR RCAS enabled          →  -8
SSAO on low GPU           →  -5
High bloom + thermal      →  -3

── Combined Combos ───────────────────────────
Low RAM + high res        →  -5
Thermal + low GPU         →  -min(thermalEvents, 6)
OOM + texture errors      →  -5

final = score.coerceIn(0, 100)
```

**Preset recommendation** (from final score — this table is the single source of truth; conditions are checked top to bottom, first match wins):

| Score | Conditions | Preset |
|-------|-----------|--------|
| any | GPU OOM ≥ 2 | Potato |
| any | isLowMem or score ≤ 20 | Potato |
| ≥ 80 | flagship GPU + Vulkan + non-4K | Ultra |
| ≥ 75 | flagship/high GPU + Vulkan | High |
| ≥ 70 | flagship/high GPU | High |
| ≥ 40 | (fallthrough) | Balanced |
| ≥ 20 | (fallthrough) | Performance |
| < 20 | (fallthrough) | Potato |

### CvarDatabase (`CvarDatabase.kt`)
Lazy-loads 3 asset files on first access (daemon thread from `WuWaConfigApp.onCreate()`):

| File | Contents | Count |
|------|----------|-------|
| `cvars/libUE4_cvars.txt` | All CVars from `libUE4.so` UTF-16 dump | ~3348 |
| `cvars/config_monitor_cvars.txt` | KuroConfigMonitor-tracked CVars | 569 |
| `cvars/config_monitor_values.txt` | Game default values | 568 |

Provides: `isKnown()`, `isMonitored()`, `gameDefault()`, `differsFromDefault()`, `categorize()` (18 category rules), `optimizeIniText()` (comments out redundant/unknown CVars), `buildCvarDetails()`, `extractCvarValues()`.

### ForbiddenCvars (`ForbiddenCvars.kt`)
Set of 33 CVars that the game actively strips/ignores (streaming, screen %, AFME/MFRC/FEstimation, DLSSG, etc.). `stripForbiddenCvars()` removes matching lines from INI text. Also checks common variants (with/without `+`/`-` prefix, with/without `r.` prefix).

### LogParser (`LogParser.kt`)
Parses WuWa's XOR-encrypted `Client.log`:

- **XOR LUT**: single-pass `LUT[i] = i xor (0xA5 if i%2==1 else 0xEF)`, strip 3-byte header `00 54 50`
- **Encryption detection**: checks first 3 bytes for `00 54 50` magic
- **UTF detection**: handles UTF-16BE (with BOM), UTF-16LE (with BOM), heuristics (zero-byte sampling), falls back to UTF-8
- **`parseLog()`** — extracts GPU, device model, SoC, RAM, resolution, FPS, shadow quality, render scale, game API (Vulkan/OpenGL), CVar overrides (`Setting CVar [[key:value]]`), thermal events, OOMs, frame drops, network errors, forbidden CVars
- **`extractConveneUrl()`** — regex for Kuro gacha URL
- **`parseBattleStats()`** — Chinese-language telemetry parsing

---

## Config Generation & Deploy — End-to-End Flow

This is the full pipeline from raw device log to a verified, deployed config. Steps 3a-3c below correspond to `ConfigGenerator.generateWithCorePaths()` internals.

```
Step 1 ── Import & Analysis ────────────────────────────────
│  User optionally imports Client.log from device
│  → LogParser parses GPU, RAM, FPS, thermal, OOM, CVars etc.
│  → SmartBrain runs scoring algorithm (base 50, up to ±80)
│  → Suggests a preset: potato / performance / balanced / high / ultra
│  (suggestion is computed per-device, not hardcoded)
│
Step 2 ── User Configures ───────────────────────────────────
│  User can accept SmartBrain's suggestion or pick any preset
│  User enables toggle options (FPS, 120fps unlock, VSync,
│  Cooling, Vulkan, HZB, Fog, CA, SSR, Bloom, Outline, etc.)
│  User selects which .ini files to generate:
│    ☑ Engine.ini    (always on)
│    ☑ DeviceProfiles.ini
│    ☑ GameUserSettings.ini
│    ☐ Scalability.ini
│    ☐ Hardware.ini
│
Step 3 ── Generation (ConfigGenerator.generateWithCorePaths) ─
│
│  3a. Build Engine.ini
│      1. configHeader() — header comment block
│      2. Core.System paths — read existing Engine.ini from
│         device if available → extractCoreSystemPaths() gets
│         [Core.System] paths (preserved, not replaced);
│         fallback: DEFAULT_CORE_SYSTEM (~40 known content
│         paths extracted from the binary) if no existing config
│      3. [SystemSettings] — ~250 CVars in 18 groups:
│         ┌─────────────────────────────────────────────────┐
│         │ Character Quality      ← p.detail, p.shadow      │
│         │ Scalability (sg.*)     ← opts.shadowOverride,    │
│         │                          opts.texOverride        │
│         │ Anti-Aliasing          ← TAA params              │
│         │ Post Processing        ← bloom, DoF, CA, AO      │
│         │ Shadow                 ← CSM cascades, res,      │
│         │                          point light shadows     │
│         │ Texture Streaming      ← MipBias, Aniso, Pool    │
│         │ Mobile Rendering       ← MSAA, HBAO, FSR, PPR    │
│         │ VRS                    ← material/mesh VRS       │
│         │ Effects / Particles    ← NiagQ, spawnRate        │
│         │ Water / Reflection     ← SSR, planar reflection  │
│         │ Screen-Space Effects   ← SSGI, SSS, SSFS         │
│         │ Environment            ← fog, clouds, foliage    │
│         │ NPC & World            ← culling, LOD, grass     │
│         │ Advanced LOD / Culling ← screen radius, dist     │
│         │ Animation & BP         ← URO, anim rate          │
│         │ Frame & Display        ← VSync, FramePace, FI    │
│         │ Pipeline / RHI         ← PSO cache, RHICmd       │
│         │ Thermal & Stability    ← auto-cool, thermal ctrl │
│         │ Forbidden CVar guards  ← FSR RCAS=0, SSAO=0      │
│         │ Performance tweaks     ← potato/perf only:       │
│         │   HZB occlusion, reflection env=0, dynamic       │
│         │   lights=0, shadow min, SSGI=0, SSS=0, fog=0     │
│         └─────────────────────────────────────────────────┘
│      4. [/Script/Engine.StreamingSettings] — streaming
│      5. [/Script/Engine.GarbageCollectionSettings] — GC
│
│  3b. Build DeviceProfiles.ini
│      1. Chipset detection from logInfo (40+ SoC profiles)
│         Adreno830 → Android_Adreno830
│         Dimensity 9400 / Mali-G925 → Android_Mali_G925
│         ... down to Mali-G57 / Adreno 4xx
│      2. Fallback: universal profiles (Android_Low/Mid/
│         VeryHigh/Ultra) based on preset
│      3. BaseProfileName = preset base (Android_Mid, etc)
│      4. +CVars= entries: device evaluation tier, texture
│         LOD bias, character draw distances, imposter
│         scale, ISM distances, foliage cull distances,
│         FPS unlock CVars
│
│  3c. Build GameUserSettings.ini
│      1. [ScalabilityGroups] — sg.* values derived from
│         p.screen (render scale), p.detail, p.shadow
│      2. [/Script/Engine.GameUserSettings] — VSync,
│         resolution, fullscreen, FrameRateLimit, HDR
│      3. [Internationalization] — Culture=en
│      4. [ShaderPipelineCache.CacheFile] — LastOpened
│
│  3d. (if enabled) Build Scalability.ini / Hardware.ini
│      Scalability.ini: ResolutionQuality, View/Anti/Shadow/
│        Post/Texture/Effects/Foliage/Shading/KuroRender
│      Hardware.ini: DeviceProfileName=Android_*, FramePace,
│        +CVars= Aniso, MipBias, foliage.LODDistance
│
Step 4 ── Post-processing (applied to Engine.ini) ────────────
│  1. (if importFromLog) mergeWithLogCvars() — imports CVars
│     from Client.log not already in the preset → adds to
│     [SystemSettings] with "; IMPORTED FROM Client.log"
│  2. applyCvarOverrides() — user's key=value overrides
│  3. (if optimizeWithCvarDb) CvarDatabase.optimizeIniText()
│     — comments out CVars matching game defaults
│     (; REDUNDANT) or unknown CVars not in libUE4_cvars.txt
│     (; UNKNOWN CVar)
│  4. deduplicateIniText() — removes duplicate CVar keys,
│     keeping last occurrence
│  5. rememberGeneratedCvars() — saves CVar names for later
│     verification
│  → produces GeneratedIni(engine, deviceProfiles,
│    gameUserSettings, scalability?, hardware?)
│
Step 5 ── Review ────────────────────────────────────────────
│  User sees full generated INI text with all optimizations
│  visible (commented-out lines, imported CVars flagged)
│  User can tap "Edit Overrides" to add key=value overrides
│
Step 6 ── Deploy ────────────────────────────────────────────
│  User taps "Deploy to Device"
│  → ConfigManager.applyCustomConfigs() writes files to
│    device via backend (base64 chunked push + MD5 verify)
│  → KuroConfigMonitor.hash refreshed with new MD5 hashes
│    and incremented ModifyCount
│
Step 7 ── Verification ──────────────────────────────────────
│  Pull Client.log from device
│  verifyDeployedCvars() compares generated CVars vs
│    game-recognized CVars in Client.log
│  VerificationReport shows:
│    - Accepted: CVars the game recognized (green)
│    - Rejected: CVars the game didn't recognize (red)
│    - Redundant tag (N): CVars matching game defaults
│    - Unknown tag (N): CVars not in libUE4_cvars.txt
│    - Monitored tag (N): CVars tracked by KuroConfigMonitor
```

**Engine.ini [Core.System] path handling** (referenced in step 3a.2):
- Before generation, the app reads the existing Engine.ini from device
- `extractCoreSystemPaths()` pulls all `Paths=` entries under `[Core.System]`
- These exact paths are injected into the generated Engine.ini, preserving the game's content mount configuration
- If no existing config exists (fresh device or after delete), `DEFAULT_CORE_SYSTEM` from ConfigGenerator is used — this contains all ~40 known Kuro/Epic content paths extracted from the binary

---

## Key Flows

### Custom Config Apply
1. User picks `.ini` files via file picker
2. HomeScreen matches filenames to `Engine.ini` / `DeviceProfiles.ini` / etc.
3. Review screen shows matched files + "Will skip: X, Y, Z"
4. Apply → if <5 files uploaded and `backup_before_apply` setting is on, **Backup Scope dialog** asks "Back Up All 5 INIs" or "Only Overwritten"
5. ViewModel calls `ConfigManager.applyCustomConfigs()` with `backupAllInis` flag
6. Backup created (if setting enabled) — backs up either all 5 or only the files being overwritten
7. Each file written to `cache/staging/`, pushed via backend, retried on failure
8. `KuroConfigMonitor.hash` refreshed with new MD5 + incremented `ModifyCount`
9. **Success popup** with green checkmark: "N file(s) deployed: X, Y, Z" + "Config files written and hash refreshed"
10. Log shows "SUCCESS: N file(s) applied (Engine.ini, ...)" and "Skipped (not provided): ..."

### Config Generation & Deploy
See [Config Generation & Deploy — End-to-End Flow](#config-generation--deploy--end-to-end-flow) above.

---

## CvarOptimizer (`CvarOptimizer.kt`)

Per-device CVar tuning engine (alternative to fixed presets). Activated via `GeneratorOptions.useAdvancedGen` toggle in ConfigGenScreen.

Computes optimal `PresetProfile` fields (screen, shadow, ssr, mipbias, etc.) directly from `LogInfo` device characteristics instead of selecting from 5 fixed presets:

- **GPU tier detection** — same regex patterns as SmartBrain (flagship/high/mid_high/mid/mid_low/low/unknown)
- **Per-field rules** — each profile field computed independently based on GPU tier, RAM, thermal events, OOM count, texture errors
- **Hard-limited detection** — OOM ≥ 1 OR (thermal ≥ 3 AND low-tier GPU) forces minimum settings (potato-level)
- **Constrained detection** — thermal ≥ 3, texture errors ≥ 5, or RAM < 6GB reduces settings by 1-2 tiers

When `useAdvancedGen` is true, `ConfigGenerator.generateWithCorePaths()` replaces `PRESETS[preset]` lookup with `CvarOptimizer.optimizeProfile(logInfo)` → converted to `PresetProfile` via `toPresetProfile()`. All existing builder functions (`buildAndroidEngineIni`, `buildAndroidDeviceProfilesIni`, etc.) work unchanged.

## Deploy History System

Tracks deployment baseline performance and compares against outcome after gameplay.

| File | Lines | Purpose |
|------|-------|---------|
| `DeployRecord.kt` | ~45 | `DeployRecord` + `DeployComparison` data classes |
| `DeployHistoryStore.kt` | ~130 | JSON persistence (`{app.filesDir}/deploy_history.json`), max 20 records |
| `HistoryScreen.kt` | ~200 | Full-screen deploy history viewer with comparison UI |

**Flow:**
1. User deploys config → `deployGeneratedConfigs()` saves baseline `LogInfo` (FPS, thermal, OOM, drops) to history
2. User plays game, returns later
3. HomeScreen shows "Deploy History" card with latest deploy + comparison status
4. Tap → HistoryScreen → "Compare Now" pulls fresh Client.log → computes Δ (FPS, thermal, OOM, drops)
5. Green numbers = improvement, Red = regression

**Settings toggle:** "Deploy History" in Settings → Generation & History card. Persisted to `deploy_history` pref key. Default: on.

## Data Model (`model/`)

| Class | File | Purpose |
|-------|------|---------|
| `LogEntry` | `LogEntry.kt` | Single log entry with timestamp, message, level |
| `LogInfo` | `LogInfo.kt` | Device/performance info extracted from Client.log |
| `LogRepository` | `LogRepository.kt` | Global log singleton; rotating file (5MB, 2 rotated copies), 1000 entry in-memory cap |
| `GamePaths` | `GamePaths.kt` | Game config/log paths on device |
| `ConfigPreset` | `ConfigPreset.kt` | Deprecated preset model (replaced by PRESETS) |
| `ConfigBackup` | `ConfigBackup.kt` | Named backup with list of `ConfigFile`s |
| `DeployRecord` | `DeployRecord.kt` | Baseline + outcome comparison per deploy |
| `DeployComparison` | `DeployRecord.kt` | FPS/thermal/OOM/drop deltas |
| `GeneratorOptions` | `PresetModels.kt` | All generation toggles and parameters |
| `GeneratedIni` | `PresetModels.kt` | Generated INI text per file |
| `CvarDetail` | `VerificationReport.kt` | CVar metadata (known, monitored, default matches, category) |
| `VerificationReport` | `VerificationReport.kt` | Deploy verification result (accepted/rejected CVars, details) |
| `AdbStatus` | `AdbStatus.kt` | ADB connection state machine |
| `AppSettings` | `AppConfig.kt` | User preferences |
| `BattleStats` | `BattleStats.kt` | Parsed battle telemetry |
| `GachaRecord` | `GachaRecord.kt` | Single gacha pull record |
| `GachaData` | `GachaRecord.kt` | Aggregated gacha data with predictions |
| `PlayerProfile` | `PlayerProfile.kt` | Player UID, server, levels, settings |
| `CvarCategory` | `VerificationReport.kt` | CVar categorization enum (18 categories) |

---

## Services

### AdbConnectionService (`AdbConnectionService.kt`)
Foreground service. Keeps ADB TCP connection alive with a persistent notification. Notification channel: `adb_connection`.

### GachaPollService (`GachaPollService.kt`)
Foreground service. Polls Client.log every 10s (max 30 attempts) for a new Convene URL. When found, fetches gacha history via GachaApi and broadcasts result. Notification channel: `gacha_poll`.

---

## Dependencies (`build.gradle.kts`)

| Library | Version | Used for |
|---------|---------|----------|
| Compose BOM | 2024.02 | UI framework |
| Navigation Compose | 2.7.7 | Screen routing |
| Lifecycle ViewModel Compose | 2.7.0 | ViewModel integration |
| OkHttp | 4.12.0 | Gacha API HTTP calls |
| Shizuku API | 13.1.5 | System-level shell access |
| DocumentFile | 1.0.1 | SAF file operations |
| Gson | 2.10.1 | JSON serialization (backups, gacha, profile) |
| Coil Compose | 2.6.0 | Background image loading |
| Media3 ExoPlayer | 1.3.1 | Background video playback |

---

## State Management

- No DI framework — `WuWaConfigApp.instance` is the global access point
- ViewModel: `MainViewModel` (`MainViewModel.kt`, ~980 lines) — single ViewModel for all screens
- Mutable state: `MutableStateFlow` / `StateFlow` exposed as backing fields + collected in composables
- `customDeploySuccess: StateFlow<String?>` — set after successful custom config deploy, observed by HomeScreen to show success popup. Cleared via `clearCustomDeploySuccess()`.
- Log system: `LogRepository` singleton with `StateFlow<List<LogEntry>>`
- Background state: `WuWaConfigApp.backgroundImageUri`, `.backgroundVideoUri`, `.backgroundOpacity` — `MutableStateFlow` for live background customization

---

## File Storage

| Path | Contents |
|------|----------|
| `Downloads/WuWaConfig/` | Public user-facing files (Client.log, log snapshots) |
| `Downloads/WuWaConfig/Backups/{name}/` | Backup INI files |
| `Downloads/WuWaConfig/logs/app.log` | Rotating app log (5MB, 2 rotations) |
| `{app.filesDir}/backups/` | Internal backup JSON manifests |
| `{app.filesDir}/adbkey` / `adbkey.pub` | RSA key pair for ADB auth |
| `{app.cacheDir}/staging/` | Temp INI files before push |

---

## Preset Profiles Quick Reference

Quick score-bucket → preset mapping (bucket edges match the detailed table in [SmartBrain](#smartbrain-smartbrainkt) — see that section for the full scoring algorithm and exact conditions):

- **0-20**: Potato
- **20-40**: Performance
- **40-70**: Balanced
- **70-80**: High (flagship/high GPU required)
- **80+**: Ultra (flagship GPU + Vulkan + non-4K required)

**Overrides regardless of score:**
- GPU OOM ≥ 2 → forced Potato
- `isLowMem` device flag → forced Potato

For the full tier table (Screen%, Shadow, MipBias, etc. per preset), see [Presets (5 tiers)](#configgenerator-configgeneratorkt) under ConfigGenerator.

---

## Color System

Defined in `Color.kt` with neon theme:

| Color | Value | Usage |
|-------|-------|-------|
| NeonPurple | `#B388FF` | Primary |
| NeonCyan | `#00E5FF` | Secondary |
| NeonPink | `#FF4081` | Tertiary |
| NeonGreen | `#00E676` | Success |
| NeonRed | `#FF1744` | Error |
| NeonAmber | `#FFAB00` | Warning |
| DarkBg | `#0A0A1A` | Background (dark) |
| DarkSurface | `#12122A` | Surface (dark) |

---

## Component Library (`ui/components/Components.kt`)

| Component | Description |
|-----------|-------------|
| `GradientBackground` | Full-screen background with optional image/video overlay + gradient |
| `GlassCard` | Semi-transparent card with accent border and gradient |
| `GlassCardHeader` | Accent dot + title row |
| `GlassButton` | Accent-colored button with subtle gradient |
| `GlassOutlinedButton` | Bordered outline button |
| `BackendStatusCard` | Connection status indicator with switch chip |
| `LogViewer` | Scrollable log display with save button |
| `MiniLogViewer` | Compact last-5-entries view |
| `GlitchText` | Animated cycling title with glitch/scramble effect |
| `VideoBackground` | ExoPlayer-based looping video background |

---

## Game Paths (`GamePaths.kt`)

```
/storage/emulated/0/Android/data/com.kurogame.wutheringwaves.global/
  files/UE4Game/Client/Client/Saved/
    Config/Android/          ← config files target
      Engine.ini
      DeviceProfiles.ini
      GameUserSettings.ini
      Scalability.ini
      Hardware.ini
    Config/Kuro/
      KuroConfigMonitor.hash  ← MD5 manifest
    Logs/
      Client.log              ← XOR-encrypted telemetry
    LocalStorage/
      LocalStorage.db         ← player data
    DeviceSaved/
      DeviceStorage.db        ← device settings
    SaveGames/
      KURO_PLAYER_PREFS.sav   ← login device ID
```
