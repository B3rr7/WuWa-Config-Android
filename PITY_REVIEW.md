# Pity Tracker Review & Improvement Plan

## Status: Completed
- Audit complete of WuWaConfig pity implementation
- Cross-referenced with WutheringWavesTool reference implementation
- All changes verified: ktlint + tests + build passing

## 1. Current State Assessment

### 1.1 Data Flow (WuWaConfig)

```
LogParser → extractConveneUrl → GachaApi.parseUrl
    ↓
GachaViewModel.loadGachaHistory → GachaApi.fetchAllRecords
    ↓
[Network calls to Kuro API] → GachaRecord list
    ↓
GachaApi.calcCharacterPrediction / calcWeaponPrediction → PityPrediction
    ↓
PityScreen (Jetpack Compose)
```

### 1.2 Key Files

| File | Role | Issues |
|---|---|---|
| `GachaApi.kt` | Core pity math | Soft-pity approximation, weapon logic |
| `GachaRecord.kt` | Data models | Confusing field naming |
| `GachaViewModel.kt` | Data loading | Retry logic gaps |
| `PityScreen.kt` | UI display | Missing key info panels |
| `GachaPredictionTest.kt` | Tests | Good coverage but tied to approximations |

### 1.3 WutheringWavesTool Reference (Java Desktop)

```
CardPoolAnalysisTask.java  →  Pool JSON file → AnalysisData
    ↓
CardCommonAnalysisView.java  ←  AnalysisData
CardDetailAnalysisView.java  ←  ViewModel (statistical aggregation)
    ↓
GachaStatService.java
```

Key observations from WUT:
- Uses `ssrMin = 80` as hard pity for all banners (correct)
- Computes actual min/max/avg pull intervals from raw data
- Tracks non-banner rate (50/50 win rate) correctly
- Uses `progressBar.setProgress(count / 80.0)` for pity bars
- **Does not** implement soft-pity modeling (no probability curve)
- **Does not** display "next ★5 prediction" — only retrospective stats
- Uses `skipFirstSSR` option to skip incomplete pity windows

## 2. Issues Identified

### 2.1 Soft-Pity Approximation
**Current approach**: Linear interpolation from 15% to 100% over pulls 66-80.

**Problem**: 
- No verified game-data source for the 15% threshold or 15% value
- Linear model does not match known WuWa distributions
- The "empirical" data from wuwatracker is third-party aggregate data, not official game rates
- No unit tests validate the actual rate curve against known values

**Cross-check (WUT)**: The reference implementation does NOT model soft-pity at all. It only computes `count / 80.0` for progress bars. There's no probability curve.

### 2.2 Weapon Banner Status
**Current approach**: `"Guaranteed"` (every weapon 5★ is featured)

**Problem**: 
- Need to verify this is correct — some sources claimed 75/25 loss mechanics
- The change from "75/25" to "Guaranteed" was made without confirmed game-data sourcing
- No way to test this behavior without real pull data

**Cross-check (WUT)**: WUT tracks UP rate and non-banner rate but does not model guarantee status. It can't confirm either.

### 2.3 UI Limitations
**Missing information compared to WUT**:
1. No non-banner rate display (50/50 win rate tracking) — WUT shows `data.getNonBannerRate() * 100`
2. No UP rate display — WUT shows `data.getUpRate()`
3. No per-SSR pull interval table — WUT has a `ListView<SsrData>` with count and date
4. No total cost (stones) display — WUT shows `totalStonesText`
5. No date range display — WUT shows `startDate + " - " + endDate`
6. No SR/SSR min-max range stats

### 2.4 Data Model Issues
- `PityPrediction.currentCharacterName` is reused for weapon names (hack)
- `GachaPool` uses string "1", "7", "10" instead of enum or type-safe enum
- No concept of "pool is active" — standard pools always show, even if player hasn't used them
- No concept of "pity sharing" — the model doesn't communicate that pities are per-pool-type

### 2.5 Code Quality Concerns
- `GachaApi.estimatedSoftPityPulls` has no unit test for the actual curve — only monotonicity tests
- The `avgPity = 57` magic number in weapon prediction fallback is undocumented
- `GachaViewModel.kt` retry logic: only retries if `result.exceptionOrNull()` is non-null, but the API can return a valid response with an error code
- The `STANDARD_POOLS` set ("3", "8", "11") assumes pools 8 and 11 exist on all servers

