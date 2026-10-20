# Status Reporting Specification

## Purpose

Reports processing progress and final outcome for each document so the API Gateway can keep users informed.

## Requirements

### Requirement: Processing status is reported on start
The system SHALL notify the API Gateway when parsing of a document begins.

#### Scenario: Start processing
- **WHEN** a consumer begins parsing a document
- **THEN** the API Gateway updates the document status to `PROCESSING`

### Requirement: Success status is reported on completion
The system SHALL notify the API Gateway when parsing completes successfully.

#### Scenario: Successful parse
- **WHEN** all transactions from a document are persisted
- **THEN** the API Gateway updates the document status to `PROCESSED`

### Requirement: Failure status is reported on error
The system SHALL notify the API Gateway when parsing fails and provide an error message.

#### Scenario: Parse failure
- **WHEN** parsing throws an exception
- **THEN** the API Gateway updates the document status to `FAILED` with the error message

### Requirement: Status updates are authenticated
The system SHALL authenticate status update calls to the API Gateway using an internal API key or service token.

#### Scenario: Invalid credentials
- **WHEN** a status request is made without valid service credentials
- **THEN** the API Gateway returns a 401 Unauthorized error

## Related implementation and history

- Documentation: [parser and gateway map](../../../docs/README.md#subsystem-map), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`GatewayStatusClient.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/client/GatewayStatusClient.java), [`InternalDocumentController.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/controller/InternalDocumentController.java), [`DocumentService.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/DocumentService.java)
- History: [Parser service](../../changes/archive/2026-09-27-parser-service/)
