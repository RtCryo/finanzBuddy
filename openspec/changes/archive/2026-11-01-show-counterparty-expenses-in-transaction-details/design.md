# Design

## Context

See [proposal](proposal.md) for motivation and [delta](specs/transaction-view-ui/spec.md) for acceptance criteria. TransactionDetailsDialog is shared by history and document lists and currently loads categories and versions together. TransactionReadRepository projects latest snapshots through JDBC and scopes history to transaction/document owner and processed status. Existing history text search matches substrings in description or counterparty, so it cannot implement this feature's equality semantics.

References: [documentation map](../../../../docs/README.md), [architecture](../../../../docs/ARCHITECTURE.md), [UI patterns](../../../../ui/DESIGN_SYSTEM.md).

## Goals / Non-Goals

**Goals:** Authoritative server matching, bounded requests, shared presentation, and failure isolation from editing.
**Non-Goals:** Merchant normalization, recurring-payment detection, totals, currency conversion, parser changes or schema migrations.

## Decisions

1. Add authenticated GET /transactions/{id}/related-expenses?page=0&size=20 to TransactionController through DocumentService and TransactionReadRepository. Return a history-style page envelope with items (id, valueDate, amount, currency), page, size, totalElements, totalPages. Reuse nonnegative-page, positive-size validation and size cap 100. Authorize source transaction/document ownership and processed status before any matching, returning 404 for inaccessible sources. Derive Counterparty on the server. Extending general text search was rejected because it matches descriptions and substrings and would leave source authorization/exclusion to the client.

2. Reuse/refactor the latest-version SQL projection for source and candidates. Apply bound equality, amount less than zero, source ID exclusion, and processed-owner predicates. Preserve case and whitespace on PostgreSQL and H2; test equality semantics. Order null dates last, then value_date DESC, id ASC before pagination. An authorized blank source returns an empty page. Exact stored-text equality and negative amounts are planning assumptions interpreting same Counterparty and expenses.

3. Place a compact section after transaction fields inside transaction-details-scroll. Reuse semantic tokens, detail headings, financial formatters and compact buttons. Show only Value date and signed amount with currency, initially 20 rows, appending pages via Load more. This bounds responses without silently truncating matches. Document the new panel section anatomy in DESIGN_SYSTEM.md during implementation.

4. Load related data separately from categories/versions. Guard responses by transaction ID and panel lifecycle, prevent duplicate loads, retry failed pages, and preserve loaded rows on append failure. Use latest source Counterparty for missing-data presentation even while an older snapshot is selected (loaded versions, opening record as fallback). Category edits do not require a reload because only category is editable. Related rows are keyboard-accessible buttons that load the selected transaction through the existing owner-scoped versions endpoint and replace panel content with its latest snapshot. Prepare target versions and its initial related page before replacing the panel content. Reuse loaded categories, focus the heading with preventScroll, and preserve panel scroll position to avoid navigation layout jumps. Reset editor, version selection and related pages for the new source; preserve the original opener and parent list state. Disable row navigation while editing or opening another transaction, show opening errors inline, and ignore closed/stale requests.

## Risks / Trade-offs

- Exact names split spelling variants → Explicit semantics and tests; normalization remains outside scope.
- Common counterparties may match many rows → Bounded pages and incremental loading. Add indexes only if query-plan evidence warrants a migration.
- Offset pages can shift during concurrent imports → Deterministic ordering and fresh loading on reopen, consistent with existing history pagination.
- Independent requests add UI states → Test retries, stale responses, historical selection and editing together.

## Migration Plan

Deploy the additive gateway endpoint before or with the UI. No data migration or dependency is expected. Roll back the UI section and then the endpoint if necessary. Implementation updates architecture HTTP routes, gateway API documentation, UI panel documentation and relevant capability references, and reviews documentation-map entries. Current implementation documentation remains unchanged during planning.
