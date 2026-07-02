# WuWaConfig — User Manual

**Version 1.0.5+** · Android · Wuthering Waves Config Toolkit

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Installation & First Run](#2-installation--first-run)
3. [Access Methods — How to Connect](#3-access-methods--how-to-connect)
4. [Home Screen](#4-home-screen)
5. [Config Generator](#5-config-generator)
6. [Backup & Restore](#6-backup--restore)
7. [Settings](#7-settings)
8. [Pity Tracker](#8-pity-tracker)
9. [Player Profile](#9-player-profile)
10. [Battle Stats](#10-battle-stats)
11. [Log Viewer](#11-log-viewer)
12. [Deploy History](#12-deploy-history)
13. [Config Generation Deep Dive](#13-config-generation-deep-dive)
14. [SmartBrain Scoring](#14-smartbrain-scoring)
15. [CVar System](#15-cvar-system)
16. [FAQ & Troubleshooting](#16-faq--troubleshooting)

---

## 1. Introduction

WuWaConfig is an Android tool that reads, optimizes, and deploys configuration files for **Wuthering Waves** (global version, `com.kurogame.wutheringwaves.global`). It works with the 5 INI files the game uses:

| File | Purpose |
|------|---------|
| `Engine.ini` | CVar settings (graphics, performance, shadows, anti-aliasing, etc.) |
| `DeviceProfiles.ini` | Device-specific GPU profiles and per-device CVars |
| `GameUserSettings.ini` | Resolution, VSync, FPS cap, scalability groups |
| `Scalability.ini` | Resolution quality, view distance, shadow/texture/effects quality |
| `Hardware.ini` | Hardware profile binding, frame pacing, Anisotropy, mip bias |

**What it can do:**

- Analyze your device's `Client.log` to extract GPU, RAM, FPS, thermal data, OOM events
- Score your device with SmartBrain (0-100 scale) and recommend the optimal preset
- Generate optimized INI configs from 5 presets (Potato→Ultra) or per-device tuning
- Deploy configs via ADB, Shizuku, Root, or SAF
- Backup configs before applying, with per-file selection
- Compare performance before/after with Deploy History
- Track gacha pity, view player profile, read battle stats from Client.log

**Package:** `com.wuwaconfig.app`  
**Min SDK:** 26 · **Target SDK:** 34  
**Language:** Kotlin · **UI:** Jetpack Compose + Material 3

---

## 2. Installation & First Run

### 2.1 Installation

1. Download the latest APK from the [Releases page](https://github.com/B3rr7/WuWa-Config-Android/releases)
2. Enable **Install from unknown sources** on your device
3. Open the APK and install

### 2.2 First Launch

1. Open the app
2. Read and accept the **Terms of Use**
3. Grant **Storage permission** when prompted (needed for backups and log saving)
4. The Setup screen shows the game config directory path — tap to proceed to Home

### 2.3 Requirements

- **Wuthering Waves** must be installed on the same device
- One of these connection methods: ADB Wireless Debugging, Shizuku, Root, or SAF
- Android 11+ recommended (Android 8.0+ minimum)

---

## 3. Access Methods — How to Connect

The app needs to read/write game config files at:
```
/storage/emulated/0/Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android/
```

Choose your connection method from the Home screen by tapping the method chip (cycles ADB → Shizuku → Root → SAF).

### 3.1 ADB (Wireless Debugging) — Recommended

**Best for:** Non-rooted users. The app implements the ADB wire protocol directly — no external ADB binary needed.

**Setup:**
1. Go to **Developer Options** on your phone
2. Enable **Wireless Debugging**
3. Tap **Wireless Debugging** → **Pair device with pairing code** (if first time)
4. Back in the app, tap **Connect** — it auto-scans ports 37000–44000
5. Accept the RSA fingerprint prompt on your phone

**Troubleshooting:**
- On Android 16+, you may need to re-pair after reboot
- The app auto-regenerates RSA keys if connection is rejected
- If auto-scan fails, tap the ADB chip and enter IP:port manually

### 3.2 Shizuku

**Best for:** Non-rooted users with Shizuku installed. Faster than ADB.

**Setup:**
1. Install [Shizuku](https://shizuku.rikka.app/) from the Play Store
2. Start the Shizuku service (follow Shizuku's in-app instructions)
3. In WuWaConfig, cycle to **Shizuku** mode
4. Tap **Permit** → **Connect**

### 3.3 Root

**Best for:** Rooted devices (Magisk, KernelSU, APatch).

**Setup:**
1. Cycle to **Root** mode
2. Tap **Test Root** — grant superuser access in your root manager
3. Tap **Connect**

### 3.4 SAF (Storage Access Framework)

**Best for:** Quick one-off edits. Limited — no shell access, so config generation and some features are unavailable.

**Setup:**
1. Cycle to **SAF** mode
2. Tap **Pick Dir**
3. Navigate to the game's Config/Android folder
4. Tap **Allow**

### 3.5 Connection Limits

| Method | Shell Access | File Push | Log Reading | Config Gen |
|--------|:---:|:---:|:---:|:---:|
| ADB | ✓ | ✓ | ✓ | ✓ |
| Shizuku | ✓ | ✓ | ✓ | ✓ |
| Root | ✓ | ✓ | ✓ | ✓ |
| SAF | ✗ | ✓ | Limited | ✗ |

---

## 4. Home Screen

The main dashboard with all key actions.

### 4.1 Backend Status Card

Shows the current connection method and state:
- **Chip:** Tap to cycle methods (ADB → Shizuku → Root → SAF → ADB)
- **Connect/Disconnect button**
- **State indicator:** Connected (green) or Disconnected (gray)

**Manual ADB (when ADB selected):**  
Enter IP:port if auto-scan fails.

### 4.2 Custom Config

Upload your own `.ini` files to apply:
1. Tap **Select Custom Configs** — opens the system file picker
2. Pick one or more `.ini` files (must match: Engine.ini, DeviceProfiles.ini, etc.)
3. Review screen shows matched files and skipped ones
4. If files < 5, a **Backup Scope** dialog asks:
   - **Back Up All 5 INIs** — saves all current game configs before overwriting
   - **Only Overwritten** — saves only the files being replaced
5. Tap **Apply** — files are pushed to the device
6. A green **success popup** confirms which files were deployed and that the hash was refreshed

**Auto-backup setting:** Toggle in Settings → Generation & History.

### 4.3 Actions

- **Delete Config Files** — removes all 5 INI files from the game directory
- **Collect Client.log** — reads and saves the game's encrypted log to `Downloads/WuWaConfig/Client.log`
- **Config Generator** — navigate to the full config generation UI
- **Backups** — manage saved config snapshots
- **Pity Tracker** — fetch gacha history
- **Profile** — view player data
- **Battle Stats** — combat telemetry
- **App Log** — open the full log viewer

### 4.4 Recent Log

Shows the last 5 log entries. Tap to open the full Log Viewer.

### 4.5 Deploy History Card

If enabled in Settings, shows the latest deploy with:
- Preset name and timestamp
- Comparison status (compared / not yet compared)
- Tap to open the full History screen

---

## 5. Config Generator

The core feature — analyze your device, tune settings, and deploy optimized configs.

### 5.1 Overview

Access from Home screen → **Config Generator**.

The generator flow:
1. **Analyze** — read Client.log or import a saved log
2. **Review analysis** — see GPU, RAM, FPS, thermal events, OOMs, active CVars
3. **SmartBrain** scores your device and recommends a preset
4. **Tune** — select preset, options, game mode, and which files to generate
5. **Generate** — produces INI files with auto-optimization
6. **Review** — inspect generated text, edit CVar overrides
7. **Deploy** — push to device, verify with KuroConfigMonitor hash refresh

### 5.2 Screen Walkthrough

#### Analysis Panel (top)

- **Analyze Device** — reads Client.log from device (chunked with gzip, even for 100MB+ files). Shows progress with animated glitch effect.
- **Import Log** — pick a saved `.log` file for offline analysis
- **Results:** device model, GPU, API (Vulkan/OpenGL), Android version, RAM, FPS (capped + actual), resolution, render scale, shadow quality, thermal events, texture errors, GPU OOMs, frame drops, network errors, forbidden CVars, active CVars list

#### Verification Badge

After a deploy, shows:
- **Accepted CVars** (green) — recognized by the game engine
- **Rejected CVars** (red) — not recognized
- **N redundant** (green chip) — CVars matching game defaults
- **N unknown** (amber chip) — CVars not in the UE4 binary dump
- **N monitored** (blue chip) — CVars tracked by KuroConfigMonitor

#### Preset Selector

Choose a base preset:

| Preset | Screen % | For |
|--------|----------|-----|
| Potato | 50% | Dead low-end, 4GB RAM or less |
| Performance | 60% | Stability first, mid-range |
| Balanced | 80% | Daily default, most devices |
| High | 100% | Sharper visuals, flagship GPUs |
| Ultra | 100% | Flagship devices, best visuals |

#### Tuning Card

Two toggles:

- **Advanced per-device tuning** — when ON, replaces the fixed preset lookup with `CvarOptimizer` which computes each field (screen%, shadow quality, SSR, mip bias, etc.) directly from your device's GPU tier, RAM, thermal events, and OOM counts. The preset selector above is ignored when this is ON. Recommended for users who want a truly custom profile.

- **CVar optimization (comment out defaults)** — when ON (default), CVars matching the game's factory defaults get commented out with `; REDUNDANT` and unknown CVars get flagged `; UNKNOWN CVar`. This makes the INI cleaner without changing behavior. Turn OFF if you want every CVar written explicitly.

#### Frame Target

Select target FPS: **30 · 45 · 60 · 90 · 120**

#### Options

Toggle options (each adds or removes the corresponding CVars from the generated config):

| Option | What it does |
|--------|-------------|
| 120 FPS unlock | Enables 120fps cap (device/display must support) |
| Ultra quality unlock | Enables highest quality settings |
| VSync | Synchronizes frame pacing with display refresh |
| Auto cooling | Thermal management CVars |
| Force Vulkan safety CVars | Vulkan-specific stability CVars |
| HZB occlusion | Hierarchical Z-buffer occlusion culling |
| Disable fog | Removes volumetric fog |
| Disable chromatic aberration | Removes CA effect |
| Disable toon outlines | Removes character outlines |
| Disable radial blur | Removes radial blur effect |
| Disable bloom | Removes bloom lighting |
| Disable auto exposure | Removes auto eye adaptation |
| Disable SSR/reflections | Disables screen-space reflections |
| Allow restricted CVars | SmartBrain won't penalize forbidden CVars |

#### Game Mode

- **Overworld** — the open world (default)
- **Domain / Tower** — instanced content (different CVar tuning)

#### Files to Generate

Choose which files to generate. Engine.ini and DeviceProfiles.ini are recommended. Scalability.ini and Hardware.ini are optional.

| File | Default |
|------|:------:|
| Engine.ini | ✓ ON |
| DeviceProfiles.ini | ✓ ON |
| GameUserSettings.ini | ✓ ON |
| Scalability.ini | ✗ OFF |
| Hardware.ini | ✗ OFF |

#### Benchmark Tuner

Auto-tune wizard that **preserves state across app restarts**:

1. **Tap Auto-Tune** → deploy current preset with your options
2. **Go Play dialog** appears → play the game for ~30 seconds to generate FPS data in logcat
3. **Tap Continue** → the tuner reads logcat, parses FPS, analyzes performance
4. If FPS ≥ target → preset stays or bumps up (if >15% headroom). If FPS < 85% of target → preset drops down. Options also adjust: disable SSR/disable bloom/disable radial blur/lower shadows based on FPS gap size.
5. Next round deploys the adjusted preset. Up to 5 rounds total.

**Survives app close** — state saved to `{filesDir}/benchmark_tuner_state.json`:
- Close the app mid-tuner (e.g., to play the game) → reopen → tuner resumes at Go Play dialog
- Results persist until you Dismiss
- Cancel Tuner clears state

### 5.3 Generating & Deploying

1. Configure all options
2. Tap **Generate** — creates all selected INI files
3. **Review screen** shows the generated text in a monospace editor:
   - Scroll through each file tab (Engine, DeviceProfiles, etc.)
   - Tap **Edit Overrides** to add custom `key=value` pairs
4. Tap **Deploy to Device**:
   - Reads current Engine.ini from device (extracts `[Core.System]` paths)
   - Regenerates with edits
   - Pushes files via backend (base64 chunk + `printf`/base64 decode/MD5 verify)
   - Refreshes `KuroConfigMonitor.hash` with new MD5 hashes
   - Sets `customDeploySuccess` state → green popup confirms

5. **Verify** — the app pulls Client.log again and cross-references deployed CVars against game-recognized CVars, showing accept/reject badges

---

## 6. Backup & Restore

Access from Home screen → **Backups**.

### 6.1 Creating a Backup

1. Tap **Create Backup**
2. Enter a **Backup name**
3. **Select which INI files to include** — all 5 are checked by default, uncheck any you don't want
4. Tap **Create**
5. The app reads selected files from the device and saves:
   - Internal JSON manifest (`{app.filesDir}/backups/{id}.json`)
   - Public INI copies (`Downloads/WuWaConfig/Backups/{name}/*.ini`)

### 6.2 Restoring a Backup

1. Find the backup in the list
2. Tap **Restore**
3. A dialog shows the backup's files as checkboxes — all checked by default
4. Uncheck any files you don't want to restore
5. Tap **Restore** — only checked files are pushed to the device
6. Config hashes are refreshed after restore

### 6.3 Auto-Backup (Custom Config)

When applying custom configs and `backup_before_apply` is enabled in Settings:
- If you upload fewer than 5 files, a **Backup Scope** dialog asks:
  - **Back Up All 5 INIs** — saves all 5 config files
  - **Only Overwritten** — saves only the files being replaced
- If all 5 files are uploaded, backup proceeds with all 5 (no dialog)

### 6.4 Deleting a Backup

Tap **Delete** on any backup card — removes both the internal JSON and the public INI folder.

---

## 7. Settings

Access from the gear icon (top-right on Home screen).

### 7.1 Access Method

Shows current method and connection state. Switch between ADB, Shizuku, Root, and SAF.

### 7.2 Theme

| Option | Description |
|--------|-------------|
| System | Follows device theme |
| Dark | Dark mode (default) |
| Light | Light mode |

### 7.3 Custom Background

Set a background image or video for the app:

- **Image** — pick a JPG, PNG, or GIF from your device
- **Video** — pick an MP4 video
- **Opacity slider** — adjusts the background visibility (5%-70%). For video backgrounds, this overlays a black layer with inverse opacity since ExoPlayer's SurfaceView doesn't support Compose alpha compositing.
- **Remove** — clears the custom background

### 7.4 Generation & History

| Setting | Default | Description |
|---------|:-------:|-------------|
| Backup before apply | ON | Automatically back up config before custom config deploy |
| Deploy History | ON | Track deployment baseline and compare outcomes |

---

## 8. Pity Tracker

Access from Home screen → **Pity Tracker**.

Fetches your gacha pull history from Kuro's servers using the Convene URL from Client.log.

### 8.1 Usage

1. **Open the Convene History** in-game at least once (this generates the URL in Client.log)
2. In the app, tap **Fetch Gacha History**
3. The app reads Client.log, finds the Convene URL, and fetches data from Kuro's API
4. Results are cached locally for 12 hours

### 8.2 Display

- **Summary:** total pulls, ★5 count, ★4 count, average pity for ★5 and ★4
- **Per-pool breakdown:** pulls per banner type (Character, Weapon, Standard, Beginner)
- **Pity Predictions:**
  - Status: Guaranteed / 50/50 / 75/25
  - Last ★5 name and time
  - Pulls since last ★5, pulls until hard pity
  - Estimated next ★5 (average estimate)
  - Soft pity warning (≥66 pulls for ★5, ≥57 for ★4)
  - ★4 tracking

### 8.3 Background Polling

If the URL isn't found yet, tap **Poll in Background** to start a foreground service that polls Client.log every 10 seconds (up to 30 attempts). Shows a notification when found.

---

## 9. Player Profile

Access from Home screen → **Profile**.

Read-only view of your game data — no files are modified.

**Data extracted from:** `LocalStorage.db`, `DeviceStorage.db`, and `.sav` files via SQLite queries and strings extraction.

### 9.1 What You See

- **UID, Server, Player Level** — header display
- **Game Progress:**
  - Tower floor cleared
  - Weekly Rogue score
  - Battle Pass status (Free/Purchased)
  - Server accounts (multiple regions)
- **Game Info:**
  - Version, Patch version, Launcher version
  - Language, Last login time
- **Config Summary:**
  - Bar chart showing setting counts per config file (Engine.ini, DeviceProfiles.ini, GameUserSettings.ini)

### 9.2 Caching

Profile data is cached locally. Auto-shown on revisit; tap **Refresh** to re-fetch from device.

---

## 10. Battle Stats

Access from Home screen → **Battle Stats**.

Parses `Client.log` for Chinese-language combat telemetry.

**Note:** Data is cumulative across all game sessions. Requires actual gameplay to show non-zero values.

### 10.1 Sections

| Section | Stats |
|---------|-------|
| **Header** | Total battles, deaths, staggers, stamina used |
| **Combat** | Echoes collected, deaths, staggers, stamina |
| **Dodge** | Forward dodges, back dodges, counter dodges |
| **Movement** | Teleports, role changes |
| **Echo Skills** | Skills used, transforms |
| **Other** | Monthly cards claimed, log file size |

---

## 11. Log Viewer

Access from Home screen → **App Log**, or tap the Recent Log card.

Full-screen log viewer with:

- **Real-time entries** — all app operations are logged with timestamps
- **Search bar** — filter log text
- **Level filter chips** — All / INFO / SUCCESS / WARNING / ERROR
- **Entry count** — shows how many entries match the current filter
- **Auto-scroll** — follows new entries to the bottom
- **Save** — exports the current log to `Downloads/WuWaConfig/WuWaConfig_yyyy-MM-dd_HH-mm-ss.txt`

### 11.1 Log Levels

| Level | Color | Meaning |
|-------|-------|---------|
| INFO | Default | Normal operation info |
| SUCCESS | Green | Successful operations |
| WARNING | Amber | Non-critical issues |
| ERROR | Red | Failed operations |

---

## 12. Deploy History

Access from Home screen → **Deploy History card** or from History screen.

Tracks each config deployment and lets you compare outcomes after gameplay.

### 12.1 How It Works

1. **Baseline** — when you deploy a config (via Config Generator), the app saves a `DeployRecord` with the current LogInfo (preset name, CVar counts, redundant/unknown/monitored tags, timestamp)
2. **Compare** — later, go to History screen and tap **Compare Now** on a record
3. The app reads a fresh Client.log and computes Δ vs the baseline:
   - FPS change
   - Thermal events change
   - OOM count change
   - Frame drop change
4. Results shown with color coding: green = improvement, red = regression
5. If the record was generated with **Advanced per-device tuning** (has an `OptimizedProfile`), a **Retune & Deploy (Auto-Adjust)** button appears after comparison:
   - **Stable** (±5 FPS, 0 OOM, ≤2 thermal, ≤5 drops) → no change
   - **OOM increased** → force potato-level minimums
   - **Degraded** (FPS↓ >5, thermal↑ >2, drops↑ >5) → screen×0.75, shadow-2, detail-1, SSR-1, viewDistance×0.6
   - **Improved** (FPS↑ >5, no regressions) → screen×1.15, shadow+1, detail+1, SSR+1
   - Generates new config with adjusted profile, deploys, saves new record — repeat until stable

### 12.2 Settings

Toggle in **Settings → Generation & History → Deploy History**. Persisted to `deploy_history` preference key. Default: ON.

---

## 13. Config Generation Deep Dive

### 13.1 Engine.ini

~300 lines of CVars organized into 18 sections:

| Section | CVars |
|---------|-------|
| Character Quality | LOD, shadow, draw distance |
| Scalability | sg.* values derived from preset |
| Anti-Aliasing | TAA parameters, temporal upsampling |
| Post Processing | Bloom, DoF, CA, auto-exposure, AO |
| Shadow | CSM cascades, shadow resolution, distance |
| Texture Streaming | MipBias, Aniso, pool size |
| Mobile Rendering | MSAA, HBAO, FSR, PPR |
| VRS | Variable Rate Shading, material/mesh VRS |
| Effects / Particles | Niagara quality, spawn rates |
| Water / Reflection | SSR, planar reflection |
| Screen-Space Effects | SSGI, SSS, SSFS |
| Environment | Fog, clouds, foliage |
| NPC & World | Culling distances, LOD |
| Advanced LOD | Screen radius, distance scaling |
| Animation & BP | URO, animation rates |
| Frame & Display | VSync, FramePace, FrameGen |
| Pipeline / RHI | PSO cache, RHI command |
| Thermal & Stability | Auto-cool, thermal control |
| Forbidden CVar guards | FSR RCAS=0, SSAO=0 |
| Performance tweaks | Potato/perf: HZB occlusion, reflection=0, dynamic lights=0, shadow min |

### 13.2 DeviceProfiles.ini

Detects your SoC and applies device-specific profiles:

- 40+ SoC profiles from Adreno 830 down to Mali-G57
- Fallback: universal profiles (Android_Low/Mid/VeryHigh/Ultra) based on preset
- Profile includes: device evaluation tier, texture LOD bias, character draw distances, imposter scale, ISM distances, foliage cull distances, FPS unlock CVars

### 13.3 GameUserSettings.ini

- `[ScalabilityGroups]` — sg.* values (ResolutionQuality, ViewDistanceQuality, AntiAliasingQuality, ShadowQuality, PostProcessQuality, TextureQuality, EffectsQuality, FoliageQuality, ShadingQuality, KuroRenderQuality)
- `[/Script/Engine.GameUserSettings]` — VSync, resolution, fullscreen, FrameRateLimit, HDR
- `[Internationalization]` — Culture=en

### 13.4 Scalability.ini & Hardware.ini

Optional files (OFF by default):
- **Scalability.ini:** ResolutionQuality, View/Anti/Shadow/Post/Texture/Effects/Foliage/Shading/KuroRender quality levels
- **Hardware.ini:** DeviceProfileName, FramePace, Aniso, MipBias, foliage.LODDistance

### 13.5 Core.System Paths

Before generation, the app reads the existing Engine.ini from the device and extracts `[Core.System]` paths. These ~40 known content paths (from the binary) are injected into the generated config, preserving the game's content mount configuration. If no existing config exists, the built-in defaults are used.

### 13.6 Post-Processing

After generation, the following passes run on Engine.ini:

1. **Import from log** — CVars from Client.log not already in the preset are added with `; IMPORTED FROM Client.log` flag
2. **CVar overrides** — user's key=value overrides are applied
3. **CVar optimization** (if enabled) — redundant CVars matching defaults are commented out (`; REDUNDANT`), unknown CVars are flagged (`; UNKNOWN CVar`)
4. **Deduplication** — duplicate CVar keys are removed, keeping the last occurrence
5. **Forbidden CVar stripping** — 33 known Kuro-restricted CVars are removed

### 13.7 Advanced Per-Device Tuning

When **Advanced per-device tuning** is ON in the Tuning card, the fixed preset lookup is replaced by the `CvarOptimizer` engine:

- **GPU tier detection** — same regex patterns as SmartBrain (flagship/high/mid_high/mid/mid_low/low/unknown)
- **Per-field rules** — each profile field (screen%, shadow, SSR, mipbias, streaming, view distance, foliage LOD, detail, LOD bias, grass cull) is computed independently based on GPU tier, RAM, thermal events, OOM count, texture errors
- **Hard-limited detection** — OOM ≥ 1 OR (thermal ≥ 3 AND low-tier GPU) forces minimum settings
- **Constrained detection** — thermal ≥ 3, texture errors ≥ 5, or RAM < 6GB reduces settings by 1-2 tiers

The result is converted to a `PresetProfile` so all existing builder functions work without changes.

**Self-tuning feedback loop** — when you deploy with Advanced Gen ON, the app saves the computed `OptimizedProfile` in the deploy history record. After playing the game:
1. Go to **History** → tap **Compare Now** — pulls fresh Client.log, computes Δs
2. Tap **Retune & Deploy (Auto-Adjust)** — `CvarOptimizer.adjustProfile()` adjusts the profile based on Δs
3. The adjusted profile is injected via `ConfigGenerator.profileOverride` → new config generated → deployed
4. A new deploy record is saved with the adjusted profile — repeat until the comparison shows stable

### 13.8 File Push Mechanism

Files are pushed via the backend using a base64 chunked transfer:

1. Content is base64-encoded
2. Split into `printf '%s'` chunks of ~4000 bytes
3. Written to `/data/local/tmp/wuwaconfig_{timestamp}_{random}.b64`
4. Decoded via `base64 -d` to the target path
5. Temp file deleted
6. MD5 verified with `md5sum` or `wc -c`
7. Retried once on failure

---

## 14. SmartBrain Scoring

SmartBrain analyzes your device from `LogInfo` and scores it from **0 to 100**, then recommends a preset.

### 14.1 Base Score: 50

### 14.2 GPU Tier

| Tier | Examples | Score Modifier |
|------|----------|:--------------:|
| Flagship | Adreno 8xx, Dimensity 93+, Tensor G3+ | **+30** |
| High | Adreno 75x-80x, Dimensity 85+, Mali-G7+ | **+20** |
| Mid-High | Adreno 7xx, Dimensity 73+, Xclipse | **+10** |
| Mid | Adreno 6xx, Mali-G6xx | **±0** |
| Mid-Low | Adreno 5xx, Mali-G5xx | **-10** |
| Low / Unknown | Everything else | **-20** |

### 14.3 Other Factors

| Factor | Condition | Score |
|--------|-----------|:-----:|
| RAM | ≥8GB / 6-8GB / 4-6GB / <4GB | +8 / +5 / 0 / -15 |
| Vulkan | Available | +8 |
| FPS met target | ≥95% of cap | +5 |
| FPS drop 10-20% | | -6 |
| FPS drop 20-30% | | -12 |
| FPS drop >30% | | -18 |
| Actual FPS <30 | | -15 |
| Actual FPS 30-45 | | -8 |
| Thermal events ≥5 / ≥3 / ≥1 | | -20 / -12 / -5 |
| GPU OOM ≥3 / ≥2 / ≥1 | | -30 / -20 / -12 |
| Frame drops ≥15 / ≥5 | | -10 / -5 |
| Low memory device | Flag from game | -15 |
| Forbidden CVars (×5 each) | If restricted | -5 each |
| Network errors ≥10 / ≥5 | | -10 / -5 |
| Render scale ≥125% / ≥110% | | +3 / +5 |
| Render scale <70% | | -10 |
| 4K resolution | And mid/low GPU | -10 + extra |
| Texture errors | Per error (max 20) | -min(errors, 20) |
| Many unknown CVars >5 | | -5 |
| Many monitored CVars >10 | | +3 |
| Well-optimized (>20 differ from default) | | +5 |
| Room to improve (<5 differ) | | -8 |
| High shadows + low GPU | | -6 |
| High shadows + <6GB RAM | | -4 |
| High textures + <6GB RAM | | -5 |
| High textures + texture errors | | -4 |
| Render scale >100% | Overclock penalty | -min(overScale, 8) |
| High FPS + thermal | | -6 |
| SSAO on low GPU | | -5 |

### 14.4 Preset Recommendation

| Score | Conditions | Preset |
|:-----:|-----------|:------:|
| any | GPU OOM ≥ 2 | **Potato** |
| any | Low memory or score ≤ 20 | **Potato** |
| ≥ 80 | Flagship GPU + Vulkan + non-4K | **Ultra** |
| ≥ 75 | Flagship/high GPU + Vulkan | **High** |
| ≥ 70 | Flagship/high GPU | **High** |
| ≥ 40 | Fallthrough | **Balanced** |
| ≥ 20 | Fallthrough | **Performance** |
| < 20 | Fallthrough | **Potato** |

---

## 15. CVar System

### 15.1 What Are CVars?

Console Variables (CVars) are Unreal Engine 4's runtime configuration system. Each CVar is a `key=value` pair that controls a specific engine behavior — shadow quality, texture filtering, LOD distances, post-processing effects, etc.

### 15.2 CVar Database

The app ships with 3 CVar reference files extracted from the game's `libUE4.so` binary:

| File | Entries | Description |
|------|:-------:|-------------|
| `libUE4_cvars.txt` | ~3348 | Every registered CVar in Kuro's UE4.26 fork |
| `config_monitor_cvars.txt` | 569 | CVars tracked by KuroConfigMonitor hash |
| `config_monitor_values.txt` | 568 | Game's default values for monitored CVars |

All 3 files are loaded at runtime by `CvarDatabase` and used for:
- `isKnown()` — checks if a CVar exists in libUE4
- `isMonitored()` — checks if KuroConfigMonitor tracks it
- `gameDefault()` — returns the game's default value
- `differsFromDefault()` — compares a value against the default
- `categorize()` — assigns one of 18 category labels (Character, Environment, Lighting & Shadow, Post Processing, etc.)
- `optimizeIniText()` — comments out redundant/unknown CVars

### 15.3 Forbidden CVars

33 CVars that the game actively strips or ignores at the engine level. These include:

- `r.Streaming.Boost` — texture streaming boost (ignored)
- `r.ScreenPercentage` — render scale (must use `sg.ResolutionQuality` instead)
- `r.AFME.Enable` — AMD FidelityFX (ignored on mobile)
- `r.DLSSG.Enable` — DLSS Frame Gen (NV-only, ignored on mobile)
- `r.FidelityFX.FSR.Enabled` — FSR toggle (never use with VRS)
- And more...

The `stripForbiddenCvars()` function removes matching lines from generated INI text. SmartBrain penalizes forbidden CVars by 5 points each (when restricted).

### 15.4 CVar Priority Chain

```
SetBySystemSettingsIni > SetByDeviceProfile > SetByProjectSetting > SetByScalability
```

Engine.ini `[SystemSettings]` always wins over DeviceProfiles.ini or Scalability.ini.

---

## 16. FAQ & Troubleshooting

### 16.1 Common Issues

**"Cannot connect via ADB"**
- Ensure Wireless Debugging is enabled in Developer Options
- Check that your phone and computer (if using ADB from PC) are on the same network
- Try restarting Wireless Debugging (toggle off/on)
- The app auto-scans ports 37000-44000 — if your device uses a different range, enter the port manually

**"Connection failed: process hasn't exited" (Shizuku)**
- This is a known Shizuku race condition. The app retries automatically after 5 seconds. If it persists, restart the Shizuku service.

**"Client.log too large"**
- The app now handles 100MB+ log files by sampling (up to 30 chunks of 1MB each with gzip compression). If you see this error, ensure your backend supports the `dd` command (SAF does not).

**"No Convene URL found"**
- Open the Convene History in-game first, wait a moment, then try again
- The app polls up to 6 times with 10-second delays
- Use **Poll in Background** to let it find the URL while the app is minimized

**"Config deploy failed"**
- Check that the game directory exists on the device
- Ensure your connection method has write access (ADB/Shizuku/Root)
- SAF mode may not have permission to overwrite files — try a different method

**"Video background opacity doesn't work"**
- This is a known limitation: ExoPlayer uses a `SurfaceView` which renders in a separate hardware layer. The app works around this by overlaying a black box with inverse opacity on top of the video.

### 16.2 File Locations

| Path | Contents |
|------|----------|
| `Downloads/WuWaConfig/` | Saved Client.log, log snapshots |
| `Downloads/WuWaConfig/Backups/{name}/` | Backup INI files (public copies) |
| `Downloads/WuWaConfig/logs/app.log` | Rotating app log (5MB, 2 rotations) |
| `{app.filesDir}/backups/` | Internal backup JSON manifests |
| `{app.filesDir}/adbkey` / `adbkey.pub` | RSA key pair for ADB auth |
| `{app.cacheDir}/staging/` | Temp INI files before push |

### 16.3 Client.log Decryption (Technical)

The game's `Client.log` is XOR-encrypted. The decryption uses a byte-value LUT:

```
LUT[i] = i xor (0xA5 if i%2==1 else 0xEF)
```

The log starts with a 3-byte header `00 54 50` (`\0TP`). Decryption:
1. Strip the 3-byte header
2. Apply LUT to each remaining byte
3. Detect encoding: UTF-16BE (with BOM), UTF-16LE (with BOM), or UTF-8

### 16.4 Log Reading for Large Files

For log files >1MB, the app uses adaptive chunking:
- **1 chunk per 5MB** of file size, min 5 chunks, max 30 chunks
- Each chunk is 1MB, read via `dd bs=1 skip=N count=1M`
- Compressed with `gzip -c` before base64 transfer
- Decompressed on-device with `GZIPInputStream`

This provides ~20% coverage of a 100MB file with representative event sampling, while keeping wire transfer to ~6MB.

### 16.5 Data Safety

The app **never** sends data off-device. All operations are local:
- Log analysis stays on your phone
- Config generation is entirely local
- Gacha fetching goes directly from your device to Kuro's API
- No telemetry, no analytics, no internet except gacha API calls

### 16.6 Getting Help

- [GitHub Issues](https://github.com/B3rr7/WuWa-Config-Android/issues)
- [Telegram](https://t.me/Yt_Player42)
- [Discord](https://discord.gg/5WP9nN2e2s)
- [YouTube (@Player42_g)](https://www.youtube.com/@Player42_g)
