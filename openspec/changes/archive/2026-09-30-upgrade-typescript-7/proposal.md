# Proposal

## Why

Renovate's Dependency Dashboard offers TypeScript 7 as an update from the UI's current `~6.0.2` range. A dedicated major-version migration will expose compiler or configuration incompatibilities without coupling them to other dependency changes.

## What Changes

- **BREAKING**: Upgrade the UI TypeScript development dependency and lockfile to the dashboard's `~7.0.0` target.
- Keep the existing project-reference and compiler configuration behavior unless TypeScript 7 requires a compatible adjustment.
- Perform the migration on a dedicated branch, run UI validation, and resolve regressions before completing the update.

## Capabilities

### New Capabilities

None. This is a build-tool update with no user-facing behavior change; specs are intentionally skipped.

### Modified Capabilities

None.

## Impact

- `ui/package.json`, `ui/package-lock.json`
- `ui/tsconfig.json`, `ui/tsconfig.app.json`, and `ui/tsconfig.node.json` if compatibility changes are required
- UI type-check and build commands in GitHub Actions
