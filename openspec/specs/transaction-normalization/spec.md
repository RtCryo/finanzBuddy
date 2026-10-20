# Transaction Normalization Specification

## Purpose

Converts parsed data from any supported document format into a single, unified transaction representation.

## Requirements

### Requirement: Transactions are normalized
The system SHALL store every extracted transaction with a common set of fields regardless of the source document type.

#### Scenario: CSV to transaction
- **WHEN** a CSV statement is parsed
- **THEN** each row is saved as a `Transaction` with booking date, value date, amount, currency, counterparty, description, and category

#### Scenario: Unsupported MVP formats
- **WHEN** a PDF or XML statement is submitted to its parser strategy
- **THEN** parsing fails with an explicit unsupported-operation error and no transactions are persisted

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

## Related implementation and history

- Documentation: [parser and sample-document map](../../../docs/README.md#subsystem-map), [Parser README](../../../parser-service/README.md)
- Implementation: [`parser-service/src/main/java/com/finanzbuddy/parser/parser/`](../../../parser-service/src/main/java/com/finanzbuddy/parser/parser/), [`Transaction.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/entity/Transaction.java), [`TransactionPersistenceService.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/service/TransactionPersistenceService.java)
- History: [Parser service](../../changes/archive/2026-09-27-parser-service/)
