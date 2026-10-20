# Design

## Context

See `proposal.md` - Why. The web client is a React 19, TypeScript, Vite, and Tailwind CSS 4 single-page application. It has authentication, workspace, dashboard, document upload/list/detail, and transaction-history screens. Shared styling currently lives in `ui/src/index.css` (about 2,559 lines) and combines a small Tailwind theme with many page-specific literal values and states; reusable React components are limited to a few cross-page elements.

The established visual direction is a light, calm fintech interface: navy text, neutral light page background, white surfaces, restrained teal/green primary actions, and amber/red semantic states. Existing UI specs also define English copy, status badges, accessible controls, responsive filters, and transaction detail panels. This change formalizes and extends those patterns; it does not redefine their business behavior.

## Goals / Non-Goals

**Goals:**

- Create a complete reference that designers and developers can use without reverse-engineering individual screens.
- Normalize existing visual values into named tokens while retaining the current brand character.
- Identify shared components and their anatomy, states, variants, and responsive behavior.
- Establish accessibility and conformance checks that apply to both existing and future UI work.
- Migrate existing screens incrementally without changing routes, API contracts, or feature behavior.

**Non-Goals:**

- Redesigning product workflows, copy language, navigation destinations, or financial/business rules.
- Changing backend services, APIs, data models, or transaction/document lifecycle behavior.
- Adding a third-party component library, icon package, or documentation runtime.
- Replacing visual design with a dark theme or adding user-selectable themes.

## Decisions

### Keep the existing brand direction and normalize it into named tokens

The current values provide a recognizable baseline and are already referenced across the UI and specs. The reference should map these observed values into semantic tokens and consolidate near-duplicates, rather than introducing an unrelated palette:

| Token role | Current reference value | Intended use |
|---|---|---|
| Primary | `#167D68` | Primary action, active navigation, key accent |
| Primary strong | `#116653` | Hover/pressed emphasis and high-contrast positive text |
| Page | `#F6F8FB` | Workspace canvas |
| Surface | `#FFFFFF` | Cards, panels, menus, and form controls |
| Ink | `#172B4D` | Headings and high-emphasis values |
| Body | `#344054` | Body text and standard control labels |
| Muted | `#718096` | Secondary text; adjust if required by contrast verification |
| Border | `#E8EDF3` | Dividers and neutral outlines |
| Success | `#EAF5F1` / `#116653` | Positive state background and text |
| Warning | `#FFF4E5` / `#9A620F` | Processing and attention states |
| Danger | `#FDECEC` / `#A53030` | Failed and error states |
| Information | `#EAF2FB` / `#3674AD` | Informational state |
| Neutral | `#F1F4F8` / `#59677A` | Pending and low-emphasis state |

These are observed starting values, not an exemption from contrast rules. Validate foreground/background pairs; adjust a token when needed to meet the specification while preserving the overall teal, navy, and neutral identity. Publish semantic token names and usage constraints so page code does not pick colors by appearance alone.

### Document a complete foundation scale, not only the palette

The reference and UI token layer should cover:

- **Typography:** the existing Inter/system sans-serif stack; named roles for page title, section title, body, label, metadata, and compact table text; documented weight, line-height, and responsive sizing.
- **Spacing:** a 4 px base scale (4, 8, 12, 16, 20, 24, 32, 40, 48, 64 px) for component interiors and layout gaps, with documented exceptions for dense data layouts.
- **Sizing and layout:** control heights, icon sizes, content maximum width, navigation widths, page gutters, card padding, and responsive breakpoints. Keep the existing 1440 px content cap and use the current 1280/900/680 px transitions as initial breakpoint candidates, validating each against real screens.
- **Shape and depth:** a compact border-radius scale for controls, cards, and prominent authentication/panel surfaces; a small elevation scale; standard border and divider treatment.
- **Motion and focus:** short purposeful transitions, visible keyboard-focus treatment, and reduced-motion fallbacks.
- **Iconography:** one consistent stroke/size/alignment vocabulary using the existing inline SVG approach; icons that are decorative are hidden from assistive technology, while icon-only controls receive accessible names.

Token scales are standardized only where they improve reuse. Table density and exceptional layouts remain documented variants rather than forcing every component to the same dimensions.

### Treat the OpenSpec capability as the normative contract and provide a usable UI reference

