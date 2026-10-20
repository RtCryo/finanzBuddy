# Transaction View UI Specification

## Purpose

Shows normalized transactions extracted from a processed document.

## Requirements

### Requirement: Transactions are shown for processed documents
The system SHALL display transactions only when the document status is `PROCESSED`.

#### Scenario: View transactions
- **WHEN** a user opens a processed document
- **THEN** the UI fetches transactions from `GET /documents/{id}/transactions` and displays them

### Requirement: Transaction columns match normalized model
The system SHALL display booking date, value date, amount, currency, counterparty, description, and category for transactions on a processed document's detail page. Transaction history SHALL display Value date, amount, currency, counterparty, description, and category, and SHALL NOT display Booking date as a separate column; each transaction's underlying normalized fields remain unchanged.

#### Scenario: CSV-derived transaction
- **WHEN** a CSV document has been parsed and a user opens its processed-document details
- **THEN** the transaction row shows both booking date and value date with the other normalized fields

### Requirement: Non-processed documents hide transactions
The system SHALL not attempt to load transactions for documents that are not `PROCESSED`.

#### Scenario: Pending or processing document
- **WHEN** a user opens a document with status `PENDING` or `PROCESSING`
- **THEN** the UI shows the processing state and does not load transactions

#### Scenario: Failed document
- **WHEN** a user opens a document with status `FAILED`
- **THEN** the UI shows the failure details and does not load transactions

### Requirement: Document details show status and download control
The system SHALL show the status text badge beside the document filename and an icon-only, right-aligned control for downloading the original document.

#### Scenario: Download original document
- **WHEN** an authenticated owner activates the download control
- **THEN** the UI downloads the original document through an authenticated API Gateway endpoint

### Requirement: Users can browse transactions across processed documents
The system SHALL provide an authenticated transaction history page listing normalized transactions from all of the current user's processed documents. Each transaction SHALL retain its normalized booking date, value date, amount, currency, counterparty, description, and category fields.

#### Scenario: Open transaction history
- **WHEN** an authenticated user opens the transaction history page
- **THEN** the system displays that user's transactions from processed documents across their uploaded statements

#### Scenario: No transactions are available
- **WHEN** an authenticated user has no transactions from processed documents
- **THEN** the page displays an empty state indicating that no transactions are available

### Requirement: Transaction history can be filtered
The system SHALL allow the user to filter transaction history by a text query matching description or counterparty, an inclusive value-date range, and category. Filters SHALL be combinable, and changing filters SHALL update the displayed results.

#### Scenario: Search transactions
- **WHEN** the user enters a text query
- **THEN** the displayed transactions include only records whose description or counterparty matches the query without case sensitivity

#### Scenario: Filter by value-date range
- **WHEN** the user sets a start date, end date, or both
- **THEN** the displayed transactions include only records with a value date within the selected inclusive bounds

#### Scenario: Filter by category
- **WHEN** the user selects a category
- **THEN** the displayed transactions include only records assigned to that category

#### Scenario: Combine filters
- **WHEN** the user applies more than one supported filter
- **THEN** the displayed transactions satisfy all active filters

#### Scenario: Clear filters
- **WHEN** the user clears the active filters
- **THEN** the page displays the unfiltered transaction history again

### Requirement: Transaction history is private to its owner
The system SHALL return transaction history only for the authenticated user, regardless of any client-supplied identifiers or filter values.

#### Scenario: Request transaction history while authenticated
- **WHEN** an authenticated user requests transaction history
- **THEN** the response contains transactions belonging only to that user

#### Scenario: Request transaction history while unauthenticated
- **WHEN** a request for transaction history has no valid authentication
- **THEN** the system rejects the request without disclosing transaction data

### Requirement: Transaction history pagination supports direct page navigation
The system SHALL present transaction results with numbered pagination. The current page SHALL be visually distinct, and the first and last page numbers SHALL remain directly selectable. When the page count cannot be shown compactly, the system SHALL use ellipses while retaining the first page, last page, and a small range around the current page. Previous and next controls SHALL be icon-only, remain visible when unavailable, and expose accessible names.

#### Scenario: Jump to a numbered page
- **WHEN** the user selects an available page number
- **THEN** the page displays that page of transaction results and marks its number as current

#### Scenario: Jump directly to the first or last page
- **WHEN** the page count is greater than one
- **THEN** the user can select page 1 or the last page number directly, including when intermediate numbers are collapsed with ellipses

#### Scenario: Navigate at a page boundary
- **WHEN** the user is on the first or last page
- **THEN** the unavailable previous or next arrow remains visible and is disabled

#### Scenario: Condense a long page list
- **WHEN** the transaction history has many pages
- **THEN** the paginator shows the first and last page numbers, a compact range around the current page, and ellipses for omitted page numbers

