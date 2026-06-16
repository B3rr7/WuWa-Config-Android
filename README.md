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
- **Config Generator** — Generate tuned `Engine.ini`, `GameUserSettings.ini`, and `DeviceProfiles.ini` with optimized settings for your device tier (flagship / high / mid / low).
- **Preset Profiles** — Choose from PERFORMANCE, BALANCED, HIGH, or ULTRA presets with fine-grained options (FPS target, VSync, cooling, Vulkan safety CVars, and more).
- **Forbidden CVar Overrides** — Automatically disables known problematic CVars detected in your log (FSR RCAS, TAA Sharpness, SSAO, VoidGT, Lens Flare).
- **Thermal-Aware Configs** — Detects thermal throttling events and applies thermal control safeguards automatically.
- **Backup & Restore** — Automatically backs up existing config files before applying changes.
- **4 Access Methods** — ROOT, ADB (in-app protocol), Shizuku, and SAF.
- **Material 3 UI** — Dark-themed glassmorphism design with dynamic theming.

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

The app provides four ways to access the game config directory. Tap the chip in the status card to cycle between them.

| Method | How it works | Setup required |
|--------|-------------|----------------|
| **ROOT** | Executes commands via `su -c` | Magisk / SuperSU installed |
| **ADB** | In-app ADB protocol client connects to the device's own ADB daemon at `127.0.0.1:5555` | Enable Developer Options → Wireless Debugging. Tap Connect — accept the RSA fingerprint dialog when it appears (one-time). Manual IP:port entry also available. |
| **SHIZUKU** | Uses the Shizuku API (v13) to run shell commands as the ADB UID | Install [Shizuku](https://shizuku.rikka.app/), start it, then tap **Permit** to grant permission. |
| **SAF** | Uses Android's Storage Access Framework tree picker | Tap **Pick Dir** and navigate to the game config folder via the system file picker. |

> **Note:** On Android 11+, SAF cannot browse into `Android/data/` directories. On such devices, use ROOT, ADB, or Shizuku instead.

---

## Usage

### 1. Connect to your device
- Cycle to your preferred method using the method toggle chip.
- Tap **Connect** (or **Pick Dir** for SAF).
- The app will auto-detect the game config directory.

### 2. Analyze your device log
- Navigate to **Config Generator**.
- Tap **Device Log** to read and parse `Client.log` from your device.
- Or tap **Import Log** to import a previously saved `.log` file.
- Review the analysis results: GPU, API, RAM, issues (thermal, frame drops, forbidden CVars, etc.).

### 3. Generate configs
- Select a **Preset** (PERFORMANCE / BALANCED / HIGH / ULTRA).
- Set your target **FPS** (30/45/60/90/120).
- Toggle **Options** (VSync, Auto cooling, HZB occlusion, fog, etc.).
- Tap **Deploy** to generate and apply the config files.

### 4. Verify deployment
- Check the **Log** section for success/failure messages.
- Config files are backed up with timestamps before overwriting.

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM (ViewModel + StateFlow)
- **Navigation:** Jetpack Navigation Compose
- **Backend:** ROOT (`su`) / ADB (in-app protocol) / Shizuku API / SAF (DocumentFile)
- **Min SDK:** 26 | **Target SDK:** 34

---

## Project Structure

```
app/
├── src/main/java/com/wuwaconfig/app/
│   ├── MainActivity.kt          # Entry point, navigation, permissions
│   ├── WuWaConfigApp.kt         # Application class
│   ├── adb/                     # ADB protocol implementation (PortScanner, AdbClient, AdbCrypto, AdbProtocol)
│   ├── backend/                 # Backend abstraction (AccessBackend, AdbBackend, RootBackend, ShizukuBackend, SafBackend)
│   ├── config/                  # Core logic
│   │   ├── ConfigGenerator.kt   # .ini generation engine
│   │   ├── ConfigManager.kt     # Read/write config files on device
│   │   ├── LogParser.kt         # Client.log parser + decryption
│   │   └── SmartBrain.kt        # Device scoring & recommendation
│   ├── model/                   # Data models
│   ├── service/                 # Background services
│   └── ui/
│       ├── MainViewModel.kt     # Shared ViewModel
│       ├── components/          # Reusable UI components
│       ├── screens/             # Screen composables
│       └── theme/               # Colors, typography, theming
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
