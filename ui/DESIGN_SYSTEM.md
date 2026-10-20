# finanzBuddy UI design system

This is the contributor-facing implementation reference for the [canonical UI design-system capability](../openspec/specs/ui-design-system/spec.md). The capability is normative; this document records the current implementation choices and is updated with approved shared-pattern changes.

## Principles and screen inventory

- Keep the existing light fintech direction: calm neutral canvases, white surfaces, navy information, and restrained teal actions.
- Prefer shared semantic tokens and the patterns below over page-specific visual literals. Keep product behavior in its existing feature specification.
- Use text and accessible names as well as color to communicate status, validation, and financial meaning.
- Preserve useful information density in data views and provide deliberate contained scrolling when columns cannot reflow.

| Screen family | Existing composition and shared patterns | Important states / interactions |
|---|---|---|
| Authentication (`/login`, `/register`) | `AuthLayout`; split form/story card; brand, form stack, field labels, notices, submit action and footer links; [authentication UI contract](../openspec/specs/user-auth-ui/spec.md) | focus, native required/type validation, submission-disabled, API error, registration success; one-column form at narrow widths |
| Workspace shell and dashboard | `AppShell`; desktop sidebar/mobile horizontal navigation, sticky topbar, breadcrumb, profile/sign-out, privacy footer; page heading and six metric cards; [shell contract](../openspec/specs/workspace-shell-ui/spec.md), [dashboard contract](../openspec/specs/dashboard/spec.md) | active/focused navigation, loading, recoverable error, empty data as explicit zero metrics |
| Document upload | page heading, upload form surface, keyboard-accessible file picker, drop zone, type selector, progress, success/error notices, next-step/privacy cards; [upload contract](../openspec/specs/document-upload-ui/spec.md) | dragging/selected file, unsupported or oversized file, uploading progress, disabled submit, success/failure |
| Document list | summary metrics, toolbar/search, contained document table, text status badge, pager, empty/loading/error states; [list contract](../openspec/specs/document-list-ui/spec.md) | row hover/focus/navigation, status polling, disabled previous/next, no-results search |
| Document detail | back link, filename/status heading, icon-only download action, information surface, transaction table, transaction detail dialog; [transaction view contract](../openspec/specs/transaction-view-ui/spec.md) | loading/error; pending/processing; failure details; processed/empty; download disabled/error; dialog read/edit/history/focus return |
| Transaction history | page heading, full-width search/date/category filters, sortable contained table, semantic amount text, numbered pagination, transaction detail dialog; [transaction view contract](../openspec/specs/transaction-view-ui/spec.md) | loading/error/empty/no matches; filter and sort focus; active sort/page; disabled pagination; dialog read/edit/history/focus return |

The shared CSS selectors in `ui/src/index.css` define the current implementation families: `.app-shell`, `.sidebar`, `.workspace-nav`, `.topbar`, `.page-heading`, `.surface`, `.button`, `.field`, `.notice`, `.status-badge`, `.document-table-wrap`, `.transaction-table-wrap`, `.history-filters`, and `.transaction-details-dialog`. React shell/layout components are in `ui/src/components/`; page composition is in `ui/src/pages/`.

## Tokens

The CSS custom properties in `ui/src/index.css` are the runtime token layer. Prefer semantic roles (for example `--color-text-muted`) rather than selecting a color by appearance.

### Color and semantic roles

Ratios below use the WCAG relative-luminance formula. Text pairs meet 4.5:1; meaningful status and control boundaries meet at least 3:1. Decorative borders may be lower contrast when they are not the sole means of identifying a control or grouping.

