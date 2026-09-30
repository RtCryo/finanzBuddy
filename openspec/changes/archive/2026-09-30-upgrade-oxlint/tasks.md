# Tasks

## 1. Update Oxlint

- [x] 1.1 Create and switch to a dedicated `chore/upgrade-oxlint` branch; update Oxlint to `1.86.0` in `ui/package.json` and regenerate `ui/package-lock.json`, verifying `npm ci` succeeds.
- [x] 1.2 Run `npm run lint`, `npx tsc -b`, `npm run build`, and `npm test` from `ui`; resolve any failures caused by the upgrade and rerun the affected checks until all pass.
