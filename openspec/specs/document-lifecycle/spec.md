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

## Related implementation and history

- Documentation: [API Gateway map](../../../docs/README.md#subsystem-map), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`DocumentService.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/DocumentService.java), [`DocumentController.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/controller/DocumentController.java), [`DocumentEventListeners.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/event/DocumentEventListeners.java)
- History: [API Gateway](../../changes/archive/2026-09-26-api-gateway/), [Parser service](../../changes/archive/2026-09-27-parser-service/)
