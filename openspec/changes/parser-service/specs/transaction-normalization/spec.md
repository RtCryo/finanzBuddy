# Spec Delta

## Purpose

Converts parsed data from any supported document format into a single, unified transaction representation.

## ADDED Requirements

### Requirement: Transactions are normalized
The system SHALL store every extracted transaction with a common set of fields regardless of the source document type.

#### Scenario: CSV to transaction
- **WHEN** a CSV statement is parsed
- **THEN** each row is saved as a `Transaction` with booking date, value date, amount, currency, counterparty, description, and category

#### Scenario: PDF to transaction
- **WHEN** a PDF statement is parsed
- **THEN** each extracted payment is saved as a `Transaction` with the same normalized fields

### Requirement: Transactions are linked to document and user
The system SHALL associate every transaction with the originating `documentId` and `userId`.

#### Scenario: Ownership link
- **WHEN** transactions are persisted
- **THEN** each row references the correct `documents` row and `users` row

### Requirement: Raw data is preserved
The system SHALL store the original raw representation of each transaction in a JSONB column.

#### Scenario: Raw CSV row preserved
- **WHEN** a CSV transaction is normalized
- **THEN** the raw CSV values are stored alongside the normalized fields
