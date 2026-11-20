# WuWaConfig UI Audit Report (Phase 1)

Date: 2025-10-16

## Overview
This report audits the current UI state across all screens, components, and theme.
Focus: inconsistencies, spacing, contrast, touch targets, clutter, hardcoded values.

## 1. HomeScreen

### Observations
- Uses GradientBackground + Scaffold with GlassTopBar
- LazyColumn with LocalSpacing.current for spacing
- Many GlassCard sections (Backend Status, Quick Actions, Status, Tools)
- Touch targets: IconButton (48dp default) and GlassButton
- Uses Neon colors for accents (NeonPurple, NeonCyan, NeonGreen etc.)
- Some icons with rotation animations on settings

### Issues
- Has a rotating settings icon animation (infinite rotation) - maybe distracting?
- Long list of quick action items - could be grouped better
- Some text uses fontWeight and colors directly
- GlassTopBar with GlitchText effect - can be heavy on low-end devices?
- Many conditional sections add visual noise

## 2. ConfigGenScreen

### Observations
- Very long file (~1058 lines) - needs decomposition
- Complex UI with many sections: Preset, Device, Core, Graphics, Misc, Optimization
- Uses GlassCard extensively
- Has sliders, switches, dropdowns, segmented buttons
- Review Tune button, Deploy button prominent

### Issues
- Monolithic composable - hard to maintain and preview
- Could be broken into smaller composables (PresetSection, GraphicsSection, etc.)
- Some nested state and callbacks passed deeply
- Potential recomposition issues with large state objects
- Many hardcoded strings in UI

## 3. SettingsScreen

### Observations
- Scrollable column with many GlassCard sections
- Uses LocalSpacing for consistent gaps
- Has theme navigation
- Update check, background settings, etc.

### Issues
- Very long scrollable list - could benefit from better grouping
- Some cards feel cramped with content
- Inconsistent spacing in places

## 4. PityScreen

### Observations
- Gacha/Pity tracking UI
- Uses progress indicators
- Has charts/stats cards

### Issues
- May need clearer visual hierarchy for stats

## 5. IniEditorScreen

### Observations
- Text editor for INI files
- Uses monospace for editing (good)
- Search functionality

### Issues
- Editor UI could be more polished (line numbers, syntax highlighting?)
- Could improve readability

## 6. LogsScreen

### Observations
- Log viewer with filtering
- Monospace styling (good for logs)

### Issues
- Dense text, could benefit from better spacing/colors per log level

## 7. Components (Components.kt)

### Observations
- GlassCard is the core component - sophisticated with blur/glow, surface modes
- GlassButton, GlassTopBar, GradientBackground, GlitchText
- Good use of tokens (LocalThemeShapeTokens, LocalSpacing)
- Handles API 31+ RenderEffect with fallbacks (good for low-end)

### Issues
- GlassCard has complex logic (light/dark branches, blur effects) - could be optimized
- GlitchText animation may impact low-end devices
- Some components have large implementations

## 8. Hardcoded Values Found

- ProfileScreen: Color(0xFFAA88FF) for hardware ini bar
- IniEditorScreen: Several hardcoded editor colors (EDITOR_BG, GUTTER_BG, etc.)
- Some CardDefaults.cardColors with hardcoded colors
- May have other hardcoded dp values not using spacing tokens

## 9. General Observations

- Theme system is already sophisticated (6 styles, 7 palettes, tokens)
- Good use of Material 3 components
- API compatibility handled (RenderEffect fallbacks)
- Uses LazyColumn for long lists
- GlassCard is a unifying component
- Low-end considerations in mind (animations can be reduced, fallbacks)

## 10. Key Areas for Improvement

1. **ConfigGenScreen decomposition** - Break into smaller composables for better maintainability and previews
2. **Visual polish** - Refine the resonance/mood with cleaner surfaces, better hierarchy
3. **Consistency** - Review spacing, typography usage across screens
4. **Motion** - Be more intentional with animations, ensure respect for reduce motion
5. **Performance** - For low-end devices, consider simplifying some effects (glitch text, heavy blur)
6. **Light theme refinement** - Ensure light theme looks as polished as dark
7. **Status indicators** - Make deploy success/fail, connection status more obvious
8. **Touch targets** - Verify all interactive elements are >= 48dp
9. **Empty/loading/error states** - Review consistency
10. **INI/Log viewers** - Improve readability and styling

## 11. Screens Summary

| Screen | Lines | Notes |
|---|---|---|
| BackupScreen | 389 | - |
| BattleStatsScreen | 364 | - |
| CalculatorScreen | 830 | - |
| ConfigGenScreen | 1058 | - |
| GachaStatsSection | 778 | - |
| HistoryScreen | 309 | - |
| HomeScreen | 1160 | - |
| IniEditorScreen | 829 | - |
| LogsScreen | 406 | - |
| MyCharacterScreen | 1244 | - |
| PityScreen | 627 | - |
| ProfileScreen | 613 | - |
| ReviewTuneScreen | 905 | - |
| SettingsScreen | 1143 | - |
| SetupScreen | 116 | - |
| TermsScreen | 246 | - |
| ThemeSettingsScreen | 503 | - |
| UserGuideScreen | 164 | - |
--- END OF PHASE 1 ---

## Question for Phase 1
Should we significantly reduce or remove the GlitchText animation on the Home screen's top bar for better performance on low-end devices? It's currently an infinite rotation on the settings icon and GlitchText effect.

