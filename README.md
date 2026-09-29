<div align="center">

# WuWaConfig — Wuthering Waves Config Toolkit for Android

[![Release](https://img.shields.io/github/v/release/B3rr7/WuWa-Config-Android?label=Download&color=purple)](https://github.com/B3rr7/WuWa-Config-Android/releases)
[![Stars](https://img.shields.io/github/stars/B3rr7/WuWa-Config-Android?style=flat&logo=github)](https://github.com/B3rr7/WuWa-Config-Android/stargazers)
[![License](https://img.shields.io/github/license/B3rr7/WuWa-Config-Android?style=flat)](https://github.com/B3rr7/WuWa-Config-Android/blob/main/LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208%2B-green)](https://github.com/B3rr7/WuWa-Config-Android)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-purple)](https://kotlinlang.org)
[![Privacy](https://img.shields.io/badge/Privacy-No%20Telemetry-blue)](https://github.com/B3rr7/WuWa-Config-Android)

**Boost FPS · Tune Graphics · Analyze Device · Track Pity · Stay Private**

Free, open-source Android toolkit that generates optimized Unreal Engine 4 INI configs for Wuthering Waves. Works without root via ADB, Shizuku, or SAF. Includes a gacha pity tracker, battle stats analyzer, and a full CVar editor — all with zero telemetry.

[YouTube](https://www.youtube.com/@Player42_g)

</div>

---

> [!WARNING]
> **Disclaimer** — This project is **not affiliated with Kuro Games or Wuthering Waves**. It is a fan-made tool for educational and research purposes. Modifying game configuration files may be subject to the game's Terms of Service. **Use at your own risk.**

> [!NOTE]
> **Platform** — Android 8.0+ (API 26) only. Not available for iPhone/iPad. Windows/macOS/Linux require an Android device or emulator.

---

## Table of Contents

<details open>
<summary><b>Click to expand / collapse</b></summary>

1. [Features](#features)
2. [Quick Start](#quick-start)
3. [Connection Methods](#connection-methods)
4. [Presets & Settings](#presets--settings)
5. [Troubleshooting](#troubleshooting)
6. [FAQ](#faq)
7. [Screenshots](#screenshots)
8. [Privacy & Security](#privacy--security)
9. [For Developers](#for-developers)
10. [Community](#community)
11. [License](#license)

</details>

---

## Features

- **FPS Booster & Config Generator** — Generates 5 tuned INI files (`Engine.ini`, `Scalability.ini`, `GameUserSettings.ini`, `DeviceProfiles.ini`, `Hardware.ini`) optimized for Snapdragon, Dimensity, Exynos, and Tensor GPUs. Fixes the Android windowed viewport bug with `FullscreenMode=0`.
- **SmartBrain Device Scoring (0–100)** — Analyzes GPU tier, RAM, Vulkan support, thermal behavior, and frame drops to automatically recommend the best preset for your device.
- **8 Quality Presets** — From Potato (maximum FPS) to Cinematic (maximum visuals), each tier adjusts screen percentage, shadow resolution, SSR, mip bias, streaming multiplier, view distance, foliage LOD, and more.
- **CVar Database (5,889 entries)** — Automatically comments out redundant or unknown CVars. Strips 31 forbidden CVars when restricted mode is enabled. 18 categories with 3-level matching.
- **Gacha Pity Tracker (11 pools)** — No pull cap. Tracks soft-pity from pull 66, hard pity at 80. Shows Guaranteed vs 50/50 status per banner. Based on a ~394K-sample dataset.
- **Battle Stats Analyzer** — Decrypts and parses the `dd` partition from `Client.log`. Displays combat, dodge, movement, and echo skill stats across 5 cards.
- **Player Profile** — Read-only extraction of UID, server, level, tower progress, and game info. Zero write footprint.
- **Backup, Restore & Hash Monitor** — Per-file backups, auto-backup before every write, hash snapshot with reconcile to guard concurrent game writes, one-tap restore.
- **INI Editor & Log Tools** — Full-screen monospace editor with line diff, search, and hash verification. Color-coded log viewer with 5MB rotation.
- **120 FPS Unlock** — Toggle available on supported flagships with thermal headroom.

---

## Quick Start

### For Players

1. Download the latest APK from [Releases](https://github.com/B3rr7/WuWa-Config-Android/releases).
2. Install on Android 8.0+ (allow "Install unknown apps" if prompted).
3. Open the app → Accept Terms → Grant Storage permission.
4. Connect using one of the [Connection Methods](#connection-methods) below.
5. Tap **Analyze Device** → review SmartBrain score → select a preset → **Generate** → **Deploy**.

### For Developers

See [For Developers](#for-developers) below.

---

## Connection Methods

The app reads and writes to `Android/data/com.kurogame.wutheringwaves.global/`. Choose the method that fits your setup:

| Method | Shell Access | File Push | Log Reading | Config Gen | Best For |
|:------:|:------------:|:---------:|:-----------:|:----------:|----------|
| **ADB** | Yes | Yes | Yes | Yes | Non-rooted users, no PC needed |
| **Shizuku** | Yes | Yes | Yes | Yes | Non-rooted users with Shizuku installed |
| **Root** | Yes | Yes | Yes | Yes | Magisk/KernelSU/APatch users |
| **SAF** | No | Yes | Limited | No | Quick one-off edits without shell |

### ADB — Wireless Debugging (No Root, No PC)

The app implements the ADB wire protocol directly — no PC daemon required.

**Auto-connect (no PC):**
1. Enable **Wireless Debugging** in Developer Options.
2. Tap **Connect** in the app (auto-scans ports 37000–44000).
3. Accept the RSA fingerprint prompt.

**With PC (USB Debugging ON):**

```bash
adb tcpip 5555
adb connect 192.168.x.x:5555
# Disable:
adb disconnect 192.168.x.x:5555
adb usb
```

> **Android 11+ / Chinese ROMs (Xiaomi/HyperOS):** If deploy fails with `Permission denied`, the app retries via `run-as`. If the game is not debuggable, switch to **SAF** or **Root**.

### Shizuku — Binder IPC

1. Install [Shizuku](https://shizuku.rikka.app/) and start the service.
2. Select **Shizuku** in the app → **Permit** → **Connect**.

> **Chinese ROMs:** If deploy fails, enable both **USB debugging** and **USB debugging (Security settings)** in Developer Options, then re-authorize Shizuku.

### Root — `su -c`

1. Select **Root** → **Test Root** → grant permission → **Connect**.

### SAF — Storage Access Framework

1. Select **SAF** → **Pick Dir**.
2. Navigate to `Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android`.
3. Tap **Allow**.

> SAF has no shell access — log reading and the config generator are unavailable. Use ADB, Shizuku, or Root for full functionality.

---

## Presets & Settings

| Preset | Detail | Screen% | Shadow | ShadowRes | SSR | MipBias | Streaming | ViewDist | Foliage LOD | LOD Bias | GrassCull |
|--------|--------|---------|--------|-----------|-----|---------|-----------|----------|-------------|----------|-----------|
| **POTATO** | 0 | 60% | 0 | 128 | 0 | 3 | 0.3× | 0.3 | 0.4 | 5 | 1,500 |
| **ENDURANCE** | 1 | 70% | 0 | 128 | 0 | 3 | 0.4× | 0.4 | 0.5 | 4 | 2,500 |
| **PERFORMANCE** | 2 | 60% | 0 | 256 | 0 | 3 | 0.5× | 0.5 | 0.6 | 3 | 4,500 |
| **COMPETITIVE** | 3 | 100% | 2 | 256 | 0 | 1 | 1.0× | 2.0 | 1.0 | 1 | 2,000 |
| **BALANCED** | 4 | 80% | 2 | 1,024 | 1 | 0 | 2.0× | 1.5 | 2.0 | 0 | 15,000 |
| **HIGH** | 5 | 100% | 4 | 2,048 | 2 | 0 | 3.0× | 2.0 | 2.5 | 0 | 20,000 |
| **ULTRA** | 6 | 100% | 5 | 2,048 | 4 | -1 | 4.0× | 3.0 | 3.0 | -1 | 30,000 |
| **CINEMATIC** | 7 | 100% | 5 | 4,096 | 4 | -2 | 6.0× | 4.0 | 4.0 | -2 | 40,000 |

**Additional options:** 120 FPS unlock, Ultra quality unlock, VSync, Auto cooling, Force Vulkan, HZB occlusion, Disable fog/CA/outlines/blur/bloom/auto-exposure/SSR, Hardware.ini via ChipsetDetector, GameMode (Overworld / Tower of Adversity).

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| FPS drops below target | Lower Screen% to 60–80%, set Shadow to 0–2, check `r.FramePace.MaxFPS` |
| Phone overheats and throttles | Set `ReflectionEnvironment=0`, disable all dynamic lights, disable volumetric fog, enable Auto Cool |
| Game crashes with GPU out of memory | Set Shadow MaxResolution to 1024, Anisotropy to 4, PoolSize to 512, disable HD texture packs |
| Missing or broken textures | Enable `r.HZBOcclusion=1`, set MipMapLodBias to 0–1, raise PoolSize, verify DeviceProfile |
| Stuttering or uneven frame pacing | Lower render scale if CPU-bound, disable VSync, enable RHICmd bypass, set MaxFPS to 60 or 120 |
| Exynos or Mali GPU underperforming | Start one tier below the Snapdragon equivalent, lower shadow and streaming quality |
| 120 FPS not unlocking | Enable 120 FPS toggle, verify `r.FramePace.MaxFPS=120`, ensure thermal headroom |
| Post-processing artifacts or visual glitches | Set PostProcessQuality to 0–2, Bloom to 0–1, disable auto-exposure, reduce radial blur |
| Deploy fails with Permission denied | Use run-as fallback (Shizuku/ADB), switch to SAF for scoped storage, or use Root |
| Wireless debugging won't connect | Ensure phone and PC are on same network, re-enable Wireless Debugging, check firewall |
| Shizuku permission denied on Chinese ROM | Enable USB debugging (Security settings) in Developer Options, re-authorize Shizuku |

---

## FAQ

**Is WuWaConfig free?**
Yes. It is free and open-source under the MIT license.

**Does WuWaConfig work without root?**
Yes. You can use ADB wireless debugging, Shizuku, or SAF — no root required for most features.

**Will this get me banned?**
The app modifies local configuration files only. It does not inject code, modify memory, or interact with the game's network protocol. However, modifying game files may be subject to the game's Terms of Service. Use at your own risk.

**Which Snapdragon/Dimensity/Exynos/Tensor phones are supported?**
Any Android 8.0+ device. SmartBrain detects your chipset and recommends the appropriate preset tier.

**How does the gacha pity tracker work?**
It reads the Convene URL from `Client.log` (you must open Convene History in-game first) and fetches your pull history from the game's API. No cap on pulls tracked.

**Can I edit the generated INI files?**
Yes. The built-in INI Editor supports full editing with line diff, search, and hash verification before deploy.

**Does the app collect any data?**
No. Zero telemetry. No Firebase, Crashlytics, or Sentry. ADB keys are encrypted at rest. Gacha data stays in-process via LocalBroadcastManager.

**Why does SAF not support log reading?**
SAF (Storage Access Framework) provides file-level access only — no shell. Log reading and the config generator require shell access via ADB, Shizuku, or Root.

---

## Screenshots

<p align="center">
  <img src="screenshots/screen-01.webp" width="200" alt="WuWaConfig home screen showing backend connection status and quick action buttons">
  <img src="screenshots/screen-02.webp" width="200" alt="Config Generator screen with SmartBrain device analysis and preset selection">
  <img src="screenshots/screen-03.webp" width="200" alt="Gacha pity tracker showing pull history and next 5-star prediction">
  <img src="screenshots/screen-04.webp" width="200" alt="Player profile screen with UID, tower progress, and game info">
  <img src="screenshots/screen-05.webp" width="200" alt="Battle stats analyzer with combat and exploration cards">
  <img src="screenshots/screen-06.webp" width="200" alt="Settings screen with theme options and backup directory">
</p>

---

## Privacy & Security

- **No telemetry** — WuWaConfig sends nothing to third parties. No Firebase, Crashlytics, or Sentry.
- **Local only** — ADB communication stays on-device (127.0.0.1). Gacha data uses `LocalBroadcastManager` (in-process only).
- **Encrypted at rest** — ADB RSA keys stored via `EncryptedFile` + `AndroidKeyStore` (AES-256-GCM).
- **No backup** — `allowBackup="false"` prevents Android backup extraction.
- **Network security** — `network_security_config.xml` allows cleartext only to `127.0.0.1`/`localhost`. The gacha endpoint is user-initiated.
- **ProGuard** — Release builds strip `Log.d`/`Log.v`. Keep rules preserve `com.wuwaconfig.app.**` and `rikka.shizuku.**`.

---

## For Developers

### Build

Prerequisites: JDK 17, Android SDK 36, Git.

```bash
git clone https://github.com/B3rr7/WuWa-Config-Android.git
cd WuWa-Config-Android

./gradlew ktlintCheck          # lint (style is load-bearing — CI gates on ktlint)
./gradlew ktlintFormat         # auto-fix style violations

./gradlew testDebugUnitTest    # all unit tests (JUnit 4 + Mockito)

./gradlew assembleDebug        # debug APK (fully offline)
./gradlew assembleRelease      # release APK (needs keystore.properties + network once)

adb install -r app/build/outputs/apk/debug/WuWaConfig-debug.apk
```

One-line verification:

```bash
./gradlew ktlintCheck && ./gradlew testDebugUnitTest && ./gradlew assembleDebug
```

### Architecture

```
WuWaConfigApp (Application)
  ├─ AccessBackend ─┬─ AdbBackend (wire protocol, PortScanner, AdbCrypto)
  │                 ├─ ShizukuBackend (ShellUserService Binder)
  │                 ├─ RootBackend (su -c)
  │                 └─ SafBackend (DocumentFile)
  ├─ CvarDatabase (async load, 5,889 entries)
  ├─ ConfigGenerator (8 presets, 5 INI builders)
  └─ LogRepository (global, 1000 entries)

MainActivity (single Activity, 13 composable routes)
  └─ ViewModels (8): MainViewModel + DeployHistory + Backup + Gacha
       + Profile + LogInsights + IniEditor + Settings
       └─ Screens → Components → Theme (Material 3)
```

### Tech Stack

| Area | Choice | Version / Notes |
|------|--------|-----------------|
| **Language** | Kotlin | 2.2.20, JDK 17, AGP 9.4.0 |
| **UI** | Jetpack Compose + Material 3 | BOM 2026.04.01 |
| **Architecture** | MVVM | ViewModel + StateFlow |
| **Navigation** | Navigation Compose | 2.9.8, 13 routes |
| **Coroutines** | kotlinx-coroutines-android | 1.11.0 |
| **Image / Video** | Coil 2.7.0 + Media3 ExoPlayer 1.7.1 | VideoBackground support |
| **Backends** | ADB (wire) / Shizuku 13.1.5 / Root / SAF | 4 access methods |
| **Security** | AndroidX Security Crypto 1.1.0 | EncryptedFile + AndroidKeyStore |
| **Serialization** | Gson 2.13.2 | JSON stores |
| **Lint / Tests** | ktlint 12.1.0 + JUnit 4.13.2 + Mockito 5.11.0 | 30 pure-logic test files |
| **SDK** | min 26 / target 36 / compile 36 | allowBackup=false |

### Source Tree

<details>
<summary><b>Click to expand source tree</b></summary>

```
app/src/main/java/com/wuwaconfig/app/
├── MainActivity.kt              # Single Activity, 13 composable routes
├── WuWaConfigApp.kt             # Application — backend, CvarDatabase, ConfigGenerator
├── adb/                         # ADB wire protocol (in-app, no PC daemon)
│   ├── AdbProtocol.kt           # 24-byte header, CRC32, message encode/decode
│   ├── AdbClient.kt             # TCP, RSA auth, 15s keepalive, drainTrailingWrite
│   ├── AdbCrypto.kt             # RSA-2048, EncryptedFile + AndroidKeyStore
│   └── PortScanner.kt           # 37000–44000 + 5555, 30s IP cache
├── backend/                     # 4 access methods
│   ├── AccessBackend.kt         # interface + AccessMethod enum
│   ├── AdbBackend.kt            # base64 chunked push, run-as fallback
│   ├── ShizukuBackend.kt        # UserService API, 60s timeout
│   ├── RootBackend.kt           # su -c, 10s timeout
│   ├── SafBackend.kt            # DocumentFile, persistable tree URI
│   └── ShellUtils.kt            # shQuote, computeMd5, PUSH_RETRY_COUNT=2
├── config/                      # Config generation & analysis
│   ├── ConfigGenerator.kt       # 8 presets, generateWithCorePaths → 5 INIs
│   ├── CvarDatabase.kt          # 5,889 entries, optimizeIniText
│   ├── CvarCategorizer.kt       # 3-level match, 18 categories
│   ├── ForbiddenCvars.kt        # 31 restricted entries
│   ├── CvarOptimizer.kt         # GPU-tier regex → tuned profile
│   ├── SmartBrain.kt            # 0–100 scoring → recommendPreset()
│   ├── ConfigGenUtil.kt        # deduplicate, parse, apply overrides
│   ├── LogParser.kt             # XOR-LUT decrypt, UTF-16/8, battle stats
│   ├── ConfigManager.kt         # deploy/restore/clean facade
│   ├── BackupStore.kt           # backup CRUD, Client.log persistence
│   ├── ProfileExtractor.kt      # log read/decode, verifyDeployedCvars
│   ├── HashMonitor.kt           # MD5 sync, snapshot/reconcile
│   ├── GachaApi.kt              # HTTP POST, 11 pools, pity calc
│   ├── DeployHistoryStore.kt    # deploy_history.json (20 records)
│   ├── GachaHistoryStore.kt     # gacha_history.json (12h TTL)
│   ├── ProfileStore.kt          # player_profile.json
│   ├── BattleStatsStore.kt      # cached_battle_stats.json (24h TTL)
│   ├── ChipsetDetector.kt       # Snapdragon/Mediatek/Exynos/Tensor
│   └── BenchmarkTuner.kt        # benchmark_tuner_state.json
├── model/                       # Data classes & store models
├── service/
│   ├── AdbConnectionService.kt  # Foreground service for ADB
│   └── ShellUserService.kt      # Binder shell for Shizuku
├── ui/
│   ├── MainViewModel.kt         # Primary shared state holder
│   ├── DeployHistoryViewModel.kt
│   ├── BackupViewModel.kt / GachaViewModel.kt / ProfileViewModel.kt
│   ├── LogInsightsViewModel.kt / IniEditorViewModel.kt / SettingsViewModel.kt
│   ├── components/Components.kt # GlassCard, GradientBackground, GlitchText
│   ├── screens/ (13)            # Home, ConfigGen, ReviewTune, Pity, Profile, etc.
│   └── theme/                   # Color.kt, Theme.kt, Type.kt
├── util/
│   ├── LineDiff.kt              # LineDiff.compute, md5Of
│   └── AtomicFile.kt            # Atomic write helpers
├── update/UpdateManager.kt      # Update checks
└── assets/cvars/                # libUE4_cvars.txt (5,889) + config_monitor_* (735 each)
```

</details>

---

## Community

- [GitHub](https://github.com/B3rr7/WuWa-Config-Android)
- [YouTube — Player42](https://www.youtube.com/@Player42_g)
- [Telegram](https://t.me/Yt_Player42)
- [Discord](https://discord.gg/5WP9nN2e2s)
- [Website](https://b3rr7.github.io/WuWa-Config-Android/)

Contributions welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Run `ktlintCheck` before submitting a PR.

---

## License

[MIT](LICENSE) · Copyright (c) 2026 Player42 · Not affiliated with Kuro Games.

---

<div align="center">

**Made for Rovers — by Rovers.**

[Back to top](#wuwaconfig--wuthering-waves-config-toolkit-for-android)

</div>

