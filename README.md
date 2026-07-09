<!-- wuthering waves config, wuwa fps boost, engine.ini optimization, unreal engine 4 mobile config, kuro games optimization, android game booster, mobile game config editor, gacha pity tracker, battle stats analyzer, fps unlock 120fps, graphics tuning, device profile config, scalability ini, hardware ini, gameusersettings ini, config generator android, wuthering waves android optimization, wuwa mobile config, wuwa lag fix android, wuthering waves stuttering fix, client log decryptor, wuwa engine ini tweak, deviceprofiles ini tutorial, increase wuwa fps, mobile gaming performance, vulkan shader cache optimization, wuthering waves 120fps, fps drop fix mobile, thermal throttling solution, kurogames config tool, snapdragon gaming optimization, adreno gpu tuning, mali gpu optimization, low end device booster, smartbrain scoring, configmonitor hash, cvar editor android, auto tune wizard, battle statistics, wuthering waves benchmark, wuwa config generator, android game booster wuwa, wuthering waves android optimization, wuthering waves 3.4 cyberpunk, wuwa version 3.4 config, kuro config monitor, wuwa graphics preset, mobile unreal engine tweaks, wuthering waves performance 2026, android data folder access, shizuku game config, adb wireless debugging game, wuwa startup config, engine ini mobile, gameusersettings fps unlock, wuthering waves low memory fix, wuwa texture streaming, cvar optimization android, wuwa config for poco, wuwa config for redmi, wuwa config for xiaomi, wuwa config for samsung, wuwa config for oneplus, wuwa config for realme, wuwa config for vivo, wuwa config for oppo, wuwa config for honor, wuwa config for huawei, wuwa config for nothing phone, wuwa config for motorola, wuwa config for asus rog, wuwa config for lenovo, wuwa poco x6 pro config, wuwa poco x5 pro config, wuwa poco f5 config, wuwa poco f6 config, wuwa redmi note 12 config, wuwa redmi note 13 config, wuwa samsung s23 config, wuwa samsung s24 config, wuwa oneplus 12 config, wuwa realme gt config, wuwa vivo x100 config, poco wuthering waves optimization, redmi wuthering waves performance, xiaomi wuwa fps boost, samsung galaxy wuwa config, galaxy s24 wuthering waves settings, poco f5 wuthering waves engine ini, poco f6 wuthering waves graphics, redmi note 13 wuthering waves lag fix, wuwa optimization snapdragon 8 gen 2, wuwa optimization snapdragon 8 gen 3, wuwa optimization dimensity 8300, wuwa optimization dimensity 9200, wuwa optimization kirin 9000 -->
# WuWaConfig — Wuthering Waves Config Toolkit

