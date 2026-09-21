---
name: Melos Luminous Enterprise
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#464555'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#777587'
  outline-variant: '#c7c4d8'
  surface-tint: '#4d44e3'
  primary: '#3525cd'
  on-primary: '#ffffff'
  primary-container: '#4f46e5'
  on-primary-container: '#dad7ff'
  inverse-primary: '#c3c0ff'
  secondary: '#4648d4'
  on-secondary: '#ffffff'
  secondary-container: '#6063ee'
  on-secondary-container: '#fffbff'
  tertiary: '#00505f'
  on-tertiary: '#ffffff'
  tertiary-container: '#006a7c'
  on-tertiary-container: '#93e8ff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2dfff'
  primary-fixed-dim: '#c3c0ff'
  on-primary-fixed: '#0f0069'
  on-primary-fixed-variant: '#3323cc'
  secondary-fixed: '#e1e0ff'
  secondary-fixed-dim: '#c0c1ff'
  on-secondary-fixed: '#07006c'
  on-secondary-fixed-variant: '#2f2ebe'
  tertiary-fixed: '#acedff'
  tertiary-fixed-dim: '#4cd7f6'
  on-tertiary-fixed: '#001f26'
  on-tertiary-fixed-variant: '#004e5c'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  display-lg:
    fontFamily: Geist
    fontSize: 40px
    fontWeight: '600'
    lineHeight: 48px
    letterSpacing: -0.025em
  headline-xl:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-xl-mobile:
    fontFamily: Geist
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Geist
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-md:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Geist
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0em
  label-md:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Geist
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.04em
  mono-code:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: -0.01em
  mono-metric:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: -0.02em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-lg: 1.5rem
  margin: 1rem
  margin-md: 1.5rem
  margin-lg: 2rem
  space-2xs: 0.125rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
  space-2xl: 2rem
  space-3xl: 3rem
---

## Brand & Style
This design system captures an ultra-modern, luminous enterprise aesthetic tailored for high-density music catalog administration, rights management, and ingestion analytics. The brand character balances crystalline clarity with surgical utility, mirroring the precision and polished authority of best-in-class developer and financial tools like Linear and Stripe.

