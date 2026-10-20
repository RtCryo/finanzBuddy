# Proposal

## Why

Transaction tables show only a subset of each transaction's normalized fields, so users cannot conveniently inspect a record in full from the transaction history or a document's transaction list. Opening a focused popup from a selected row will make the existing transaction data easier to review without leaving the current view.

## What Changes

- Make transaction rows in both transaction history and processed-document transaction lists open a popup with the selected transaction's available normalized fields.
- Include booking date in the popup, including for transactions opened from history, where it is not shown as a separate table column.
- Provide accessible popup behavior, including a clear close action and dismissal via Escape; preserve the underlying list and its current state when the popup closes.
- Use the transaction data already loaded by the respective views; no additional transaction fields or API endpoint are introduced.

## Capabilities

### New Capabilities

<!-- None; this extends the existing transaction viewing capability. -->

### Modified Capabilities

- `transaction-view-ui`: Add popup-based transaction details from the history table and document transaction list.

## Impact

- UI transaction-history and document-detail row interactions, a shared or reusable transaction-details popup, and its styling/accessibility behavior.
- Existing `TransactionRecord` data and APIs are sufficient; no backend or API contract changes are expected.
- Transaction history and document-detail UI tests need coverage for opening, displayed fields, dismissal, and retaining the current view state.
