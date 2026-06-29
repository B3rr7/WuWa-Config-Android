# WuWaConfig — Wuthering Waves Config Toolkit

[![Release](https://img.shields.io/github/v/release/Berry7650/WuWap42?label=Download&color=purple)](https://github.com/Berry7650/WuWap42/releases)

A fan-made Android application for analyzing, generating, and deploying optimized configuration files (`.ini`) for **Wuthering Waves (WuWa)** on mobile devices — Engine.ini tweaks, FPS boost, graphics optimization, CVar tuning, and low-end device performance fixes. Includes Pity Tracker (gacha pity calculator), Battle Stats, Player Profile, and SmartBrain log analysis.

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

1. **Download** the latest APK from [Releases](https://github.com/Berry7650/WuWap42/releases).
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
- **Custom Config** — pick `.ini` files to apply (Engine.ini, DeviceProfiles.ini, GameUserSettings.ini, Scalability.ini, Hardware.ini). Auto-backup before applying.
- **Delete Config Files** — removes all 5 config files from game directory
- **Quick Actions** — Backups, Collect Client.log, Config Generator, Cancel
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
| Forbidden CVars | -5 each (can toggle off) |
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
Single button — generates configs, shows review dialog with monospace text editor. Edit CVars inline, then deploy from dialog or close without deploying.

#### 8. Deploy
Reads device Engine.ini for `[Core.System]` paths, regenerates with edits, pushes to device, refreshes KuroConfigMonitor hashes. Automatic deploy verification — pulls fresh Client.log, cross-references deployed CVars against engine-recognized ConfigMonitor CVars, shows accept/reject badge for each CVar.

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

- Parses `Client.log` for CN keyword battle counters
- Cards: **Combat**, **Exploration**, **Economy**, **Social**, **System**
- Each shows stat chips with current values
- Requires gameplay for non-zero data

### Backup & Restore

- **Auto-backup** — created before any config write
- **Manual backup** — named backups from Backups screen
- **Restore** — push saved files back to device
- **Delete** — remove old backups
- Also saved as `.ini` files in `Downloads/WuWaConfig/Backups/{name}/` for browsing

### Settings

- **Theme**: System / Light / Dark
- **Custom Background**: Image (jpg/png/gif) or Video (MP4). Opacity slider 5-70%. 15% gradient overlay. Persistent URI.
- **Backup Directory** changer
- **Device info**: Chipset, RAM, API level
- **App version**, Links (GitHub, YouTube, Telegram, Discord)

---

## Project Structure

```
app/
└── src/main/java/com/wuwaconfig/app/
    ├── MainActivity.kt           # Navigation (7 screens), permissions
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
    │   ├── ConfigGenerator.kt    # INI generation, CVar overrides, Scalability.ini
    │   ├── ConfigManager.kt      # Device I/O, backups, logs, profiles, battle stats, hashes
    │   ├── LogParser.kt          # XOR decryption, Convene URL extract, battle stat parse
    │   ├── SmartBrain.kt         # Scoring engine, recommendation
    │   ├── ForbiddenCvars.kt    # 31 known Kuro restricted CVars + strip/filter helpers
    │   ├── BenchmarkTuner.kt     # Auto-tune: FPS capture, preset adjustment
    │   ├── GachaApi.kt           # Gacha API client (HTTP POST, pity calc, predictions)
    │   ├── GachaHistoryStore.kt  # Local gacha history persistence (12hr TTL)
    │   ├── ProfileStore.kt       # Profile cache persistence
    │   └── ChipsetDetector.kt    # Local SoC detection
    ├── model/
    │   ├── GachaRecord.kt        # GachaRecord, GachaPool, GachaData, PityPrediction, GachaHistoryEntry
    │   ├── PlayerProfile.kt      # Profile data class
    │   ├── BattleStats.kt        # BattleStats data class
    │   ├── LogInfo.kt            # Parsed log data
    │   ├── PresetModels.kt       # CvarEntry, GameMode, GeneratorOptions (5 file toggles + allowRestrictedCvars), GeneratedIni
    │   ├── GamePaths.kt          # Directory paths, hash monitor config
    │   ├── ConfigPreset.kt       # ConfigFile, ConfigBackup
    │   └── VerificationReport.kt # Deploy verification: accepted/rejected CVars from ConfigMonitor
    ├── service/
    │   ├── AdbConnectionService.kt  # ADB foreground service
    │   └── GachaPollService.kt      # Background gacha polling service
    └── ui/
        ├── MainViewModel.kt      # Shared ViewModel (~880 lines)
        ├── components/
        │   └── Components.kt     # GlassCard, GradientBackground, GlitchText, GlassButton, etc.
        ├── screens/
        │   ├── HomeScreen.kt     # Backend control, actions, log viewer
        │   ├── ConfigGenScreen.kt # Analysis, presets, options, auto-tune, 4-tab review
        │   ├── PityScreen.kt     # Gacha fetcher, summary, predictions, history
        │   ├── ProfileScreen.kt  # Player profile view (cached)
        │   ├── BattleStatsScreen.kt # Battle stats from Client.log
        │   ├── BackupScreen.kt   # Backup list + CRUD
        │   ├── SettingsScreen.kt # Theme, backgrounds, info
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

- [GitHub](https://github.com/Berry7650/WuWap42)
- [YouTube (@Player42_g)](https://www.youtube.com/@Player42_g)
- [Telegram](https://t.me/Yt_Player42)
- [Discord](https://discord.gg/5WP9nN2e2s)

---

## License

[MIT](LICENSE)

Copyright (c) 2026 Player42