Visual characteristics center on:
- **Luminosity over Flatness:** High-key, multi-tiered white and cool off-white canvases (#FFFFFF, #F8FAFC) paired with hairline structural framing.
- **Architectural Clarity:** Distinct hierarchy established via border segmentation (#E2E8F0) and strict contrast levels rather than intrusive background fills.
- **Vibrant Precision:** Electric indigo and violet accents (#6366F1, #4F46E5) reserved strictly for interactive affordances, key status indicators, audio stream progress, and analytical peaks.
- **Tone:** Professional, reliable, forward-thinking, and effortless under intense multi-pane data density.

## Colors
The palette utilizes deliberate tonal tiers engineered for luminous daylight visibility and optical crispness:

- **Canvas & Surfaces:**
  - `surface-canvas`: `#FFFFFF` — Primary application backdrops and main workspaces.
  - `surface-muted`: `#F8FAFC` — Canvas offset, sidebars, metadata panels, and card backdrops.
  - `surface-subtle`: `#F1F5F9` — Table headers, hover states, segmented control backdrops, and active item containers.
- **Borders & Dividers:**
  - `border-subtle`: `#F1F5F9` — Internal cell borders and soft separators.
  - `border-default`: `#E2E8F0` — Structural hairline grid lines, component shells, and input outlines.
  - `border-strong`: `#CBD5E1` — Hovered inputs, high-contrast borders, and active control frames.
- **Typography & Icons:**
  - `text-primary`: `#0F172A` (Slate 900) — Primary headlines, track metadata titles, active states.
  - `text-secondary`: `#334155` (Slate 700) — Body copy, table values, input entries.
  - `text-tertiary`: `#64748B` (Slate 500) — Captions, column headers, placeholders, secondary metadata (e.g., ISRC codes).
- **Accents & Semantics:**
  - `primary`: `#4F46E5` (Indigo 600) — Dominant action surfaces, primary buttons, confirmed audio play states.
  - `primary-hover`: `#4338CA` (Indigo 700) — Interactive press states.
  - `primary-subtle`: `#EEF2FF` (Indigo 50) — Badge fills, active navigation row washes, selected rows.
  - `secondary`: `#6366F1` (Indigo 500) — Active metrics, secondary audio waveforms, interactive highlights.
  - `tertiary`: `#06B6D4` (Cyan 500) — Streaming analytics, sync rights flags, royalty balance indicators.

## Typography
The system employs `Geist` for interfaces, displaying razor-sharp rendering, tight letterforms, and structural neutrality suited for complex dashboards. `JetBrains Mono` handles strict alignment tasks: timestamps, track durations, ISRC/ISWC identification numbers, and tabular financial summaries.

Typographic guidelines:
- Apply negative letter-spacing dynamically across headlines (`-0.025em` to `-0.01em`) to maintain structural tension.
- Set all column headers, table keys, and system badges in `label-sm` or `label-md` with full uppercase transformation and `+0.04em` tracking.
- Numerics within audio meters, track length counts (`03:42`), and currency fields must use `mono-metric` with tabular figures enabled (`font-variant-numeric: tabular-nums`).

## Layout & Spacing
The layout follows a fluid-hybrid model optimized for complex administrative tooling:
- **Structure:** 
  - Collapsible persistent navigation rail fixed at `240px` (collapsing to `64px` icon-only).
  - Main administrative surface conforms to a fluid 12-column grid system with dynamic internal split panels (e.g., track table list on the left, release inspector panel on the right).
- **Rhythm & Grid:** Built upon a compact 4px base step (`0.25rem`), enabling extreme information density without visual crowding.
- **Breakpoints:**
  - `Mobile` (< 768px): Single-column reflow, sidebars converted to sheets, tables collapse to responsive card lists, margins set to `margin` (`1rem`).
  - `Tablet` (768px - 1024px): 8-column layout, persistent bottom mini-player, drawer-based inspector panels, margins set to `margin-md` (`1.5rem`).
  - `Desktop` (> 1024px): 12-column fluid grid, concurrent side-by-side metadata inspection, pinned multi-track operations bar, margins set to `margin-lg` (`2rem`).

## Elevation & Depth
Depth is produced via subtle tonal juxtaposition and surgical, high-diffusion ambient shadows rather than stark elevation planes:

- **Surface Tiers:**
  - `Tier 0 (Base Canvas)`: `#FFFFFF` or `#F8FAFC`.
  - `Tier 1 (Cards & Workspaces)`: `#FFFFFF` framed by a 1px `#E2E8F0` border.
  - `Tier 2 (Popovers, Dropdowns, Flyouts)`: `#FFFFFF` framed by a 1px `#CBD5E1` border, elevated with `0 4px 16px -2px rgba(15, 23, 42, 0.06), 0 2px 6px -1px rgba(15, 23, 42, 0.04)`.
  - `Tier 3 (Modals & Command Palettes)`: `#FFFFFF` with `0 20px 25px -5px rgba(15, 23, 42, 0.08), 0 8px 10px -6px rgba(15, 23, 42, 0.04)`.
- **Inner Rim Insets:** Elevated components like primary buttons and modal containers apply a delicate inner highlight: `inset 0 1px 0 0 rgba(255, 255, 255, 0.2)` on buttons, or `inset 0 1px 0 0 rgba(255, 255, 255, 0.8)` on top of cards.

## Shapes
The design adopts a `Soft` (Level 1) shape profile to emphasize technical discipline, data density, and an executive enterprise feel:
- **Base Components:** Inputs, buttons, segmented controls, audio scrubber handles, and dropdowns use `4px` (`0.25rem`).
- **Cards & Data Grids:** Outer containers, analytics cards, and modal dialogs use `rounded-lg` (`8px` / `0.5rem`).
- **Pills / Status Badges:** Exclusively applied to ingestion status pills, audio tags, and active filters (`9999px` full round).
- **Artwork Thumbnails:** Strict `4px` rounding with a 1px inset border (`rgba(15, 23, 42, 0.08)`) to preserve cover design integrity.

## Components

### Buttons
- **Primary:** Solid `#4F46E5` background, `#FFFFFF` text, 1px border `#4338CA`. Subtle top inner shadow (`inset 0 1px 0 rgba(255,255,255,0.2)`). Hover: `#4338CA`.
- **Secondary / Outline:** `#FFFFFF` background, `#0F172A` text, 1px border `#E2E8F0`. Hover: `#F8FAFC`, border `#CBD5E1`.
- **Ghost:** Transparent background, `#334155` text. Hover: `#F1F5F9`, text `#0F172A`.
- **Destructive:** `#FFFFFF` background, `#EF4444` text, 1px border `#FECACA`. Hover: `#FEF2F2`.

### Inputs & Search Bars
- Background: `#FFFFFF`. Border: 1px `#E2E8F0`. Typography: `14px` (`body-md`), text `#0F172A`, placeholder `#64748B`.
- Focus state: Border color `#4F46E5`, outline: `2px solid rgba(99, 102, 241, 0.15)`.
- Metadata / ISRC Inputs: Embed fixed prefix badges on `#F8FAFC` background with right-hand divider.

### Data Tables & Row Lists
- Header: `#F8FAFC` fill, height `36px`, border-bottom `1px solid #E2E8F0`, typography in `label-sm` uppercase.
- Row: Background `#FFFFFF`, height `48px` (compact) or `56px` (standard), bottom hairline border `#F1F5F9`.
- Row Hover: `#F8FAFC` with immediate cursor change.
- Selected State: `#EEF2FF` background with left `2px solid #4F46E5` indicator.

### Badges & Status Indicators
- Format: `label-sm`, height `20px`, padding `0 8px`, border radius `9999px`.
- Ingestion Active / Delivered: `#ECFDF5` fill, `#059669` text, 1px `#A7F3D0` border.
- Processing / Transcoding: `#EEF2FF` fill, `#4F46E5` text, 1px `#C7D2FE` border.
- Draft / Metadata Incomplete: `#FFFBEB` fill, `#D97706` text, 1px `#FDE68A` border.

### Audio Player Dock & Waveforms
- Persistent bottom fixed strip: Height `64px`, `#FFFFFF` backdrop, 1px top border `#E2E8F0`.
- Waveform Scrubber: Background idle bars `#E2E8F0`, played progress filled in `#4F46E5`, hover playhead line `#6366F1`.
- Track Meta display: Album thumb `40x40px`, rounded `4px`, title in `headline-sm`, artist and ISRC in `label-md` `#64748B`.

### Cards & Metrics
- Background `#FFFFFF`, border `1px solid #E2E8F0`, padding `space-xl` (`1.5rem`).
- Header displays metric label in `label-md` `#64748B`, metric total in `headline-xl` `#0F172A`, and delta pill inline.