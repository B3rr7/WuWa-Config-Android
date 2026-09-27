# WuWaConfig

**WuWaConfig** is an open-source Android toolkit for tuning *Wuthering Waves* (Unreal Engine 4, package `com.kurogame.wutheringwaves.global`) on-device. It reads and decodes the game's encrypted `Client.log`, profiles your device (GPU tier, SoC, RAM, thermal history), and generates five tuned UE4 INI config files that are pushed into the game's config directory through one of four access methods: a from-scratch wireless ADB client, Shizuku, Root, or SAF. It also backs up and restores configs, maintains the game's `KuroConfigMonitor.hash` file, tracks gacha pity, extracts player profile and battle statistics, and verifies after every deploy that the engine actually accepted the CVars.

> Fan-made, zero telemetry, no game assets distributed. Not affiliated with Kuro Games.

## Features

- **Config Generator** — 8 quality presets (`potato`, `endurance`, `performance`, `competitive`, `balanced`, `high`, `ultra`, `cinematic`) across 5 INI files: `Engine.ini`, `DeviceProfiles.ini`, `GameUserSettings.ini`, `Scalability.ini`, `Hardware.ini`. Optional per-device "advanced" tuning, CVar-DB optimization, restricted-CVar stripping, Vulkan safety CVars, GSR upscaling, 120 FPS / ultra-quality unlocks, Tower of Adversity game mode, experimental CVars.
- **SmartBrain** — scores your device 0–100 from log signals (GPU tier, RAM, thermals, GPU OOM, frame drops, auto-adjust events, graphics API) and recommends a preset with human-readable signals and warnings.
- **Review & Tune** — per-file editor with line numbers, unified diff against the on-device file, MD5 comparison, lock/copy/reset, and a deploy summary before anything is written.
- **Deploy verification** — re-parses `Client.log` after deploy and reports which CVars the engine accepted or rejected (color-coded ratio, redundant/unknown/monitored counts).
- **Deploy History** — keeps the last 20 deploys with baseline vs. after FPS / thermal / OOM / frame-drop deltas, per-record log re-pull, and one-tap "Retune & Deploy" auto-adjustment.
- **Backup / Restore / Clean** — private JSON backups plus a user-shareable copy under `Downloads/WuWaConfig/Backups/`; clean strips CVars while preserving `[Core.System]` paths.
- **Hash Monitor** — recomputes MD5s for the 5 monitored INIs and rewrites `KuroConfigMonitor.hash` atomically (`ModifyCount` capped at 8), reconciling against concurrent game writes.
- **Pity Tracker** — extracts the Convene URL from `Client.log`, fetches full pull history from Kuro's gacha API (11 pool types), and predicts the next ★5 (hard pity 80, soft pity from 66, 50/50 vs. guaranteed, Astrite cost estimate). Cached 12 hours.
- **Player Profile & Battle Stats** — read-only account info (UID, level, server, Tower floor, versions) from `LocalStorage.db` / `DeviceStorage.db`, and cumulative combat counters (battles, echoes, dodges, deaths, teleports, stamina) parsed from the log's Chinese battle-stat lines.
- **INI Editor** — syntax-highlighted editor for the 5 on-device INIs with search and hash sync on save.
- **In-app updates** — checks GitHub Releases, verifies the downloaded APK's signature against the installed app, and hands off to the system installer.
- **Custom configs** — pick your own `.ini` files and deploy them with optional backup scope.

## Access methods

