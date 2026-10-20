# Tasks

## 1. Audit and publish the design-system reference

- [x] 1.1 Inventory current UI tokens, screen patterns, component variants, responsive transitions, and interaction states against the `ui-design-system` spec; verify the inventory covers authentication, workspace/dashboard, document, and transaction screens.
- [x] 1.2 Contrast-check the observed foreground/background pairs and define the named color, semantic, typography, spacing, sizing, layout, radius, elevation, icon, focus, and motion tokens; verify text and essential non-text pairs meet WCAG 2.2 AA or are adjusted centrally.
- [x] 1.3 Create `ui/DESIGN_SYSTEM.md` with the token tables, complete component/state catalog, responsive rules, accessibility standards, approved-exception process, and a future-change checklist; verify each requirement in `openspec/specs/ui-design-system/spec.md` is represented and linked.

## 2. Implement design foundations and shared patterns

- [x] 2.1 Replace duplicated global visual literals with semantic CSS/Tailwind tokens and standard foundation rules; verify the UI production build succeeds and representative pages retain the intended light fintech direction.
- [x] 2.2 Consolidate shared shell, navigation, headings, surfaces, buttons, fields, notices, and status patterns around the new tokens; update/add focused component tests and verify keyboard focus, disabled, validation, and semantic states.
- [x] 2.3 Define shared responsive and reduced-motion behavior for the shell, authentication layout, tables, filters, and transaction detail panel; verify representative narrow/tablet/wide layouts have no unintended page overflow and honor reduced-motion preferences.

## 3. Align existing product screens

- [x] 3.1 Align login and registration screens with the documented typography, form, validation, responsive, and focus patterns; verify existing authentication UI tests and production build pass.
- [x] 3.2 Align dashboard and document upload/list/detail screens with the shared heading, card, metric, table, feedback, status, and empty/loading/failure patterns; verify existing dashboard and document UI tests pass without changing their behavior.
- [x] 3.3 Align transaction history, transaction tables, pagination, amount semantics, and transaction detail panel with the shared patterns; verify existing transaction UI tests pass and filters, sorting, pagination, editing, and focus behavior remain intact.

## 4. Verify cross-screen conformance

- [x] 4.1 Run the complete UI test suite, TypeScript production build, and lint; verify all checks pass and resolve any regression introduced by design-system migration.
- [x] 4.2 Review each screen family at wide, tablet, and narrow viewport sizes and check keyboard operation, visible focus, semantic color/text cues, contrast, and reduced motion; record results and any approved exceptions in `ui/DESIGN_SYSTEM.md`.
- [x] 4.3 Cross-check the delivered token/component catalog and conformance checklist against the canonical spec and existing UI capability specs; verify future UI work can follow the documented reference without relying on undocumented one-off styles.