| Token | Value | Use / verified foreground-background pair |
|---|---|---|
| `--color-primary` | `#167D68` | Primary action, active navigation, positive amount text; on white 5.04:1 |
| `--color-primary-strong` | `#116653` | Hover/pressed action, success text; on white 6.89:1 |
| `--color-canvas` | `#F6F8FB` | Workspace page background |
| `--color-surface` | `#FFFFFF` | Cards, dialogs, menus, fields |
| `--color-text-strong` | `#172B4D` | Headings and high-emphasis values; on white 14.10:1 |
| `--color-text` | `#344054` | Body and standard control text; on white 10.46:1 |
| `--color-text-muted` | `#59677A` | Supporting text, metadata, placeholders; on white 5.76:1 |
| `--color-border` | `#E8EDF3` | Decorative dividers and card edges |
| `--color-control-border` | `#718096` | Resting essential field/control boundaries; on white 4.02:1 |
| `--color-focus` | `#167D68` | Visible focus outline and focused control border; on white 5.04:1 |
| `--color-border-subtle` | `#EEF1F5` | Repeated non-interactive dividers |
| `--color-success-bg` / `--color-success` | `#EAF5F1` / `#116653` | Success and processed; text pair 6.18:1 |
| `--color-success-border` | `#C5E5D9` | Success notice and selected-page outline |
| `--color-warning-bg` / `--color-warning` | `#FFF4E5` / `#9A620F` | Warning and processing; text pair 4.68:1 |
| `--color-danger-bg` / `--color-danger` | `#FDECEC` / `#A53030` | Error and failed; text pair 6.00:1 |
| `--color-danger-border` / `--color-danger-surface` | `#F1CACA` / `#FEF6F6` | Error notice/detail surfaces |
| `--color-primary-soft-border` / `--color-primary-soft-surface` | `#AFCFC4` / `#F7FBF9` | Hover/selected soft accent surface |
| `--color-drop-zone-surface` / `--color-drop-zone-active-surface` | `#FAFCFD` / `#F3FAF7` | Upload drop zone at rest and while dragging/holding a selected file |
| `--color-table-header-surface` | `#F8FAFC` | Shared document and transaction table header surface |
| `--color-table-row-hover-surface` | `#FAFCFB` | Document row hover surface; row content remains high-contrast |
| `--color-info-bg` / `--color-info` | `#EAF2FB` / `#2F6799` | Informational status; text pair 5.28:1 |
| `--color-neutral-bg` / `--color-neutral` | `#F1F4F8` / `#59677A` | Pending/neutral state; text pair 5.22:1 |
| `--color-positive-amount` | `#167D68` | Positive amount text; text stays explicit |
| `--color-negative-amount` | `#B54747` | Negative amount text; on white 5.31:1 |
| `--color-metric-purple-bg` / `--color-metric-purple` | `#F2EDFB` / `#7656A8` | Decorative unique-category metric icon; its adjacent label/value carries meaning |
| `--color-metric-teal-bg` / `--color-metric-teal` | `#E8F5F4` / `#287C78` | Decorative unique-counterparty metric icon; its adjacent label/value carries meaning |

Contrast audit adjustments from the observed stylesheet: muted `#718096` becomes `#59677A` for text (4.02:1 to 5.76:1 on white); informational foreground `#3674AD` becomes `#2F6799` (4.37:1 to 5.28:1 on its surface); pale input borders use `#718096` when they are the control's only visible boundary. The decorative `--color-border` remains intentionally pale. Positive/negative transaction meaning also appears as the formatted amount, not color alone.

### Typography, spacing, sizing and layout

| Role | Standard |
|---|---|
| Typeface | `Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif` |
| Page title | 26–30 px, weight 700, line-height 1.2; clamp to viewport |
| Section title | 16–19 px, weight 600–650, line-height about 1.3 |
| Body / control | 14–15 px, weight 400–500, line-height 1.45–1.6 |
| Label | 13 px, weight 600; labels stay adjacent to their control |
| Metadata / dense table | 11–12 px, normal line-height at least 1.4; do not use for essential long-form instructions |
| Spacing scale | 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 px; dense data rows may use documented compact values |
| Content width | 1440 px maximum; workspace desktop sidebar 248 px (210 px through tablet); main gutters 24–48 px wide and 18 px narrow |
| Common dimensions | 46 px primary action; 38–42 px compact action/filter; 50 px auth field; 56–68 px data row; 24 px card radius for auth, 8–14 px general control/surface radii |
| Elevation | Subtle navy-tinted shadows only for raised surfaces/dialogs; use the existing low/medium/overlay treatment rather than decorative glow |

