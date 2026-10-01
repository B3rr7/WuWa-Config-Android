# WuWaConfig — Config Generator Reference

## Preset Tiers

| Preset | Detail | Screen% | Shadow | ShadowRes | SSR | MipBias | Streaming | ViewDist | Foliage LOD | LOD Bias | GrassCull |
|--------|--------|---------|--------|-----------|-----|---------|-----------|----------|-------------|----------|-----------|
| **POTATO** | 0 | 60% | 0 | 128 | 0 | 3 | 0.3x | 0.3 | 0.4 | 5 | 1,500 |
| **ENDURANCE** | 1 | 70% | 0 | 128 | 0 | 3 | 0.4x | 0.4 | 0.5 | 4 | 2,500 |
| **PERFORMANCE** | 2 | 60% | 0 | 256 | 0 | 3 | 0.5x | 0.5 | 0.6 | 3 | 4,500 |
| **COMPETITIVE** | 3 | 100% | 2 | 256 | 0 | 1 | 1.0x | 2.0 | 1.0 | 1 | 2,000 |
| **BALANCED** | 4 | 80% | 2 | 1,024 | 1 | 0 | 2.0x | 1.5 | 2.0 | 0 | 15,000 |
| **HIGH** | 5 | 100% | 4 | 2,048 | 2 | 0 | 3.0x | 2.0 | 2.5 | 0 | 20,000 |
| **ULTRA** | 6 | 100% | 5 | 2,048 | 4 | -1 | 4.0x | 3.0 | 3.0 | -1 | 30,000 |
| **CINEMATIC** | 7 | 100% | 5 | 4,096 | 4 | -2 | 6.0x | 4.0 | 4.0 | -2 | 40,000 |

## GeneratorOptions

Mirrors `GeneratorOptions` in `app/src/main/java/com/wuwaconfig/app/model/PresetModels.kt`.

### Frame rate & rendering

- `fps` — Target frame rate written to `GameUserSettings.ini` (e.g. `60`, `90`, `120`)
- `unlock120` — Unlock the 120 FPS frame-pace cap
- `unlockUltra` — Unlock the Ultra quality tier
- `vsync` — Enable VSync
- `cool` — Auto cooling / thermal headroom
- `vulkan` — Force Vulkan renderer (global, not tier-scoped)
- `hzb` — Enable HZB occlusion
- `fog` — Disable volumetric fog
- `ca` — Disable chromatic aberration

### Feature trims

- `disableOutline` — Disable toon outlines
- `disableRadialBlur` — Disable radial blur
- `disableBloom` — Disable bloom
- `disableAutoExposure` — Disable auto exposure
- `disableSSR` — Disable screen-space reflections

### Safety & optimization

- `allowRestrictedCvars` — Keep the 31 CVars known to destabilise this build
- `optimizeWithCvarDb` — Comment out redundant / unknown / platform-dead CVars
- `experimentalCvars` — Enable the 23 experimental CVars (verified on Adreno 618)
- `enableGSR` — GSR upscaling (`[/Script/GSRTUModule.GSRSettings]`), aimed at low-end devices
- `useAdvancedGen` — Advanced per-device tuning
- `disableAutoAdjust` — Disable the game's automatic quality adjustment
- `importFromLog` — Seed the overrides from the CVars found in `Client.log`
- `mode` — `Overworld` or `ToA` (Tower of Adversity), which lightens the environment for the closed arena

### Output selection

- `generateEngine` / `generateDeviceProfiles` / `generateGameUserSettings` / `generateScalability` / `generateHardware`

### Custom overrides

- `cvarOverrides` — `Map<String, String>` written verbatim after the preset values, so any CVar in the database can be pinned by hand.

## GameMode

- `Overworld` — Standard exploration mode
- `ToA` — Tower of Adversity

## Output Files

1. `Engine.ini` — Core engine settings, renderer config, experimental CVars
2. `DeviceProfiles.ini` — Device-specific profiles
3. `GameUserSettings.ini` — User-facing graphics settings
4. `Scalability.ini` — Quality scalability groups (`KuroRenderQuality@0..@3`)
5. `Hardware.ini` — Chipset-specific tuning (via ChipsetDetector)

Every file is passed through the CVar database before it is written: redundant,
unknown and platform-dead lines are commented out with a `; [CvarDB] <reason>`
marker rather than deleted, so a single character restores them. `GameUserSettings.ini`
always sets `FullscreenMode=0`, which avoids the Android windowed-viewport bug.
