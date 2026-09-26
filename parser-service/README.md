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

The CSV parser expects a semicolon-separated file with a header row:

```csv
BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category
15.03.24;15.03.24;-12.50;EUR;Supermarket;Weekly groceries;Food
```

Dates are parsed using the configured locale (`Locale.GERMANY` is used by default for CSV).

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
