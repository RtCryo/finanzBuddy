# Proposal

## Why

Uploading the same bank statement twice stores every transaction twice. Observed with a 328-row CSV: the second upload produced 656 transactions, doubled the dashboard counts and totals, and polluted search, tags, rules, and related-expense lists. Overlapping exports (for example August–November, then August–December) cause the same problem for the shared period. The only deduplication today is per `documentId`, which protects against Kafka redelivery of one event; a re-upload gets a new `documentId` and passes straight through.

## What Changes

- The parser skips incoming transactions that the same user already has, instead of saving them again.
- Matching is line by line and count aware: a transaction counts as already present when the user has an identical one (booking date, value date, amount, currency, counterparty, description). Identical lines within one file are matched by quantity, so two equal coffee payments on one day are both kept on first import, and a re-import adds nothing.
- A document whose lines are all duplicates ends as `PROCESSED` with zero saved transactions, not as `FAILED`.
- The parser reports how many transactions it saved and how many it skipped as duplicates. The gateway stores both numbers on the document and returns them from the document endpoints.
- The documents list and the document detail page show the saved and skipped counts, and the detail page explains an all-duplicate result instead of the generic empty state.
- Existing duplicate rows are not modified or removed (no data migration).

## Capabilities

### New Capabilities

- `transaction-deduplication`: per-user, line-level duplicate detection when transactions are persisted, including quantity matching, concurrency safety, and the saved/skipped outcome per document.

### Modified Capabilities

- `document-lifecycle`: document responses expose the saved and skipped numbers reported by the parser (delivered as an ADDED requirement; existing requirements are unchanged).
- `document-list-ui`: the documents list and detail page show the deduplication outcome (delivered as an ADDED requirement; existing requirements are unchanged).

## Impact

- `parser-service`: `TransactionPersistenceService`, `DocumentProcessor`, `TransactionRepository`, `ProcessedDocument`, `GatewayStatusClient`; new Liquibase changeset for `processed_documents` counters and a `(user_id, booking_date)` index on `transactions`.
- `api-gateway`: `DocumentStatusUpdateRequest`, `DocumentResponse`, `Document` entity, `DocumentService.updateDocumentStatus`; new Liquibase changeset adding two nullable counter columns to `documents`.
- `ui`: `DocumentRecord` type, `DocumentsPage`, `DocumentDetailPage`.
- Docs: finanzBuddy `docs/ARCHITECTURE.md` and the parser/gateway READMEs where they describe deduplication.
- Single-service change inside `finanzBuddy`; no cross-service contract changes, so no root umbrella change is needed.
- Not affected: the identity contract, other services, and the Kafka event schema.
