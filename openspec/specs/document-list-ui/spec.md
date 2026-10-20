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
The system SHALL automatically poll the document list for status updates and SHALL NOT provide a separate manual Refresh control on the documents list page.

#### Scenario: Status update
- **WHEN** a document transitions from `PROCESSING` to `PROCESSED` while the documents page is open
- **THEN** the UI reflects the new status through automatic polling without requiring a manual refresh control

### Requirement: Selecting a document row opens its details
The system SHALL make each document row a navigation target and SHALL NOT require a separate action column.

#### Scenario: Open document details
- **WHEN** a user selects a document row
- **THEN** the UI navigates to that document's detail page

## Related implementation and history

- Documentation: [UI map](../../../docs/README.md#subsystem-map), [UI README](../../../ui/README.md)
- Implementation: [`DocumentsPage.tsx`](../../../ui/src/pages/DocumentsPage.tsx), [`DocumentController.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/controller/DocumentController.java), [`DocumentService.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/DocumentService.java)
- History: [initial UI](../../changes/archive/2026-09-28-ui/), [document and transaction UI polish](../../changes/archive/2026-10-10-polish-document-transaction-ui/)
