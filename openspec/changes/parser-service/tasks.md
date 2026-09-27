# Tasks: Parser Service

## 1. Project Scaffolding

- [x] 1.1 Create `parser-service/` Maven module with Spring Boot 4.1 parent and verify `mvn clean compile` succeeds
- [x] 1.2 Add dependencies: Spring Web, Spring for Apache Kafka, Spring Data JPA, PostgreSQL driver, MinIO client, OpenCSV, Apache PDFBox, Jackson XML, Liquibase, and verify no dependency conflicts
- [x] 1.3 Create package structure `com.finanzbuddy.parser` with main application class and verify the context loads
- [x] 1.4 Add `application.yml` with `parser.formats.csv/pdf/xml` flags and Kafka consumer configuration; verify placeholders use environment variables

## 2. Database Schema

- [x] 2.1 Create Liquibase changelog `db/changelog/changes/001-create-transactions-table.xml` and verify the `transactions` table is created on startup
- [x] 2.2 Map `Transaction` JPA entity with normalized fields and `rawData` JSONB column and verify it matches the schema

## 3. File Retrieval and Event Consumption

- [x] 3.1 Implement MinIO client configuration and verify connectivity
- [x] 3.2 Implement `DocumentUploadedEvent` DTO and Kafka deserializer and verify mapping from JSON
- [x] 3.3 Implement conditional `@KafkaListener` beans for `documents.csv`, `documents.pdf`, and `documents.xml` and verify only enabled listeners are created
- [x] 3.4 Implement file download from MinIO using `storagePath` from the event and verify the stream is readable
- [x] 3.5 Add idempotency check using `documentId` and verify duplicate events are skipped

## 4. Parsing Strategies

- [x] 4.1 Define `DocumentParser` interface returning `List<Transaction>` and verify compilation
- [x] 4.2 Implement `CsvTransactionParser` using OpenCSV with locale-aware date parsing and verify it parses the sample CSV correctly
- [x] 4.3 Implement `PdfTransactionParser` as a stub that throws unsupported-operation for MVP and verify it is registered
- [x] 4.4 Implement `XmlTransactionParser` as a stub that throws unsupported-operation for MVP and verify it is registered
- [x] 4.5 Implement parser factory/router by document type and verify correct parser is selected

## 5. Transaction Normalization

- [x] 5.1 Define `Transaction` normalization mapping from CSV columns and verify field correctness
- [x] 5.2 Persist normalized transactions to PostgreSQL linked to `documentId` and `userId` and verify rows are inserted
- [x] 5.3 Store raw CSV row in `rawData` JSONB and verify the content is preserved
- [x] 5.4 Add unit tests for normalization edge cases and verify they pass

## 6. Status Reporting

- [x] 6.1 Configure WebClient/RestTemplate to call API Gateway internal status endpoint and verify bean creation
- [x] 6.2 Implement `PUT /internal/documents/{id}/status` caller with service API key and verify requests are authenticated
- [x] 6.3 Report `PROCESSING` when parsing starts, `PROCESSED` on success, and `FAILED` with message on error and verify status transitions in the gateway
- [x] 6.4 Add integration test with embedded Kafka and verify end-to-end CSV processing updates document status

## 7. Docker and Documentation

- [x] 7.1 Add `Dockerfile` for `parser-service` and verify the image builds
- [x] 7.2 Add service definition to root `docker-compose.yml` and verify the parser starts with Postgres, Kafka, and MinIO
- [x] 7.3 Document parser configuration, supported formats, and environment variables in `parser-service/README.md` and verify the documented commands work
