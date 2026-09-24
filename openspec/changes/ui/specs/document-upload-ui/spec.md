# Spec Delta

## Purpose

Allows authenticated users to upload financial documents and tag them by type before sending them to the API Gateway.

## ADDED Requirements

### Requirement: User can select a file
The system SHALL provide a file input that accepts the supported document formats.

#### Scenario: Select CSV file
- **WHEN** a user selects a `.csv` file
- **THEN** the file is ready for upload

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

#### Scenario: Successful upload
- **WHEN** the file and type are submitted
- **THEN** the UI shows a success message and the new document appears in the list

#### Scenario: Upload failure
- **WHEN** the server returns an error
- **THEN** the UI displays the error message