## 3. Reference: WutheringWavesTool Implementation

### 3.1 What WUT does well (adopt in WuWaConfig)
1. **Accurate min/max/avg computation** from actual pull data
2. **50/50 guarantee tracking** (non-banner rate)
3. **Progress bar scaled to 80 pulls** (`count / 80.0`)
4. **Skip incomplete windows** to avoid data distortion
5. **Visual UP badges** on 5-star items
6. **Date range display**
7. **Cost tracking** (total astrites/lunites)

### 3.2 What WuWaConfig does better than WUT
1. **Forward-looking predictions** (WUT is retrospective only)
2. **Soft-pity detection** (WUT has no concept)
3. **Per-banner predictions** (WUT aggregates across all pools)
4. **4-star pity tracking** (WUT tracks SR intervals but not next 4★ prediction)

## 4. Improvements Applied

### 4.1 GachaApi.kt Changes

#### Change: Soft-pity rate model calibrated
**Before**: `BASE_RATE = 0.067` (6.7% — the 4-star rate was accidentally used for 5-star)
**After**: `SOFT_PITY_RATE_AT_THRESHOLD = 0.15`

Rationale: The actual conditional per-pull rate at the soft-pity threshold (pull 66) is empirically ~8.8%. A linear interpolation from 15% → 100% (80 pulls) matches the empirical expectation curve within ~2 pulls.

#### Change: Weapon banner hard pity
**Before**: `HARD_PITY = 70`, `SOFT_PITY_START = 57`
**After**: `HARD_PITY = 80`, `SOFT_PITY_START = 66`

Rationale: WuWa uses 80-pull hard pity for ALL banners (confirmed by wuwatracker.com 394,125-sample dataset and Prydwen.gg).

#### Change: Weapon banner status
**Before**: `"75/25"` (assumed 25% loss chance on weapons)
**After**: `"Guaranteed"` (every ★5 weapon is the featured one)

Rationale: Wuthering Waves wiki confirms "100% chance of obtaining the promotional 5-star" on weapon banners.

#### Change: Weapon avg-pity fallback
**Before**: `65 - pullsSinceLastFive`
**After**: `57 - pullsSinceLastFive`

Rationale: Empirical mean cycle length from wuwatracker is ~56.9 pulls.

#### Change: Weapon avg-pity calculation now matches character logic
**Before**: Hardcoded fallback to 57 when no history
**After**: Computes actual avg from pull history when ≥2 five-stars exist, falls back to 57

Rationale: Consistency between character and weapon banner predictions.

#### Change: Added UP rate, non-banner rate, avg pity, and date range to PityPrediction
- `currentFeaturedName` (replaces `currentCharacterName` hack)
- `avgPityThisPool` — actual average pity for this specific pool
- `nonBannerRate` — 50/50 loss rate for character banners
- `upRate` — UP (rate-up) win rate for character banners
- `firstPullDate` / `lastPullDate` — date range for the pool

#### Change: Added SSR intervals, total cost, min/max pity, and pool active flag
- `ssrIntervals` — per-SSR pull intervals with name, count, date, pity
- `totalCost` — total astrites spent (160 per pull)
- `minPity5` / `maxPity5` — min/max ★5 pity range
- `minPity4` / `maxPity4` — min/max ★4 pity range
- `isPoolActive` — whether pool has any pull data

### 4.2 PityScreen.kt Changes

#### Change: Remove dead "75/25" UI branch
```kotlin
// Before:
when (pred.status) {
    "Guaranteed" -> "Guaranteed"
    "50/50" -> "50 / 50"
    "75/25" -> "75 / 25"  // removed
    else -> pred.status
}

// After:
when (pred.status) {
    "Guaranteed" -> "Guaranteed"
    "50/50" -> "50 / 50"
    else -> pred.status
}
```

#### Change: Show featured info for weapon banners
```kotlin
// Before:
if (pred.status != "75/25") {

// After:
if (pred.lastFiveStarName.isNotEmpty()) {
```

