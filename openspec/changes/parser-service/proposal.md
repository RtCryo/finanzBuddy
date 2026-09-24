# Proposal: Parser Service

## Why

After the API Gateway accepts a financial document, a dedicated processor must parse the file and normalize the extracted data into a unified transaction model. A single parser service that supports all formats but can be configured to consume only selected topics keeps the codebase simple while allowing format-specific scaling.

## What Changes

- Introduce a new Spring Boot 4.1 service named `parser-service` in the `parser-service/` monorepo module.
- Consume `DocumentUploadedEvent` messages from Kafka topics `documents.csv`, `documents.pdf`, and `documents.xml`.
- Allow runtime configuration of which document formats the service instance processes (e.g., only `CSV` for the first deployment).
- Download the referenced file from MinIO.
- Parse the file using format-specific strategies.
- Normalize extracted data into a unified `Transaction` entity.
- Persist transactions in PostgreSQL.
- Update the parent document status via the API Gateway (`PROCESSING`, `PROCESSED`, `FAILED`).
- Provide Docker Compose service definition for local development.

## Capabilities

### New Capabilities

- `document-consumption`: The service consumes document events from Kafka topics based on enabled formats.
- `document-parsing`: Format-specific parsers extract raw transaction data from CSV, PDF, and XML files.
- `transaction-normalization`: Parsed data is mapped to a common `Transaction` model regardless of source format.
- `status-reporting`: The parser service reports processing progress and final outcome back to the API Gateway.

### Modified Capabilities

- None.

## Impact

- New Maven module `parser-service/`.
- New PostgreSQL table: `transactions`.
- Kafka consumer groups bound to configured formats.
- Adds parsing libraries for CSV, PDF, and XML.
- API Gateway must expose an internal endpoint for status updates.
