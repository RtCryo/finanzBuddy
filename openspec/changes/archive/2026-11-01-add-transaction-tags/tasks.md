# Tasks

## 1. Tag persistence and atomic editing API

- [x] 1.1 Add the next Gateway Liquibase changeset for owner-scoped personal tags and assignments, normalized-name uniqueness, ownership-consistent constraints, lookup indexes and rollback; extend LiquibaseMigrationTest to verify schema/constraints and startup without parser tables.
- [x] 1.2 Implement tag repository/service and authenticated dictionary/assignment reads with ownership checks; verify tests for zero/multiple tags, reusable unassigned tags, case/trim deduplication, invalid names, and unauthenticated/foreign access.
- [x] 1.3 Add unified PUT /transactions/{id} with optional category/newCategoryName and full desired tag set; refactor category mutation into the same transaction, coordinate document locking with legacy category writes, and handle dictionary-creation collisions through fresh-transaction retry; verify service/controller tests cover tag-only and uncategorized saves, unchanged saves, combined rollback, concurrency, original snapshot preservation and unchanged legacy endpoints.
- [x] 1.4 Document new read/write contracts, tag ownership, independent metadata and migration rollout in api-gateway/README.md and docs/ARCHITECTURE.md; verify examples match controller DTOs and existing version responses remain tag-free.

## 2. Server-side multi-tag history search

- [x] 2.1 Extend controller/service/history repository with repeated tagIds and AND/OR tagMode, validation and identical count/page membership predicates; verify repository/controller tests for all/any, extra assigned tags, duplicate selections, untagged transactions, no selections, invalid mode/IDs, foreign IDs, other filters, sorting and pagination without duplicate results.
- [x] 2.2 Document query examples and all/any semantics in the Gateway README and architecture HTTP interface; verify examples use repeated bare tagIds and state empty-selection/default behavior.

## 3. Detail-panel tag workflow

- [x] 3.1 Add separate tag API/types and unified edit calls without extending snapshot records; verify ui/src/api/transactions.test.ts covers read/write shapes and request failures.
- [x] 3.2 Add shared accessible chip selection and inline tag creation/removal to TransactionDetailsDialog under Edit transaction, preserving common Save changes/Cancel, current metadata on historical views and tag-only dirty detection; extend dialog tests for both list entry points, empty tags, reuse/creation/removal, uncategorized saves, cancel/close, failed-save drafts, historical read-only tags, and unchanged version count after tag-only saves.
- [x] 3.3 Style and document chip/multi-select states in ui/DESIGN_SYSTEM.md using existing tokens; update ui/README.md with the tag workflow. Verify keyboard adding/removing, named remove controls, focus recovery, announced changes, long names, contrast, reduced motion and wide/tablet/narrow contained layout; run relevant UI tests and rtk npm run build from ui.

## 4. History tag filter and refresh behavior

- [x] 4.1 Add tag multi-selection and all/any mode to TransactionHistoryPage without tag table cells, serialize repeated IDs, reset page on filter/mode change and clear selection/default mode with Clear filters; verify history/API tests cover AND/OR, combination with existing filters, no matches and unchanged row columns.
- [x] 4.2 Refresh results/counts and reusable choices after editor saves while preserving active filters/sorting, clamp out-of-range pagination, and provide close-focus fallback if the edited row disappears; verify history/dialog integration tests cover removal from active results, final-page emptying and both document/history entry points. Preserve related-expenses UI if the neighboring change has landed.
- [x] 4.3 Document tag filter semantics in ui/README.md and the responsive filter pattern in DESIGN_SYSTEM.md; verify the controls remain usable at all required viewport ranges and run relevant UI tests, rtk npm run build and rtk npm run lint from ui.

## 5. Integration and documentation integrity

- [x] 5.1 Run the Gateway Maven suite and complete UI test/build/lint checks using rtk prefixes; verify end-to-end tag persistence, shared category/tag atomic save, tag-only history stability, private choices and AND/OR results across sessions and pages. Record any unavailable checks.
- [x] 5.2 Update docs/README.md for materially changed subsystem capabilities/paths, maintain module/current-spec return links, and add transaction-tags to the map when it becomes a current spec during sync/archive; verify rtk proxy python scripts/check_documentation_map.py and strict OpenSpec validation pass without links to nonexistent current artifacts.

## Verification (2026-11-01)

- Gateway Maven suite passed, including migration, controller, service, repository, ownership, concurrency, atomic edit, and tag filter coverage.
- UI: 72 tests passed; production build passed; lint passed with the existing `src/auth/returnPath.ts` control-regex warning.
- Playwright: existing tags persisted across two fresh browser sessions; owner tag choices and OR filtering were available. The editor and filter stayed within 1440, 900, and 390 px viewports, and keyboard removal restored focus and announced the change.
- Documentation map integrity and strict OpenSpec validation passed. No checks were unavailable. `transaction-tags` remains a delta spec; add it to the current-capability map when the change is synced or archived.