[![Release](https://img.shields.io/github/v/release/B3rr7/WuWa-Config-Android?label=Download&color=purple)](https://github.com/B3rr7/WuWa-Config-Android/releases)

Android app for **Wuthering Waves (WuWa) performance optimization** — analyzes your device log, generates optimized Engine.ini configs for FPS boost and graphics tuning, and deploys them via ADB/Shizuku/Root. Includes CVar tuning, SmartBrain scoring, Pity Tracker, Battle Stats, and Player Profile. Designed for low-end to flagship Android devices. 🔒 **Privacy-first:** No analytics, no telemetry, no data sent to third parties. Only connects to localhost ADB and Kuro's official gacha API (user-initiated).

> **⚠️ DISCLAIMER**
> This project is **NOT affiliated with Kuro Games or Wuthering Waves**.
> It is a fan-made tool for editing game configuration files.
> Modifying game files may be subject to the game's Terms of Service.
> **Use at your own risk.** The creator is not responsible for any account actions, bans, or issues that may arise.

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
**Best for:** Non-rooted users. App implements ADB wire protocol directly — connects to localhost ADB daemon. (maybe broken yeat)

**Setup:**
- Enable **Wireless Debugging** in Developer Options
- Tap **Connect** in app — auto-scans ports 37000-44000
- Accept RSA fingerprint on phone
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
- **Device Log** — reads `Client.log` from device (chunked, XOR-decrypted). Shows animated progress with cyberpunk glitch effect.
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
| Preset | Screen % | Shadow | SSR | View Dist | Foliage LOD |
|--------|----------|--------|-----|-----------|-------------|
| POTATO | 50% | 0 (128) | 0 | 0.3 | 0.4 |
| PERFORMANCE | 60% | 0 | 0 | 0.5 | 0.7 |
| BALANCED | 100% | 2 | 1 | 1.5 | 2.0 |
| HIGH | 100% | 4 | 2 | 2.0 | 2.5 |
| ULTRA | 100% | 5 | 4 | 3.0 | 3.0 |

#### 4. Options
120 FPS unlock, Ultra quality unlock, VSync, Auto cooling, Force Vulkan safety, HZB occlusion, Disable fog/CA/outlines/blur/bloom/auto-exposure/SSR, Allow restricted CVars, Hardware.ini generation with device-tier CVars

#### 5. Game Mode
Overworld / Domain & Tower

#### 6. Files to Generate
Toggle each: Engine.ini, DeviceProfiles.ini, GameUserSettings.ini, Scalability.ini, Hardware.ini

#### 7. Generate
Single button — generates configs with automatic CVar optimization: redundant lines matching game defaults are commented out (`; REDUNDANT`), and unknown CVars not in the UE4 binary dump are flagged (`; UNKNOWN CVar`). Shows review dialog with monospace text editor. Edit CVars inline, then deploy from dialog or close without deploying.

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

- **100% file coverage** — contiguous `dd` partition reads across all cores, XOR-decrypted per partition. No more spaced sampling gaps.
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
    ├── MainActivity.kt           # Navigation (10 screens), permissions
    ├── WuWaConfigApp.kt          # Application class, backend holder, background settings
    ├── adb/
    │   ├── AdbProtocol.kt        # Wire protocol message encode/decode
    │   ├── AdbClient.kt          # TCP client, auth handshake, shell, drainTrailingWrte
    │   ├── AdbCrypto.kt          # RSA key generation, signing, SSH format
    │   └── PortScanner.kt        # Port scan 37000-44000 + 5555, 30s cache
    ├── backend/
    │   ├── AccessBackend.kt      # Interface + AccessMethod enum
    │   ├── AdbBackend.kt         # ADB shell, run-as fallback, push via base64
    │   ├── RootBackend.kt        # su -c, 10s timeout
    │   ├── ShizukuBackend.kt     # Shizuku API (reflection), 15s timeout
    │   └── SafBackend.kt         # DocumentFile, empty-path filter, 3-strategy fallback
    ├── config/
    │   ├── ConfigGenerator.kt    # INI generation, CVar overrides, CvarDatabase optimization, Scalability.ini
    │   ├── CvarDatabase.kt       # Loads 3 CVar files from assets (libUE4_cvars.txt, config_monitor_cvars.txt, config_monitor_values.txt), lookup + optimization + SmartBrain scoring
    │   ├── CvarCategorizer.kt    # Pure CVar categorization logic (standalone object, testable without Android)
    │   ├── CvarOptimizer.kt      # Per-device profile optimizer (Advanced Gen mode)
    │   ├── ConfigManager.kt      # Device I/O, backups, logs, profiles, battle stats, hashes, pushSingleFile, cleanIniContent, snapshotHashFile + reconcileAfterModify
    │   ├── DeployHistoryStore.kt # Deploy history JSON persistence (20 records max)
    │   ├── LogParser.kt          # XOR decryption, Convene URL extract, battle stat parse
    │   ├── SmartBrain.kt         # Scoring engine, recommendation
    │   ├── ForbiddenCvars.kt    # Kuro restricted CVars + strip/filter helpers (called in ConfigGenerator before deploy when restricted CVars are OFF)
    │   ├── BenchmarkTuner.kt     # Auto-tune: FPS capture, preset adjustment
    │   ├── GachaApi.kt           # Gacha API client (HTTP POST, pity calc, predictions)
    │   ├── GachaHistoryStore.kt  # Local gacha history persistence (12hr TTL)
    │   ├── ProfileStore.kt       # Profile cache persistence
    │   └── ChipsetDetector.kt    # Local SoC detection
    ├── model/
    │   ├── GachaRecord.kt        # GachaRecord, GachaPool, GachaData, PityPrediction, GachaHistoryEntry
    │   ├── PlayerProfile.kt      # Profile data class
    │   ├── BattleStats.kt        # BattleStats data class
    │   ├── DeployRecord.kt       # DeployRecord + DeployComparison for deploy history
    │   ├── LogInfo.kt            # Parsed log data
    │   ├── LogRepository.kt      # Global log singleton with rotating file
    │   ├── PresetModels.kt       # CvarEntry, GameMode, GeneratorOptions (5 file toggles + allowRestrictedCvars), GeneratedIni
    │   ├── GamePaths.kt          # Directory paths, hash monitor config
    │   ├── ConfigPreset.kt       # ConfigFile, ConfigBackup
    │   └── VerificationReport.kt # Deploy verification: accepted/rejected CVars + CvarDetail (isKnown, isMonitored, gameDefault, matchesDefault)
    ├── service/
    │   ├── AdbConnectionService.kt  # ADB foreground service
    │   └── GachaPollService.kt      # Background gacha polling service
    └── ui/
        ├── MainViewModel.kt      # Shared ViewModel (~880 lines)
        ├── components/
        │   └── Components.kt     # GlassCard, GradientBackground, GlitchText, GlassButton, etc.
        ├── screens/
        │   ├── HomeScreen.kt     # Backend control, custom config (with backup scope dialog + success popup), clean config, actions (Profile → Battle Stats → Pity → Backups → Collect Log → Config Gen → INI Editor → App Log), log viewer, deploy history card
        │   ├── ConfigGenScreen.kt # Analysis, presets, options, advanced per-device tuning, auto-tune, verification
        │   ├── PityScreen.kt     # Gacha fetcher, summary, predictions, history
        │   ├── ProfileScreen.kt  # Player profile view (cached)
        │   ├── BattleStatsScreen.kt # Battle stats from Client.log
        │   ├── BackupScreen.kt   # Backup list + CRUD with per-file selection
        │   ├── HistoryScreen.kt  # Deploy history viewer with comparison
        │   ├── IniEditorScreen.kt # Full-screen monospace text editor for any of the 5 monitored INI files. Push + hash refresh on save. Auto-syncs hashes on open.
        │   ├── LogsScreen.kt     # Full-screen log viewer with search/filter
        │   ├── SettingsScreen.kt # Theme, backgrounds, info, deploy history toggle
        │   ├── SetupScreen.kt    # First-run setup
        │   └── TermsScreen.kt    # Terms of use
        └── theme/
            ├── Color.kt          # Neon palette + glass colors
            ├── Theme.kt          # Dark/Light Material3 schemes
            └── Type.kt           # Typography
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

Set these topics on the repo for better search visibility: `wuthering-waves`, `wuwa`, `android`, `fps-boost`, `engine-ini`, `config-optimizer`, `gacha-tracker`, `pity-calculator`, `kuro-games`, `mobile-gaming`, `performance`, `android-optimization`, `ue4`, `unreal-engine-4`, `adb`, `shizuku`, `gaming-tool`

Additional keywords: `game-config-tool`, `fps-unlock`, `graphics-tuning`, `cvars-editor`, `client-log-analyzer`, `wuthering-waves-android`, `smart-brain-scoring`, `config-monitor`, `kuro-config`, `mobile-game-booster`, `gacha-history`, `genshin-alternative`, `open-world-mobile`, `wuwa-mobile-config`, `wuthering-waves-optimization`, `engine-ini-tweak`, `deviceprofiles-ini`, `vulkan-optimization`, `thermal-fix`, `low-end-booster`, `auto-tune-wizard`, `fps-benchmark`, `snapdragon-gaming`, `adreno-tuning`, `mali-gpu-config`, `shizuku-tool`, `adb-config-deploy`, `kuro-monitor-hash`, `cvar-database`

---

## License

[MIT](LICENSE)

Copyright (c) 2026 Player42
