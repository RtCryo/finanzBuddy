# Document Lifecycle Specification

## Purpose

Tracks the processing state of each uploaded document and exposes status transitions to users.

## Requirements

### Requirement: Documents start in PENDING status
The system SHALL create every new document with status `PENDING`.

#### Scenario: New upload status
- **WHEN** a document is uploaded successfully
- **THEN** its status is `PENDING`

### Requirement: Status transitions follow the allowed lifecycle
The system SHALL allow transitions only from `PENDING` to `PROCESSING`, from `PROCESSING` to `PROCESSED` or `FAILED`, and from any state to `FAILED`.

#### Scenario: Processor starts work
- **WHEN** a parser service begins processing a document
- **THEN** the status transitions from `PENDING` to `PROCESSING`

#### Scenario: Processor succeeds
- **WHEN** a parser service finishes successfully
- **THEN** the status transitions from `PROCESSING` to `PROCESSED`

#### Scenario: Processor fails
- **WHEN** a parser service reports an error
- **THEN** the status transitions to `FAILED` and stores an error message

### Requirement: User can query document status
The system SHALL allow an authenticated user to retrieve the status of their own documents.

#### Scenario: List own documents
- **WHEN** a user requests `GET /documents`
- **THEN** the system returns only documents belonging to that user with current status

#### Scenario: Retrieve single document
- **WHEN** a user requests `GET /documents/{id}` for a document they own
- **THEN** the system returns the document metadata and status

#### Scenario: Access foreign document
- **WHEN** a user requests `GET /documents/{id}` for a document they do not own
- **THEN** the system returns a 404 Not Found error

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

## Related implementation and history

- Documentation: [API Gateway map](../../../docs/README.md#subsystem-map), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`DocumentService.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/DocumentService.java), [`DocumentController.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/controller/DocumentController.java), [`DocumentEventListeners.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/event/DocumentEventListeners.java)
- History: [API Gateway](../../changes/archive/2026-09-26-api-gateway/), [Parser service](../../changes/archive/2026-09-27-parser-service/)
