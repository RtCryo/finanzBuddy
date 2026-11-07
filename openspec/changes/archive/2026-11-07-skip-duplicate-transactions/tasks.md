# Tasks

## 1. Parser: schema and deduplicating persistence

- [x] 1.1 Add a parser Liquibase changeset (with rollback) that adds nullable `saved_count` and `skipped_count` to `processed_documents` and an index on `transactions (user_id, booking_date)`; verify the parser starts against a fresh database and `./mvnw verify` in `parser-service/` still passes the schema-dependent tests.
- [x] 1.2 Add a repository query that returns the key columns of an owner's stored transactions for a booking-date range plus null booking dates; verify with a repository test that other owners' rows and out-of-range rows are not returned.
- [x] 1.3 Implement the identity key (trimmed strings, null equals empty, amount at scale 4) and the quantity comparison in `TransactionPersistenceService`, saving only the surplus and returning saved and skipped counts; verify unit tests for same file again, overlapping statements, different owners, two equal payments on first import, re-upload of two equal payments, three incoming against one stored, and a stored row whose category was edited.
- [x] 1.4 Take the per-owner `pg_advisory_xact_lock` before reading the stored slice inside the persisting transaction, and write `saved_count` and `skipped_count` with the `processed_documents` row; verify with an integration test that two simultaneous uploads of the same file leave one copy of the transactions.
- [x] 1.5 Make the redelivery path return the stored counts and verify a test that a repeated event neither saves rows nor changes the counts.
- [x] 1.6 Document the behavior in `parser-service/README.md` and the parser row of `docs/ARCHITECTURE.md` (deduplication by document id versus by transaction content), and verify the text matches the implemented key and lock.

## 2. Parser: reporting the outcome

- [x] 2.1 Extend `GatewayStatusClient.StatusUpdateRequest` with optional `savedTransactions` and `skippedDuplicates`, send them only with `PROCESSED`, and verify a client test that other statuses omit the fields.
- [x] 2.2 Update `DocumentProcessor` so an all-duplicate file ends `PROCESSED` with counts while an empty parse still fails with the current error; verify processor tests for partial, all-duplicate, and empty files, including the existing metrics counters.

## 3. Gateway: storing and exposing the counters

- [x] 3.1 Add a gateway Liquibase changeset (with rollback) adding nullable `saved_transaction_count` and `skipped_duplicate_count` to `documents`; verify Liquibase applies and rolls back in the test database.
- [x] 3.2 Extend `DocumentStatusUpdateRequest` with optional non-negative `savedTransactions` and `skippedDuplicates` (both or neither), store them in `DocumentService.updateDocumentStatus` on `PROCESSING` to `PROCESSED`, keep them on a repeated `PROCESSED` without numbers, and clear them on any transition away from `PROCESSED`; verify `DocumentServiceTest` cases for each, plus a 400 for a negative or single number.
- [x] 3.3 Return `savedTransactions` and `skippedDuplicates` from `DocumentResponse` (null when absent) for the list and single-document endpoints; verify controller or service tests that unfinished, failed, and historic documents return null and a processed one returns both.
- [x] 3.4 Update the gateway documentation (`api-gateway/README.md` internal status contract and document response) and verify it lists the new optional fields and null semantics.

## 4. UI

- [x] 4.1 Add the optional fields to `DocumentRecord` and show "N saved, M duplicates skipped" on processed rows in `DocumentsPage`; verify a `DocumentsPage` test for partial duplicates and for a historic document with no numbers.
- [x] 4.2 Show the counts on `DocumentDetailPage` and replace the "no transactions available yet" empty state with an all-imported explanation when saved is 0 and skipped is positive; verify `DocumentDetailPage` tests for the all-duplicate, partial, and historic cases.
- [x] 4.3 Keep the text readable without color and following `ui/DESIGN_SYSTEM.md`, then run `npm run lint` and `npm test` in `ui/` and verify both pass with no new warnings.

## 5. Integration and archive

- [x] 5.1 With the compose stack running, upload `documents/20261107-76085158-umsatz.CSV` twice as one user through the UI and verify the owner has 328 transactions, the second document shows "0 saved, 328 duplicates skipped" and is `PROCESSED`, and the dashboard still shows 328 transactions.
- [x] 5.2 Upload a second user's copy of the same file and verify that user receives all 328 transactions; verify the first user's totals are unchanged.
- [x] 5.3 Run `./mvnw verify` in `parser-service/` and `api-gateway/`, then `openspec validate skip-duplicate-transactions --strict`, and verify all pass before archiving.
