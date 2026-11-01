# Tasks

## 1. Gateway related-expense query and API

- [x] 1.1 Add source ownership/processed-status lookup and paginated latest-version related-expense query in TransactionReadRepository; verify repository tests cover cross-document matches, exact case/whitespace equality, source exclusion, negative amounts only, null dates, tie ordering, page boundaries, versions, and foreign/unprocessed candidates.
- [x] 1.2 Add compact response DTOs and DocumentService/TransactionController endpoint with authenticated user resolution, page validation and size cap; verify service/controller tests cover owned and blank sources, missing/foreign/unprocessed source 404, unauthenticated rejection, invalid pagination and the response envelope.
- [x] 1.3 Document the endpoint and matching/pagination contract in api-gateway/README.md and docs/ARCHITECTURE.md; verify examples and routes against controller tests and run the relevant gateway Maven tests with rtk.

## 2. Shared transaction details section

- [x] 2.1 Add related-expense page types and API client in ui/src/types/transaction.ts and ui/src/api/transactions.ts; verify API tests cover source-ID route encoding, page parameters and response mapping.
- [x] 2.2 Add compact date/amount/currency rows and independent initial/append loading, empty, missing-counterparty, error/retry and stale-response handling to TransactionDetailsDialog; verify component tests cover formatting, Load more, preserved rows on failure, retry, close/switch races, latest-source semantics during historical selection and coexistence with category editing.
- [x] 2.3 Reuse semantic styles and document the section anatomy in ui/DESIGN_SYSTEM.md and UI behavior in ui/README.md; verify wide, tablet and narrow layouts, contained scrolling, keyboard access, focus return and contrast, then run relevant UI tests and rtk npm run build from ui.

## 3. Integrated verification and traceability

- [x] 3.1 Verify opening the section from history and processed-document lists preserves parent filters, sorting, page and scroll state; cover both entry points in relevant page integration tests.
- [x] 3.2 Review docs/README.md subsystem rows and capability implementation references, updating only materially changed mappings; verify rtk proxy python scripts/check_documentation_map.py and rtk openspec validate show-counterparty-expenses-in-transaction-details --strict pass.

## 4. Related transaction navigation

- [x] 4.1 Make related rows keyboard-accessible actions that open the latest target details in the same panel; guard loading, errors, unsaved edits and stale requests, preserve parent list/opener, update documentation, run UI checks and rebuild Docker UI.

- [x] 4.2 Prevent navigation jumps by preparing target content before replacement, retaining panel scroll and avoiding focus scrolling/loading layout shifts; verify regression tests and update Docker UI.
