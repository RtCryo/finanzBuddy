# Proposal

## Why

The UI already has a recognizable light fintech visual language, but its colors, spacing, controls, layouts, and states are implemented as page-specific rules in a large stylesheet. A canonical design system will make the current experience coherent, document every shared visual element, and give future UI work an explicit standard to follow.

## What Changes

- Define a complete, maintained visual specification for the finanzBuddy web UI, covering design principles, color and semantic tokens, typography, spacing, sizing, layout, responsive behavior, component anatomy, interaction states, content conventions, and accessibility.
- Establish a reusable inventory and behavior contract for the application shell, authentication panels, headings, surfaces, buttons, form fields, notices, badges, tables, empty/loading/error states, pagination, and transaction details panels.
- Require new and materially updated UI screens and components to use the canonical tokens, components, and patterns; document how a genuinely new pattern is proposed and incorporated.
- Use the current teal/green, navy, neutral-surface visual direction as the starting point, reconcile existing screen-specific variations, and specify how existing UI is brought into alignment.

## Capabilities

### New Capabilities
- `ui-design-system`: Canonical visual language, component and pattern inventory, responsive and accessible interaction rules, and ongoing conformance for current and future web UI.

### Modified Capabilities
- None. The new capability establishes shared visual implementation standards without changing the existing authentication, document, dashboard, or transaction behavior contracts.

## Impact

- Affected project area: `ui/` React/TypeScript frontend, especially the shared styles in `ui/src/index.css` and cross-page components under `ui/src/components/`.
- Planning and product documentation: add the `ui-design-system` OpenSpec capability and its implementation plan; the system becomes the canonical reference for subsequent UI changes.
- No API, data model, service behavior, or new runtime dependency is required by this change.
