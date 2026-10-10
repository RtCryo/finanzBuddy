# Proposal

## Why

The current workspace repeats upload and privacy messaging, and offers a manual refresh control beside document statuses that already refresh automatically. Transaction history also has a cramped filter layout, duplicate date columns, no user-controlled sorting, and amounts that do not distinguish credits from debits. Streamlining these screens will make routine document and transaction review clearer and easier to scan.

## What Changes

- Remove manual Refresh controls from the document list and individual document details; retain automatic document-status polling and fetch current data whenever details are opened again.
- Remove the duplicate “Upload a file” workspace navigation item; keep upload available from the Documents page.
- Show the financial-data privacy reassurance only once in the authenticated workspace.
- Expand the transaction-history filter area across the available card width.
- Display Value date (instead of Booking date) in transaction history and make its From/To filter use Value date.
- Allow sorting transaction history by each displayed column, with sorting applied to the complete filtered result set across pages.
- Use soft palette-compatible green styling for positive amounts and red styling for negative amounts.

## Capabilities

### New Capabilities

- `workspace-shell-ui`: Defines streamlined shared workspace navigation and non-duplicated privacy reassurance.

### Modified Capabilities

- `document-list-ui`: Retain automatic status polling without a manual refresh action.
- `transaction-view-ui`: Remove the document-detail manual refresh action while retaining processing-status polling; use Value date for history display and date filtering; add sortable columns, full-width filters, and polarity-based amount colors.

## Impact

- UI: `ui/src/components/AppShell.tsx`, `ui/src/pages/DocumentsPage.tsx`, `ui/src/pages/DocumentDetailPage.tsx`, `ui/src/pages/TransactionHistoryPage.tsx`, related tests, and `ui/src/index.css`.
- API: transaction-history sorting parameters and the date field used by the history range filter; the API must sort before pagination so results remain correct across pages.
- OpenSpec: add `workspace-shell-ui` and update the document-list and transaction-view UI capability requirements.
