# WuWaConfig

**Wuthering Waves Config Toolkit & FPS Booster for Android** — generates and deploys optimized
UE4 INI configs for `com.kurogame.wutheringwaves.global`.

| | |
|---|---|
| Package | `com.wuwaconfig.app` |
| Version | **1.1.4** (versionCode 15) |
| Min / Target / Compile SDK | 26 / 34 / 34 |
| Toolchain | JDK 17 · Gradle 8.6 · AGP 8.4.2 · Kotlin 1.9.24 |
| Compose | BOM 2024.10.00 · Compiler 1.5.14 |
| Size | 78 Kotlin files · ~21,200 lines |
| Telemetry | **None** |

## What it does

WuWaConfig analyzes your device's `Client.log`, scores your hardware with **SmartBrain**
(0–100), then generates 5 optimized INI files and deploys them over **ADB · Shizuku · Root · SAF**.

- **8 presets** — Potato → Cinematic, from max battery savings to max fidelity
- **120 FPS unlock** and **Ultra quality unlock** toggles
- **CVar editor** with live validation against a 5,885-entry CVar database
- **Gacha pity tracker** — pull history, 50/50 status, soft/hard pity countdown
- **Battle stats** analyzer — combat / exploration / economy / social / system
- **Auto-Tune Wizard** — iterative benchmark loop that hunts your target FPS
- **Backup & restore** — per-file backups before every write, recover in one tap
- **Privacy-first** — no analytics, no telemetry, nothing leaves the device

> **Disclaimer** — This project is **not affiliated with Kuro Games or Wuthering Waves**.
> It is a fan-made tool for editing game configuration files. Modifying game files may be
> subject to the game's Terms of Service. **Use at your own risk.**

## Docs

| Doc | Covers |
|---|---|
| [Architecture](./ARCHITECTURE.md) | MVVM layout, ViewModels, backends, config subsystems, data stores |
| [Setup & Build](./SETUP.md) | SDKs, Gradle, signing, verification order, test commands |
| [Config Generator](./CONFIG-GENERATOR.md) | Presets, `GeneratorOptions`, generated INIs, CVar optimization |

## Quick links

- [GitHub](https://github.com/B3rr7/WuWa-Config-Android)
- [Website](https://b3rr7.github.io/WuWa-Config-Android/)
- [Discord](https://discord.gg/5WP9nN2e2s)

## License

[MIT](../LICENSE) · Copyright (c) 2026 Player42