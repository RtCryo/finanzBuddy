# Document Upload UI Specification

## Purpose

Allows authenticated users to upload financial documents and tag them by type before sending them to the API Gateway.

## Requirements

### Requirement: User can select a file
The system SHALL provide a file drop zone and file picker that accept the supported document formats.

#### Scenario: Select CSV file
- **WHEN** a user selects a `.csv` file
- **THEN** the file is ready for upload

#### Scenario: Drag and drop a supported file
- **WHEN** a user drops a supported file into the upload area
- **THEN** the file is selected and ready for upload

### Requirement: User must select a document type
The system SHALL require the user to choose a document type before upload.

#### Scenario: Type selected
- **WHEN** a user chooses `CSV` from the type dropdown
- **THEN** the upload request includes `type=CSV`

#### Scenario: Missing type
- **WHEN** a user tries to upload without selecting a type
- **THEN** the UI shows a validation error and does not submit

### Requirement: Upload shows progress and result
The system SHALL display upload progress and the final server response.

The upload page SHALL follow the approved English-language application shell and visual design.

#### Scenario: Successful upload
- **WHEN** the file and type are submitted
- **THEN** the UI shows a success message and the new document appears in the list

#### Scenario: Upload failure
- **WHEN** the server returns an error
- **THEN** the UI displays the error message

## Related implementation and history

- Documentation: [UI map](../../../docs/README.md#subsystem-map), [UI README](../../../ui/README.md)
- Implementation: [`UploadPage.tsx`](../../../ui/src/pages/UploadPage.tsx), [`ui/src/api/documents.ts`](../../../ui/src/api/documents.ts)
- History: [initial UI](../../changes/archive/2026-09-28-ui/)
