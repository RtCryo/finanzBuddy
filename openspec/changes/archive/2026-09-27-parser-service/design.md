# Design: Parser Service

## Context

The `parser-service` is a downstream consumer in the finanzBuddy monorepo. It receives `DocumentUploadedEvent` messages from Kafka, fetches files from MinIO, parses them, and writes normalized transactions to PostgreSQL. See `proposal.md` for the motivation and scope.

## Goals / Non-Goals

**Goals:**
- Support a single deployable service that can parse CSV, PDF, and XML documents.
- Allow runtime configuration to enable or disable consumption per format.
- Normalize all parsed data into a unified `Transaction` model.
- Report processing status back to the API Gateway.
- Implement CSV parsing first; PDF and XML are scaffolded behind interfaces.

**Non-Goals:**
- Real-time streaming or event sourcing beyond Kafka consumption.
- OCR for scanned PDFs (out of scope for MVP).
- Complex reconciliation or duplicate detection beyond idempotent document id.

## Decisions

### Single Service with Format Strategies

**Rationale:** One codebase reduces duplication of Kafka/MinIO/DB plumbing while still allowing per-format scaling by running multiple instances with different `parser.formats.*` flags.

**Alternatives considered:** Separate services per format (more deployment overhead), monolithic parser inside API Gateway (breaks async processing goals).

### Strategy Pattern for Parsers

```
DocumentParser
  ├── CsvTransactionParser
  ├── PdfTransactionParser (stub)
  └── XmlTransactionParser (stub)
```

**Rationale:** New formats can be added without modifying consumer logic. Each parser returns `List<Transaction>`.

### Conditional Kafka Listeners

```java
@ConditionalOnProperty(name = "parser.formats.csv", havingValue = "true")
@KafkaListener(topics = "documents.csv", groupId = "parser-service")
```

**Rationale:** Spring creates consumers only for enabled formats. The same Docker image can be reused for CSV-only, PDF-only, or all-format deployments.

### Locale-Aware Date Parsing

**Rationale:** The sample CSV uses German date format (`dd.MM.yy`). The parser accepts a locale hint (default from user profile or document metadata) and uses `DateTimeFormatter` with that locale.

**Alternatives considered:** Hard-coded German format (not flexible for future XML/PDF sources).

### Status Reporting via REST

**Rationale:** The API Gateway owns document state. Parser service calls `PUT /internal/documents/{id}/status` with a service API key.

**Alternatives considered:** Kafka bidirectional topic (more complexity), parser service writing directly to `documents` table (breaks service boundaries).

### Idempotency via Document ID

**Rationale:** Kafka may redeliver messages. The parser service checks whether transactions already exist for `documentId` before parsing.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Parser crashes on malformed files | Wrap parsing in try/catch; report `FAILED` with message |
| Large CSV files cause memory issues | Stream rows instead of loading entire file |
| Locale not available | Default to `Locale.GERMANY` for CSV; make configurable per format |
| PDF is image-only | Return explicit error; OCR deferred |

## Migration Plan

1. Build `parser-service` Docker image.
2. Configure `parser.formats.csv=true`, `pdf=false`, `xml=false`.
3. Start service in Docker Compose.
4. Upload a CSV via API Gateway and verify transactions appear in PostgreSQL.

## Open Questions

- None at this time.
