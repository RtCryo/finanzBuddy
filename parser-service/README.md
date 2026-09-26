# Parser Service

Consumes `DocumentUploadedEvent` messages from Kafka, downloads the referenced file from MinIO, parses it, and persists normalized transactions to PostgreSQL. Reports processing status back to the API Gateway.

## Supported Formats

| Format | Status | Default |
|--------|--------|---------|
| CSV    | Implemented | enabled |
| PDF    | Stub (MVP)  | disabled |
| XML    | Stub (MVP)  | disabled |

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
| `MINIO_ENDPOINT` | MinIO URL | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | MinIO access key | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO secret key | `minioadmin` |
| `MINIO_BUCKET` | MinIO bucket | `finanzbuddy-documents` |
| `GATEWAY_BASE_URL` | API Gateway base URL | `http://localhost:8080` |
| `INTERNAL_API_KEY` | Shared internal API key | (empty) |
| `PARSER_FORMAT_CSV` | Enable CSV consumer | `true` |
| `PARSER_FORMAT_PDF` | Enable PDF consumer | `false` |
| `PARSER_FORMAT_XML` | Enable XML consumer | `false` |
| `PARSER_FORMAT_CSV_CHARSET` | Charset for CSV files | `ISO-8859-1` |

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

The CSV parser supports semicolon-separated German bank statement exports as well as the English header variant. The following columns are mapped:

| CSV column (DE) | CSV column (EN) | Transaction field |
|-----------------|-----------------|-------------------|
| `Buchungstag` | `BookingDate` | booking date |
| `Valutadatum` | `ValueDate` | value date |
| `Betrag` | `Amount` | amount |
| `Waehrung` | `Currency` | currency |
| `Beguenstigter/Zahlungspflichtiger` | `Counterparty` | counterparty |
| `Verwendungszweck` | `Description` | description |
| `Kategorie` | `Category` | category |

Amounts may use a comma or a dot as the decimal separator. Files are read using the configured charset (`ISO-8859-1` by default for German umlauts).

Example German export row:

```csv
"Auftragskonto";"Buchungstag";"Valutadatum";"Buchungstext";"Verwendungszweck";"Glaeubiger ID";"Mandatsreferenz";"Kundenreferenz (End-to-End)";"Sammlerreferenz";"Lastschrift Ursprungsbetrag";"Auslagenersatz Ruecklastschrift";"Beguenstigter/Zahlungspflichtiger";"Kontonummer/IBAN";"BIC (SWIFT-Code)";"Betrag";"Waehrung";"Info";"Kategorie"
"DE12345678901234567890";"23.09.26";"23.09.26";"FOLGELASTSCHRIFT";"Order 123";"DE94ZZZ00000561653";"";"";"";"";"";"AMAZON PAYMENTS EUROPE S.C.A.";"DE87300308801908262006";"TUBDDEDD";"-22,09";"EUR";"Umsatz gebucht";"Einkäufe"
```

## Kafka Topics

- `documents.csv` — consumed when `parser.formats.csv=true`
- `documents.pdf` — consumed when `parser.formats.pdf=true`
- `documents.xml` — consumed when `parser.formats.xml=true`

## Status Reporting

The service reports the following statuses to `PUT /internal/documents/{id}/status` on the API Gateway:

- `PROCESSING` — parsing has started
- `PROCESSED` — all transactions persisted successfully
- `FAILED` — parsing failed with an error message

All status calls include the `X-Internal-Api-Key` header.
