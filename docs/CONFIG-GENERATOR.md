# Config Generator — Presets & Fields

`ConfigGenerator` (`config/ConfigGenerator.kt`) builds 5 INI files from a preset name +
`GeneratorOptions`: `Engine.ini` · `DeviceProfiles.ini` · `GameUserSettings.ini` ·
`Scalability.ini` · `Hardware.ini`.

## Preset tiers

8 tiers live in `ConfigGenerator.PRESETS`, each a `PresetProfile` with distinct `detail`
ranks 0..7. `generate()` resolves a preset via `PRESETS[preset] ?: PRESETS["balanced"]`
(safe fallback — an unknown name can never reach a builder).

### Base `PresetProfile` scalars (tier-scoped)

| Field | Meaning |
|---|---|
| `screen` | resolution scale % |
| `shadow` / `shadowRes` | shadow quality / max shadow map res |
| `ssr` | screen-space reflections level |
| `mipbias` | texture mip bias (negative = sharper) |
| `streaming` | texture streaming pool scale |
| `vd` | view distance scale |
| `flod` | foliage LOD scale |
| `detail` | master quality rank (0..7), drives `q0/q1/q2` gates |
| `lod_bias` | skeletal/static LOD bias |
| `grasscull` | grass cull distance |

### Tier-scoped quality fields (added)

These four make each tier a coherent visual/perf tradeoff instead of only
screen/shadow/LOD scalars. They are **set by the preset**, not user-toggled.

| Field | Type | Writes | Notes |
|---|---|---|---|
| `characterDetail` | Int 0..3 | `r.KuroMaterialQualityLevel`, `r.Kuro.KuroToonFFTHighQuality` | material/toon quality. Gameplay effect on this title is **unverified** — flagged. |
| `postProcess` | Int 0..3 | `sg.PostProcessQuality` (GameUserSettings + Scalability) | replaced the old `q0/q1`-derived `postQ`. |
| `staticLighting` | Boolean | `r.AllowStaticLighting` | low/mid tiers disable for perf headroom. |
| `cutsceneQuality` | Int 0..3 | `r.Kuro.Movie.EnableCGMovieRendering` | only a boolean exists in the game; `>=1` enables CG movie rendering. |

**`vulkan` / force-OpenGL stays global.** It is a `GeneratorOptions` toggle
(driver-crash workaround), intentionally **not** tier-scoped — tiering it would
create broken combos (e.g. cinematic on a Vulkan-crashing device). Set
`vulkan = false` to force OpenGL ES.

### Per-tier field values

| Preset | detail | characterDetail | postProcess | staticLighting | cutsceneQuality |
|---|---|---|---|---|---|
| POTATO | 0 | 0 | 0 | false | 0 |
| ENDURANCE | 1 | 0 | 0 | false | 0 |
| PERFORMANCE | 2 | 1 | 1 | false | 1 |
| COMPETITIVE | 3 | 1 | 1 | false | 1 |
| BALANCED | 4 | 2 | 2 | true | 2 |
| HIGH | 5 | 2 | 2 | true | 2 |
| ULTRA | 6 | 3 | 3 | true | 3 |
| CINEMATIC | 7 | 3 | 3 | true | 3 |

All four new CVars exist in `libUE4_cvars.txt`, so `optimizeIniText` never strips
them. `postProcess`/`characterDetail` are `coerceIn(0,3)` before writing to avoid
out-of-range values from a custom `profileOverride`.

## `GeneratorOptions` (user-custom, global — not tier-scoped)

Boolean quality toggles (`ca`, `disableBloom`, `disableSSR`, `disableAutoExposure`,
`disableRadialBlur`, `disableOutline`, `fog`, `hzb`, `cool`, `vsync`, `unlock120`,
`unlockUltra`, `enableGSR`, `experimentalCvars`, `useAdvancedGen`, `optimizeWithCvarDb`,
`allowRestrictedCvars`, `vulkan`, …), `fps`, `mode: GameMode`, `cvarOverrides`.

Selecting a preset rewrites only the numeric `PresetProfile`; the boolean toggles
persist independently and **survive preset switches** (saved to JSON via
`MainViewModel.save/loadGeneratorOptions`, seeded into `rememberSaveable` UI state).
There is intentionally **no custom numeric profile** — numerics are always
recomputed from the preset name.

## GameMode (arena / TOA)

`GameMode` has `Overworld` and `ToA` ("Tower of Adversity") — ToA is the arena/PvP
mode. `buildGameModeToaSection` emits ToA-specific CVars when `mode == GameMode.ToA`.
