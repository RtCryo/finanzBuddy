# Proposal: API Gateway

## Why

The finanzBuddy platform needs a single entry point for authenticated users to upload financial documents and trigger asynchronous processing pipelines. The API Gateway is responsible for user identity, document intake, file storage, and event publishing so that downstream parser services can consume and process documents reliably.

## What Changes

- Introduce a new Spring Boot 4.1 service named `api-gateway` in the `api-gateway/` monorepo module.
- Implement JWT-based user authentication (registration + login) using Spring Security.
- Provide an authenticated endpoint for uploading financial documents (`POST /documents`).
- Require the user to tag each document with a type (`CSV`, `PDF`, `XML`).
- Store uploaded files in MinIO under a user-scoped path.
- Persist document metadata and processing status in PostgreSQL.
- Publish a `DocumentUploadedEvent` to the Kafka topic `documents.{type}` after successful upload.
- Expose endpoints to list documents and retrieve document status/results.
- Provide Docker Compose service definition for local development.

## Capabilities

### New Capabilities

- `user-authentication`: User registration and login with stateless JWT authentication. Secures all document endpoints.
- `document-upload`: Authenticated users can upload a file and select its document type. Files are persisted to MinIO and metadata to PostgreSQL.
- `document-lifecycle`: Documents transition through statuses `PENDING`, `PROCESSING`, `PROCESSED`, and `FAILED`. Status updates are exposed via REST.
- `kafka-publishing`: The gateway emits typed Kafka events so that downstream parser services can consume documents by format.

### Modified Capabilities

- None.

## Impact

- New Maven module `api-gateway/`.
- New PostgreSQL tables: `users`, `documents`.
- New Kafka topics: `documents.csv`, `documents.pdf`, `documents.xml`.
- New MinIO bucket/prefix structure for user uploads.
- UI must integrate with `/auth/**` and `/documents/**` endpoints.
- Parser services depend on the event schema published by this service.
