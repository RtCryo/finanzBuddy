# Design

## Context

See proposal.md - Why. The UI is mounted with `createRoot` inside `StrictMode`, uses the automatic JSX transform, and currently pairs React 18 with React 18 type packages. UI tests use React Testing Library 16, Vitest, and React's `act` helper for timer-driven updates.

## Goals / Non-Goals

**Goals:**
- Keep React runtime packages and their TypeScript declarations on the same major version.
- Preserve existing UI behavior and use the current testing stack to catch runtime and typing regressions.

**Non-Goals:**
- Adopt new React 19 APIs or perform application refactors unrelated to compatibility.
- Upgrade unrelated UI libraries or combine this work with the TypeScript 7 change.

## Decisions

- Upgrade `react`, `react-dom`, `@types/react`, and `@types/react-dom` together in one lockfile update. Keeping runtime and declaration packages aligned avoids mixed-major type resolution.
- Preserve `createRoot`, `StrictMode`, and the automatic JSX transform unless React 19 validation identifies a concrete incompatibility; do not migrate APIs merely because newer alternatives exist.
- Keep React Testing Library and other packages at their current versions unless npm peer resolution or test execution proves a compatibility adjustment is required.
- Perform the change on `chore/upgrade-react-19` and validate installation, type-check, production build, lint, and all UI tests. Resolve migration-related failures and rerun them before completion.

## Risks / Trade-offs

- [React 19 or its type declarations may expose deprecated APIs, stricter type errors, or rendering differences] → Make targeted source/test fixes and verify the full UI test suite, type-check, lint, and build.
- [Testing dependencies may have incompatible React peer requirements or changed `act` behavior] → Check `npm ci` and tests against the resolved dependency tree; adjust only the incompatible testing dependency if needed.
- [UI CI currently does not run the `npm test` script] → Explicitly run `npm test` as a required check for this migration in addition to existing CI checks.

## Migration Plan

1. Create the dedicated branch and update the four React runtime/type packages plus the lockfile together.
2. Run `npm ci`, `npx tsc -b`, `npm run build`, `npm run lint`, and `npm test` from `ui`.
3. Resolve failures caused by React 19 and repeat affected checks until all pass; revert the branch if compatibility cannot be achieved without unrelated changes.
