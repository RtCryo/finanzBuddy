# Spec Delta

## Purpose

Enables the parser service to consume document upload events from Kafka topics based on configured formats.

## ADDED Requirements

### Requirement: Service consumes only enabled formats
The system SHALL create Kafka consumers only for document formats that are enabled in configuration.

#### Scenario: CSV enabled
- **WHEN** `parser.formats.csv` is `true`
- **THEN** a consumer subscribes to `documents.csv`

#### Scenario: PDF disabled
- **WHEN** `parser.formats.pdf` is `false`
- **THEN** no consumer subscribes to `documents.pdf`

### Requirement: Service downloads files from storage
The system SHALL download the file referenced by `storagePath` in the event from MinIO.

#### Scenario: Successful download
- **WHEN** a valid `DocumentUploadedEvent` is consumed
- **THEN** the file is retrieved from MinIO into a local stream

#### Scenario: Missing file
- **WHEN** the referenced file does not exist in MinIO
- **THEN** processing fails and the document status is set to `FAILED`

### Requirement: Events are processed idempotently
The system SHALL ignore duplicate events for the same `documentId`.

#### Scenario: Duplicate event
- **WHEN** the same `documentId` is received more than once
- **THEN** only the first event triggers parsing; duplicates are acknowledged without reprocessing
