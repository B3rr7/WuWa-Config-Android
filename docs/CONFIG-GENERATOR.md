# WuWaConfig — Config Generator Reference

## Preset Tiers

| Preset | Detail | Screen% | Shadow | ShadowRes | SSR | MipBias | Streaming | ViewDist | Foliage LOD | LOD Bias | GrassCull |
|--------|--------|---------|--------|-----------|-----|---------|-----------|----------|-------------|----------|-----------|
| **POTATO** | 0 | 50% | 0 | 128 | 0 | 3 | 0.3x | 0.3 | 0.4 | 5 | 1,500 |
| **ENDURANCE** | 1 | 55% | 0 | 128 | 0 | 3 | 0.4x | 0.4 | 0.5 | 4 | 2,500 |
| **PERFORMANCE** | 2 | 60% | 0 | 256 | 0 | 3 | 0.5x | 0.5 | 0.6 | 3 | 4,500 |
| **COMPETITIVE** | 3 | 100% | 0 | 256 | 0 | 1 | 1.0x | 2.0 | 1.0 | 1 | 2,000 |
| **BALANCED** | 4 | 80% | 2 | 1,024 | 1 | 0 | 2.0x | 1.5 | 2.0 | 0 | 15,000 |
| **HIGH** | 5 | 100% | 4 | 2,048 | 2 | 0 | 3.0x | 2.0 | 2.5 | 0 | 20,000 |
| **ULTRA** | 6 | 100% | 5 | 2,048 | 4 | -1 | 4.0x | 3.0 | 3.0 | -1 | 30,000 |
| **CINEMATIC** | 7 | 100% | 5 | 4,096 | 4 | -2 | 6.0x | 4.0 | 4.0 | -2 | 40,000 |

## GeneratorOptions

- `fps120` — Unlock 120 FPS cap
- `ultraQuality` — Unlock ultra quality settings
- `vsync` — Enable VSync
- `autoCool` — Auto cooling
- `forceVulkan` — Force Vulkan renderer
- `hzbOcclusion` — Enable HZB occlusion
- `disableFog` — Disable volumetric fog
- `disableCA` — Disable chromatic aberration
- `disableOutlines` — Disable outlines
- `disableBlur` — Disable motion blur
- `disableBloom` — Disable bloom
- `disableAutoExposure` — Disable auto exposure
- `disableSSR` — Disable screen-space reflections
- `experimentalCvars` — Enable experimental CVars (verified on Adreno 618)

## GameMode

- `Overworld` — Standard exploration mode
- `ToA` — Tower of Adversity

## Output Files

1. `Engine.ini` — Core engine settings, renderer config, experimental CVars
2. `DeviceProfiles.ini` — Device-specific profiles
3. `GameUserSettings.ini` — User-facing graphics settings
4. `Scalability.ini` — Quality scalability groups
5. `Hardware.ini` — Chipset-specific tuning (via ChipsetDetector)