The new `openspec/specs/ui-design-system/spec.md` defines what all UI work must follow. Implementation should add a concise `ui/DESIGN_SYSTEM.md` reference for contributors that links to the capability and includes token tables, component examples/variants, responsive behavior, and a checklist. Token values in the code are the runtime expression of the documented reference, not an independent competing source. Update both in the same change when a design foundation changes.

Alternatives considered: leaving OpenSpec as the only reference would make the catalog less discoverable to frontend contributors; putting the only contract in a code README would leave future feature planning without a normative requirement. Linking the contributor reference to the OpenSpec capability keeps design detail accessible while retaining a clear source of truth.

### Extract small reusable primitives without introducing a component framework

Use the existing React and CSS/Tailwind setup. Consolidate common styles around semantic tokens and introduce or refine small shared React components only when they improve consistency across pages. The inventory should describe:

| Area | Shared patterns and required variants |
|---|---|
| Application shell | Brand, desktop sidebar/mobile navigation, breadcrumb, profile/avatar, sign-out, content region, privacy footer |
| Authentication | Split form/story layout; field stack; validation and server error; responsive single-column form |
| Page composition | Page heading, supporting text, section heading, card/surface, summary metric card |
| Actions and inputs | Primary/secondary/tertiary and icon-only actions; text, date, and select fields; enabled, hover, focus, disabled, validation states |
| Feedback | Success/error notices; pending, processing, processed, failed status badges; empty, loading, and recoverable failure states |
| Data presentation | Document/transaction tables, sortable headings, amount sign treatment, category/type indicators, pagination, deliberate narrow-viewport table scrolling |
| Detail inspection | Document detail sections and right-side transaction panel, read/edit/read-only history states, mobile full-width adaptation, close/focus behavior |

The inventory specifies visual and interaction contracts; existing capability specs continue to own product behavior such as polling, routing, sorting, filtering, and transaction editing.

Alternatives considered: adding a large UI framework would impose a dependency and visual conventions not present in the project; maintaining only global utility classes would preserve the current fragmentation. Small composable primitives are the lowest-friction option for this codebase.

### Apply the system incrementally and protect existing behavior

First establish tokens and the reference, then consolidate the shared shell and common primitives, and then align authentication, dashboard, document, and transaction screens in coherent passes. Keep current interactions and data semantics intact; use the existing page/component tests plus the UI build, lint, and tests to detect regressions. Review representative wide, tablet, and narrow layouts, especially tables, filters, auth panels, navigation, and the transaction detail panel.

### Make accessibility a token and component acceptance criterion

Contrast-check foreground/background pairs, targeting WCAG 2.2 AA (4.5:1 for normal text and 3:1 for large text and essential non-text information). Ensure focus indicators are not clipped, icons are not the sole state signal, controls have names and semantic roles, keyboard order follows visual hierarchy, and motion respects reduced-motion preferences. Existing low-emphasis text tokens may need a darker accessible value; adjust centrally rather than adding screen-specific overrides.

## Risks / Trade-offs

- [Normalizing a large stylesheet can cause visual regressions across less frequently used states] → Preserve a screen/state inventory, migrate in small passes, and compare key screens at wide, tablet, and narrow sizes while running existing UI checks.
- [A strict token system can make a necessary product-specific distinction awkward] → Allow only documented semantic variants or justified exceptions and update the reference with the introducing feature.
- [Some current palette combinations may not meet contrast targets] → Measure pairs before adoption and adjust central tokens, retaining the established palette direction instead of bypassing contrast requirements.
- [A contributor document and OpenSpec contract could drift] → Link the contributor reference to the OpenSpec capability and update both alongside token or component contract changes.

## Migration Plan

1. Audit existing UI colors, type styles, spacing, borders, elevation, breakpoints, and interaction states against the catalog in this design and the existing UI specs.
2. Define semantic tokens and write `ui/DESIGN_SYSTEM.md`, including the complete shared component/state inventory and the contribution/conformance checklist.
3. Refactor the global foundation and shared shell/primitives to consume the tokens, preserving current routes, behavior, and visual intent.
4. Align authentication, dashboard, document upload/list/detail, and transaction-history/panel patterns; document any intentional page-specific variants.
5. Verify existing component/page tests, TypeScript production build, lint, WCAG contrast, keyboard focus, reduced motion, and representative responsive layouts.

Rollback can revert the UI token/component migration and contributor reference without data/API migration; retain the approved design-system specification as the intended standard and resolve any implementation mismatch before reapplying.

## Open Questions

- None. Use the current visual direction as the baseline and make only contrast- or consistency-driven refinements during implementation.
