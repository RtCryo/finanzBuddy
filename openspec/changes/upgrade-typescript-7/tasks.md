# Tasks

## 1. Upgrade the TypeScript toolchain

- [ ] 1.1 Create and switch to `chore/upgrade-typescript-7`; verify it is the current branch with `git branch --show-current`.
- [ ] 1.2 Upgrade TypeScript to `~7.0.0` and regenerate the lockfile; verify `npm ci` succeeds from `ui`.

## 2. Resolve compiler and UI validation regressions

- [ ] 2.1 Preserve or minimally adjust the project-reference and compiler configurations for TypeScript 7; run `npx tsc -b` and `npm run build` from `ui`, resolve migration-related failures, and rerun both commands until they pass.
- [ ] 2.2 Run `npm run lint` and `npm test` from `ui`; resolve any regressions introduced by the toolchain update and rerun failed checks until they pass.
