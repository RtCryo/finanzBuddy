# Tasks

## 1. Prepare the React 19 update branch

- [x] 1.1 Create and switch to `chore/upgrade-react-19`; verify it is the current branch with `git branch --show-current`.

## 2. Upgrade React runtime and types together

- [x] 2.1 Upgrade `react`, `react-dom`, `@types/react`, and `@types/react-dom` to the dashboard's React 19 versions and regenerate the lockfile; verify `npm ci` succeeds from `ui`.

## 3. Resolve compatibility issues and validate the UI

- [x] 3.1 Make only React 19 compatibility fixes required in source or tests; run `npx tsc -b`, `npm run build`, `npm run lint`, and `npm test` from `ui`, resolve migration-related failures, and rerun failed checks until all pass.
