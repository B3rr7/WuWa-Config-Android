<!-- wuthering waves config, wuwa fps boost, engine.ini optimization, unreal engine 4 mobile config, kuro games optimization, android game booster, mobile game config editor, gacha pity tracker, battle stats analyzer, fps unlock 120fps, graphics tuning, device profile config, scalability ini, hardware ini, gameusersettings ini, config generator android, wuthering waves android optimization, wuwa mobile config, wuwa lag fix android, wuthering waves stuttering fix, wuwa engine ini tweak, deviceprofiles ini tutorial, increase wuwa fps, mobile gaming performance, vulkan shader cache optimization, wuthering waves 120fps, fps drop fix mobile, thermal throttling solution, kurogames config tool, snapdragon gaming optimization, adreno gpu tuning, mali gpu optimization, low end device booster, smartbrain scoring, configmonitor hash, cvar editor android, auto tune wizard, battle statistics, wuthering waves benchmark, wuwa config generator, android game booster wuwa, wuthering waves android optimization, wuthering waves 3.4 cyberpunk, wuwa version 3.4 config, kuro config monitor, wuwa graphics preset, mobile unreal engine tweaks, wuthering waves performance 2026, android data folder access, shizuku game config, adb wireless debugging game, wuwa startup config, engine ini mobile, gameusersettings fps unlock, wuthering waves low memory fix, wuwa texture streaming, cvar optimization android, wuwa config for poco, wuwa config for redmi, wuwa config for xiaomi, wuwa config for samsung, wuwa config for oneplus, wuwa config for realme, wuwa config for vivo, wuwa config for oppo, wuwa config for honor, wuwa config for huawei, wuwa config for nothing phone, wuwa config for motorola, wuwa config for asus rog, wuwa config for lenovo, wuwa poco x6 pro config, wuwa poco x5 pro config, wuwa poco f5 config, wuwa poco f6 config, wuwa redmi note 12 config, wuwa redmi note 13 config, wuwa samsung s23 config, wuwa samsung s24 config, wuwa oneplus 12 config, wuwa realme gt config, wuwa vivo x100 config, poco wuthering waves optimization, redmi wuthering waves performance, xiaomi wuwa fps boost, samsung galaxy wuwa config, galaxy s24 wuthering waves settings, poco f5 wuthering waves engine ini, poco f6 wuthering waves graphics, redmi note 13 wuthering waves lag fix, wuwa optimization snapdragon 8 gen 2, wuwa optimization snapdragon 8 gen 3, wuwa optimization dimensity 8300, wuwa optimization dimensity 9200, wuwa optimization kirin 9000 -->
# WuWaConfig — Wuthering Waves (WuWa) Config Toolkit & FPS Booster for Android

