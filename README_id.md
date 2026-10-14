<!-- prettier-ignore -->

<div align="center">

<img src="app_icon.png" width="120" alt="WuWaConfig logo">

# WuWaConfig

**Pandai FPS · Tune Grafis · Analisis Perangkat · Pantau Keberuntungan · Privasi**

*Terjemahan dihasilkan oleh AI*

[![Release](https://img.shields.io/github/v/release/B3rr7/WuWa-Config-Android?label=Download&color=purple)](https://github.com/B3rr7/WuWa-Config-Android/releases)
[![Stars](https://img.shields.io/github/stars/B3rr7/WuWa-Config-Android?style=flat&logo=github)](https://github.com/B3rr7/WuWa-Config-Android/stargazers)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![No telemetry](https://img.shields.io/badge/telemetry-none-4CAF50)](https://github.com/B3rr7/WuWa-Config-Android)

Bebas biaya, open-source Android toolkit yang menghasilkan, mengoptimalkan dan men-deploy
konfigurasi Unreal Engine untuk Wuthering Waves — tanpa root, tanpa PC, tanpa telemetry.

[Features](#features) · [Quick start](#quick-start) · [Backends](#backends) · [Presets](#presets) · [Build](#build) · [Architecture](#architecture)

</div>

---

> [!WARNING]
> **Disclaimer** — WuWaConfig **tidak tergabung atau tidak didukung oleh Kuro Games atau Wuthering Waves**. Ini adalah alat dibuat oleh fan untuk tujuan pendidikan dan penelitian. Alat ini hanya mengedit file konfigurasi lokal: tidak ada code injection, tidak ada memory patching, dan tidak ada perubahan protokol jaringan. Memodifikasi file game masih tetap bisa di bawah Syarat Layanan (Terms of Service). **Gunakan dengan risiko sendiri.**

> [!NOTE]
> **Hanya Android 8.0 (API 26) dan versi baru.** Tidak ada build iOS, dan tidak diperlukan PC, Mac, atau Linux — aplikasi menerapkan protokol ADB sendiri.

---

## Features

- **Config generator** — menulis 5 file INI (`Engine.ini`, `Scalability.ini`, `GameUserSettings.ini`, `DeviceProfiles.ini`, `Hardware.ini`) disesuaikan untuk chipset Anda di Snapdragon, Dimensity, Exynos, dan Tensor. Menetapkan `FullscreenMode=0` untuk menghindari bug viewport windowed Android.
- **SmartBrain device scoring** — skor 0–100 yang diderivasi dari GPU tier, RAM, resolusi, support Vulkan, perilaku termal, dan frame drops tercatat, dipetakan ke preset yang direkomendasikan.
- **8 quality presets** — `potato` → `cinematic`, setiap tingkat memiliki rank detail sendiri. Setiap tier sebenarnya berbeda dalam screen percentage, shadow resolution, SSR, mip bias, streaming multiplier, view distance, foliage LOD, LOD bias, dan grass culling.
- **CVar database** — 5,372 CVar engine plus 735 kunci yang diawasi. CVar redundant, tidak dikenal, dan mati platform dikomentari dengan marker `; [CVarDB] <reason>`. Mode terbatas strips 31 CVar yang diketahui membuat aplikasi tidak stabil.
- **Engine generation detection** — membaca banner `++UE4+…` / `++UE5+…` dari `Client.log` dan menekan CVar hanya untuk UE5 yang tidak aktif pada build UE4.
- **Gacha pity tracker** — semua 11 pool convene, tanpa batas pencatatan, dengan state guaranteed/50-50 per banner dan prediksi 5★ selanjutnya.
- **Battle stats & player profile** — mendekripsi dan menganalisis `Client.log` untuk figur combate, dodge, gerak, dan echo-skill, serta UID baca-saja, server, dan tower.
- **Character build guide** — login passport Kuro, pilihan akun dalam game, pemilihan karakter, dan build per karakter (peralatan terpasang plus rekomendasi resmi dari Kuro).
- **Material calculator** — biaya progres karakter dan senjata (level, ascension, dan level keterampilan) dari tabel pengambilan wiki, dengan total "Shell Credit".
- **Backup, restore & hash monitor** — backup otomatis sebelum menulis, snapshot MD5/reconcile untuk bertahan saat game menimpa file, dan pulih satu sentuh.
- **INI editor & log viewer** — editor monospace dengan line diff, pencarian, dan verifikasi hash sebelum deploy; tampilan log berwarna dengan rotasi 5 MB.
- **System agent surface** — 13 [AppFunctions](#appfunctions) hanya baca sehingga asisten di perangkat bisa meminta preset, menganalisis log, dan membaca riwayat deploy.

<p align="center">
  <img src="screenshots/screen-01.webp?v=1.1.7" width="180" alt="Halaman utama dengan status koneksi backend dan opsi cepat">
  <img src="screenshots/screen-02.webp?v=1.1.7" width="180" alt="Config generator dengan SmartBrain score dan pemilihan preset">
  <img src="screenshots/screen-03.webp?v=1.1.7" width="180" alt="Gacha pity tracker dengan riwayat pull dan breakdown rarity">
  <img src="screenshots/screen-04.webp?v=1.1.7" width="180" alt="Player profile dengan level akun, progres season, dan info perangkat">
  <img src="screenshots/screen-05.webp?v=1.1.7" width="180" alt="Battle stats analyzer dengan kartu combat dan eksplorasi">
  <img src="screenshots/screen-06.webp?v=1.1.7" width="180" alt="Layar tampilan Appearance dengan UI Style Engine dan palette warna">
  <img src="screenshots/screen-07.webp?v=1.1.7" width="180" alt="Karakter dengan roster Kuro yang disinkronisasi, senjata, dan resonance">
</p>

---

## Quick start

1. Instal APK terbaru dari [Releases](https://github.com/B3rr7/WuWa-Config-Android/releases) (Android 8.0+; izinkan *Install unknown apps* jika diminta).
2. Buka aplikasi, terima syarat, dan berikan izin penyimpanan yang diminta.
3. Hubungkan dengan salah satu [backends](#backends) di bawah ini.
4. Pergi ke **Config Generator** → review SmartBrain score → pilih preset → **Generate**.
5. **Review & Tune** menampilkan diff terhadap file live. Edit apa saja yang Anda inginkan, lalu **Deploy**.

> [!TIP]
> Selalu Deploy dengan game tertutup. Hash monitor akan meresonakan tulisan game, tetapi klien yang berjalan akan menimpa bagian dari apa yang Anda baru saja push.

---

## Kata kunci sekcja

### 🚀 fix lag

| Gejala | Solusi |
|---|---|
| FPS di bawah target | Turunkan screen % ke 60–80%, shadow ke 0–2, periksa `r.FramePace.MaxFPS` |
| Ponsel mengalami throttling | Atur `ReflectionEnvironment=0`, potong lampu dinamis dan volumetric fog, aktifkan auto-cooling |
| GPU crash out of memory | Bayangkan `MaxResolution` 1024, anisotropy 4, `PoolSize` 512, tanpa paket texture HD |
| Tekstur hilang atau rusak | Aktifkan `r.HZBOcclusion=1`, mip bias 0–1, tambah `PoolSize`, verifikasi DeviceProfiles |
| Delay / tidak merata | Kurangi render scale jika bound CPU, nonaktifkan VSync, batas MaxFPS ke 60 atau 120 |
| 120 FPS tidak bisa dibuka | Aktifkan toggle 120 FPS, verifikasi `r.FramePace.MaxFPS=120`, pasti thermal headroom |
| H artefact post-process | Post-process 0–2, bloom 0–1, nonaktifkan auto-exposure, kurangi radial blur |
| `Permission denied` saat Deploy | Gunakan fallback `run-as`, atau pindah ke **SAF** / **Root** |
| Wireless debugging tidak terkoneksi | Same network dengan target, aktifkan kembali wireless debugging, cek firewall |
| Shizuku ditolak di ROM China | Aktifkan *USB debugging (Security settings)*, re-authorize Shizuku |

### 🎮 best settings

| Chipset | Preset | Key CVars |
|---|---|---|
| **Snapdragon 8-series** | `high` atau `ultra` | `r.MipBias=0`, `r.Shadow.MaxResolution=2048`, `r.Streaming.InfiniteRadius=1` |
| **Dimensity 8-series/9-series** | `balanced` atau `high` | `r.MipBias=0`, `r.Shadow.MaxResolution=1024`, `r.Streaming.InfiniteRadius=0` |
| **Exynos 2200/9-series** | `performance` atau `balanced` | `r.MipBias=-1`, `r.Shadow.MaxResolution=1024`, `r.Streaming.Multiplier=2.0×` |
| **Google Tensor** | `potato` atau `endurance` | `r.MipBias=3`, `r.Shadow.MaxResolution=128`, `r.Streaming.Multiplier=0.3×` |

### ⚡ 120 FPS

- Aktifkan **toggle 120 FPS unlock** di Config Generator
- Periksa `r.FramePace.MaxFPS=120` muncul di `Engine.ini` yang dihasilkan
- Pastikan thermal headroom — mempertahankan 120 FPS berkelanjutan membutuhkan sistem pendingin yang memadai
- Di Snapdragon 8-series: gabung dengan preset `high`/`ultra` untuk hasil terbaik
- Di Dimensity/Exynos: gunakan preset `balanced` dan turunkan shadow/resolution jika tidak stabil
- Nonaktifkan VSync baik di aplikasi maupun pengaturan sistem untuk FPS maksimum

### 📱 per-chipset

| Chipset Family | Preset Disarankan | Screen % | Shadow Res | SSR | Streaming Mip Bias |
|---|---|---|---|---|---|
| **Snapdragon 8 Gen 3** | `ultra` | 100% | 4096 | 4 | -2 |
| **Snapdragon 8 Gen 2** | `ultra` | 100% | 4096 | 4 | -2 |
| **Snapdragon 888** | `high` | 100% | 2048 | 2 | 0 |
| **Dimensity 9200** | `high` | 100% | 2048 | 2 | 0 |
| **Dimensity 8200** | `balanced` | 80% | 1024 | 1 | 0 |
| **Exynos 2200** | `performance` | 80% | 1024 | 2 | 0 |
| **Exynos 980** | `endurance` | 60% | 128 | 0 | 3 |
| **Google Tensor G3** | `potato` | 60% | 128 | 0 | 3 |
| **MediaTek Dimensity 700** | `potato` | 60% | 128 | 0 | 3 |

---

## Backends

Aplikasi membaca dan menulis
`Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android`.
Pilih backend yang sesuai dengan perangkat Anda:

| Backend | Shell | File push | Log reading | Config gen | Needs | Best for |
|:--|:--:|:--:|:--:|:--:|:--|--|
| **ADB** | Yes | Yes | Yes | Yes | Wireless debugging | Non-rooted, no PC |
| **Shizuku** | Yes | Yes | Yes | Yes | Shizuku service | Non-rooted dengan Shizuku |
| **Root** | Yes | Yes | Yes | Yes | `su` | Magisk / KernelSU / APatch |
| **SAF** | No | Yes | Limited | No | Folder grant | Edit tangan sekali, tidak ada yang lain |

### ADB — wireless debugging, tidak PC

Protokol ADB wire diterapkan dalam-process (`adb/AdbProtocol.kt`, header 24-byte, RSA-2048 auth), sehingga tidak perlu `adb` binary dan tidak ada PC di dalam loop.

1. Aktifkan **Wireless debugging** di Developer options.
2. Tekan **Connect** — port scanner memindai 37 000–44 000 plus 5555.
3. Terima prompt fingerprint RSA di perangkat.

Jika Anda pun PC, jalur klasik tetap berkerja:

```bash
adb tcpip 5555
adb connect 192.168.1.42:5555
# cabut ketika selesai
adb disconnect 192.168.1.42:5555
adb usb
```

> [!IMPORTANT]
> **Android 11+ dan ROM China (Xiaomi/HyperOS, vivo, OPPO)** menolak pengguna `shell` menulis ke `Android/data/`. `Permission denied` memicu retry otomatis `run-as`. Jika game tidak debuggable, retry tidak akan kerja — pindah ke **SAF** atau **Root**.

### Shizuku — Binder IPC

Instal [Shizuku](https://shizuku.rikka.app/), mulai layanan, lalu pilih **Shizuku → Permit → Connect**. Aplikasi berbicara dengan `ShellUserService` melalui UserService API Shizuku, dengan script-file fallback untuk payloads di atas 4096 karakter.

> [!TIP]
> Jika Shizuku melaporkan bind timeout, aktifkan **USB debugging (Security settings)** bersama-sama dengan USB debugging reguler dan re-authorize. Banyak ROM mempatch `makeApplicationInner()` Shizuku; `RomBackgroundSettings` di dalam-app memandu Anda melalui perbaikan.

### Root — `su -c`

Pilih **Root → Test Root**, terima permintaan, lalu **Connect**. Root adalah backend satu-satunya yang totaland reliable di dalam `Android/data/` pada Android 11+.

### SAF — Storage Access Framework

Pilih **SAF → Pick Dir** dan berikan hak aturannya pada tree game. Picker terbuka sebelumnya ditujukan ke `Android/data/com.kurogame.wutheringwaves.global`, yang Android 11+ menyembunyikan.

> [!NOTE]
> SAF hanya file-level — tidak ada shell. Membaca log dan config generator tidak ada dengan sengaja dirancang pada backend ini; ia ada untuk edit tangan langsung.

---

## Presets

Setiap preset berada di rank detail masing-masing, sehingga dua tier tidak pernah menghasilkan output yang sama.

| Preset | Detail | Screen % | Shadow | Shadow res | SSR | Mip bias | Streaming | View dist | Foliage LOD | LOD bias | Grass cull |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| **potato** | 0 | 60% | 0 | 128 | 0 | 3 | 0.3× | 0.3 | 0.4 | 5 | 1 500 |
| **endurance** | 1 | 70% | 0 | 128 | 0 | 3 | 0.4× | 0.4 | 0.5 | 4 | 2 500 |
| **performance** | 2 | 60% | 0 | 256 | 0 | 3 | 0.5× | 0.5 | 0.6 | 3 | 4 500 |
| **competitive** | 3 | 100% | 2 | 256 | 0 | 1 | 1.0× | 2.0 | 1.0 | 1 | 2 000 |
| **balanced** | 4 | 80% | 2 | 1 024 | 1 | 0 | 2.0× | 1.5 | 2.0 | 0 | 15 000 |
| **high** | 5 | 100% | 4 | 2 048 | 2 | 0 | 3.0× | 2.0 | 2.5 | 0 | 20 000 |
| **ultra** | 6 | 100% | 5 | 2 048 | 4 | −1 | 4.0× | 3.0 | 3.0 | −1 | 30 000 |
| **cinematic** | 7 | 100% | 5 | 4 096 | 4 | −2 | 6.0× | 4.0 | 4.0 | −2 | 40 000 |

**Toggles:** 120 FPS unlock, ultra-quality unlock, VSync, auto-cooling, forced Vulkan,
HZB occlusion, per-feature disables (fog, chromatic aberration, outlines, motion blur,
bloom, auto-exposure, SSR), `Hardware.ini` generation dari chipset terdeteksi, dan
GameMode (Overworld / Tower of Adversity).

Lantai opsi penuh ter dokumentasi di
[docs/CONFIG-GENERATOR.md](docs/CONFIG-GENERATOR.md).

---

## Troubleshooting

| Gejala | Solusi |
|---|---|
| FPS di bawah target | Turunkan screen % ke 60–80%, shadow ke 0–2, periksa `r.FramePace.MaxFPS` |
| Ponsel mengalami throttling | Atur `ReflectionEnvironment=0`, potong lampu dinamis dan volumetric fog, aktifkan auto-cooling |
| GPU crash out of memory | Bayangkan `MaxResolution` 1024, anisotropy 4, `PoolSize` 512, tanpa paket texture HD |
| Tekstur hilang atau rusak | Aktifkan `r.HZBOcclusion=1`, mip bias 0–1, tambah `PoolSize`, verifikasi DeviceProfiles |
| Delay / tidak merata | Kurangi render scale jika bound CPU, nonaktifkan VSync, batas MaxFPS ke 60 atau 120 |
| Exynos atau Mali kalah performa | Mulai satu tier di bawah equivalent Snapdragon; kurangi shadow dan streaming quality |
| 120 FPS tidak bisa dibuka | Aktifkan toggle, verifikasi `r.FramePace.MaxFPS=120`, pasti thermal headroom |
| H artefact post-process | Post-process 0–2, bloom 0–1, nonaktifkan auto-exposure, kurangi radial blur |
| `Permission denied` saat Deploy | Gunakan fallback `run-as`, atau pindah ke **SAF** / **Root** |
| Wireless debugging tidak terkoneksi | Same network dengan target, aktifkan kembali wireless debugging, cek firewall |
| Shizuku ditolak di ROM China | Aktifkan *USB debugging (Security settings)*, re-authorize Shizuku |

---

## Build

**Yêubutuhan:** JDK 17, Android SDK dengan **platform 37** terinstal. Gradle sendiri disediakan oleh wrapper (9.6.0).

```bash
git clone https://github.com/B3rr7/WuWa-Config-Android.git
cd WuWa-Config-Android

./gradlew ktlintCheck        # style gate (CI enforces this)
./gradlew lint               # Android Lint
./gradlew testDebugUnitTest  # 66 test classes, 988 tests
./gradlew assembleDebug      # app/build/outputs/apk/debug/WuWaConfig-debug.apk
```

Jalankan 4 perintah ini dalam urutan sebelum membuka PR — itu urutan yang sama CI gunakan.

> [!IMPORTANT]
> **Release signing tidak ada fallback keystore, tujuannya.** `assembleRelease` gagal dengan `GradleException` kecuali *empat* `storeFile`, `storePassword`, `keyAlias`, dan `keyPassword`hadir — melalui `keystore.properties` di root repo (gitignored) atau variabel lingkungan `STORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`. APK yang dicetak sendirian tanpa jelas akan lewat ujian certificate UpdateManager dan ditawarkan kepada setiap pengguna.

Build debug menggunakan `applicationIdSuffix = ".debug"`, sehingga mereka memiliki storage dan preferences masing-masing dan ditolak UpdateManager di atas install release — dan sebaliknya.

> [!NOTE]
> `compileSdk` **37 adalah tepi batu**, bukan pilihan: `android.app.appfunctions` hanya ada di `android-37`'s `android.jar`, dan turun ke 36 memecah `kspDebugKotlin`. `targetSdk` tetap 36. Setelah mengubah versi manapun di `gradle/libs.versions.toml`, perlu memperbarui file pin dependency:

```bash
./gradlew --write-verification-metadata sha256 help
```

`gradle/verification-metadata.xml` sudah di-commit dan wajib; tanpa ia build akan gagal.

### Tech stack

| Area | Pilihan |
|---|---|
| Bahasa | Kotlin 2.2.20, JDK 17, AGP 9.4.0, KSP 2.3.12 |
| UI | Jetpack Compose, Material 3, BOM 2026.04.01 |
| Navigation | Navigation 3 (`navigation3-runtime` / `-ui` 1.1.7), `@Serializable` route keys tipe |
| Architecture | MVVM — 11 `AndroidViewModel`s + `StateFlow`, `Application` sebagai service locator |
| Async | kotlinx-coroutines 1.11.0 |
| Media | Coil 3.3.0, Media3 ExoPlayer 1.7.1 |
| Security | `AndroidKeyStore` platform (AES-256-GCM), Shizuku UserService, DocumentFile |
| Serialization | Gson 2.13.2, kotlinx-serialization (nav keys) |
| Agents | `androidx.appfunctions` 1.0.0-alpha11 |
| SDK | min 26 · target 36 · compile 37 |
| Kualitas | ktlint 12.1.0 · Android Lint · JUnit 4.13.2 · Mockito 5.11.0 |

---

## Architecture

```
WuWaConfigApp (Application)  — service locator, no DI framework
├── AccessBackend ──┬── AdbBackend      wire protocol, base64 chunked push, run-as fallback
│                   ├── ShizukuBackend  ShellUserService Binder IPC, script-file fallback
│                   ├── RootBackend     su -c
│                   └── SafBackend      DocumentFile, persistable tree URI
├── CvarDatabase    5,372 CVars, async-loaded from assets
├── ConfigGenerator 8 presets → 5 INIs
└── LogRepository   ring buffer, 1 000 entries

MainActivity — single Activity, Navigation 3
├── 16 type-safe destinations (nav/Destinations.kt)
└── 11 ViewModels (all Activity-scoped)
    └── Screens → Components → Theme
```

<details>
<summary><b>Package map — 122 file Kotlin</b></summary>

```
app/src/main/java/com/wuwaconfig/app/

├── MainActivity.kt               the single Activity; NavDisplay, entryProvider, transitions
├── WuWaConfigApp.kt              Application — service locator, backend, CvarDatabase, generator

├── adb/                          the ADB wire protocol, implemented in-process (no PC daemon)
│   ├── AdbProtocol.kt            24-byte header, CRC32, frame encode/decode, typed failures
│   ├── AdbClient.kt              TCP transport, RSA auth handshake, 15s heartbeat, run-as wrapper
│   ├── AdbCrypto.kt              RSA-2048 keypair generation and the adb public/private key files
│   ├── AdbKeyVault.kt            AES-256-GCM at rest, keyed by the platform AndroidKeyStore
│   └── PortScanner.kt            sweeps 37 000–44 000 + 5555, banner sniffing, 30s IP cache

├── backend/                      the four ways to reach the game's files
│   ├── AccessBackend.kt          AccessMethod enum, BackendStatus, the interface itself
│   ├── AdbBackend.kt             base64-chunked push over AdbClient, wireless pairing, run-as fallback on denial
│   ├── ShizukuBackend.kt         Shizuku UserService IPC, 45s bind (retried), 75s calls, script-file fallback
│   ├── RootBackend.kt            su -c, 10s timeout
│   ├── SafBackend.kt             DocumentFile over a persistable tree URI — no shell
│   └── ShellUtils.kt             shQuote, computeMd5, runAsCommand, PUSH_RETRY_COUNT

├── config/                       generation, analysis, persistence
│   ├── ConfigGenerator.kt        PresetProfile, the 8 PRESETS, the 5 INI builders
│   ├── ConfigGenUtil.kt          dedupe, entry parsing, CVar overrides, dead-CVar marking
│   ├── ConfigManager.kt          facade over BackupStore / ProfileExtractor / HashMonitor
│   ├── TuningProfile.kt          externalised tuning data (presets, paths, classifier patterns)
│   ├── GameProfile.kt            externalised game knowledge (paths, log names, CVar lists, pity)
│   ├── CvarDatabase.kt           5,372 CVars from assets, optimizeIniText rewriter
│   ├── CvarCategorizer.kt        3-level prefix/substring matching → 19 categories
│   ├── CvarOptimizer.kt          GPU-tier regex → tuned profile for the current device
│   ├── CvarPlatformScope.kt      OS × API-level × engine-generation CVar applicability
│   ├── ForbiddenCvars.kt         31 unstable CVars, stripped in restricted mode
│   ├── SmartBrain.kt             0–100 device score → recommendPreset()
│   ├── LogParser.kt              XOR-LUT decrypt, UTF-16/8 detect, battle stats, gacha URLs
│   ├── ProfileExtractor.kt       reads/decodes Client.log, verifies deployed CVars
│   ├── HashMonitor.kt            KuroConfigMonitor MD5s, atomic patch, concurrent-write detect
│   ├── HashSync.kt               one shared device hash-sync check for deploy + editor
│   ├── IniHashUtil.kt            extractHash() — pulls one file's hash out of the monitor file
│   ├── BackupStore.kt            backup CRUD, backup/public dirs, Client.log persistence
│   ├── GachaApi.kt               convene history fetch, 11 pools, pity computation
│   ├── GachaHistoryStore.kt      gacha_history.json, 12h TTL
│   ├── GachaStats.kt             lifetime gacha aggregates (pulls, avg pity, 50/50 rate)
│   ├── DeployHistoryStore.kt     deploy_history.json, 20 records
│   ├── ProfileStore.kt           player_profile.json
│   ├── KuroClient.kt             HTTP client for Kuro's guide + gacha endpoints
│   ├── KuroGuide.kt              official character guide + featured-character flags
│   ├── KuroSession.kt            Kuro passport session (login, account, token)
│   ├── KuroSessionStore.kt       persisted Kuro session
│   ├── CharacterBuild.kt         equipped gear + recommended build for one character
│   ├── PlayerRoster.kt           the player's owned characters and their builds
│   ├── BuildSummary.kt           aggregate build summary
│   ├── MaterialCalculator.kt     character/weapon progression cost lookup
│   ├── MaterialData.kt           material tables loaded from calculator_materials.json
│   └── ChipsetDetector.kt        Snapdragon / MediaTek / Exynos / Tensor identification

├── data/                         theme engine persistence (the project's only DataStore)

├── model/                        data classes, stores and the log buffer
│   ├── PresetModels.kt           GameMode, CvarEntry, GeneratorOptions (29 fields), GeneratedIni
│   ├── GamePaths.kt              every on-device path the app touches, in one place
│   ├── LogInfo.kt                device facts parsed out of the log banner
│   ├── LogEntry.kt               LogLevel, LogEntry
│   ├── LogRepository.kt          1 000-entry ring buffer + credential redaction
│   ├── LogAnalysisStore.kt       cached_log_analysis.json, 24h TTL
│   ├── PlayerProfile.kt          read-only UID, server, level, tower
│   ├── BattleStats.kt            combat, dodge, movement, echo-skill figures
│   ├── BattleStatsStore.kt       cached_battle_stats.json, 24h TTL
│   ├── ConfigHashInfo.kt         per-file MD5 snapshot
│   ├── ConfigPreset.kt           ConfigFile, ConfigBackup
│   ├── DeployRecord.kt           DeployRecord, DeployComparison
│   ├── GachaRecord.kt            GachaPoolType (11 pools), GachaData, pity state
│   ├── CalculatorModels.kt       CalculatorCharacter/Weapon/Result, MaterialGroup/Item
│   ├── CacheTtl.kt               TTL helper shared by the cached stores
│   └── VerificationReport.kt     CvarCategory (19), CvarDetail, VerificationReport

├── nav/                          Navigation 3
│   ├── Destinations.kt           16 @Serializable NavKey objects, ALL_DESTINATIONS, startDestination
│   ├── NavigationState.kt        wraps a single rememberNavBackStack
│   └── Navigator.kt              the only code allowed to mutate the back stack

├── service/
│   ├── AdbConnectionService.kt   dataSync foreground service holding the ADB socket
│   └── ShellUserService.kt       Binder hosted by Shizuku's UserService process

├── appfunctions/                 system-agent surface (13 read-only functions)
│   ├── BaseWuWaAppFunctionService.kt   the @AppFunction entry points
│   ├── AppFunctionModels.kt            the deliberately reduced @AppFunctionSerializable DTOs
│   └── AppFunctionMapping.kt           wire↔domain mappings, frame caps, preset blurbs

├── ui/
│   ├── MainViewModel.kt          primary shared state holder
│   ├── DeployHistoryViewModel.kt deploy + verify coordination
│   ├── BackupViewModel.kt        backup CRUD and directory prefs
│   ├── GachaViewModel.kt         pity tracker
│   ├── ProfileViewModel.kt       player profile
│   ├── LogInsightsViewModel.kt   log analysis, SmartBrain, battle stats
│   ├── IniEditorViewModel.kt     editor document, search, diff
│   ├── SettingsViewModel.kt      theme, prefs, update state, C# env probe
│   ├── ThemeViewModel.kt         theme picker state
│   ├── CalculatorViewModel.kt    material / progression calculator
│   ├── CharacterViewModel.kt     Kuro passport login, character selection, builds
│   ├── DeviceOps.kt              app-scoped mutex serializing every device-touching operation
│   ├── components/Components.kt  GlassCard, GlassButton, GradientBackground, GlitchText, LogViewer
│   ├── theme/
│   │   ├── Color.kt              single immutable neon palette behind one state holder
│   │   ├── Theme.kt              Material 3 schemes, edge-to-edge, dynamic colour
│   │   └── Type.kt               Rajdhani, serif and monospace families + Typography
│   └── screens/                  16 screens
│       ├── SetupScreen.kt            onboarding
│       ├── TermsScreen.kt            terms of use
│       ├── UserGuideScreen.kt        local HTML guide in a WebView
│       ├── HomeScreen.kt             connection status, quick actions, SAF picker
│       ├── ConfigGenScreen.kt        preset selection and generation
│       ├── ReviewTuneScreen.kt       diff, inline edit, deploy
│       ├── IniEditorScreen.kt        full-screen monospace editor with search
│       ├── BackupScreen.kt           backups and restore
│       ├── HistoryScreen.kt          deploy history
│       ├── PityScreen.kt             gacha pity tracker
│       ├── ProfileScreen.kt          player profile
│       ├── BattleStatsScreen.kt      battle stats
│       ├── LogsScreen.kt             colour-coded log viewer
│       ├── SettingsScreen.kt         preferences, theme, updates
│       ├── ThemeSettingsScreen.kt    style × palette × mode picker
│       ├── MyCharacterScreen.kt      Kuro character view: login, builds, gear
│       └── CalculatorScreen.kt       material / progression calculator

├── util/
│   ├── LineDiff.kt           DiffLine/DiffResult, LineDiff.compute, Hashing.md5Of
│   ├── AtomicFile.kt         temp + fsync + rename(2), so a crash can't truncate a store
│   ├── Format.kt             formatBytes() — 1024-based byte-count display
│   ├── LocalOnlyImage.kt     asserts a Coil URI is a content:// the user picked
│   ├── SensitiveClipboard.kt clipboard copies marked EXTRA_IS_SENSITIVE
│   └── RomBackgroundSettings.kt  deep-links the ROM's background-process settings

├── update/UpdateManager.kt   GitHub release check; refuses an APK whose cert differs

└── assets/cvars/              libUE4_cvars.txt (5,372 active) + config_monitor_{cvars,values}.txt (735 each)
```

</details>

### Keputusan desain yang menonjol

- **Navigation 3 menggantikan Navigation 2.** Rute lama berupa string sekarang menjadi `@Serializable` `NavKey` objects, sehingga kesalahan typ di `navigate()` adalah error compile, bukan no-op sunyi. `DestinationsTest` mengunci setiap key serial name: `rememberNavBackStack` menyimpan stack state melalui saved state, sehingga pengubahan nama key akan merusak pemulihan back-stack melalui update.
- **Semua 11 ViewModels dihoist ke `MainActivity`.** `navigation3-runtime` tidak memiliki decorator `ViewModelStoreOwner` per entry, sehingga `viewModel()` di dalam `entry` akan pelan-pelan ke Activity — ini adalah lingkup yang jelas.
- **Process UserService Shizuku terpisah.** `WuWaConfigApp.onCreate()` berjalan dua kali: sekali di dalam aplikasi, sekali sebagai uid 2000 tanpa akses storage aplikasi. `isUserServiceProcess()` guard pulang sebelum bootstrap apa pun; apa saja dilempark di atas guard ini akan membuat binder bind Shizuku timeout tanpa diagnostic.
- **CVar optimizer adalah rewriter, bukan validator.** Setiap barisnya sentuh tetap terjaga dengan comment `; [CVarDB] <reason>`, sehingga deploy selalu bisa dicek dan dibalik bukan kehilangan data secara tiba-tiba.

---

## AppFunctions

`BaseWuWaAppFunctionService` membuka 13 fungsi hanya baca untuk system agents — `listPresets`, `generateConfig`, `getDeviceProfile`, `recommendPreset`, `analyzeGameLog`, `getCachedLogAnalysis`, `getCachedBattleStats`, `getDeployHistory`, `getDeployOutcome`, `getGachaPrediction`, `getGachaStats`, `lookupCvar`, `listBackups`. KSP menciptakan subclass service dan assets schema-nya saat compile time.

Lihat benda di perangkat yang sudah terkoneksi:

```bash
adb shell cmd app_function list-app-functions

adb shell cmd app_function execute-app-function \
  --package com.wuwaconfig.app.debug \
  --function 'com.wuwaconfig.app.appfunctions.BaseWuWaAppFunctionService#listPresets'
```

> [!NOTE]
> Service adalah `exported="true"` karena kebutuhan — system adalah pemanggil — dan dibatasi oleh `android:permission="BIND_APP_FUNCTION_SERVICE".` Operasi yang mengubah perangkat sebenarnya *tidak* diakses: mereka butuh backend yang sedang hidup, dan beberapa akan menghancurkan state yang tidak bisa dipulihkan. Identitas akun di battle stats dan bearer token di gacha history diambil dari DTOs.

---

## Privasi & keamanan

- **Tidak ada telemetry.** Tidak ada Firebase, Crashlytics, atau Sentry. Aplikasi tidak pernah menghubungi server yang Anda tidak memulai request.
- **Jaringan lokal saja.** `network_security_config.xml` hanya mengizinkan cleartext ke `127.0.0.1` / `localhost`. Riwayat gacha diBroadcast dalam-process melalui `LocalBroadcastManager`.
- **Kunci dienkripsi di tempat.** Material kunci ADB ditulis melalui `AndroidKeyStore` AES-256-GCM key (`AdbKeyVault`). Ketergantungan `androidx.security:security-crypto` lama hanya bertahan agar blob Tink ditulis oleh release lama tetap bisa dibaca — key yang tidak bisa dibaca akan secara sendirian memutar identitas ADB pengguna dan memaksa otorisasi RSA baru.
- **Backup dinonaktifkan.** `allowBackup="false"`, sehingga tidak ada yang bisa diekstrak melalui Android backup.
- **Provider diakses melalui izin.** `ShizukuProvider` dikunci di balik `INTERACT_ACROSS_USERS_FULL`. Ini adalah kebutuhan Shizuku, bukan kesalahan pengabaian: `ShizukuProvider.call()` tidak melakukan pengecekan caller sendirinya, dan tanpa atribut ini, setiap aplikasi yang terinstall bisa mengambil binder Shizuku yang sedang hidup.

Lihat [SECURITY.md](SECURITY.md) untuk kebijakan keamanan penuh — untuk melaporkan celah, buka [GitHub Security Advisory](https://github.com/B3rr7/WuWa-Config-Android/security/advisories/new) pribadi dan jangan sertakan data sensitif dalam laporan isu.

---

## Credits

- **Kuro Games** — [Wuthering Waves](https://wutheringwaves.kurogames.com/), panduan resmi, SDK dan APIs gacha (builds karakter, coba featured-character, login passport, player roster, riwayat convene), dan seni karakter dan item yang dibundel di `gacha_avatars/` (181 file) dan `material_icons/` (148 file). Semua nama game, seni karakter dan data dalam-game adalah Kuro; setiap API call adalah user-initiated dan aplikasi tidak memegang credential Kuro pun.
- **Wuthering Waves Fandom Wiki** — sumber seni yang dibundel dan tabel biaya calculator diambil daripadanya, tetap terkini oleh `tools/download_gacha_avatars.py`, `tools/download_material_icons.py` dan `tools/update_calculator_data.py`.
- **Rajdhani Bold** (Indian Type Foundry, [SIL OFL 1.1](https://scripts.sil.org/OFL)) — typeface UI yang dibundel.

---

## Masyarakat

- [GitHub](https://github.com/B3rr7/WuWa-Config-Android) — issues dan releases
- [Website](https://b3rr7.github.io/WuWa-Config-Android/)
- [YouTube — Player42](https://www.youtube.com/@Player42_g)
- [Telegram](https://t.me/Yt_Player42) · [Discord](https://discord.gg/5WP9nN2e2s)

Laporan issues dan bug report adalah kontribusi yang paling berguna — model perangkat, ROM, backend yang digunakan, dan teks kesalahan yang jelas sangat membantu.

---

<div align="center">

**Made for Rovers — by Rovers.**

[Back to top](#wuwaconfig)

</div>