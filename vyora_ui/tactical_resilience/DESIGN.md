---
name: Tactical Resilience
colors:
  surface: '#051424'
  surface-dim: '#051424'
  surface-bright: '#2c3a4c'
  surface-container-lowest: '#010f1f'
  surface-container-low: '#0d1c2d'
  surface-container: '#122131'
  surface-container-high: '#1c2b3c'
  surface-container-highest: '#273647'
  on-surface: '#d4e4fa'
  on-surface-variant: '#e3beb3'
  inverse-surface: '#d4e4fa'
  inverse-on-surface: '#233143'
  outline: '#aa897f'
  outline-variant: '#5b4138'
  surface-tint: '#ffb59d'
  primary: '#ffb59d'
  on-primary: '#5d1900'
  primary-container: '#ff5e1e'
  on-primary-container: '#561600'
  inverse-primary: '#ab3500'
  secondary: '#4cd7f6'
  on-secondary: '#003640'
  secondary-container: '#03b5d3'
  on-secondary-container: '#00424e'
  tertiary: '#4edea3'
  on-tertiary: '#003824'
  tertiary-container: '#00a874'
  on-tertiary-container: '#003421'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdbd0'
  primary-fixed-dim: '#ffb59d'
  on-primary-fixed: '#390c00'
  on-primary-fixed-variant: '#832700'
  secondary-fixed: '#acedff'
  secondary-fixed-dim: '#4cd7f6'
  on-secondary-fixed: '#001f26'
  on-secondary-fixed-variant: '#004e5c'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#051424'
  on-background: '#d4e4fa'
  surface-variant: '#273647'
typography:
  display-lg:
    fontFamily: Space Grotesk
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Space Grotesk
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.02em
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.06em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.5rem
  gutter-desktop: 2rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system projects mission-critical calm, operational precision, and survival-grade dependability. Designed specifically for extreme situations—natural disasters, infrastructure grid collapse, tactical wilderness operations, and zero-connectivity environments—the interface prioritizes absolute clarity over ornamental decoration.

The target audience encompasses search and rescue teams, civilian survivalists, first responders, and ordinary individuals navigating high-stress crises. Under physical fatigue, sensory overload, direct midday sunlight, or total pitch-black field environments, cognitive load must remain near zero. 

The aesthetic is a hybrid of **Tactical Utilitarianism** and **Modern Android / Material 3**. It relies on high-contrast surfaces, razor-sharp information density, tactile card architecture, and unambiguous telemetry indicators. Visual signals—particularly emergency triggers, mesh hop topologies, and AI triage recommendations—deliver urgent situational awareness without inducing panic.

## Colors

The palette is engineered for extreme contrast, OLED power conservation, and instant visual triage under outdoor sunlight or night-vision conditions.

- **Primary Canvas & Surfaces**: Deep ballistic navy tones anchor the hierarchy. The absolute base is `#0B111E` (canvas base/OLED efficiency), transitioning to `#111A2E` (tactile containers/panels) and `#18233C` (interactive cards and floating sheets). Border dividers use `#223354`.
- **Text & Contrast Engine**: Primary headlines and mission-critical metrics strictly use `#FFFFFF` (100% contrast). Supporting typography and descriptions utilize `#E2E8F0` (88% luminance) to reduce optical glare while preserving legibility. Metadata, timestamp telemetry, and dormant state labels rely on `#94A3B8`.
- **Primary & Critical Alerts**: `#FF5E1E` (tactical blaze orange) serves as the primary system accent and active emergency trigger. For lethal hazards and immediate panic/distress signals, `#FF3B30` acts as the critical override. Advisory states and warning markers utilize `#FF9F0A`.
- **Mesh & Telemetry Status Accents**: 
  - **Active Mesh Backbone**: `#10B981` (emerald green) indicates healthy node reachability, encrypted relay validation, and stable radio handshake.
  - **Relaying / Queued Buffers**: `#F59E0B` (amber) flags store-and-forward packet queues and packet collisions.
  - **Direct P2P / Proximity Link**: `#06B6D4` (tactical cyan) defines direct local Bluetooth LE/Wi-Fi Direct connections and low-latency tactical peers.

## Typography

The typographic system utilizes a tri-font hierarchy calibrated for high-stress legibility and technical telemetry:

1. **Headlines (`Space Grotesk`)**: Technical, structured, and geometric. Used for view headers, emergency titles, and high-level section transitions. It provides a sharp, mechanical cadence that reads instantly during motion or vehicle transit.
2. **Body & Prose (`Hanken Grotesk`)**: Modern, highly legible grotesque with open counters and balanced proportions. Delivers fluid reading for offline AI emergency manuals, field first-aid directives, and incoming emergency broadcasts.
3. **Labels, Telemetry & Status (`JetBrains Mono`)**: Monospaced clarity for coordinates, radio frequencies, battery metrics, signal RSSI/SNR values, timestamps, and mesh node counts. Tabular numbers prevent jitter during live signal tracking.

