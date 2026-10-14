<!-- prettier-ignore -->

<div align="center">

<img src="app_icon.png" width="120" alt="WuWaConfig logo">

# WuWaConfig

**Tăng FPS · Cấu hình đồ họa · Phân tích thiết bị · Theo dõi may mắn · Bảo mật**

*Dịch bằng AI*

[![Release](https://img.shields.io/github/v/release/B3rr7/WuWa-Config-Android?label=Download&color=purple)](https://github.com/B3rr7/WuWa-Config-Android/releases)
[![Stars](https://img.shields.io/github/stars/B3rr7/WuWa-Config-Android?style=flat&logo=github)](https://github.com/B3rr7/WuWa-Config-Android/stargazers)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![No telemetry](https://img.shields.io/badge/telemetry-none-4CAF50)](https://github.com/B3rr7/WuWa-Config-Android)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

Một bộ công cụ Android mã nguồn mở miễn phí để tạo, tối ưu và triển khai
file cấu hình Unreal Engine cho Wuthering Waves — không cần root, không cần PC, không có telemetry.

[Features](#features) · [Quick start](#quick-start) · [Backends](#backends) · [Presets](#presets) · [Build](#build) · [Architecture](#architecture)

</div>

---

> [!WARNING]
> **Lưu ý hết phách** — WuWaConfig **không liên quan hoặc không được bảo trợ bởi Kuro Games hay Wuthering Waves**. Đây là công cụ do fan làm ra mục đích giáo dục và nghiên cứu. Nó chỉ chỉnh sửa file cấu hình cục bộ: không có chèn code, không có patch nhớ, không có thay đổi giao thức mạng. Việc chỉnh sửa file game có thể vẫn nằm dưới quyền Hợp đồng Sử dụng (Terms of Service). **Sử dụng tại chính mình rủi ro.**

> [!NOTE]
> **Chỉ Android 8.0 (API 26) và mới hơn.** Không có bản iOS, và không cần máy tính để bàn hay Linux — ứng dụng triển khai giao thức ADB tự thân.

---

## Features

- **Config generator** — viết 5 file INI (`Engine.ini`, `Scalability.ini`, `GameUserSettings.ini`, `DeviceProfiles.ini`, `Hardware.ini`) được tùy chỉnh cho chipset của bạn trên Snapdragon, Dimensity, Exynos và Tensor. Đặt `FullscreenMode=0` để tránh lỗi viewport cửa sổ Android.
- **SmartBrain device scoring** — một điểm số 0–100 được suy ra từ GPU tier, RAM, độ phân giải, 지원 Vulkan, hành vi nhiệt và frame drops đã đăng ký, được ánh xạ đến preset được khuyến nghị.
- **8 quality presets** — `potato` → `cinematic`, mỗi hạng mục riêng rank detail. Mỗi tier thực sự khác nhau về screen percentage, độ phân đèn shadow, SSR, mip bias, streaming multiplier, view distance, foliage LOD, LOD bias và grass culling.
- **CVar database** — 5,372 CVar của engine cộng 735 key được theo dõi. CVar thừa, chưa biết và chết nền tảng bị comment với marker `; [CVarDB] <reason>`. Chế độ giới hạn strips 31 CVar biết làm ứng dụng không ổn định.
- **Engine generation detection** — đọc banner `++UE4+…` / `++UE5+…` từ `Client.log` và áp dụng các CVar chỉ dành cho UE5 vô hiệu trên bản UE4.
- **Gacha pity tracker** — tất cả 11 pool convene, không giới hạn số lượt theo dõi, với trạng thái guaranteed/50-50 cho mỗi banner và dự đoán 5★ tiếp theo.
- **Battle stats & player profile** — giải mã và phân tích `Client.log` để lấy con số chiến đấu, tránh, di chuyển và echo-skill, cộng với UID đọc-only, server và tower.
- **Character build guide** — login Kuro passport, chọn tài khoản trong game, chọn nhân vật, và builds cá nhân (công cụ trang bị cộng khuyến nghị từ API chính thức của Kuro).
- **Material calculator** — chi phí tiến trình nhân vật và vũ khí (level, ascension và level kỹ năng) từ bảng giá wiki, với tổng "Shell Credit".
- **Backup, restore & hash monitor** — backup tự trước khi ghi, snapshot MD5/reconcile để tồn tại khi game ghi đè file, và khôi phục một chạm.
- **INI editor & log viewer** — biên tập monospace với line diff, tìm kiếm và hash xác thực trước khi deploy; xem log có màu với rotation 5 MB.
- **System agent surface** — 13 [AppFunctions](#appfunctions) chỉ đọc để trợ lý trên thiết bị có thể truy vấn preset, phân tích log và đọc lịch sử deploy.

<p align="center">
  <img src="screenshots/screen-01.webp?v=1.1.7" width="180" alt="Man hạc với trạng thái kết nối backend và các tác vụ nhanh">
  <img src="screenshots/screen-02.webp?v=1.1.7" width="180" alt="Config generator với SmartBrain score và lựa chọn preset">
  <img src="screenshots/screen-03.webp?v=1.1.7" width="180" alt="Gacha pity tracker với lịch sử pull và phân loại rarity">
  <img src="screenshots/screen-04.webp?v=1.1.7" width="180" alt="Player profile với cấp độ tài khoản, tiến độ season và thông tin thiết bị">
  <img src="screenshots/screen-05.webp?v=1.1.7" width="180" alt="Battle stats analyzer với thẻ chiến đấu và khám phá">
  <img src="screenshots/screen-06.webp?v=1.1.7" width="180" alt="Bảng hiển thị Appearance với UI Style Engine và palette màu">
  <img src="screenshots/screen-07.webp?v=1.1.7" width="180" alt="Nhân vật với thủ tục Kuro đồng bộ,무장 và resonance">
</p>

---

## Quick start

1. Cài bản APK mới nhất từ [Releases](https://github.com/B3rr7/WuWa-Config-Android/releases) (Android 8.0+; cho phép *Install unknown apps* nếu được nhắc).
2. Mở ứng dụng, chấp nhận điều khoản và cấp quyền lưu trữ mà nó yêu cầu.
3. Kết nối với một trong [backends](#backends) dưới đây.
4. Vào **Config Generator** → xem SmartBrain score → chọn preset → **Generate**.
5. **Review & Tune** cho thấy sự khác biệt so với file live. Sửa sửa gì bạn muốn, sau đó **Deploy**.

> [!TIP]
> Luôn deploy khi game đóng. Hash monitor sẽ đối soát các ghi chép của game, nhưng client đang chạy sẽ ghi đè phần nào file bạn vừa đẩy.

---

## Phím từ khóa

### 🚀 fix lag

| Triệu chứng | Giải pháp |
|---|---|
| FPS dưới mục tiêu | Giảm screen % xuống 60–80%, shadow xuống 0–2, kiểm tra `r.FramePace.MaxFPS` |
| Điện thoại giảm hiệu suất (throttling) | Đặt `ReflectionEnvironment=0`, cắt giảm đèn động và phun sương, bật auto-cooling |
| GPU crash out of memory | Đặt `MaxResolution` 1024, anisotropy 4, `PoolSize` 512, không dùng gói texture HD |
| Vật liệu bị mất hoặc hư | Bật `r.HZBOcclusion=1`, mip bias 0–1, tăng `PoolSize`, xác minh DeviceProfiles |
| Delay / giai đoạn không đều | Giảm render scale nếu CPU-bound, tắt VSync, giới hạn MaxFPS xuống 60 hoặc 120 |
| 120 FPS không mở | Bật toggle 120 FPS, kiểm tra `r.FramePace.MaxFPS=120`, đảm bảo nhiệt độ phù hợp |
| Hậu-process artefact | Post-process 0–2, bloom 0–1, tắt auto-exposure, giảm radial blur |
| `Permission denied` khi deploy | Sử dụng fallback `run-as`, hoặc chuyển sang **SAF** / **Root** |
| Wireless debugging không kết nối | cùng mạng với mục tiêu, bật lại wireless debugging, kiểm tra tường lửa |
| Shizuku bị từ chối trên ROM Trung Hoa | Bật *USB debugging (Security settings)*, cấp lại Shizuku |

### 🎮 best settings

| Chipset | Preset | Key CVars |
|---|---|---|
| **Snapdragon 8-series** | `high` hoặc `ultra` | `r.MipBias=0`, `r.Shadow.MaxResolution=2048`, `r.Streaming.InfiniteRadius=1` |
| **Dimensity 8-series/9-series** | `balanced` hoặc `high` | `r.MipBias=0`, `r.Shadow.MaxResolution=1024`, `r.Streaming.InfiniteRadius=0` |
| **Exynos 2200/9-series** | `performance` hoặc `balanced` | `r.MipBias=-1`, `r.Shadow.MaxResolution=1024`, `r.Streaming.Multiplier=2.0×` |
| **Google Tensor** | `potato` hoặc `endurance` | `r.MipBias=3`, `r.Shadow.MaxResolution=128`, `r.Streaming.Multiplier=0.3×` |

### ⚡ 120 FPS

- Bật **toggle 120 FPS unlock** trong Config Generator
- Kiểm tra `r.FramePace.MaxFPS=120` xuất hiện trong `Engine.ini` đã tạo
- Đảm bảo thermal headroom — duy trì 120 FPS liên tục cần hệ thống làm mát tốt
- Trên Snapdragon 8-series: kết hợp với preset `high`/`ultra` cho kết quả tốt nhất
- Trên Dimensity/Exynos: dùng preset `balanced` và giảm shadow/resolution nếu không ổn định
- Tắt VSync cả trong ứng dụng lẫn cài hệ thống để đạt FPS cao nhất

### 📱 per-chipset

| Chipset Family | Preset khuyên dùng | Screen % | Shadow Res | SSR | Streaming Mip Bias |
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

Ứng dụng đọc và ghi
`Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android`.
Chọn backend phù hợp với thiết bị của bạn:

| Backend | Shell | File push | Log reading | Config gen | Needs | Best for |
|:--|:--:|:--:|:--:|:--:|:--|--|
| **ADB** | Yes | Yes | Yes | Yes | Wireless debugging | Non-rooted, no PC |
| **Shizuku** | Yes | Yes | Yes | Yes | Dịch vụ Shizuku | Non-rooted với Shizuku |
| **Root** | Yes | Yes | Yes | Yes | `su` | Magisk / KernelSU / APatch |
| **SAF** | No | Yes | Limited | No | Phân quyền thư mục | Chỉ sửa tay, không cần |

### ADB — wireless debugging, không PC

Giao thức ADB wire được triển khai trong trình tự (`adb/AdbProtocol.kt`, header 24-byte, RSA-2048 auth), không cần `adb` binary và không cần PC trong vòng.

1. Bật **Wireless debugging** trong Developer options.
2. Nhấn **Connect** — port scanner quét 37 000–44 000 cộng 5555.
3. Chấp nhận yêu cầu fingerprint RSA trên thiết bị.

Nếu bạn có PC, con đường truyền thống vẫn hoạt động:

```bash
adb tcpip 5555
adb connect 192.168.1.42:5555
# gỡ bỏ khi đã xong
adb disconnect 192.168.1.42:5555
adb usb
```

> [!IMPORTANT]
> **Android 11+ và ROM Trung Hoa (Xiaomi/HyperOS, vivo, OPPO)** cấm người dùng `shell` ghi vào `Android/data/`. `Permission denied` kích hoạt thử lai tự động `run-as`. Nếu game không debuggable, thử lai không hoạt động — chuyển sang **SAF** hoặc **Root**.

### Shizuku — Binder IPC

Cài [Shizuku](https://shizuku.rikka.app/), bắt đầu dịch vụ, sau đó chọn **Shizuku → Permit → Connect**. Ứng dụng trò chuyện với `ShellUserService` qua API UserService của Shizuku, có fallback script-file cho payloads trên 4096 ký tự.

> [!TIP]
> Nếu Shizuku báo timeout bind, bật **USB debugging (Security settings)** kết hợp với USB debugging thông thường và cấp lại. Số ROM nhiều patch `makeApplicationInner()` của Shizuku; `RomBackgroundSettings` trong-app hướng dẫn bạn qua bản sửa chữa.

### Root — `su -c`

Chọn **Root → Test Root**, cấp xác nhận, sau đó **Connect**. Root là backend duy nhất hoàn toàn tin cậy bên trong `Android/data/` trên Android 11+.

### SAF — Storage Access Framework

Chọn **SAF → Pick Dir** và cấp cây thư mục của game. Picker mở sẵn tại `Android/data/com.kurogame.wutheringwaves.global`, Android 11+ ẩn đi cách khác.

> [!NOTE]
> SAF chỉ cấp file — không có shell. Xem log và config generator không có sẵn trên backend này theo thiết kế; nó tồn tại cho sửa tay trực tiếp.

---

## Presets

Mỗi preset nằm ở rank detail riêng, nên hai tier không bao giờ cùng đầu ra.

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
bloom, auto-exposure, SSR), `Hardware.ini` generation từ chipset được nhận diện, và
GameMode (Overworld / Tower of Adversity).

Bề mặt tùy chọn đầy đủ được ghi trong
[docs/CONFIG-GENERATOR.md](docs/CONFIG-GENERATOR.md).

---

## Troubleshooting

| Triệu chứng | Giải pháp |
|---|---|
| FPS dưới mục tiêu | Giảm screen % xuống 60–80%, shadow xuống 0–2, kiểm tra `r.FramePace.MaxFPS` |
| Điện thoại giảm hiệu suất (throttling) | Đặt `ReflectionEnvironment=0`, cắt giảm đèn động và phun sương, bật auto-cooling |
| GPU crash out of memory | Đặt `MaxResolution` 1024, anisotropy 4, `PoolSize` 512, không dùng gói texture HD |
| Vật liệu bị mất hoặc hư | Bật `r.HZBOcclusion=1`, mip bias 0–1, tăng `PoolSize`, xác minh DeviceProfiles |
| Delay / giai đoạn không đều | Giảm render scale nếu CPU-bound, tắt VSync, giới hạn MaxFPS xuống 60 hoặc 120 |
| Exynos hoặc Mali kém hiệu suất | Bắt đầu một tier dưới equivalent Snapdragon; giảm shadow và streaming quality |
| 120 FPS không mở | Bật toggle, kiểm tra `r.FramePace.MaxFPS=120`, đảm bảo thermal headroom |
| Hậu-process artefact | Post-process 0–2, bloom 0–1, tắt auto-exposure, giảm radial blur |
| `Permission denied` khi deploy | Sử dụng fallback `run-as`, hoặc chuyển sang **SAF** / **Root** |
| Wireless debugging không kết nối | Cùng mạng với mục tiêu, bật lại wireless debugging, kiểm tra tường lửa |
| Shizuku bị từ chối trên ROM Trung Hoa | Bật *USB debugging (Security settings)*, cấp lại Shizuku |

---

## Build

**Yêu cầu:** JDK 17, Android SDK với **platform 37** cài đặt. Gradle tự đi kèm (9.6.0).

```bash
git clone https://github.com/B3rr7/WuWa-Config-Android.git
cd WuWa-Config-Android

./gradlew ktlintCheck        # style gate (CI enforces this)
./gradlew lint               # Android Lint
./gradlew testDebugUnitTest  # 66 test classes, 988 tests
./gradlew assembleDebug      # app/build/outputs/apk/debug/WuWaConfig-debug.apk
```

Chạy 4 lệnh này theo thứ tự trước khi mở PR — đó là cùng thứ tự CI dùng.

> [!IMPORTANT]
> **Release signing không có fallback keystore, có đích xác.** `assembleRelease` báo `GradleException` trừ khi *cả bốn* `storeFile`, `storePassword`, `keyAlias` và `keyPassword` có sẵn — qua `keystore.properties` tại gốc repo (gitignored) hay biến môi trường `STORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`. Một APK ký một cách yên tĩnh sẽ vượt qua kiểm tra certificate của UpdateManager và được đưa ra cho mọi người dùng.

Build debug sử dụng `applicationIdSuffix = ".debug"`, nên họ có storage và preferences riêng và bị UpdateManager拒绝 cài đặt đối với bản release — và ngược lại.

> [!NOTE]
> `compileSdk` **37 là ngưỡng đá**, không phải sở thích: `android.app.appfunctions` chỉ có trong `android-37`'s `android.jar`, và hạ về 36 làm `kspDebugKotlin` không hoạt động. `targetSdk` vẫn là 36. Sau khi thay đổi bất kỳ phiên bản nào trong `gradle/libs.versions.toml`, làm mới file pin dependency:

```bash
./gradlew --write-verification-metadata sha256 help
```

`gradle/verification-metadata.xml` đã được commit và bắt buộc; không có nó build sẽ thất bại.

### Tech stack

| Khái chọn | Lựa chọn |
|---|---|
| Ngôn ngữ | Kotlin 2.2.20, JDK 17, AGP 9.4.0, KSP 2.3.12 |
| UI | Jetpack Compose, Material 3, BOM 2026.04.01 |
| Navigation | Navigation 3 (`navigation3-runtime` / `-ui` 1.1.7), `@Serializable` route keys có kiểu |
| Architecture | MVVM — 11 `AndroidViewModel`s + `StateFlow`, `Application` làm service locator |
| Async | kotlinx-coroutines 1.11.0 |
| Media | Coil 3.3.0, Media3 ExoPlayer 1.7.1 |
| Security | `AndroidKeyStore` nền tảng (AES-256-GCM), Shizuku UserService, DocumentFile |
| Serialization | Gson 2.13.2, kotlinx-serialization (nav keys) |
| Agents | `androidx.appfunctions` 1.0.0-alpha11 |
| SDK | min 26 · target 36 · compile 37 |
| Quality | ktlint 12.1.0 · Android Lint · JUnit 4.13.2 · Mockito 5.11.0 |

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

### Các quyết định thiết kế nổi bật

- **Navigation 3 thay Navigation 2.** Các route cũ dưới dạng string giờ là `@Serializable` `NavKey` objects, nên lỗi typ trong `navigate()` là lỗi compile chứ không phải no-op lặng lẽ. `DestinationsTest` khóa mỗi key serial name: `rememberNavBackStack` lưu stack state qua saved state, nên việc đổi tên key sẽ hỏng phục hồi back-stack qua update.
- **Tất cả 11 ViewModels đều hoisted lên `MainActivity`.** `navigation3-runtime` không có decorator `ViewModelStoreOwner` riêng cho mỗi entry, nên `viewModel()` bên trong `entry` sẽ rơi lặng lẽ qua Activity — đây là phạm vi rõ ràng.
- **Một process UserService riêng của Shizuku.** `WuWaConfigApp.onCreate()` chạy hai lần: một lần trong app, một lần như uid 2000 không có quyền truy cập storage của app. `isUserServiceProcess()` guard trả về trước bất cứ bootstrap nào; bất cứ thứ gì ném ra trên guard này sẽ khiến binder bind của Shizuku timeout mà không có diagnostic.
- **CVar optimizer là một rewriter, không phải validator.** Mỗi dòng nó chạm vào giữ nguyên với comment `; [CVarDB] <reason>`, nên deploy luôn có thể kiểm tra và hoàn tác thay vì mất mát vô hình.

---

## AppFunctions

`BaseWuWaAppFunctionService` bật 13 hàm chỉ đọc cho system agents — `listPresets`, `generateConfig`, `getDeviceProfile`, `recommendPreset`, `analyzeGameLog`, `getCachedLogAnalysis`, `getCachedBattleStats`, `getDeployHistory`, `getDeployOutcome`, `getGachaPrediction`, `getGachaStats`, `lookupCvar`, `listBackups`. KSP tạo subclass service và assets schema của nó lúc compile time.

Xem bề mặt trên thiết bị đã kết nối:

```bash
adb shell cmd app_function list-app-functions

adb shell cmd app_function execute-app-function \
  --package com.wuwaconfig.app.debug \
  --function 'com.wuwaconfig.app.appfunctions.BaseWuWaAppFunctionService#listPresets'
```

> [!NOTE]
> Service là `exported="true"` theo sự cần thiết — system là người gọi — và bị gated bởi `android:permission="BIND_APP_FUNCTION_SERVICE"`. Các hoạt động thay đổi thiết bị được *không* bao bọc: chúng cần backend đang sống, và vài cái sẽ hủy bỏ state không thể khôi phục lại. Mã nhận diện tài khoản trong battle stats và bearer token trong gacha history được loại khỏi DTOs.

---

## Bảo mật & quyền riêng tư

- **Không có telemetry.** Không có Firebase, Crashlytics hay Sentry. Ứng dụng không bao giờ liên hệ với server mà bạn chưa khởi động yêu cầu.
- **Mạng nội bộ.** `network_security_config.xml` chỉ cho phép cleartext tới `127.0.0.1` / `localhost`. Lịch sử gacha广播 trong-process qua `LocalBroadcastManager`.
- **Khóa được mã hóa tại chỗ.** Material khóa ADB được viết qua `AndroidKeyStore` AES-256-GCM key (`AdbKeyVault`). Phụ thuộc `androidx.security:security-crypto` cũ chỉ tồn tại để các Tink blobs viết bởi các bản release cũ vẫn đọc được — một key không đọc được sẽ vô hiệu hóa identitas ADB của user và ép buộc xác thực RSA mới.
- **Backup tắt.** `allowBackup="false"`, nên không có gì có thể trích xuất qua Android backup.
- **Providers được kiểm soát bằng quyền.** `ShizukuProvider` bị khóa sau `INTERACT_ACROSS_USERS_FULL`. Đây là yêu cầu Shizuku, không phải bỏ quên: `ShizukuProvider.call()` không kiểm tra caller riêng của nó, và không có attribute này bất kỳ app nào cài đặt có thể lấy binder Shizuku đang sống.

Xem [SECURITY.md](SECURITY.md) để xem chính sách bảo mật toàn phần — để báo lỗ hở, mở [GitHub Security Advisory](https://github.com/B3rr7/WuWa-Config-Android/security/advisories/new) riêng và không bao gồm dữ liệu nhạy cảm trong báo cáo vấn đề.

---

## Credits

- **Kuro Games** — [Wuthering Waves](https://wutheringwaves.kurogames.com/), hướng dẫn chính thức, SDK và APIs gacha (builds nhân vật, cờ featured-character, login passport, player roster, lịch sử convene), và nghệ phẩm nhân vật và item bundled trong `gacha_avatars/` (181 file) và `material_icons/` (148 file). Tất cả tên game, nghệ nhân art và dữ liệu trong-game là của Kuro; mỗi API call là do user khởi động và app không giữ bất kỳ credential Kuro nào.
- **Wuthering Waves Fandom Wiki** — nguồn nghệ phẩm bundled và bảng giá료 cho calculator được lấy từ, được cập nhật bởi `tools/download_gacha_avatars.py`, `tools/download_material_icons.py` và `tools/update_calculator_data.py`.
- **Rajdhani Bold** (Indian Type Foundry, [SIL OFL 1.1](https://scripts.sil.org/OFL)) — typeface UI bundled.

---

## Cộng đồng

- [GitHub](https://github.com/B3rr7/WuWa-Config-Android) — issues và releases
- [Website](https://b3rr7.github.io/WuWa-Config-Android/)
- [YouTube — Player42](https://www.youtube.com/@Player42_g)
- [Telegram](https://t.me/Yt_Player42) · [Discord](https://discord.gg/5WP9nN2e2s)

Báo cáo issues và bug report là đóng góp có ích nhất — thiết bị model, ROM, backend đang dùng, và đoạn text lỗi chính xác đi rất xa.

---

<div align="center">

**Made for Rovers — by Rovers.**

[Back to top](#wuwaconfig)

</div>