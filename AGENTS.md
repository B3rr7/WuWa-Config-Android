# WuWaConfig — Agent Guide

## Build

```sh
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release (requires local release.jks, gitignored)
```

AGP 8.2.2, Kotlin 1.9.22, Java 17. No lint/typecheck commands — standard Gradle.

## Structure

Single `:app` module. Entrypoint: `WuWaConfigApp` (Application class), then `MainActivity` at
`app/.../ui/MainActivity.kt`.

```text
app/src/main/java/com/wuwaconfig/app/
├── WuWaConfigApp.kt          # Application, backend holder, ADB crypto init
├── MainActivity.kt           # 7-screen NavHost, permissions, terms gate
├── adb/                      # Custom ADB wire protocol (no external adb binary)
│   ├── AdbProtocol.kt        # Message encode/decode
│   ├── AdbClient.kt          # TCP socket, auth handshake, shell, push
│   ├── AdbCrypto.kt          # RSA key gen/signing
│   └── PortScanner.kt        # Port scan 37000-44000 + 5555
├── backend/
│   ├── AccessBackend.kt      # Interface + AccessMethod enum (ADB/SHIZUKU/ROOT/SAF)
│   ├── AdbBackend.kt         # Shell via ADB, run-as fallback, base64 push
│   ├── RootBackend.kt        # su -c (10s timeout)
│   ├── ShizukuBackend.kt     # Shizuku API via reflection (15s timeout)
│   └── SafBackend.kt         # DocumentFile with 3-strategy fallback
├── config/
│   ├── ConfigGenerator.kt    # INI generation — object with mutable activePreset/logInfo
│   ├── ConfigManager.kt      # Device I/O, backups, logs, profiles, hashes
│   ├── LogParser.kt          # XOR decryption, Convene URL extract, battle stats
│   ├── SmartBrain.kt         # Scoring engine — object singleton
│   ├── GachaApi.kt           # HTTP POST to Kuro's gacha API, pity calc
│   ├── GachaHistoryStore.kt  # Local persistence (12hr TTL)
│   ├── ProfileStore.kt       # Profile cache
│   ├── ChipsetDetector.kt    # SoC detection
│   └── BenchmarkTuner.kt     # Auto-tune: iterative FPS capture → adjust → redeploy
├── model/                    # Data classes
├── service/
│   ├── AdbConnectionService.kt   # ADB foreground service
│   └── GachaPollService.kt       # Gacha polling foreground service
└── ui/
    ├── MainViewModel.kt      # Single shared ViewModel (~880 lines)
    ├── components/Components.kt  # GlassCard, GradientBackground, GlitchText, etc.
    ├── screens/               # One file per screen (7 screens + Terms + Setup)
    └── theme/                 # Color, Theme, Type
```

## Architecture

- MVVM with a single `MainViewModel` shared across all screens via `viewModel()`.
- Navigation: Jetpack Navigation Compose with string routes: `home`, `backups`, `configgen`, `settings`, `pity`, `profile`, `battlestats`, `setup`, `terms`.
- `AccessBackend` interface abstracts device access. Swap method via `WuWaConfigApp.switchTo()`.
- `ConfigGenerator` and `SmartBrain` are `object` singletons with mutable state — reset `activePreset`/`logInfo` before generation.
- No DI framework — `WuWaConfigApp.instance` accessed globally from composables.
- State: `MutableStateFlow` + `StateFlow` exposed from ViewModel.

## Key facts

- **Game package**: `com.kurogame.wutheringwaves.global` — paths in `GamePaths.kt`.
- **Target dir**: `/storage/emulated/0/Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android`
- **Config files**: Engine.ini, DeviceProfiles.ini, GameUserSettings.ini, Scalability.ini, Hardware.ini
- **`usesCleartextTraffic="true"`** in manifest — required for local ADB connections.
- **No tests** exist in the repo. No CI.
- **Release signing**: credentials hardcoded in `app/build.gradle.kts`. **Do not commit changes to signing config or passwords.**
- **`.gitignore`**: `release.jks`, `*.apk`, `.gradle`, `/build`, `*.log`.
- **Assets**: `presets.json`, `cvar_knowledge.json`, `brain-data.json` in `app/src/main/assets/`.
- **Log decryption**: Wuthering Waves `Client.log` is XOR-encrypted; `LogParser` handles decryption.

## Code patterns & gotchas

- **File push** (ADB/Shizuku): base64 encode → chunked printf (4096) to `.wuwap42.b64` temp → `base64 -d` → rm. Not a direct file write.
- **Error handling**: `Result<T>` throughout. `friendlyBackendError()` in ViewModel maps raw messages to user strings.
- **`ConfigManager`** is lazily created per backend — recreate after switching backends.
- **Config deploy flow**: read device Engine.ini → extract `[Core.System]` paths → generate with `ConfigGenerator.generateWithCorePaths()` → push → MD5 hash refresh to `KuroConfigMonitor.hash`.
- **Log parsing**: single-pass line scan in `LogParser.parseLog()`. Battle stats use CN keywords. Convene URL regex: `https://aki-gm-resources(-oversea)?\.aki-game\.(net|com)/aki/gacha/index\.html#/record...`.
- **Profile**: pulls `LocalStorage.db` and `DeviceStorage.db` via base64, opens locally with `SQLiteDatabase`. Also parses `.sav` files via `strings | grep`.
- **Gacha**: POSTs JSON to Kuro gacha API endpoint (based on playerId prefix: `1` → `.aki-game2.com`, else `.aki-game2.net`). One request per pool type (11 types).
- **Background services**: `GachaPollService` broadcasts `"com.wuwaconfig.app.GACHA_DATA_READY"` intent. `AdbConnectionService` maintains ADB foreground notification.
- **Backup storage**: JSON files named `{uuid}.json` in configured backup dir. `ConfigBackup` objects with `type` field (`"auto"` or `"manual"`).
- **UI logging**: `addLog()` prepends timestamp, caps at 200 entries, color-coded (SUCCESS=green, FAILED=red, connected=cyan).

## Dependencies (key)

OkHttp 4.12, Gson 2.10, Coil 2.6, ExoPlayer 1.3, Shizuku API 13.1.5, DocumentFile 1.0, Compose BOM 2024.02.
