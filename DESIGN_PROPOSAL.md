# Design System Proposal (Phase 2)

## Design Direction Summary
- Mood: dark, premium, "resonance" feel
- Primary: Teal/Cyan accent (#22D3EE) - tech/resonance vibe
- Secondary: Warm Amber (#F59E0B) - contrast and warmth
- Surfaces: Deep navy/black (navy #050510, slate #0B1026) 
- Subtle glow, thin lines, soft glass panels
- Elegant, not noisy
- Light theme must also look polished

## Color Tokens (Proposed)

### Dark Theme (Primary)
- Background: #050510 (near-black with hint of navy)
- Surface: #0B1026 (slightly lighter than bg, deep navy)
- SurfaceVariant: #111827 (elevated surfaces)
- Card/Panel: Soft glass - translucent with blur, or solid elevated surface
- Primary (Teal/Cyan): #22D3EE (vibrant cyan)
- Primary Container: rgba(34, 211, 238, 0.15)
- Secondary (Warm Amber): #F59E0B (warm accent for CTAs, highlights)
- Secondary Container: rgba(245, 158, 11, 0.15)
- On Background/Surface: #E6EDF3 (soft white)
- On Surface Variant: #94A3B8 (muted)
- Outline: rgba(148, 163, 184, 0.25) (thin lines)
- Success: #10B981 (green)
- Warning: #F59E0B (amber)
- Error: #EF4444 (red)
- Info: #3B82F6 (blue)

### Light Theme
- Background: #F8FAFC (soft, not stark white)
- Surface: #FFFFFF
- SurfaceVariant: #F1F5F9
- Primary: #0891B2 (teal/cyan - darker for contrast on light)
- Secondary: #D97706 (warm amber - darker)
- On Background: #0F172A (slate 900)
- On Surface: #0F172A
- Outline: rgba(15, 23, 42, 0.12)

## Typography
- Primary display: Maintain current approach but refine scale
- Headline Large: 32sp (was 28sp) - more premium
- Headline Medium: 26sp (was 24sp)
- Title Large: 22sp (was 20sp)
- Title Medium: 18sp (was 16sp)
- Body Large: 16sp
- Body Medium: 14sp
- Body Small: 12sp
- Label Large/Medium: Keep monospace for INI/Logs (preserve functionality)
- Use consistent font weights, generous line heights

## Shapes & Corners
- Card corners: 20dp (more modern, softer than current 16dp)
- Button corners: 16dp (rounded, not pill, not sharp)
- Dialog corners: 28dp (large, premium)
- Field corners: 12dp
- Consistent radius hierarchy

## Spacing Scale (Refined)
- xs: 4dp (half gap)
- sm: 8dp (item gaps)
- md: 16dp (section padding/gaps) - slightly increased from 12dp
- lg: 24dp (screen gutters) - increased from 16dp
- xl: 32dp (major sections)

## Surface Modes & Effects
- Default: Tonal surfaces for Material 3 feel with subtle elevation
- Glass: Soft glass panels for cards with blur (API 31+ with fallback)
- Thin hairline borders where needed, not heavy outlines
- Subtle glow on primary accent elements (glowWidth reduced, more refined)
- Avoid heavy animations, glitch effects toned down for low-end devices

## Component List (Shared)
- GlassCard (refined) - cleaner, better contrast
- GlassButton - primary/secondary variants
- GlassOutlinedButton
- GlassTopBar - cleaner, no heavy glitch animation
- GradientBackground - can include subtle waveform/frequency motif (optional, performance-aware)
- SectionHeader - consistent hierarchy
- StatusChip - for connected/disconnected, success/fail
- ProgressGauge/Ring - for SmartBrain score (proper gauge)
- EmptyState, ErrorState, LoadingState - consistent
- SectionDivider - thin lines

## Animation Principles
- Short (200-300ms), spring/fast-out-slow-in
- Respect system "reduce animations"
- Purposeful (transitions, presses, progress)
- Avoid infinite distracting animations (e.g. spinning settings icon)

## Special Notes
- Preserve all existing functionality and signatures
- Use tokens everywhere - eliminate hardcoded dp/colors where possible
- Break large screens into smaller composables (especially ConfigGenScreen)
- Monospace for INI/Logs as currently done
- Touch targets >= 48dp
- WCAG AA contrast compliance
