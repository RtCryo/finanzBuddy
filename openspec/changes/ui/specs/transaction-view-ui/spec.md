# Spec Delta

## Purpose

Shows normalized transactions extracted from a processed document.

## ADDED Requirements

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

The API Gateway download endpoint and response contract are a prerequisite and are not currently defined.
