# Tasks

## 1. Aggregate Transaction API

- [x] 1.1 Add a pageable aggregate transaction query that scopes by authenticated owner, includes only processed documents, applies text/date/category filters, and sorts deterministically; verify repository tests cover combined filters, nullable fields, and page boundaries.
- [x] 1.2 Expose the authenticated aggregate endpoint and page response, including the user's available categories, while preserving the per-document endpoint contract; verify API integration tests cover owner isolation, unauthenticated access, and exclusion of non-processed documents.
- [x] 1.3 Add or update API tests for empty results, inclusive booking-date bounds, case-insensitive description/counterparty search, and category selection; verify with `api-gateway\mvnw.cmd test`.

## 2. Transaction History UI

- [x] 2.1 Add the protected `/transactions` route and workspace navigation entry with matching page labeling; verify navigation tests confirm authenticated access and the new link target.
- [x] 2.2 Implement the transaction history page, filter controls, clear-filters behavior, numbered pagination, and loading/error/empty states using the aggregate API; verify page tests cover filter combinations, page reset when filters change, rendered transaction fields, direct first/last-page jumps, ellipsis behavior, current-page highlighting, and disabled boundary arrows.
- [x] 2.3 Extend UI API types/client and add tests for aggregate query parameters and page response handling; verify with `npm test` in `ui` and `npm run build` in `ui`.
