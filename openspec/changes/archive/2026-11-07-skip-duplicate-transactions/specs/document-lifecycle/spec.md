# Spec Delta

## ADDED Requirements

### Requirement: Document responses expose the deduplication outcome
The system SHALL include the number of saved transactions and the number of transactions skipped as duplicates in every document representation returned to the owner (list and single document). Both numbers SHALL be absent while the document is `PENDING`, `PROCESSING`, or `FAILED`, and for documents processed before deduplication existed. The parser SHALL provide both numbers together with the `PROCESSED` status report, and the system SHALL keep the stored numbers when the same status is reported again without numbers.

#### Scenario: Processed document with duplicates
- **WHEN** the parser reports `PROCESSED` with 28 saved and 300 skipped
- **THEN** `GET /documents/{id}` returns both numbers for that document

#### Scenario: Document not finished
- **WHEN** a user reads a document that is `PENDING`, `PROCESSING`, or `FAILED`
- **THEN** the response contains no saved or skipped numbers

#### Scenario: Repeated PROCESSED report without numbers
- **WHEN** the parser repeats `PROCESSED` for a document that already has stored numbers and sends none
- **THEN** the stored numbers are unchanged
