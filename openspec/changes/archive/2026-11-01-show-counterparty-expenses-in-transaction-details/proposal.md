# Proposal

## Why

Transaction details currently show only one transaction and its version history. Seeing other expenses paid to the same counterparty makes it easier to compare recent payments without leaving the details panel.

## What Changes

- Add a compact navigable "Other expenses with this counterparty" section to the shared transaction details panel opened from history or a processed document.
- Show other negative-amount transactions with the same exact Counterparty across the owner's processed documents, excluding the selected transaction.
- Order by Value date from newest to oldest and show Value date and signed amount with currency; make all matching expenses reachable through incremental loading.
- Allow selecting an expense to open its latest details in the same panel while preserving the originating list.
- Provide independent loading, empty, missing-counterparty, and recoverable error states without disrupting details or category editing.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `transaction-view-ui`: Show owner-scoped expenses with the same counterparty inside transaction details.

## Impact

- UI: `TransactionDetailsDialog`, transaction API/types, component/API tests, and shared semantic styles.
- API Gateway: additive authenticated `GET /transactions/{id}/related-expenses`, service ownership checks, latest-version JDBC query, and repository/controller tests.
- No parser, event, database ownership, or migration changes are expected.
- Implementation must update API/architecture documentation and relevant UI documentation; review the documentation map. This proposal does not change current implementation documentation.
