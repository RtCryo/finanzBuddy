# Proposal

## Why

Automatically parsed transaction categories can be inaccurate, and users currently cannot correct them from transaction details. Letting users reassign categories while retaining the full version history makes corrections visible across transaction history and document details without losing the original parsed record.

## What Changes

- Allow a user to change a transaction's category from its transaction-details side panel, choosing an existing category or creating a personal reusable category.
- Open transaction details in a read-only inspection state; require an explicit, generic edit action before any field becomes editable.
- Keep category creation inside the category editor and save it with the transaction through one generic save action.
- Preserve a full, read-only snapshot for the original transaction and after each successful category change. Show version navigation only when multiple versions exist.
- Show the latest transaction version in transaction history and in the transaction list on a document's detail page.
- Keep category edits and category choices scoped to the authenticated user.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `transaction-view-ui`: add category editing, reusable categories, transaction-version history, and latest-version behavior across transaction history and document details.

## Impact

- UI: transaction-details side panel, transaction history, and document-detail transaction list.
- API Gateway: owner-scoped transaction/category read and update operations, including transaction version history.
- Persistence: transaction version snapshots and user-owned reusable categories; the current transaction data becomes the initial version.
- Parser service and its transaction table are currently the write owner, while the API Gateway reads transactions through JDBC. The implementation must establish a safe write/persistence boundary without changing the user's category history when documents are reprocessed.
