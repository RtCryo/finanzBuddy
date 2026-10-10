# Spec Delta

## MODIFIED Requirements

### Requirement: Transaction columns match normalized model
The system SHALL display booking date, value date, amount, currency, counterparty, description, and category for transactions on a processed document's detail page. Transaction history SHALL display Value date, amount, currency, counterparty, description, and category, and SHALL NOT display Booking date as a separate column; each transaction's underlying normalized fields remain unchanged.

#### Scenario: CSV-derived transaction
- **WHEN** a CSV document has been parsed and a user opens its processed-document details
- **THEN** the transaction row shows both booking date and value date with the other normalized fields

### Requirement: Transaction history can be filtered
The system SHALL allow the user to filter transaction history by a text query matching description or counterparty, an inclusive value-date range, and category. Filters SHALL be combinable, and changing filters SHALL update the displayed results.

#### Scenario: Search transactions
- **WHEN** the user enters a text query
- **THEN** the displayed transactions include only records whose description or counterparty matches the query without case sensitivity

#### Scenario: Filter by booking-date range
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

## ADDED Requirements

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
