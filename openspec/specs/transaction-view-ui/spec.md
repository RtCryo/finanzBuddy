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
The system SHALL display booking date, value date, amount, currency, counterparty, description, and category.

#### Scenario: CSV-derived transaction
- **WHEN** a CSV document has been parsed
- **THEN** the UI shows each transaction with the normalized fields

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
The system SHALL allow the user to filter transaction history by a text query matching description or counterparty, an inclusive booking-date range, and category. Filters SHALL be combinable, and changing filters SHALL update the displayed results.

#### Scenario: Search transactions
- **WHEN** the user enters a text query
- **THEN** the displayed transactions include only records whose description or counterparty matches the query without case sensitivity

#### Scenario: Filter by booking-date range
- **WHEN** the user sets a start date, end date, or both
- **THEN** the displayed transactions include only records with a booking date within the selected inclusive bounds

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

## Implementation Notes

- The API Gateway transaction route is authenticated and owner-scoped. It currently returns an empty list; the controller contains a TODO to connect the transaction read model.
- The authenticated download route is owner-scoped and currently returns `501 Not Implemented`; the controller contains a TODO to stream the original object from MinIO with a download filename.
- `DocumentResponse.errorMessage` is populated only for documents in the `FAILED` state so the detail screen can show parser failure details.