### Icons, focus and motion

- Use the existing inline SVG approach with consistent 16–24 px strokes and `currentColor`; hide decorative icons with `aria-hidden="true"` and give icon-only controls visible or programmatic names.
- Keep a 3 px solid primary outline with a 2 px offset for keyboard focus. Composite search/date fields expose the same ring on their enclosing boundary when a child receives keyboard focus; native selectors keep their own ring. Focus must not be communicated solely by a subtle shadow or color shift.
- Use short 130–180 ms transitions for hover/progress and reserve longer movement for deliberate selection animation. Under `prefers-reduced-motion: reduce`, disable non-essential transitions and animations while preserving state changes.

## Shared component and state catalog

| Pattern | Anatomy and variants | Required states and responsive behavior |
|---|---|---|
| Application shell | Brand; workspace navigation; breadcrumb; profile/avatar and sign-out; main content; one privacy reassurance footer | active, hover, focus; desktop sidebar becomes a horizontally scrollable navigation row on narrow screens; profile email may collapse while sign-out remains available |
| Authentication layout | Split form/story card; brand; eyebrow/title/description; field stack; feedback; footer and privacy text | form errors/success and disabled submission; story panel is omitted on narrow screens; form remains full width and vertically scrollable |
| Page/section headings | One page `h1`, optional support text and one trailing action; section `h2` and support text | title wraps without clipping; action moves below title on narrow screens |
| Surface and metric card | White surface, standard border/radius/elevation; label, value and optional decorative icon | cards collapse to one column on narrow screens; loading/error is separate from a valid zero metric |
| Buttons and icon-only actions | Primary, secondary, tertiary/link, and icon-only action; 44 px minimum target where practical | hover, visible focus, disabled/wait; icon-only actions have accessible names; disabled actions are not conveyed by opacity alone where the reason matters |
| Fields and selectors | Persistent label, control, optional hint/error; text/date/select variants | default, hover, focus, disabled, invalid; clear visible boundary, focus ring and associated text error; responsive width is 100% of its form column |
| Notices and status badges | Success/error notice with text; pending/processing/processed/failed badge with label and optional shape cue | loading/status uses live/status semantics as appropriate; color supplements readable status text |
| Tables and categories | Document/transaction headers and rows; sortable headers; category/type indicators; signed amount text | preserve semantic table roles; table wrapper owns horizontal scrolling; row focus is visible; transaction sign remains represented in text |
| Empty/loading/failure | Explicit loading status; empty explanation and relevant next action; recoverable error; processing and failure detail panels | never display unavailable data as a successful blank/zero; concise status copy; content remains readable at narrow widths |
| Pagination | Result range, numbered pages, first/last page, ellipses, previous/next controls | current page is visually and semantically selected; unavailable arrows stay visible and disabled; controls wrap/scroll within their own region |
| Transaction detail panel | Right-side dialog; heading/close; summary; version selector/comparison; detail fields; optional editor/footer actions | read-only, edit, loading, error; focus returns to opener; scroll stays inside panel; full viewport width on narrow screens; footer actions remain reachable |

Product-specific behavior such as polling intervals, upload constraints, transaction sorting/filtering/editing, routing, and pagination semantics remains defined by each capability spec rather than this visual catalog.

## Responsive rules

