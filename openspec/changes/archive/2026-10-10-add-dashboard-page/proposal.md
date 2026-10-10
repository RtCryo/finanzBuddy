# Proposal

## Why

Users currently have to visit the documents and transaction-history pages separately to understand their financial data. An authenticated dashboard will provide a quick, owner-scoped summary of document processing and the transactions extracted from those documents.

## What Changes

- Add an authenticated dashboard page showing total uploaded documents, processed documents, documents in progress, saved transactions, unique categories, and unique counterparties.
- Provide aggregate dashboard data for the authenticated user rather than deriving totals from paginated document or transaction results.
- Add Dashboard to authenticated workspace navigation and make it the workspace landing page.
- Refresh dashboard metrics automatically while the page is open so asynchronous document processing is reflected without a manual refresh.
- Treat "in progress" as documents in `PENDING` or `PROCESSING`; count unique categories and counterparties among non-empty values in the user's processed transactions. Counts cover all available history, with no date filter.

## Capabilities

### New Capabilities
- `dashboard`: Authenticated dashboard page and owner-scoped aggregate statistics for documents and transactions.

### Modified Capabilities
- `workspace-shell-ui`: Add a Dashboard destination to authenticated workspace navigation and use it as the workspace root destination.
- `user-auth-ui`: Make Dashboard the post-login default while preserving a safe protected-page destination when login was triggered by a direct link.

## Impact

- UI routing, authenticated workspace navigation, post-login return-path handling, and a new dashboard view in `ui`.
- Current post-login return-path validation accepts only `/documents` routes; extend it for valid protected workspace routes and use Dashboard as the fallback.
- A new authenticated aggregate-data API and read-side aggregation across `documents` and `transactions` in `api-gateway`.
- Existing document and transaction data models already provide user ownership, lifecycle status, category, and counterparty fields. Current document-list counts cover uploaded, processed, and in-progress documents, while transaction history is paginated and does not expose total transaction or counterparty counts.
- No new external dependencies are anticipated.
