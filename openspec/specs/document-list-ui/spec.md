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

### Requirement: Deduplication outcome is shown for processed documents
The system SHALL show the number of saved transactions and the number skipped as duplicates for a `PROCESSED` document in the documents list and on the document detail page, in text that does not rely on color alone. Documents without these numbers SHALL show neither number. When a processed document has no saved transactions because every line was a duplicate, the detail page SHALL say so and SHALL NOT show the generic "no transactions available yet" message.

#### Scenario: Partial duplicates in the list
- **WHEN** a processed document has 28 saved and 300 skipped
- **THEN** the documents list shows "28 saved, 300 duplicates skipped" for that row

#### Scenario: All duplicates on the detail page
- **WHEN** a user opens a processed document whose lines were all skipped
- **THEN** the detail page explains that all transactions were already imported and shows the skipped count

#### Scenario: Historic document
- **WHEN** a processed document has no saved and skipped numbers
- **THEN** the list and detail page show no deduplication text for it

## Related implementation and history

- Documentation: [UI map](../../../docs/README.md#subsystem-map), [UI README](../../../ui/README.md)
- Implementation: [`DocumentsPage.tsx`](../../../ui/src/pages/DocumentsPage.tsx), [`DocumentController.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/controller/DocumentController.java), [`DocumentService.java`](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/DocumentService.java)
- History: [initial UI](../../changes/archive/2026-09-28-ui/), [document and transaction UI polish](../../changes/archive/2026-10-10-polish-document-transaction-ui/)
