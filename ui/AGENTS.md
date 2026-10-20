# UI implementation instructions

These instructions apply to every task that creates or materially changes files under `ui/`.

## Required design-system workflow

1. Before editing, read [`DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) and the applicable UI capability spec under `../openspec/specs/`.
2. Treat `DESIGN_SYSTEM.md` and the linked `ui-design-system` capability as the shared UI contract. Reuse its semantic tokens, component anatomy, content conventions, and responsive patterns; do not introduce undocumented colors, spacing, or component variants.
3. If the screen genuinely needs a pattern not covered by the catalog, document the pattern, rationale, accessibility/contrast result, and responsive behavior in `DESIGN_SYSTEM.md` in the same change.
4. Preserve feature behavior, routes, API contracts, financial formatting, and existing accessible names unless the task explicitly changes them.

## Required conformance checks

- Check wide (>1280 px), tablet (681–1280 px), and narrow (≤680 px) behavior. Keep wide tables and panels in intentional contained scrolling regions; do not introduce page-level horizontal overflow.
- Keep controls keyboard-operable with visible focus, accessible names, and clear disabled/validation states. Never communicate status or transaction meaning by color alone.
- Check text and essential control/state contrast against WCAG 2.2 AA tokens/pairs, and respect `prefers-reduced-motion`.
- Run the relevant UI tests and `npm run build` from `ui/`; run `npm run lint` for broad or shared UI changes. Report any check that could not be run.
- When changing a shared foundation or pattern, update the implementation and `DESIGN_SYSTEM.md` together.
