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

#### Scenario: Pending document
- **WHEN** a user opens a document with status `PENDING`
- **THEN** the UI shows a message that processing is pending
