# Tasks: API Gateway

## 1. Project Scaffolding

- [x] 1.1 Create `api-gateway/` Maven module with Spring Boot 4.1 parent and verify `mvn clean compile` succeeds
- [x] 1.2 Add dependencies: Spring Web, Spring Security, Spring Data JPA, Spring for Apache Kafka, PostgreSQL driver, MinIO client, Liquibase, Lombok, JWT (jjwt), and verify no dependency conflicts
- [x] 1.3 Create package structure `com.finanzbuddy.api` with main application class and verify the context loads
- [x] 1.4 Add `application.yml` with profiles for local development and verify placeholders for secrets use environment variables

## 2. Database Schema

- [x] 2.1 Create Liquibase changelog `db/changelog/changes/001-create-users-table.xml` and verify the `users` table is created on startup
- [x] 2.2 Create Liquibase changelog `db/changelog/changes/002-create-documents-table.xml` and verify the `documents` table with foreign key to `users` is created
- [x] 2.3 Map `User` and `Document` JPA entities and verify they match the schema

## 3. Authentication

- [x] 3.1 Implement `UserDetailsService` backed by the `users` table and verify user lookup works
- [x] 3.2 Implement password hashing with BCrypt and verify hashes are never stored in plain text
- [x] 3.3 Implement `POST /auth/register` endpoint and verify successful registration returns 201
- [x] 3.4 Implement `POST /auth/login` endpoint and verify it returns a signed JWT on valid credentials
- [x] 3.5 Configure Spring Security to protect document endpoints and permit `/auth/**`; verify unauthenticated requests return 401
- [x] 3.6 Add unit tests for JWT generation/validation and verify they pass

## 4. Document Upload

- [x] 4.1 Implement MinIO client configuration and bucket creation on startup; verify connectivity via health indicator
- [x] 4.2 Implement `POST /documents` multipart upload endpoint accepting `file` and `type`; verify 201 response with document id
- [x] 4.3 Validate document type against allowed values (`CSV`, `PDF`, `XML`) and verify unsupported types return 400
- [x] 4.4 Store files in MinIO under `users/{userId}/{documentId}-{originalFilename}` and verify path format
- [x] 4.5 Persist document metadata in PostgreSQL with status `PENDING` and verify the row is inserted
- [x] 4.6 Ensure users can only access their own documents and verify foreign document access returns 404

## 5. Kafka Event Publishing

- [x] 5.1 Configure Kafka producer with `documents.{type}` topic routing and verify producer bean is created
- [x] 5.2 Define `DocumentUploadedEvent` record with `documentId`, `userId`, `type`, `storagePath` and verify serialization
- [x] 5.3 Publish event after successful upload transaction and verify the message reaches `documents.csv` topic
- [x] 5.4 Ensure no event is published when upload fails and verify transactional behavior
- [x] 5.5 Add integration test with embedded Kafka and verify event publishing

## 6. Document Query Endpoints

- [x] 6.1 Implement `GET /documents` returning paginated list of user's documents and verify pagination works
- [x] 6.2 Implement `GET /documents/{id}` returning document metadata and status and verify correct response for owned documents
- [x] 6.3 Implement `PUT /documents/{id}/status` for parser services to update status and verify status transitions
- [x] 6.4 Add tests for status transition rules and verify invalid transitions are rejected

## 7. Docker and Documentation

- [x] 7.1 Add `Dockerfile` for `api-gateway` and verify the image builds
- [x] 7.2 Add service definition to root `docker-compose.yml` and verify the gateway starts with Postgres, Kafka, Zookeeper, and MinIO
- [x] 7.3 Document the API endpoints and environment variables in `api-gateway/README.md` and verify the documented curl commands work
