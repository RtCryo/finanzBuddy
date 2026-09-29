# Document List UI Specification

## Purpose

Displays the user's uploaded documents with their processing status and allows navigation to details.

## Requirements

### Requirement: Documents are listed with status
The system SHALL show a table or list of documents including filename, type, status, and upload date.

#### Scenario: List documents
- **WHEN** a user visits the documents page
- **THEN** the UI fetches and displays their documents from `GET /documents`

### Requirement: Status is visually distinct
The system SHALL show a text badge with status-specific color for `PENDING`, `PROCESSING`, `PROCESSED`, and `FAILED`; status meaning SHALL NOT rely on color alone.

#### Scenario: Failed document
- **WHEN** a document has status `FAILED`
- **THEN** the UI shows the error message on request

### Requirement: User can refresh the list
The system SHALL allow manual refresh and optionally poll for status updates.

#### Scenario: Status update
- **WHEN** a document transitions from `PROCESSING` to `PROCESSED`
- **THEN** the UI reflects the new status after refresh or poll

### Requirement: Selecting a document row opens its details
The system SHALL make each document row a navigation target and SHALL NOT require a separate action column.

#### Scenario: Open document details
- **WHEN** a user selects a document row
- **THEN** the UI navigates to that document's detail page