| Method | How it works | Limitations |
|---|---|---|
| **ADB** | Built-in ADB wire protocol client (24-byte header frames, RSA-2048 auth, 15 s heartbeat). Auto-scans ports 37000–44000 + 5555 on loopback and the device's Wi-Fi IP; manual `IP:port` entry supported. Keys encrypted at rest with `EncryptedFile` + `AndroidKeyStore`. | Android 14+ TLS-required daemons are rejected — use Shizuku. |
| **Shizuku** | Binder `UserService` (`ShellUserService`, `sh -c` with 60 s watchdog, 900 KB output cap). Requires the [Shizuku](https://shizuku.rikka.app/) app. | Needs Shizuku running (wireless debugging or root). |
| **Root** | `su -c` with 15 s/60 s timeouts. | Requires a rooted device. |
| **SAF** | Storage Access Framework tree — the picker pre-targets the game's folder since Android 11+ hides `Android/data`. | No shell: cannot read logs or run the config generator. |

On scoped-storage denials (common on Android 11+ / Chinese ROMs writing into `Android/data/`), ADB and Shizuku automatically retry via `run-as <game>`; if the production game is not debuggable the app tells you to switch to SAF or Root. Pushes are chunked under the kernel `MAX_ARG_STRLEN` (4096) limit, base64-staged through `/data/local/tmp`, and verified by `md5sum` with 2 retries.

## Screens

Setup · Home (dashboard: backend status, custom config apply, quick actions, deploy-history teaser, recent log) · Config Generator · Review & Tune · INI Editor · Logs · Backups · Deploy History · Pity Tracker · Player Profile · Battle Stats · Settings · User Guide · Terms gate on first run.

## Requirements

- Android 8.0+ (minSdk 26; targetSdk/compileSdk 36)
- Wuthering Waves (global) installed
- One access method above: wireless debugging (ADB), Shizuku, root, or SAF
- "All Files Access" permission for log reading and public backups

## Build from source

JDK 17 required. Android Studio or command line:

```bash
./gradlew ktlintCheck          # style gate
./gradlew testDebugUnitTest    # JUnit 4 + Mockito unit tests (28 test classes)
./gradlew assembleDebug        # → app/build/outputs/apk/debug/WuWaConfig-debug.apk
./gradlew assembleRelease      # → WuWaConfig-v<version>-release.apk (R8 minified, resource-shrunk)
```

Release signing reads `keystore.properties` at the repo root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; default store `release.jks`) or falls back to `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` environment variables.

Toolchain: Gradle 9.6.0 · AGP 9.4.0 · Kotlin 2.2.20 · Compose BOM 2026.04.01 (Material 3) · Shizuku 13.1.5 · Gson 2.13.2.

## Architecture

Single-module MVVM app (`com.wuwaconfig.app`, ~78 Kotlin files):

- `adb/` — ADB wire protocol, RSA crypto, port scanner (implemented from scratch, no libadb).
- `backend/` — `AccessBackend` interface + 4 implementations and shared `ShellUtils` (POSIX quoting, MD5, chunking).
- `config/` — generator, CVar database (5,889 known + 735 monitored CVars shipped as assets), log decrypt/parser, hash monitor, backup store, gacha API, SmartBrain, chipset detector.
- `ui/` — Compose Material 3, 14 screens, 8 ViewModels, neon-glass theme with configurable saturation/font/background.
- `service/` — foreground `dataSync` service holding the ADB connection; Shizuku shell user service.
- `update/` — GitHub Releases updater with signature verification.

## Privacy & security

- No analytics, no crash reporting, no tracking. Network access is used only for the GitHub update check and the user-initiated gacha API call (`gmserver-api.aki-game2.com`).
- Cleartext traffic is disabled except for `127.0.0.1`/`localhost` (ADB loopback).
- ADB private keys are encrypted at rest (`EncryptedFile` + AndroidKeyStore, AES-256-GCM); downloaded update APKs must match the installed app's signing certificates.
- `allowBackup=false`.

## Permissions

| Permission | Purpose |
|---|---|
| `MANAGE_EXTERNAL_STORAGE`, `READ/WRITE_EXTERNAL_STORAGE` (scoped) | Read/write the game's config, logs, and hash files under `Android/data/`; export backups to `Downloads/` |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | ADB over Wi-Fi, update check, gacha history fetch |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` | Keep the ADB/shell connection alive with a low-priority notification |
| `REQUEST_INSTALL_PACKAGES` | In-app self-update |

## Links

- GitHub: https://github.com/B3rr7/WuWa-Config-Android
- YouTube: @Player42_g · Telegram: t.me/Yt_Player42

## Disclaimer

This tool edits `.ini` files of Wuthering Waves on your device. Modifying game files may violate the game's Terms of Service and could result in account penalties. Use at your own risk — the authors accept no liability for bans or damage. WuWaConfig is fan-made and not affiliated with Kuro Games.