### Requirement: Document details update without a manual refresh control
The document detail page SHALL NOT provide a manual Refresh control. It SHALL automatically poll while a document is `PENDING` or `PROCESSING`, and opening or reopening a document detail page SHALL fetch the current document and, when processed, its transactions.

#### Scenario: Processing status updates automatically
- **WHEN** a user views a document whose status is `PENDING` or `PROCESSING`
- **THEN** the detail page updates its status through automatic polling without a manual refresh control

#### Scenario: Reopen document details
- **WHEN** a user opens or reopens a document detail page
- **THEN** the page fetches the latest document state and loads its transactions when the document is processed

### Requirement: Transaction history displays Value date without a separate Booking date column
Transaction history SHALL display Value date, amount, currency, counterparty, description, and category, and SHALL NOT display Booking date as a separate column. Each transaction's underlying normalized fields remain unchanged.

#### Scenario: View the transaction-history date column
- **WHEN** an authenticated user views transaction history
- **THEN** each transaction row displays Value date and the other history columns, without a separate Booking date column

### Requirement: Transaction history can be sorted by each displayed column
The system SHALL allow users to sort transaction history by Value date, amount, currency, counterparty, description, and category. Sorting SHALL apply to the complete filtered result set before pagination, and the active sort column and direction SHALL be conveyed accessibly.

#### Scenario: Sort by a displayed column
- **WHEN** the user activates a sortable transaction-history column
- **THEN** the displayed results are ordered by that column across all matching pages and the active direction is indicated

#### Scenario: Reverse a column's sort direction
- **WHEN** the user activates the currently sorted column again
- **THEN** the results use the opposite sort direction

#### Scenario: Keep pagination consistent with sorting
- **WHEN** the user changes the sort while viewing any page
- **THEN** the results return to the first page of the newly sorted filtered result set

### Requirement: Transaction-history filters use the available card width
The transaction-history filter panel SHALL use the full available width of its card, distributing its controls across that width on wide layouts while keeping them usable on narrow layouts.

#### Scenario: View filters on a wide screen
- **WHEN** a user views transaction history on a wide screen
- **THEN** the filter controls span the available card width instead of clustering at the left

#### Scenario: View filters on a narrow screen
- **WHEN** a user views transaction history on a narrow screen
- **THEN** the filter controls wrap within the card without horizontal page overflow

### Requirement: Transaction amounts communicate their sign accessibly
The transaction-history table SHALL style positive amounts with a soft green from the application palette and negative amounts with a soft red compatible with that palette. Zero, missing, or invalid amounts SHALL retain neutral styling, and the displayed amount value SHALL remain available as text.

#### Scenario: Display a credit
- **WHEN** a transaction has a positive amount
- **THEN** its amount is shown with soft green styling

#### Scenario: Display a debit
- **WHEN** a transaction has a negative amount
- **THEN** its amount is shown with soft red styling

#### Scenario: Display a neutral amount
- **WHEN** a transaction amount is zero or unavailable
- **THEN** its amount uses neutral styling

### Requirement: Users can open transaction details from transaction lists
The system SHALL let users open a popup with the selected transaction's available normalized fields from both the transaction-history list and a processed document's transaction list. The popup SHALL show booking date, value date, amount, currency, counterparty, description, and category, representing unavailable values consistently with the corresponding list. Opening and closing the popup SHALL preserve the current list, filters, sorting, pagination, and scroll position.

#### Scenario: Open transaction details from history
- **WHEN** the user activates a transaction in transaction history
- **THEN** a popup opens for that transaction and displays its booking date, value date, amount, currency, counterparty, description, and category

#### Scenario: Open transaction details from a processed document
- **WHEN** the user activates a transaction in a processed document's transaction list
- **THEN** a popup opens for that transaction and displays its booking date, value date, amount, currency, counterparty, description, and category

#### Scenario: Close transaction details and continue browsing
- **WHEN** the user closes the popup using its close control or the Escape key
- **THEN** the popup closes, focus returns to the transaction that opened it, and the current list state and scroll position are preserved

#### Scenario: Open transaction details using the keyboard
- **WHEN** the user navigates to a transaction in either list using the keyboard and activates it
- **THEN** the same transaction-details popup opens with an accessible name and keyboard-operable close behavior

## Implementation Notes

- The API Gateway transaction route is authenticated and owner-scoped. It currently returns an empty list; the controller contains a TODO to connect the transaction read model.
- The authenticated download route is owner-scoped and currently returns `501 Not Implemented`; the controller contains a TODO to stream the original object from MinIO with a download filename.
- `DocumentResponse.errorMessage` is populated only for documents in the `FAILED` state so the detail screen can show parser failure details.
