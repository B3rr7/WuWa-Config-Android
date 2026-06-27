# ConfigMonitor Integration Plan

## Goal
After deploying config to device, automatically verify which CVars the game actually recognized by reading ConfigMonitor output from Client.log.

## Feature: Deploy Verification

### User Flow
1. User generates + deploys config (existing flow, unchanged)
2. After push succeeds, app automatically pulls fresh Client.log
3. Parses ConfigMonitor CVar entries (already handled by LogParser.parseLog())
4. Cross-references deployed CVars vs recognized CVars
5. Shows result panel: "✔ 24/26 CVars accepted" with details

### What Changes

#### ConfigGenerator.kt
- Add a `recordGeneratedCvars()` method that captures the set of CVar names produced during generation (from `activePreset`). Store in a new `generatedCvarNames: Set<String>` field.

#### ConfigManager.kt
- Add `verifyDeployedCvars(generatedCvars: Set<String>): Result<VerificationReport>` that:
  1. Pulls Client.log (via existing `readClientLogTextWithMetadata`)
  2. Parses for ConfigMonitor CVar entries
  3. Compares: returns `(accepted: Set<String>, rejected: Set<String>)`

#### model/VerificationReport.kt (new file)
- Data class: `accepted: Set<String>`, `rejected: Set<String>`, `recognizedCount: Int`, `totalCount: Int`

#### MainViewModel.kt
- After `deployGeneratedConfigs()` success, call `verifyDeployedCvars()`
- New `_verificationReport: StateFlow<VerificationReport?>` for UI
- Add log entries: "Verification: 24/26 CVars accepted, 2 rejected"

#### UI (ConfigGenScreen.kt or ResultDialog)
- After deploy, show a small badge/pill: "✔ 24/26" next to result
- Optionally expandable detail list showing rejected CVars

### Data Source
ConfigMonitor writes lines like:
```
Setting CVar [[r.Foo:1]]
Value remains '1' ... variable 'r.Bar'
```
Already parsed by `LogParser.parseLog()` into `LogInfo.activeCvars` (Map<String,String>).

### Implementation Steps
1. Create `VerificationReport` data class
2. Add `generatedCvarNames` tracking to `ConfigGenerator`
3. Add `verifyDeployedCvars()` to `ConfigManager`
4. Wire into ViewModel deploy flow
5. Add UI display in ConfigGenScreen

### Future Extensions (not in scope)
- ConfigMonitor Browser screen (searchable CVar list)
- Smart Brain weighting by ConfigMonitor confirmation
- Continuous polling for real-time CVar feedback
