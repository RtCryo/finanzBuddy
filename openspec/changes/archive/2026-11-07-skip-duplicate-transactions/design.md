# Design

## Context

See proposal.md for motivation. Current state:

- The parser (`parser-service`) consumes `DocumentUploadedEvent`, parses the file, and calls `TransactionPersistenceService.saveIfNotProcessed`, which inserts a `processed_documents` row keyed by `documentId` and then saves every parsed transaction. That key only guards against redelivery of one event.
- `parser-service` and `api-gateway` use the same PostgreSQL database. The parser owns `transactions` and `processed_documents` (its own Liquibase changelog); the gateway owns `documents` and everything else (its changelog).
- The parser reports status to the gateway through `PUT /internal/documents/{id}/status` (`DocumentStatusUpdateRequest`: `status`, `message`). On `PROCESSING` to `PROCESSED` the gateway applies the owner's tag rules to the document's transactions.
- Bank CSVs have no stable transaction id. The columns `Sammlerreferenz`, `Kundenreferenz`, and `Mandatsreferenz` are often shared by many rows or empty, so they cannot serve as an identity.
- Users edit only category and tags of a stored transaction; the identity fields are never edited.

## Goals / Non-Goals

**Goals:**
- Re-uploading a statement, and uploading overlapping statements, saves each real transaction once per user.
- Keep legitimately identical payments (same day, amount, counterparty) on first import.
- Tell the user what happened (saved and skipped).
- Stay correct under Kafka redelivery and concurrent uploads.

**Non-Goals:**
- Removing or merging duplicates that are already stored.
- Fuzzy matching, for example a pending booking that later appears booked with a different value date.
- Blocking an upload based on the file hash; the file is always accepted and stored.
- Deduplication for PDF and XML parsers (not enabled today); the logic sits behind the shared persistence step, so they inherit it.

## Decisions

### D1. Identity is a content key, compared by quantity

The key is booking date, value date, amount, currency, counterparty, and description, with strings trimmed and null equal to empty, and the amount normalized to scale 4. For each key the persistence step computes `surplus = max(0, incoming count - stored count)` for the owner and saves that many lines, skipping the first lines of the file in order for the remainder.

Alternatives: (a) reject a file by SHA-256 of its bytes: simple but fails for overlapping exports, which was the second reported case; (b) key-based skip without counting: would collapse two real identical coffees into one on first import; (c) bank reference columns as identity: not stable or unique. Counting is the only option that satisfies both overlap and legitimate repeats.

### D2. Compare in the application against a bounded slice of stored rows

The parser loads the owner's stored rows whose `booking_date` lies between the minimum and maximum booking date of the incoming file, plus rows with a null booking date, selecting only the key columns. It groups both sides by key in memory. A new index on `transactions (user_id, booking_date)` keeps this query bounded by the file's period, not by the owner's whole history.

Alternatives: a stored fingerprint column with a unique or counting index: needs a backfill of existing rows and a hash that the application and a SQL migration must compute identically, which is fragile and touches historic data (a stated non-goal). A SQL `NOT EXISTS` per row: one query per line, too slow for large files.

### D3. Serialize per owner with a transaction-scoped advisory lock

`saveIfNotProcessed` takes `pg_advisory_xact_lock` for the owner (one fixed lock namespace key plus the user id) before reading the stored slice, inside the same transaction that inserts `processed_documents` and the surplus rows. Two uploads of one owner therefore process one after the other, and the second sees the first one's rows. Different owners do not block each other. The lock is released at commit or rollback.

Alternatives: a unique constraint cannot express "up to N identical rows"; `SERIALIZABLE` isolation would retry-storm on large inserts; an application-level lock does not cover several parser instances.

### D4. The outcome is stored in `processed_documents` and reported with `PROCESSED`

`processed_documents` gets nullable `saved_count` and `skipped_count`, written in the same transaction as the rows. The parser sends both numbers in the `PROCESSED` status report. If the event is redelivered, or the report failed after the commit and is retried, the duplicate-event path reads the stored numbers and reports them again, so the gateway never loses them.

The gateway adds nullable `saved_transaction_count` and `skipped_duplicate_count` to `documents`, sets them when a request carries both, keeps them when a repeated `PROCESSED` carries none, and clears them on any transition away from `PROCESSED` (for example a `FAILED` to `PENDING` reprocess). `DocumentResponse` exposes them as nullable `savedTransactions` and `skippedDuplicates`. The new request fields are optional, so an older parser that sends none keeps working.

Alternative: derive the saved count from `count(*) where document_id = ?` at read time: gives no skipped number and costs a query per listed document.

### D5. All-duplicate files are successful

The existing guard "no transactions found" stays and applies to the parsed list. After deduplication, zero surplus rows is a normal `PROCESSED` outcome with `saved = 0`. The tag rule step runs against the document's own rows, so it simply has nothing to do.

### D6. UI shows plain text counters

The documents list row and the detail page show "N saved, M duplicates skipped" for `PROCESSED` documents when both numbers are present. When `saved` is 0 and `skipped` is positive, the detail page replaces the "no transactions available yet" empty state with an explanation that everything was already imported. No new component: the existing detail-state styles are reused, per the design system rules in `ui/DESIGN_SYSTEM.md`.

## Risks / Trade-offs

- [A pending booking later appears booked with a changed value date and imports again] → accepted and listed as a non-goal; the counters make the effect visible.
- [Two genuinely identical payments appear in an overlapping later export with a different count, for example the bank adds a third identical payment] → quantity matching imports exactly the surplus, so the third is saved.
- [A user deleted nothing but wants a forced re-import] → not supported by this change; deleting documents is outside it.
- [Large owner history slows the slice query] → the period-bounded slice plus the new index keeps it proportional to the file's date range.
- [Advisory lock held during a long insert blocks that owner's other uploads] → files are at most a few thousand rows and parsing happens before the lock is taken; only the compare and insert run under it.
- [Mixed deployment where the gateway is new and the parser old] → optional request fields and nullable columns keep both directions compatible; counters stay absent until the parser is updated.

## Migration Plan

1. Deploy gateway and parser together (order is free, see the compatibility risk above); both Liquibase changesets are additive, with rollbacks that drop the added columns and the index.
2. No data migration and no backfill: existing documents show no counters.
3. Rollback: redeploy the previous versions; the added columns and index are ignored by old code and can stay.
