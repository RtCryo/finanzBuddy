# Kafka Publishing Specification

## Purpose

Publishes typed document processing events to Kafka so that downstream parser services can consume documents by format.

## Requirements

### Requirement: Upload publishes a Kafka event
The system SHALL publish a `DocumentUploadedEvent` to Kafka immediately after the file and metadata are persisted.

#### Scenario: CSV event published
- **WHEN** a user uploads a CSV document successfully
- **THEN** an event is published to the `documents.csv` topic

#### Scenario: Event contains required fields
- **WHEN** a document upload event is published
- **THEN** it contains `documentId`, `userId`, `type`, and `storagePath`

### Requirement: Events are routed by document type
The system SHALL publish events to the topic named `documents.{type}` where `{type}` matches the document tag provided by the user.

#### Scenario: PDF event routing
- **WHEN** a user uploads a document tagged as `PDF`
- **THEN** the event is published to `documents.pdf`

#### Scenario: XML event routing
- **WHEN** a user uploads a document tagged as `XML`
- **THEN** the event is published to `documents.xml`

### Requirement: Failed uploads do not publish events
The system SHALL not publish a Kafka event when the upload fails before persistence.

#### Scenario: Storage failure
- **WHEN** MinIO storage fails during upload
- **THEN** no Kafka event is published and the transaction rolls back

## Related implementation and history

- Documentation: [API Gateway and Kafka map](../../../docs/README.md#subsystem-map), [Gateway README](../../../api-gateway/README.md)
- Implementation: [`DocumentService.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/DocumentService.java), [`OutboxRelay.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/outbox/OutboxRelay.java), [`DocumentUploadedEvent.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/event/DocumentUploadedEvent.java)
- History: [API Gateway](../../changes/archive/2026-09-26-api-gateway/)
