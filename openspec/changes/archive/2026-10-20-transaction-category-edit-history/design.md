# Design

## Context

See `proposal.md` for motivation and `specs/transaction-view-ui/spec.md` for behavior. The transaction-details popup is shared by transaction history and document details. The API Gateway currently reads the parser-service-owned `transactions` table through JDBC; its repository intentionally avoids a JPA schema-validation dependency because that table may not exist when the gateway starts. Transaction-history categories are currently derived from category values on the user's current transaction rows.

## Goals / Non-Goals

**Goals:**
- Keep parser-produced transaction data and user category changes separately owned.
- Provide durable, owner-scoped version snapshots and reusable personal categories.
- Ensure transaction history and document details resolve to the same latest transaction state.

**Non-Goals:**
- Editing parsed transaction fields other than category.
- Restoring an earlier version as the current version or deleting version history.
- Introducing shared/global categories or changing document parsing behavior.

## Decisions

### Open transaction details in a read-only side panel

Opening a transaction shows a right-side panel over the current list, keeping the originating page visible. The panel uses one content column: transaction identity and amount, optional version selector and change summary, then normalized transaction fields. Category is displayed as a plain value in the details list, not as a chip or an active control. A generic **Edit transaction** action is visible in the panel header. Only after activating it does category become editable; other fields remain read-only. **Save changes** commits the edit and **Cancel** discards it.

Category creation stays inside the category editor. Its **+ Create category…** option reveals a name input inline; the user then commits the category assignment and transaction snapshot through **Save changes**. There is no separate category-creation action or category-specific save action.

### Select versions and compare snapshots without crowding the history

An unedited transaction still has one initial version in the data model, but the panel does not render a version selector or comparison section for that single version. When multiple versions exist, a compact, chronologically ordered horizontal selector appears above transaction details; the latest version is selected initially. The selected snapshot remains read-only unless it is the latest and the user activates **Edit transaction**.

For a selected version after the original, show a concise field-by-field difference between that snapshot and the immediately older snapshot. Label the comparison **Changes since version N** so its direction is clear. Selecting the original version shows that it was imported from the statement and has no older snapshot to compare. The comparison must be derived from full snapshots, not hardcoded to category, so future editable fields can appear without redesigning the history.

#### One version, inspection state

```text
+-----------------------------------------------+
| Transaction details             [Edit] [×]    |
| Nordstrom Rack                    −$84.50 USD |
| 14 Mar 2024 · Everyday •• 2841                |
|                                               |
| Transaction                                   |
| Booking date                         14 Mar   |
| Value date                           15 Mar   |
| Counterparty                  Nordstrom Rack  |
| Description                    Card payment  |
| Category                         Uncategorized|
+-----------------------------------------------+
```

No version selector or comparison section appears for this transaction.

#### Multiple versions, latest selected

```text
+-----------------------------------------------+
| Transaction details             [Edit] [×]    |
| Nordstrom Rack                    −$84.50 USD |
| [Original] [Version 2] [Version 3 · Current] |
|                                               |
| Changes since version 2                       |
| Category        Groceries → Shopping          |
| Description     — → Clothing store            |
|                                               |
| Transaction                                   |
| ...                                           |
| Category                         Shopping     |
+-----------------------------------------------+
```

Selecting an older version updates the displayed snapshot and its comparison with the immediately older snapshot. Older versions remain read-only and do not show **Edit transaction**.

### Store user changes in API-Gateway-owned tables

Add append-only transaction-version rows and a user-owned personal-category table using API Gateway Liquibase migrations. Keep the parser service as the sole writer of parsed transaction rows; do not update its table or introduce a synchronous API Gateway-to-parser-service write dependency. The version table stores complete normalized snapshots, including category, while the category table stores user-created reusable names. Avoid a foreign key from gateway tables to the parser-owned transaction table so gateway startup remains independent of parser-service migration order; retain user ownership and transaction identity in each version row and validate transaction ownership at runtime.

An unedited transaction is returned as its initial version from the parsed row. When the first category edit is committed, create a stored initial snapshot and the new snapshot in the same database transaction. Later edits append another snapshot. This avoids a bulk backfill that would require the parser-owned table to exist during gateway migration while still exposing the original transaction as version one.

Alternative considered: modify the parser-service-owned transaction row and add versioning there. This would cross the existing service ownership boundary and couple category management to parser persistence. A parser-service write API would preserve table ownership but add synchronous service availability and failure handling to a user-facing edit; the gateway-owned ledger avoids both.

### Resolve a single current version for all transaction reads

Use a shared gateway read path for transaction history and document transaction details. For transactions with snapshots, the highest version number is current; otherwise the parser-produced row is the initial current version. Apply category filters, sorting, and pagination after resolving the current version so historical snapshots never appear as additional transactions. Category choices combine category values present in the user's current or historical versions with the user's saved personal categories.

Alternative considered: update a separate current-category field and maintain snapshots independently. Keeping the latest complete snapshot as the current override avoids two writable representations that could disagree.

### Make edits and category creation owner-scoped and atomic

Derive the user ID from the authenticated principal, then verify transaction ownership through its document and transaction ownership fields before reading or writing versions. A category creation and its assignment/version append commit together. Reject blank category names and enforce per-user uniqueness after trimming and case-insensitive comparison while preserving the first saved display spelling. Append the initial and new versions atomically; use a unique transaction/version constraint and return a conflict if concurrent edits race, allowing the client to reload the current version.

Expose authenticated gateway operations for listing/creating personal categories, reading a transaction's versions, and changing its category. Keep the exact endpoint shape aligned with existing `/transactions` and `/documents/{id}/transactions` routes during implementation; neither client-supplied user IDs nor document IDs alone establish authorization.

Alternative considered: store custom category names as free text only on transactions. That would not make unused categories reusable or provide a user-owned category list.

## Risks / Trade-offs

- [Transaction rows may be unavailable while parser-service schema is not initialized] → Keep gateway migrations independent of the transaction table and access parsed rows only at request time, as existing transaction reads already do.
- [Concurrent edits could attempt to append the same next version] → Enforce unique transaction/version numbering and fail one write atomically with a conflict; the client reloads before retrying.
- [A future parser workflow could replace transaction IDs or parsed fields] → Keep user edits in the gateway ledger and verify parser retry/idempotency behavior; do not let ordinary document retry overwrite snapshots. Any future transaction replacement flow must explicitly preserve or reconcile the associated ledger.
- [Category names differing only by case may be considered duplicates] → Normalize only for uniqueness checks and preserve the stored display spelling.

## Migration Plan

1. Add backward-compatible gateway tables for personal categories and transaction snapshots; do not require the parser-owned table during migration.
2. Deploy gateway read/write support that falls back to parsed rows when no snapshot exists, exposes an initial version for unedited transactions, and writes the baseline plus first edit atomically.
3. Deploy the shared read-only details view, explicit generic edit mode, conditional version navigation, and latest-version list behavior after the API is available. No bulk transaction rewrite is required.
4. On rollback, disable the new UI/API while retaining the additive tables and their data. Remove those tables only through a separately approved destructive migration after confirming that version history and personal categories may be discarded.
