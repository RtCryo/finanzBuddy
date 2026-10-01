# Parser Service

Consumes `DocumentUploadedEvent` messages from Kafka, downloads the referenced file from S3-compatible object storage (SeaweedFS), parses it, and persists normalized transactions to PostgreSQL. Reports processing status back to the API Gateway.

## Supported Formats

| Format | Status | Default |
|--------|--------|---------|
| CSV    | Implemented | enabled |
| PDF    | Stub (MVP)  | disabled |
| XML    | Stub (MVP)  | disabled |

## Architecture

The service is organized into two hexagonal (ports & adapters) vertical slices under
`com.finanzbuddy.parser`.

### Processing slice (`processing/`)

Orchestrates the consume → fetch → parse → persist → report flow:

```
processing/
  domain/
    DocumentProcessor              implements ProcessDocumentUseCase; framework-free orchestration
    model/      ProcessingOutcome  terminal result (PROCESSED | DUPLICATE); failures are exceptions
                WriteResult        idempotent-write result (WRITTEN | DUPLICATE)
    exception/  FileFetchException, TransactionPersistenceException
  port/
    in/   ProcessDocumentUseCase   handle one DocumentUploadedEvent
    out/  FileFetchPort            fetch the stored object by key
          TransactionParserPort    route + parse to domain transactions
          TransactionWriterPort    idempotent dedupe + insert
          StatusReporterPort       report PROCESSING/PROCESSED/FAILED to the gateway
          ProcessingMetricsPort    processed/failed/duplicate counters + processing timer
  adapter/
    in/kafka/        KafkaDocumentEventAdapter (@KafkaListener) + DocumentUploadedEventDeserializer
    out/storage/     MinioFileFetchAdapter          MinIO client
    out/parsing/     RoutingTransactionParserAdapter delegates to the parsing slice router
    out/persistence/ JpaTransactionWriterAdapter    @Transactional dedupe+insert; TransactionEntity, ProcessedDocument, repositories
    out/status/      RestClientStatusReporterAdapter @Retryable RestClient
    out/metrics/     MicrometerProcessingMetricsAdapter preserves exact metric names
  config/
    ProcessingConfiguration        wires the framework-free orchestrator as a Spring bean
```

The processing domain depends only on `java.*`, `org.slf4j`, and project domain types.
Micrometer, Spring, MinIO, Kafka, and Jackson are confined to adapters; framework
exceptions (`DataAccessException`, `RestClientException`) are translated to domain
exceptions inside the adapters. The Kafka consumer is a thin adapter that only invokes
the inbound port. These boundaries are enforced by `ProcessingArchitectureTest` (ArchUnit).

### Parsing slice (`parsing/`)

Turns a raw document into normalized domain transactions:

```
parsing/
  domain/
    DocumentParserRouter          selects a parser by document type (framework-free)
    model/        Transaction      framework-free transaction extracted from a document
    normalization/ CsvRowMapper    row -> domain Transaction (column aliasing, currency rules)
                   CsvAmountParser  locale-aware amount normalization
                   CsvDateParser    locale-aware date normalization
  port/
    DocumentParser                 supports(type) + parse(...) -> domain transactions
  adapter/
    csv/  CsvParserAdapter          OpenCSV / commons-io / Jackson; reads rows, delegates mapping
    pdf/  PdfParserAdapter          MVP stub (future PDFBox home)
    xml/  XmlParserAdapter          MVP stub (future Jackson-XML home)
  config/
    ParsingConfiguration           wires the framework-free router as a Spring bean
```

The normalization domain depends only on `java.*` and project domain types — format
libraries (OpenCSV, commons-io, Jackson) are confined to the adapters. The
`DocumentParser` port returns the domain `Transaction`, which both slices share; mapping
to the JPA entity happens in the processing persistence adapter. These boundaries are
enforced by `ParsingArchitectureTest` (ArchUnit).

## Configuration

Configuration is read from `application.yml` and can be overridden with environment variables:

| Environment Variable | Description | Default |
|---------------------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database | `finanzbuddy` |
| `DB_USER` | PostgreSQL user | `finanzbuddy` |
| `DB_PASSWORD` | PostgreSQL password | `finanzbuddy` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:29092` |
| `MINIO_ENDPOINT` | S3 object-storage endpoint (SeaweedFS) | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | S3 access key | `minioadmin` |
| `MINIO_SECRET_KEY` | S3 secret key | `minioadmin` |
| `MINIO_BUCKET` | Object-storage bucket | `finanzbuddy-documents` |
| `GATEWAY_BASE_URL` | API Gateway base URL | `http://localhost:8080` |
| `INTERNAL_API_KEY` | Shared internal API key | (empty) |
| `GATEWAY_CONNECT_TIMEOUT` | HTTP connect timeout | `5s` |
| `GATEWAY_READ_TIMEOUT` | HTTP read timeout | `30s` |
| `PARSER_FORMAT_CSV` | Enable CSV consumer | `true` |
| `PARSER_FORMAT_PDF` | Enable PDF consumer | `false` |
| `PARSER_FORMAT_XML` | Enable XML consumer | `false` |
| `PARSER_FORMAT_CSV_CHARSET` | Charset for CSV files | `windows-1252` |
| `PARSER_FORMAT_CSV_SEPARATOR` | CSV field separator (`;`, `,`, `tab`, `semicolon`, `comma`) | `;` |
| `PARSER_FORMAT_CSV_MAX_ROWS` | Maximum rows allowed per CSV file | `100000` |
| `PARSER_FORMAT_CSV_LOCALE` | Locale for date parsing | `de-DE` |

