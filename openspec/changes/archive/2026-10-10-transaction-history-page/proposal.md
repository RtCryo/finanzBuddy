# Proposal

## Why

Users can currently view extracted transactions only by opening each processed document, making it difficult to review transaction activity across all uploaded statements. A dedicated, user-scoped transaction page with practical filters will provide a single place to browse those records.

## What Changes

- Add a protected page that lists transactions from all of the user's processed documents and can be reached from the workspace navigation.
- Provide text search across description and counterparty, a booking-date range, and category filtering.
- Add or extend an authenticated API capability to retrieve the user's transactions across documents, with results suitable for browsing at scale.
- Preserve the existing per-document transaction view.

## Capabilities

### New Capabilities

<!-- None; this extends the existing transaction viewing capability. -->

### Modified Capabilities

- `transaction-view-ui`: Add a cross-document transaction history page and filtering behavior while retaining the existing document-specific view.

## Impact

- UI routing, workspace navigation, transaction listing UI, and API client.
- API Gateway transaction query/controller/service/repository behavior; the current transaction endpoint is scoped to a single document, and transaction rows already carry a user ID.
- Existing transaction-view-ui specification.
