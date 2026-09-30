# Design

## Context

See proposal.md - Why. The UI uses TypeScript through project references (`tsconfig.json`), with separate application and Node/Vite configurations. CI runs `npx tsc -b`; the production build runs `tsc -b && vite build`.

## Goals / Non-Goals

**Goals:**
- Upgrade the compiler while preserving the current project-reference, bundler, and Node configuration intent.
- Surface and resolve TypeScript 7 diagnostics in UI source and configuration before the update is complete.

**Non-Goals:**
- Refactor application types or change runtime behavior beyond fixes required by new compiler diagnostics.
- Upgrade React or other UI dependencies as part of this change.

## Decisions

- Update TypeScript independently to the dashboard's `~7.0.0` target and regenerate the npm lockfile. Keeping the compiler migration separate makes newly surfaced diagnostics attributable to one change.
- Preserve current `tsconfig` options and project references unless TypeScript 7 reports an unsupported or changed option; make the smallest compatible configuration/source adjustment for each diagnostic.
- Use Node 24, matching UI CI, and verify both the direct type-check command and the full production build. Run the UI test and lint scripts as regression checks even though the current workflow does not run the test script.
- Make all changes on `chore/upgrade-typescript-7` and resolve dependency-installation, compiler, build, lint, or test failures before completion.

## Risks / Trade-offs

- [TypeScript 7 may remove or change options and APIs used by this project's configs or source] → Check every diagnostic and error against the migration, update only affected usages, and rerun type-check and build.
- [Vite/Vitest or another tool may constrain its accepted TypeScript peer range] → Verify `npm ci` and UI commands with the resolved dependency graph; make only a necessary compatible tooling adjustment and keep unrelated upgrades out.

## Migration Plan

1. Create the dedicated branch and update TypeScript plus the lockfile.
2. Run `npm ci`, `npx tsc -b`, `npm run build`, `npm run lint`, and `npm test` from `ui`.
3. Resolve migration-related failures and rerun affected checks until all pass; revert the branch if a compatible compiler/toolchain cannot be established.