## Build

```bash
mvn clean package
```

## Run Locally

Start dependencies from the repository root:

```bash
docker-compose up -d postgres kafka minio api-gateway
```

Then run the parser service:

```bash
mvn spring-boot:run
```

## Run Tests

```bash
mvn test
```

## Docker

Build the image:

```bash
docker build -t finanzbuddy/parser-service .
```

Or use Docker Compose from the repository root:

```bash
docker-compose up -d parser-service
```

## CSV Format

The CSV parser supports semicolon-separated German bank statement exports as well as English/comma-separated variants. The following columns are mapped:

| CSV column (DE) | CSV column (EN) | Transaction field |
|-----------------|-----------------|-------------------|
| `Buchungstag` | `BookingDate` | booking date |
| `Valutadatum` | `ValueDate` | value date |
| `Betrag` | `Amount` | amount |
| `Waehrung` | `Currency` | currency |
| `Beguenstigter/Zahlungspflichtiger` | `Counterparty` | counterparty |
| `Verwendungszweck` | `Description` | description |
| `Kategorie` | `Category` | category |

### Supported date formats

The parser tries the following formats in order:

1. Locale-specific short date format (configured by `PARSER_FORMAT_CSV_LOCALE`)
2. German `dd.MM.yy`
3. German `dd.MM.yyyy`
4. ISO-like `yyyy-MM-dd`

### Supported amount formats

| Input | Parsed as |
|-------|-----------|
| `-12,50` | `-12.50` |
| `1.234,56` | `1234.56` |
| `1234.56` | `1234.56` |
| `1,234` | `1.234` with the German locale; `1234` with an English locale |
| `1,234.56` | `1234.56` |

Files are read using the configured charset (`windows-1252` by default for German umlauts). UTF-8 Byte Order Marks (BOM) are stripped automatically.

Example German export row:

```csv
"Auftragskonto";"Buchungstag";"Valutadatum";"Buchungstext";"Verwendungszweck";"Glaeubiger ID";"Mandatsreferenz";"Kundenreferenz (End-to-End)";"Sammlerreferenz";"Lastschrift Ursprungsbetrag";"Auslagenersatz Ruecklastschrift";"Beguenstigter/Zahlungspflichtiger";"Kontonummer/IBAN";"BIC (SWIFT-Code)";"Betrag";"Waehrung";"Info";"Kategorie"
"DE12345678901234567890";"23.09.26";"23.09.26";"FOLGELASTSCHRIFT";"Order 123";"DE94ZZZ00000561653";"";"";"";"";"";"AMAZON PAYMENTS EUROPE S.C.A.";"DE87300308801908262006";"TUBDDEDD";"-22,09";"EUR";"Umsatz gebucht";"Einkäufe"
```

## Kafka Topics

- `documents.csv` — consumed when `parser.formats.csv=true`
- `documents.pdf` — consumed when `parser.formats.pdf=true`
- `documents.xml` — consumed when `parser.formats.xml=true`

### Dead Letter Topics

If a record cannot be deserialized or processed after three retries, it is published to the corresponding DLT:

- `documents.csv.dlt`
- `documents.pdf.dlt`
- `documents.xml.dlt`

## Status Reporting

The service reports the following statuses to `PUT /internal/documents/{id}/status` on the API Gateway:

- `PROCESSING` — parsing has started
- `PROCESSED` — all transactions persisted successfully
- `FAILED` — parsing failed with an error message

All status calls include the `X-Internal-Api-Key` header when the key is configured.

Status reporting uses the modern `RestClient` with configurable connect/read timeouts and retries failed requests up to 3 times with exponential backoff.

## Monitoring

Spring Boot Actuator is enabled. Exposed endpoints:

- `/actuator/health` — liveness and readiness probes
- `/actuator/info` — build information
- `/actuator/metrics` — Micrometer metrics
- `/actuator/prometheus` — Prometheus scrape endpoint

Custom metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `parser.documents.processed` | Counter | Successfully processed documents |
| `parser.documents.failed` | Counter | Failed document processing attempts |
| `parser.documents.duplicate` | Counter | Duplicate document events ignored |
| `parser.documents.processing.duration` | Timer | Document processing duration |

## Performance

Database writes use Hibernate batch inserts (`spring.jpa.properties.hibernate.jdbc.batch_size=100`) to reduce round-trips for large bank statements.

## Retry & Error Handling

- Failed status reports to the gateway are retried up to 3 times.
- Kafka consumer errors are retried 3 times before the record is moved to the DLT.
- Invalid events and parsing errors are not retried; they mark the document as `FAILED` and go to the DLT.
- Transient storage/database failures are retried 3 times and then sent to the DLT; the gateway status is marked `FAILED` after retries are exhausted.
- Error messages are truncated to 4000 Unicode code points.
