# Design

## Context

See proposal.md - Why. Oxlint is a UI development dependency; `npm run lint` invokes it from `ui/package.json`, and UI CI runs that script on Node 24.

## Goals / Non-Goals

**Goals:**
- Update Oxlint and its lockfile while preserving the existing lint command and configuration behavior.
- Verify the new linter against current UI source.

**Non-Goals:**
- Change lint rules, introduce plugins, or modify application code unless required by a new Oxlint diagnostic.
- Combine the update with other UI dependency upgrades.

## Decisions

- Update only the Oxlint development dependency to the dashboard's `1.86.0` target and regenerate the lockfile. A separate update keeps lint-result changes attributable to this version.
- Keep the existing `npm run lint` entry point; no CI workflow changes are needed because the workflow already calls it.
- Make the update on `chore/upgrade-oxlint` and run installation, lint, type-check, build, and UI tests; fix migration-related regressions and rerun failed checks before completion.

## Risks / Trade-offs

- [A newer Oxlint release may report diagnostics differently or add warnings] → Review each changed diagnostic, make only necessary fixes, and require a clean lint run along with the UI regression checks.

## Migration Plan

1. Create the dedicated branch, update Oxlint, and regenerate the npm lockfile.
2. Run `npm ci`, `npm run lint`, `npx tsc -b`, `npm run build`, and `npm test` from `ui`.
3. Resolve migration-related failures and rerun affected checks until all pass; revert the branch if the linter cannot be integrated without unrelated changes.