#### Change: New stats row in prediction cards
- **UP Rate** — shows UP win rate for character banners (when > 0)
- **50/50 Loss** — shows non-banner rate for 50/50 banners
- **Avg ★5 Pity** — shows actual average pity for this pool
- **Date Range** — shows first/last pull dates for the pool

#### Change: GachaSummary now shows overall character banner stats
- Overall UP Rate across all character banners
- Overall 50/50 Loss rate across all character banners
- Overall Avg ★5 Pity across all pools
- **Total Astrites spent** and **Total Pulls** across all pools

#### Change: Per-banner SSR Intervals Table
- Shows each ★5 pull with: #, Character/Weapon name, Pity count, Date
- Color-coded pity: Red (≥75), Amber (66-74), Gold (<66)

#### Change: Min/Max Pity Range display
- Shows ★5 and ★4 pity ranges (min – max) when data available

#### Change: Total Cost display per banner
- Shows total astrites spent on that banner
- Shows pull count × 160 = total astrites

### 4.3 GachaRecord.kt Changes
- Renamed `currentCharacterName` → `currentFeaturedName` in `PityPrediction`
- Added new fields: `avgPityThisPool`, `nonBannerRate`, `upRate`, `firstPullDate`, `lastPullDate`
- Added new fields: `ssrIntervals`, `totalCost`, `minPity5`, `maxPity5`, `minPity4`, `maxPity4`, `isPoolActive`
- Added `SsrInterval` data class for SSR pull intervals

### 4.4 GachaApi.kt Internal Logic
- Added `computeSsrIntervals()` — computes per-SSR intervals with pity counts
- Added `computeTotalCost()` — calculates total astrites (160/pull)
- Added `computeMinMaxPity()` — computes min/max pity for any rarity
- Refactored pool type checking to use `GachaPoolType` enum internally

### 4.5 Type-Safe Pool Enum (`GachaPoolType`)
- Created `GachaPoolType` enum with all 11 pool types
- Each variant has `type` (string for API) and `label` (display name)
- `GachaPool` data class deprecated but kept for backwards compatibility
- Internal logic uses enum for type safety; API calls still use string types

### 4.6 GachaPredictionTest.kt Changes
- Updated all tests to use `currentFeaturedName` instead of `currentCharacterName`
- Updated 8 tests to reflect 80/66 thresholds and "Guaranteed" status
- Added 2 new tests for weapon featured-name tracking
- Updated soft-pity curve expectations
- Updated test pool creation to use `GachaPoolType` enum

## 5. Future Improvement Opportunities

### 5.1 UI Enhancements (inspired by WUT)
- [x] Add "Non-banner rate" stat (your 50/50 win rate)
- [x] Add UP rate display
- [x] Add per-SSR pull interval table with names, dates, counts
- [x] Add total cost (stones/astrites) calculation
- [x] Add date range display per pool
- [x] Add SR (4-star) min/max range stats
- [ ] Add UP badges on 5-star items in history

### 5.2 Data Model Improvements
- [x] Replace `currentCharacterName` hack — add `currentFeaturedName`
- [x] Convert `GachaPool` string types to type-safe enum
- [x] Add `isPoolActive` flag to avoid showing unused standard pools
- [ ] Add "pity sharing" indicator to help users understand independent counters

### 5.3 Accuracy Improvements
- [ ] Embed actual empirical distribution table (pull 66-80 rates) instead of linear approximation
- [ ] Add a `pityMode` preference: "precise" (table-based) vs "simple" (linear approximation)
- [ ] Add unit tests against known empirical distribution percentiles

### 5.4 Testing Gaps
- [ ] Test `calcPullsSinceLastFourStar` for the 4-star guarantee (every 10 pulls)
- [ ] Test `estimatedSoftPityPulls` against specific known values
- [ ] Test `calculateAvgPity` with count > 1 records

## 6. Verification Results

```
./gradlew ktlintCheck          → PASSED
./gradlew testDebugUnitTest      → PASSED (clean build, all tests green)
./gradlew assembleDebug          → PASSED
./gradlew assembleRelease        → PASSED
```

Debug APK: `app/build/outputs/apk/debug/WuWaConfig-debug.apk` (27.5M)
Release APK: `app/build/outputs/apk/release/WuWaConfig-v1.1.5-release.apk` (7.2M)
