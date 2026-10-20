# Design

## Context

See proposal.md - Why. The history page and processed-document details page each render their own custom transaction table. Both are populated with the same `TransactionRecord` fields, including booking date, while history currently omits that date as a separate column. There is no existing dialog component or transaction-details API; the popup can use the selected row data already in memory.

## Goals / Non-Goals

**Goals:**

- Reuse a single transaction-details presentation and formatting approach in both list contexts.
- Keep transaction selection local to each page so the existing query, sort, pagination, and loaded document remain intact.
- Use platform dialog behavior for accessible modal semantics and keyboard dismissal without adding a dependency.

**Non-Goals:**

- Fetching additional transaction or source-document metadata, changing transaction APIs, or persisting UI state in the URL.
- Changing which columns are displayed in either transaction table.

## Decisions

- **Use one reusable transaction-details component backed by the selected `TransactionRecord`.** This avoids duplicating field labels, missing-value handling, and dialog behavior between the two pages. Fetching a separate detail record was considered, but all requested normalized fields are already present in the list response and it would add unnecessary API work.
- **Use the native HTML `<dialog>` element in modal mode.** It supplies dialog semantics, modal focus behavior, and Escape dismissal without an additional library. A custom positioned popup was considered, but would require implementing and maintaining equivalent focus management and keyboard behavior. The component should provide an explicit close button and restore focus to its activating transaction control after dismissal.
- **Keep each table's current data and browsing state mounted while the dialog is open.** Selection is an overlay state, not navigation; closing the dialog should return users to the same table position and filters/sort/page. The history date range and the document-specific table remain independent of dialog behavior.
- **Represent missing field values using existing list conventions.** Keep formatting consistent with the tables, including `Uncategorized` for an empty category and an em dash for other unavailable text/date values; display amount and currency as separate labeled fields.

## Risks / Trade-offs

- [Native dialog behavior can vary slightly across browser implementations] → Use the browser-supported modal API, retain an explicit close control, and verify Escape, focus entry, and focus restoration in UI tests.
- [Making table rows interactive can reduce discoverability or create invalid nested interactive semantics] → Expose each transaction as a clearly named keyboard-operable action and retain the existing table header/cell structure; verify accessible names and keyboard activation in both views.
- [Long descriptions or counterparty names may exceed the popup's available width] → Allow field values to wrap and ensure the dialog remains usable at narrow viewport widths.

## Migration Plan

No data or API migration is required. Rollback consists of removing the dialog interaction/component and restoring the current non-interactive rows.
