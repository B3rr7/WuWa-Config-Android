# WuWaP42 — Wuthering Waves Config Toolkit

A fan-made Android application for analyzing, generating, and deploying optimized configuration files (`.ini`) for **Wuthering Waves** on mobile devices.

> **⚠️ DISCLAIMER**
> This project is **NOT affiliated with Kuro Games or Wuthering Waves**.
> It is a fan-made tool for editing game configuration files.
> Modifying game files may be subject to the game's Terms of Service.
> **Use at your own risk.** The creator is not responsible for any account actions, bans, or issues that may arise.

---

## Features

- **Device Log Analysis** — Parse encrypted `Client.log` from your device or import one. Detects GPU, RAM, API (Vulkan/OpenGL), Android version, thermal throttling, frame drops, forbidden CVars, and more.
- **Smart Brain Scoring** — AI-driven preset recommendation based on your device's hardware and detected issues. Scores from 0–100 with detailed signal breakdown.
- **Config Generator** — Generate tuned `Engine.ini`, `GameUserSettings.ini`, and `DeviceProfiles.ini` with optimized settings for your device tier.
- **Preset Profiles** — PERFORMANCE (lag-free), BALANCED, HIGH, or ULTRA with fine-grained options.
- **Game-Mode Presets** — Separate Overworld and Domain/Tower profiles with different CVar sets per mode.
- **Review & Tune CVars** — After generation, review every CVar across all three INI files. Tap any value to edit inline. Filter by key/value. Redeploy with your custom overrides.
- **Auto-Tune Wizard** — Iteratively deploys, captures FPS via logcat, adjusts preset/options, and re-benchmarks until your target FPS is stable.
- **Cyberpunk Glitch Progress** — Animated neon percentage number with glitch jitter during log reading.
- **Forbidden CVar Overrides** — Automatically disables known problematic CVars detected in your log.
- **Thermal-Aware Configs** — Thermal throttle detection + thermal control CVar safeguards.
- **Custom Background** — Set an image (jpg/png/gif) or video (MP4) as the app background. Opacity slider (5–70%). Theme-aware gradient overlay. Coil for images, Media3 ExoPlayer for video.
- **Backup & Restore** — Automatic backup of existing config files before applying changes.
- **4 Access Methods** — ROOT, ADB (in-app protocol), Shizuku, and SAF.
- **Material 3 UI** — Dark-themed glassmorphism with neon accents and gradient backgrounds.

---

## Requirements

- **Android 8.0+** (API 26)
- **Wuthering Waves** installed on the device
- One of: **root access**, **Wireless Debugging** enabled, **Shizuku** installed, or access via **SAF picker**

---

## Installation

1. Download the latest APK from the [Releases](https://github.com/Berry7650/WuWap42/releases) page.
2. Install the APK on your Android device.
3. Open the app and accept the Terms of Use.
4. Configure your backup directory (default: `/Download/wuwap42/backup`).
5. Connect via one of the available methods.

---

## Access Methods

| Method | How it works | Setup required |
|--------|-------------|----------------|
| **ROOT** | Executes commands via `su -c` | Magisk / SuperSU installed |
| **ADB** | In-app ADB protocol client connects to device's own ADB daemon at `127.0.0.1:5555` | Enable Developer Options → Wireless Debugging. Tap Connect — accept the RSA fingerprint dialog. Manual IP:port entry available. |
| **SHIZUKU** | Shizuku API (v13) to run shell commands as ADB UID | Install [Shizuku](https://shizuku.rikka.app/), start it, grant permission. |
| **SAF** | Storage Access Framework tree picker | Tap **Pick Dir** and navigate to the game config folder. |

> **Note:** On Android 11+, SAF cannot browse `Android/data/`. Use ROOT, ADB, or Shizuku instead.

---

## Usage

### 1. Connect
- Cycle methods with the status chip.
- Tap **Connect** (or **Pick Dir** for SAF).
- App auto-detects game config directory.

### 2. Analyze
- Navigate to **Config Generator**.
- Tap **Device Log** to read `Client.log` from device (progress shows animated neon % number).
- Or **Import Log** to load a saved `.log` file.
- Review GPU, API, RAM, thermal events, frame drops, forbidden CVars, Smart Brain recommendation.

### 3. Configure
- Select **Game Mode**: Overworld or Domain/Tower.
- Choose **Preset**: PERFORMANCE (maximum FPS stability), BALANCED, HIGH, ULTRA.
- Set target **FPS**: 30/45/60/90/120.
- Toggle **Options**: VSync, cooling, HZB, fog, CA, outline/bloom/radial blur/SSR/auto-exposure toggles.
- Tap **Deploy** to generate and apply.

### 4. Review & Tune
- After deploy, tap **Review & Tune Config**.
- Browse all CVars across `Engine.ini`, `DeviceProfiles.ini`, and `GameUserSettings.ini` in tabbed view.
- Tap any value to edit inline. Edited entries highlighted.
- Use the filter field to search CVars by name or value.
- Tap **Redeploy** to push your custom overrides.

### 5. Auto-Tune (optional)
- Tap **Auto-Tune** for iterative benchmarking.
- App deploys config, captures FPS via logcat (20s), then adjusts preset or disables effects until target FPS is reached.
- Up to 5 rounds with progress displayed inline.

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM (ViewModel + StateFlow)
- **Navigation:** Jetpack Navigation Compose
- **Image loading:** Coil (`io.coil-kt:coil-compose`)
- **Video playback:** Media3 ExoPlayer (`androidx.media3:media3-exoplayer`)
- **Backend:** ROOT (`su`) / ADB (in-app protocol) / Shizuku API / SAF (DocumentFile)
- **Min SDK:** 26 | **Target SDK:** 34

---

## Project Structure

```
app/
└── src/main/java/com/wuwaconfig/app/
    ├── MainActivity.kt          # Entry point, navigation, permissions
    ├── WuWaConfigApp.kt         # Application class
    ├── adb/                     # ADB protocol (PortScanner, AdbClient, AdbCrypto, AdbProtocol)
    ├── backend/                 # Backend abstraction (AccessBackend, AdbBackend, RootBackend, ShizukuBackend, SafBackend)
    ├── config/                  # Core logic
    │   ├── BenchmarkTuner.kt    # Auto-tune FPS capture + iterative adjustment
    │   ├── ConfigGenerator.kt   # .ini generation engine (stateless)
    │   ├── ConfigManager.kt     # Read/write config files on device
    │   ├── LogParser.kt         # Client.log decryption + CVar extraction
    │   └── SmartBrain.kt        # Device scoring & preset recommendation
    ├── model/                   # Data models (CvarEntry, GameMode, GeneratorOptions, etc.)
    ├── service/                 # Background services
    └── ui/
        ├── MainViewModel.kt     # Shared ViewModel
        ├── components/          # Reusable UI components (GlitchText, GlassCard, GradientBackground)
        ├── screens/             # Screen composables (ConfigGenScreen, HomeScreen, SettingsScreen)
        └── theme/               # Colors, typography
```

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