[![Release](https://img.shields.io/github/v/release/B3rr7/WuWa-Config-Android?label=Download&color=purple)](https://github.com/B3rr7/WuWa-Config-Android/releases)

WuWaConfig is a free **Android app to boost Wuthering Waves FPS and tune graphics**. It analyzes your device `Client.log`, generates optimized **Engine.ini**, **Scalability.ini**, **GameUserSettings.ini**, **DeviceProfiles.ini**, and **Hardware.ini** configs, and deploys them via ADB (wireless debugging), Shizuku, Root, or SAF. Features include a **CVar editor**, **SmartBrain** device scoring (0-100), 8 performance presets (Potato → Cinematic), **gacha pity tracker**, **battle stats** analyzer, and an **Auto-Tune Wizard**. Works on Snapdragon/Adreno, MediaTek Dimensity/Mali, Exynos, and Tensor phones — from low-end to flagship. 🔒 **Privacy-first:** no analytics, no telemetry, no data sent to third parties. Only connects to localhost ADB and Kuro's official gacha API (user-initiated).

> **⚠️ DISCLAIMER**
> This project is **NOT affiliated with Kuro Games or Wuthering Waves**.
> It is a fan-made tool for editing game configuration files.
> Modifying game files may be subject to the game's Terms of Service.
> **Use at your own risk.** The creator is not responsible for any account actions, bans, or issues that may arise.

> **📱 PLATFORM**
> WuWaConfig is an **Android-only app** (Android 8.0+). It is **not available for iPhone or iPad** — iOS cannot install APKs and lacks the Android-only access backends (ADB / Shizuku / Root / SAF) the app relies on. It also cannot run on Windows, macOS, or Linux as a native app; use an Android device or emulator. The landing page and in-app User Guide are web pages viewable on any platform, but the config tool itself requires Android.

---

## Table of Contents

1. [Installation & First Run](#installation--first-run)
2. [Access Methods](#access-methods-how-to-connect)
3. [Screens](#screens)
   - [Home](#home)
   - [Config Generator](#config-generator)
   - [Pity Tracker](#pity-tracker)
   - [Player Profile](#player-profile)
   - [Battle Stats](#battle-stats)
   - [Backup & Restore](#backup--restore)
   - [Settings](#settings)
4. [Project Structure](#project-structure)
5. [Tech Stack](#tech-stack)
6. [Links](#links)

---

## Installation & First Run

1. **Download** the latest APK from [Releases](https://github.com/B3rr7/WuWa-Config-Android/releases).
2. **Install** on your Android device (enable "Install from unknown sources").
3. **Open** the app — accept Terms of Use.
4. **Grant storage permission** when prompted (for backups).
5. **Connect** using one of the 4 access methods below.

---

## Access Methods (How to Connect)

The app needs to read/write game config files in `Android/data/com.kurogame.wutheringwaves.global/`.

### 🔧 ADB (In-App Wireless Debugging)
**Best for:** Non-rooted users. App implements ADB wire protocol directly — connects to localhost ADB daemon.

**Setup:**

**Option A — In-app auto-connect (no PC needed):**
- Enable **Wireless Debugging** in Developer Options
- Tap **Connect** in app — auto-scans ports 37000-44000
- Accept RSA fingerprint on phone

**Option B — PC commands:**

*Requirements: USB Debugging ON in Developer Options, phone connected via USB.*

**Enable ADB:**
```bash
adb tcpip 5555                  # switch to TCP mode (USB connected)
adb connect 192.168.x.x:5555    # your device ip:port number
```

**Disable ADB:**
```bash
adb disconnect 192.168.x.x:5555  # close wireless session, your device ip:port number
adb usb                          # switch back to USB-only
```
If you have multiple devices (USB + wireless): `adb -s 192.168.x.x:5555 usb`

**No PC?** Just **reboot your phone** — ADB TCP mode is cleared on boot. The app's **Disconnect** button only closes the socket; a reboot fully closes the port.

- On Android 16+, may need re-pair after reboot (auto key regeneration handled)

### 📱 Shizuku
**Best for:** Non-rooted users with Shizuku installed.

**Setup:**
1. Install [Shizuku](https://shizuku.rikka.app/)
2. Start Shizuku service
3. Select **Shizuku** mode in app → **Permit** → **Connect**

### 🦸 Root
**Best for:** Rooted devices (Magisk, KernelSU, APatch).

**Setup:**
1. Select **Root** mode
2. **Test Root** → grant in root manager
3. **Connect**

### 📂 SAF (Storage Access Framework)
**Best for:** Quick one-off edits. Limited — no shell access, no config generator.

**Setup:**
1. Select **SAF** mode → **Pick Dir**
2. Navigate to `Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android`
3. **Allow**

### Connection Limits

What each access method can do:

| Method | Shell Access | File Push | Log Reading | Config Gen |
|--------|--------------|-----------|-------------|------------|
| ADB | ✓ | ✓ | ✓ | ✓ |
| Shizuku | ✓ | ✓ | ✓ | ✓ |
| Root | ✓ | ✓ | ✓ | ✓ |
| SAF | ✗ | ✓ | Limited | ✗ |

> SAF has no shell access, so it cannot read logs or run the config generator — only push INI files via the Storage Access Framework.

---

## Screens

### Home

- **Backend Status** — current access method, connection state. Tap chip to cycle methods.
- **Manual ADB** — enter IP:port for Wireless Debugging
- **Custom Config** — pick `.ini` files to apply (Engine.ini, DeviceProfiles.ini, GameUserSettings.ini, Scalability.ini, Hardware.ini). When <5 files uploaded, Backup Scope dialog asks: back up all 5 or only overwritten files. Success popup with green checkmark after deploy.
- **Clean Config Files** (NeonRed) — strips all CVar values from all 5 INI files while preserving `[Core.System]` Engine content paths. Game regenerates defaults on next boot.
- **Auto-sync on Connect** — every time you connect, app auto-checks `KuroConfigMonitor.hash` vs actual INI MD5s, fixes desync before you do anything.
- **Quick Actions** — Profile, Battle Stats, Pity Tracker, Backups, Collect Client.log, Config Generator, INI Editor, App Log
- **Real-time log viewer** with color-coded messages, save icon to export to `Downloads/WuWaConfig/`

> All user-facing files (saved logs, collected Client.log, config backups) are stored in `Downloads/WuWaConfig/` — accessible from any file manager.

### Config Generator

#### 1. Analyze Device
- **Device Log** — reads `Client.log` from device (chunked, decrypted). Shows animated progress with cyberpunk glitch effect.
- **Import Log** — pick a saved `.log` file for offline analysis
- **Analysis displays**: device model, GPU, API, Android version, RAM, FPS, thermal events, texture errors, OOM, forbidden CVars, active CVars

#### 2. Smart Brain Scoring
Algorithm evaluates device from 0-100:

| Signal | Impact |
|--------|--------|
| GPU tier | +30 to -20 |
| RAM | +8 to -15 |
| Vulkan | +8 |
| FPS drops | -6 to -18 |
| Thermal throttling | -5 to -20 |
| GPU OOM | -12 to -30 |
| Frame drops | -5 to -10 |
| Forbidden CVars | -5 each (can toggle off — when OFF, also stripped from generated INIs before deploy) |
| Unknown CVars | -5 if >5 in log |
| Active CVars differ from game defaults | +5 if well-optimized, -8 if room to improve |
| Combined signals | -5 to -6 |

**Recommendations:** Ultra (80+), High (75+ / 70+), Balanced (55+ / 40+), Performance (<40), Potato (≤20 or OOM ≥2)

#### 3. Presets
| Preset | Screen % | Shadow | ShadowRes | SSR | MipBias | Streaming | View Dist | Foliage LOD | Detail | LOD Bias | Grass Cull |
|--------|----------|--------|-----------|-----|---------|-----------|-----------|-------------|--------|----------|------------|
| POTATO | 50% | 0 | 128 | 0 | 3 | 0.3 | 0.3 | 0.4 | 0 | 5 | 1,500 |
| ENDURANCE | 55% | 0 | 128 | 0 | 3 | 0.4 | 0.4 | 0.5 | 0 | 4 | 2,500 |
| PERFORMANCE | 60% | 0 | 256 | 0 | 3 | 0.5 | 0.5 | 0.6 | 0 | 3 | 4,500 |
| COMPETITIVE | 100% | 0 | 256 | 0 | 1 | 1.0 | 2.0 | 1.0 | 0 | 1 | 2,000 |
| BALANCED | 80% | 2 | 1,024 | 1 | 0 | 2.0 | 1.5 | 2.0 | 1 | 0 | 15,000 |
| HIGH | 100% | 4 | 2,048 | 2 | 0 | 3.0 | 2.0 | 2.5 | 2 | 0 | 20,000 |
| ULTRA | 100% | 5 | 2,048 | 4 | -1 | 4.0 | 3.0 | 3.0 | 3 | -1 | 30,000 |
| CINEMATIC | 100% | 5 | 4,096 | 4 | -2 | 6.0 | 4.0 | 4.0 | 4 | -2 | 40,000 |

Potato/Performance presets also apply **extra aggressive tweaks**: HZB forced on, ReflectionEnvironment=0, all dynamic lights=0, spotlights disabled, shadow minimum (Quality=1, CSM=1, MaxRes=512, PerObject=256, MinRes=32), DistanceFieldShadowing=0, CapsuleShadows=0, ContactShadows=0, SSGI=0, SubsurfaceScattering=0, SSR half-res, aggressive LOD culling (MinScreenRadius=0.015, ScreenSizeCull=5.0), reduced density (foliage=0.5, grass=0.4).

#### 4. Options
120 FPS unlock, Ultra quality unlock, VSync, Auto cooling, Force Vulkan safety, HZB occlusion, Disable fog/CA/outlines/blur/bloom/auto-exposure/SSR, Allow restricted CVars, Hardware.ini generation with device-tier CVars

#### 5. Game Mode
Overworld / Domain & Tower

#### 6. Files to Generate
Toggle each: Engine.ini, DeviceProfiles.ini, GameUserSettings.ini, Scalability.ini, Hardware.ini

#### 7. Generate
Single button — generates configs with automatic CVar optimization: redundant lines matching game defaults are commented out (`; REDUNDANT`), and unknown CVars not in the UE4 binary dump are flagged (`; UNKNOWN CVar`). Opens the ReviewTune screen — a generated-config reviewer/editor/deploy view with a monospace text editor. Edit CVars inline, then deploy from the screen or close without deploying. All generated configs use `FullscreenMode=0` (fullscreen) — the 3D viewport fills the screen while `sg.ResolutionQuality` controls render resolution for performance.

#### 8. Deploy
Reads device Engine.ini for `[Core.System]` paths, regenerates with edits, pushes to device, refreshes KuroConfigMonitor hashes. Uses **hash snapshot + reconcile** pattern: saves hash file before deploy, compares afterward to detect concurrent game access, always recomputes from actual files. `ModifyCount` is capped at 8 to avoid suspicion. When "Allow restricted CVars" is OFF, forbidden CVars are stripped from all 5 INIs before push. Automatic deploy verification — pulls fresh Client.log, cross-references deployed CVars against engine-recognized ConfigMonitor CVars, shows accept/reject badge with color-coded tag chips: **N redundant** (matches game defaults), **N unknown** (not in UE4 binary dump), **N monitored** (ConfigMonitor-tracked).

#### 9. Auto-Tune Wizard
Iterative benchmark loop (up to 5 rounds): deploys preset → captures FPS via logcat → adjusts preset/options → redeploys until target FPS reached.

### Pity Tracker

- **Fetch Gacha History** — reads Client.log for Convene URL, auto-retries up to 6 times (10s apart). Parses URL and fetches full pull history from Kuro's gacha API.
- **Summary**: total pulls, ★5/★4 counts, avg pity per rarity
- **Per-pool breakdown**: pulls per banner type, ★5/★4 counts per pool
- **Pity Prediction**: per-banner 50/50 or Guaranteed status, last ★5 details, estimated next ★5 pity, soft pity detection (≥66 ★5 / ≥57 ★4), hard pity countdown, 4★ tracking
- **Result History**: last fetch result saved locally with 12-hour auto-expiry. Load or clear from the history banner.
- **Background Polling**: start foreground service to keep polling while app is minimized. Posts notification when URL found.

### Player Profile

- **Read-only** — zero footprint, game cannot detect
- **UID, Server, Level** header
- **Game Progress**: Tower floor, Weekly Rogue score, Battle Pass status
- **Game Info**: Version, Language, Login device ID, Last login time
- **Config bars**: Engine.ini / DeviceProfiles.ini / GameUserSettings.ini setting counts
- **Cached** — profile data stored locally. Auto-shown on revisit; Refresh button re-fetches from device.

### Battle Stats

- **100% file coverage** — contiguous `dd` partition reads across all cores, decrypted per partition. No more spaced sampling gaps.
- **Global version patterns** — battle counters, deaths, role changes, teleports, stamina, dodges, echoes, and ultimates matched from real global Client.log
- Cards: **Combat**, **Exploration**, **Economy**, **Social**, **System**
- Each shows stat chips with current values
- No cache — always re-reads from device for fresh data

### Backup & Restore

- **Per-file selection** — both create and restore allow picking which .ini files to include via checkboxes
- **Create Backup** — dialog shows checkboxes for all 5 INI files (default: all checked). Only checked files are read from device and saved.
- **Restore Backup** — "Restore" opens dialog with the backup's files as checkboxes (default: all checked). Only checked files are pushed to device.
- **Auto-backup** — created before any config write; when <5 files uploaded, asks whether to back up all 5 or only the files being overwritten
- **Delete** — remove old backups
- Also saved as `.ini` files in `Downloads/WuWaConfig/Backups/{name}/` for browsing

### Settings

- **Theme**: System / Light / Dark
- **Custom Background**: Image (jpg/png/gif) or Video (MP4). Opacity slider 5-70%. 15% gradient overlay. Persistent URI.
- **Backup Directory** changer (also mirrored to `Downloads/WuWaConfig/`)
- **Device info**: Chipset, RAM, API level
- **App version**, Links (GitHub, YouTube, Telegram, Discord)

---

## Project Structure

```
app/
└── src/main/java/com/wuwaconfig/app/
    ├── MainActivity.kt           # Navigation (13 screens), permissions, terms gate
    ├── WuWaConfigApp.kt          # Application class, backend holder, CvarDatabase, ConfigGenerator, background settings
    ├── adb/
    │   ├── AdbProtocol.kt        # Wire protocol message encode/decode (24-byte header, CRC32, magic)
    │   ├── AdbClient.kt          # TCP client, auth handshake (2 sig → public key), 15s keepalive, drainTrailingWrite
    │   ├── AdbCrypto.kt          # RSA 2048-bit, encrypted at rest (EncryptedFile + AndroidKeyStore), SSH-RSA pubkey
    │   └── PortScanner.kt        # Port scan 37000-44000 + 5555, 50-port batches, 30s IP cache
    ├── backend/
    │   ├── AccessBackend.kt      # Interface (9 suspend ops) + AccessMethod enum + BackendStatus
    │   ├── AdbBackend.kt         # ADB shell, run-as fallback, base64 chunked push with MD5/size verify
    │   ├── RootBackend.kt        # su -c, 10s timeout, redirectErrorStream
    │   ├── ShizukuBackend.kt     # Shizuku API (reflection newProcess), 60s timeout, script-file fallback, MAX_ARG_STRLEN
    │   ├── SafBackend.kt         # DocumentFile, 3-strategy path resolution, persistable tree URI
    │   └── ShellUtils.kt         # shQuote, computeMd5, maxPushChunkSize, PUSH_RETRY_COUNT=2, MAX_ARG_STRLEN=4096
    ├── config/
    │   ├── ConfigGenerator.kt    # INI generation, 8 presets, Core.System paths, DeviceProfiles chipset mapping
    │   ├── CvarDatabase.kt       # Loads 3 CVar files from assets, optimizeIniText (REDUNDANT/UNKNOWN comments)
    │   ├── CvarCategorizer.kt    # Pure CVar categorization (419 lines, standalone object, 3-level matching, 18 categories)
    │   ├── CvarOptimizer.kt      # GPU tier detection, per-device profile optimizer, adjustProfile for retune
    │   ├── ConfigManager.kt      # Device I/O (1219 lines), backups, logs, profiles, battle stats, hash sync, readProfile
    │   ├── DeployHistoryStore.kt # Deploy history JSON persistence (20 records max, comparison)
    │   ├── LogParser.kt          # Log decryption (XOR LUT), Convene URL extract, battle stat parse, CVar extraction
    │   ├── SmartBrain.kt         # Scoring engine (359 lines), 0-100, ~20 signals, preset recommendation
    │   ├── ForbiddenCvars.kt     # 31 restricted CVars + variant handling, stripForbiddenCvars (called when restricted OFF)
    │   ├── BenchmarkTuner.kt     # Auto-tune state machine, FPS logcat parsing, preset stepping
    │   ├── GachaApi.kt           # Gacha API client (HTTP POST, 11 pool types, character/weapon pity calc)
    │   ├── GachaHistoryStore.kt  # Local gacha history persistence (12hr TTL)
    │   ├── ProfileStore.kt       # Profile cache persistence (player_profile.json, no TTL)
    │   └── ChipsetDetector.kt    # Local SoC detection (Snapdragon/MediaTek/Exynos/Tensor)
    ├── model/
    │   ├── GachaRecord.kt        # GachaRecord, GachaPool, GachaData, PityPrediction, GachaApiResponse, GachaHistoryEntry
    │   ├── PlayerProfile.kt      # Profile data class (UID, server, level, tower, rogue, BP, config counts)
    │   ├── BattleStats.kt        # BattleStats data class (14 counters + operator fun plus for parallel merge)
    │   ├── BattleStatsStore.kt   # cached_battle_stats.json (24hr TTL)
    │   ├── DeployRecord.kt       # DeployRecord + DeployComparison (fpsDelta, thermalDelta, oomDelta, dropFramesDelta)
    │   ├── LogInfo.kt            # Parsed log data (gpu, deviceModel, socName, ramMb, fps, cvars, error counts)
    │   ├── LogEntry.kt           # LogEntry + LogLevel (INFO, SUCCESS, WARNING, ERROR) + auto-increment ID
    │   ├── LogRepository.kt      # Global log singleton, 1000 entries in-memory, 5MB file cap, rotation to .1/.2
    │   ├── LogAnalysisStore.kt   # cached_log_analysis.json (24hr TTL, stores LogInfo + BrainRecommendation)
    │   ├── PresetModels.kt       # CvarEntry, GameMode, GeneratorOptions (5 file toggles + all options), GeneratedIni
    │   ├── GamePaths.kt          # Directory paths, hash monitor config, 5 monitored files
    │   ├── ConfigPreset.kt       # ConfigFile, ConfigBackup (UUID, name, timestamp, files, type)
    │   ├── ConfigHashInfo.kt     # File name + modify count
    │   └── VerificationReport.kt # CvarCategory (18 values) + CvarDetail (isKnown, isMonitored, gameDefault, matchesDefault)
    ├── service/
    │   ├── AdbConnectionService.kt  # ADB foreground service (dataSync, START_STICKY)
    │   └── GachaPollService.kt      # Background gacha polling (30 attempts, 10s apart, LocalBroadcastManager)
    └── ui/
        ├── MainViewModel.kt      # Shared ViewModel (1477 lines) — all state, backend, deploy, verify, gacha, profile
        ├── components/
        │   └── Components.kt     # GlassCard, GradientBackground, GlitchText, GlassButton, log viewer (623 lines)
        ├── screens/
        │   ├── HomeScreen.kt        # Backend control, custom config (backup scope dialog + success popup), clean config, quick actions, log viewer, deploy history
        │   ├── ConfigGenScreen.kt   # Analysis, presets, options, advanced tuning, auto-tune, verification (1182 lines)
    │   ├── ReviewTuneScreen.kt # Generated-config reviewer/editor/deploy screen (driven by ReviewTune* StateFlows)
        │   ├── PityScreen.kt        # Gacha fetcher, summary, predictions, per-pool breakdown, history, background polling
        │   ├── ProfileScreen.kt     # Player profile view (cached, UID/server/level/tower/rogue/BP)
        │   ├── BattleStatsScreen.kt # Battle stats from Client.log (combat, exploration, economy, social, system cards)
        │   ├── BackupScreen.kt      # Backup list + CRUD with per-file selection checkboxes
        │   ├── HistoryScreen.kt     # Deploy history viewer with comparison, per-record delete, clear all
        │   ├── IniEditorScreen.kt   # Full-screen monospace editor, push + hash refresh on save, auto-syncs on open
        │   ├── LogsScreen.kt        # Full-screen log viewer with search/filter by level
        │   ├── SettingsScreen.kt    # Theme, custom backgrounds (image/video), backup dir, device info, app version
        │   ├── SetupScreen.kt       # First-run setup
        │   ├── TermsScreen.kt       # Terms of use
        │   └── UserGuideScreen.kt   # In-app usage guide
        └── theme/
            ├── Color.kt          # Neon palette + glass colors
            ├── Theme.kt          # Dark/Light Material3 schemes
            └── Type.kt           # Typography
    └── util/
        └── LineDiff.kt          # DiffLine/DiffSummary/DiffResult, LineDiff.compute(old,new), md5Of
```

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM (ViewModel + StateFlow)
- **Navigation:** Jetpack Navigation Compose
- **Image loading:** Coil
- **Video playback:** Media3 ExoPlayer
- **Backends:** Root / ADB (in-app protocol) / Shizuku API / SAF
- **Serialization:** Gson
- **Min SDK:** 26 | **Target SDK:** 34

---

## Links

- [GitHub](https://github.com/B3rr7/WuWa-Config-Android)
- [YouTube (@Player42_g)](https://www.youtube.com/@Player42_g)
- [Telegram](https://t.me/Yt_Player42)
- [Discord](https://discord.gg/5WP9nN2e2s)

---

## Topics

These topics on the repo for better search visibility: `wuthering-waves`, `wuwa`, `android`, `fps-boost`, `engine-ini`, `config-optimizer`, `gacha-tracker`, `pity-calculator`, `kuro-games`, `mobile-gaming`, `performance`, `android-optimization`, `ue4`, `unreal-engine-4`, `adb`, `shizuku`, `gaming-tool`

Additional keywords: `game-config-tool`, `fps-unlock`, `graphics-tuning`, `cvars-editor`, `client-log-analyzer`, `wuthering-waves-android`, `smart-brain-scoring`, `config-monitor`, `kuro-config`, `mobile-game-booster`, `gacha-history`, `genshin-alternative`, `open-world-mobile`, `wuwa-mobile-config`, `wuthering-waves-optimization`, `engine-ini-tweak`, `deviceprofiles-ini`, `vulkan-optimization`, `thermal-fix`, `low-end-booster`, `auto-tune-wizard`, `fps-benchmark`, `snapdragon-gaming`, `adreno-tuning`, `mali-gpu-config`, `shizuku-tool`, `adb-config-deploy`, `kuro-monitor-hash`, `cvar-database`

---

## License

[MIT](LICENSE)

Copyright (c) 2026 Player42
