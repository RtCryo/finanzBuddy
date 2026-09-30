# Proposal

## Why

Renovate's Dependency Dashboard is rate-limiting the Oxlint update from the current `^1.81.0` declaration to `1.86.0`. Updating the UI linter independently keeps its maintenance isolated and allows the project to confirm that lint behavior remains stable.

## What Changes

- Update the Oxlint development dependency and lockfile to the dashboard's `1.86.0` target.
- Validate installation, linting, type-checking, build, and UI tests on a dedicated update branch; resolve regressions before completing the update.

## Capabilities

### New Capabilities

None. This is a tooling dependency update with no user-facing behavior change; specs are intentionally skipped.

### Modified Capabilities

None.

## Impact

- `ui/package.json` and `ui/package-lock.json`
- UI lint and CI validation using Oxlint
