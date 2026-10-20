# Tasks

## 1. Persistence and version model

- [x] 1.1 Add API Gateway Liquibase tables for personal categories and full transaction snapshots, with user/version uniqueness constraints and no startup dependency on the parser-owned transaction table; verify the gateway changelog applies without requiring that table.
- [x] 1.2 Implement owner-scoped category and version persistence, including initial-version fallback/materialization, category normalization, and atomic category-change snapshot appends; verify repository/service tests cover first edit, later edits, blank/duplicate categories, rollback on failure, and concurrent version conflicts.

## 2. API and current-version reads

- [x] 2.1 Add authenticated gateway operations to list/create personal categories, read transaction versions, and update a transaction category; verify controller integration tests cover valid owners, unauthenticated requests, and cross-user access rejection.
- [x] 2.2 Resolve the latest snapshot in transaction-history and document-transaction reads before filtering, sorting, and pagination; verify repository tests show one current transaction per source row and apply category filters to its latest category.
- [x] 2.3 Return category choices from current and historical transaction versions plus saved personal categories; verify API tests confirm former categories remain selectable and another user's categories are never returned.

## 3. Transaction-details editing UI

- [x] 3.1 Add typed UI API clients and models for category listing/creation, category updates, and transaction versions; verify `ui/src/api/transactions.test.ts` covers request paths, payloads, and responses.
- [x] 3.2 Extend the shared transaction-details popup with a category editor, personal-category creation, save/error states, and chronological read-only version history; verify `TransactionDetailsDialog.test.tsx` covers existing and new categories, saved versions, and failed saves.
- [x] 3.3 Refresh the selected transaction and originating list after a successful save while preserving filters, sorting, pagination, and scroll position; verify both transaction-history and document-detail page tests show the new category without duplicate rows.

## 4. Integration verification

- [x] 4.1 Run `mvn -f api-gateway/pom.xml test` and `npm test --prefix ui` plus `npm run build --prefix ui`; verify all backend/UI tests and the production TypeScript/Vite build pass together.

## 5. Transaction details interaction redesign

- [x] 5.1 Refactor the shared transaction-details popup to open read-only and enter edit mode only through the generic `Edit transaction` action; keep non-category fields read-only, provide generic `Save changes` and `Cancel`, and verify view/edit/cancel behavior in `TransactionDetailsDialog.test.tsx`.
- [x] 5.2 Move personal-category creation into the category selector as an inline create option and name input, committed with the same `Save changes` action; verify selecting existing categories and creating/assigning a new category in `TransactionDetailsDialog.test.tsx`.
- [x] 5.3 Replace the always-visible version list with chronological selector and previous/next navigation shown only when multiple versions exist; default to latest, keep older versions read-only, and verify single-version hiding and multi-version navigation in `TransactionDetailsDialog.test.tsx`.
- [x] 5.4 Update transaction-history and document-detail tests for the redesigned popup; verify successful edits refresh the current row without duplicates and preserve filters, sorting, pagination, and scroll position.
- [x] 5.5 Run `npm test --prefix ui` and `npm run build --prefix ui`; verify the redesigned popup passes the full UI suite and production build.

## 6. Side-panel inspection redesign

- [x] 6.1 Replace the centered transaction dialog with a responsive right-side panel; keep the initial state read-only, show category as plain text, and expose edit mode only through the explicit `Edit transaction` action.
- [x] 6.2 Replace version arrows/dropdown with a compact chronological selector; show the selected full snapshot and a generic field-by-field comparison with the immediately older version, while omitting selector/comparison for a single version and comparison for the original.
- [x] 6.3 Update shared-panel interaction and responsive tests, then run `npm test --prefix ui` and `npm run build --prefix ui`.
