# Proposal

## Why

Renovate's Dependency Dashboard offers React 19 and its matching type packages as a grouped major update from React 18. Upgrading them in isolation makes it possible to identify runtime, typing, and test compatibility issues before accepting the new React major.

## What Changes

- **BREAKING**: Upgrade `react`, `react-dom`, `@types/react`, and `@types/react-dom` together to the dashboard's React 19 target and regenerate the UI lockfile.
- Make only the source or test adjustments required for React 19 compatibility.
- Perform the update on a dedicated branch, run the UI checks and test suite, and resolve regressions before completing the update.

## Capabilities

### New Capabilities

None. This is a UI framework dependency update intended to preserve existing behavior; specs are intentionally skipped.

### Modified Capabilities

None.

## Impact

- `ui/package.json` and `ui/package-lock.json`
- React application entry point and UI components/tests if React 19 compatibility adjustments are required
- UI runtime and React type-checking