| Viewport | Rules |
|---|---|
| Wide (>1280 px) | 248 px sidebar; capped centered content; filters use available card width; multi-column metrics and upload layout |
| Tablet (681–1280 px) | At 900 px and below, sidebar narrows and cards/upload/auth composition reflows; at 1280 px and below, transaction filters wrap to usable columns |
| Narrow (≤680 px) | Workspace navigation becomes a horizontal strip; content gutters shrink; headings/actions stack; summary/upload/detail grids become one column; auth story is hidden; wide tables scroll inside their wrappers; detail dialog fills the viewport |

No page-level horizontal scrolling is intentional. Give wide tables, nav strips, pagers, and version tabs their own contained scroll regions, and retain access to essential controls without hover.

## Conformance review record — 2026-10-21

Reviewed the CSS breakpoints, page/component structure, focus states, and contained scrolling rules against wide (>1280 px), tablet (681–1280 px), and narrow (≤680 px) layouts. The repository does not configure a browser screenshot/viewport runner, so viewport results below are source-level responsive review backed by UI DOM interaction tests rather than screenshot diffs.

| Check | Result |
|---|---|
| Wide | 248 px shell sidebar and capped 1440 px content remain; multi-column summary/upload layouts remain; history filters span the card; document and transaction tables scroll inside their wrappers. |
| Tablet | Sidebar reduces to 210 px at 900 px; upload/auth and transaction filters reflow; table scrolling stays contained. |
| Narrow | At 680 px, shell navigation becomes its own horizontal strip, content/actions and summary grids stack, auth story is hidden, tables/pagination retain contained scrolling, and the transaction detail dialog fills the viewport with a stacked summary and reachable footer actions. |
| Keyboard and state | Shared shell destinations/sign-out are keyboard reachable; transaction rows open by keyboard and dialog close restores focus; loading/error/status text and disabled pagination states remain covered by the UI tests. |
| Focus and contrast | Shared focus outline is solid 3 px primary; search/date filter wrappers and category selectors visibly retain it during keyboard focus; field boundaries use the contrast-checked control-border token; documented semantic text pairs meet 4.5:1 and essential control boundaries meet 3:1. |
| Reduced motion | Global `prefers-reduced-motion: reduce` rules minimize transitions/animations without suppressing state content. |
| Exceptions | No new approved screen-specific exceptions were required. Decorative metric accent colors remain labeled by adjacent text and are tokenized. |

Verification on this review: `npm run test` (63 tests), `npm run build`, and `npm run lint` completed. Lint reports the existing `no-control-regex` warning in `ui/src/auth/returnPath.ts`; that file is outside this change.

## Accessibility and content standards

- Target WCAG 2.2 AA: 4.5:1 normal text, 3:1 large text and essential non-text controls/state boundaries. Verify actual token pairs when introducing a new foreground/background combination.
- Support keyboard operation, logical focus order, persistent labels, semantic headings/regions/table roles, accessible icon-only names, and a clearly visible focus indicator.
- Pair semantic colors with status text or accessible names. Amounts keep their signs and formatted value in text.
- Respect reduced motion. Avoid using motion as the only indication of state.
- Use concise English UI copy and sentence case; preserve established financial date, amount, and currency formatting from `ui/src/format.ts`.

## Exceptions and change checklist

A screen-specific treatment is allowed only when the shared catalog cannot express the need. Record the component/screen, rationale, affected token or pattern, accessibility/contrast result, responsive behavior, and whether the exception is temporary in this document. Prefer converting repeated exceptions into a shared token or documented variant.

For new or materially changed UI:

1. Read the [canonical capability](../openspec/specs/ui-design-system/spec.md) and the applicable feature UI spec.
2. Reuse named tokens and documented component anatomy; document an approved exception or new reusable pattern here.
3. Check normal/large text and essential control/state contrast, text alternatives for semantic color, keyboard order/focus, disabled and validation states, and reduced motion.
4. Review wide, tablet, and narrow layouts; verify contained table/panel scrolling does not create page overflow.
5. Run the relevant UI tests, production build, and lint; record any intentional exception and its rationale above.
6. Keep this catalog and the canonical capability aligned when an approved shared foundation or pattern changes.
