# Spec Delta

## Purpose

Allows authenticated users to upload financial documents, tag them by type, and persist both the file and its metadata.

## ADDED Requirements

### Requirement: Authenticated user can upload a document
The system SHALL accept a multipart file upload from an authenticated user together with a document type tag.

#### Scenario: Successful CSV upload
- **WHEN** an authenticated user sends a CSV file and type `CSV` to `POST /documents`
- **THEN** the system stores the file in MinIO, persists document metadata, and returns the document id

#### Scenario: Unsupported document type
- **WHEN** a user uploads a file with a type not in the allowed set
- **THEN** the system returns a 400 Bad Request error

#### Scenario: Missing file or type
- **WHEN** a user submits the upload request without a file or without a type
- **THEN** the system returns a 400 Bad Request error

### Requirement: Files are stored user-scoped
The system SHALL store uploaded files in MinIO under a path scoped to the authenticated user.

#### Scenario: User-scoped path
- **WHEN** a user uploads a document
- **THEN** the storage path contains the user id and a unique document id

### Requirement: Document metadata is persisted
The system SHALL record the original filename, storage path, document type, initial status `PENDING`, and owning user.

#### Scenario: Metadata creation
- **WHEN** a document is uploaded successfully
- **THEN** a `documents` row exists with status `PENDING` and correct metadata