## Layout & Spacing

The layout is grounded in a fluid 8pt spatial grid configured to prioritize single-handed thumb accessibility and gloved-hand touch accuracy on standard Android devices.

- **Handheld Grid Architecture**: Mobile layouts rely on a 4-column fluid system with 16px (`1rem`) outer margins and 16px gutters. Tablet expansions shift to an 8-column layout (24px margins/gutters), and tactical tablet/docked command interfaces span a 12-column layout (32px margins/gutters).
- **Target Reachability**: Interactive controls, distress triggers, and frequency switchers are pinned strictly to the bottom 45% screen boundary (the thumb comfort zone). Primary telemetry readouts and broadcast headers occupy the upper optical zone.
- **Rhythm & Touch Density**: Standard component gap is `space-sm` (8px) within groupings, and `space-md` (16px) between independent cards. Touch targets never drop below 48x48dp, even for condensed telemetry chips.

## Elevation & Depth

Visual hierarchy is established through **Tonal Surface Layering** reinforced with **Low-Contrast Precision Ghost Outlines**. Blurred shadows are omitted entirely to maintain battery conservation and maximize outdoor edge clarity.

- **Level 0 (Canvas Base)**: `#0B111E`. The primary viewport substrate.
- **Level 1 (Docked Containers & Lists)**: `#111A2E` paired with a 1px solid border of `#223354`. Used for telemetry panels, message lists, and toolbars.
- **Level 2 (Tactile Cards & Action Sheets)**: `#18233C` bounded by a 1px outline of `#2E436E`. Provides noticeable physical separation without elevation blur.
- **Active / Alert Elevation**: When an element represents an active transmission or priority alarm, surface elevation is established via an inner glow or crisp 1.5px high-visibility keyline using `#FF5E1E` (Warning), `#10B981` (Mesh Transmit), or `#06B6D4` (Peer Link).

## Shapes

The design system adopts **Soft / Tactical** curvature (`roundedness: 1`). Radii are kept compact to evoke ruggedized hardware devices, military radios, and specialized survival instruments.

- **Standard Base Elements (Chips, Inputs, Buttons)**: `0.25rem` (4px) corner radius.
- **Surface Cards & Action Sheets (`rounded-lg`)**: `0.5rem` (8px) corner radius.
- **Persistent Bottom Sheets & Floating SOS Trays (`rounded-xl`)**: `0.75rem` (12px) corner radius.
- **Strict Exception (SOS & Critical FABs)**: Circular or pill elements are preserved exclusively for round tactile push buttons (e.g., Hold-to-Activate SOS).

## Components

### Buttons & SOS Controls
- **SOS Critical Action**: A large, centered trigger built with a dual-ring layout. Minimum height is 64dp. Background is `#FF3B30` with bold white text in `Space Grotesk`. Requires a 2-second tactile long-press with visual perimeter fill to prevent accidental activation.
- **Primary Action**: Tactical orange `#FF5E1E` background with `#FFFFFF` text. Flat finish with a 4px corner radius.
- **Secondary Action**: `#18233C` background, 1px `#2E436E` border, and `#E2E8F0` text.
- **Tactical Ghost Action**: Transparent fill, 1px `#223354` border, and `#94A3B8` label.

### Status Indicators & Badges
- **P2P Direct Chip**: Cyan `#06B6D4` badge with 10% opacity cyan fill, a 1px cyan outline, and `JetBrains Mono` text (`label-sm`). Includes a 6px solid cyan dot.
- **Mesh Active Badge**: Emerald green `#10B981` tone with 12% opacity fill. Visualizes node count and hop latency (e.g., `MESH: 14 NODES (2 HOPS)`).
- **Relay / Queue Chip**: Amber `#F59E0B` fill with a pulsing indicator representing store-and-forward outbox packets awaiting transmission.

### Input Fields & Search Bars
- Background: `#111A2E`.
- Outlines: 1px `#223354` default; transforms to a crisp 1.5px `#06B6D4` (Cyan) focus outline when active.
- Placeholder text in `#94A3B8`.
- Monospace font mode toggle for entering technical coordinates, frequencies, and grid references.

### Lists & Chat Stream
- Alternating rows avoid striped fills; instead, each transmission container is enclosed within a `#18233C` bubble with an integrated node routing header (e.g., `FROM: RELAY-NODE-7 | TTL: 3`).
- Unsent or outbound mesh messages show a small amber clock queue glyph; verified localized deliveries switch immediately to an emerald check glyph.

### Cards & Triage Panels
- Surface fill `#18233C` with a 1px `#223354` perimeter stroke.
- Header bands incorporate high-contrast categorical indicators (e.g., a 4px left-edge border strip in `#FF5E1E` for urgent triage recommendations).
- Offline AI summary cards use a subtle `#06B6D4` header badge confirming 100% on-device local execution without cloud dependency.