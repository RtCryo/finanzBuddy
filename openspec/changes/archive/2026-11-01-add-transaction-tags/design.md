# Design

## Context

See [proposal](proposal.md) for motivation. `TransactionDetailsDialog.tsx` is shared by history and processed-document views. It loads category choices and snapshots, permits edits only on the latest version, and currently disables save unless category changes. `TransactionCategoryService` persists category snapshots through `TransactionCategoryRepository`; imported rows remain parser-owned. `TransactionReadRepository.findHistory` shares predicates between count and page queries and projects the latest snapshot. The existing transaction DTO also appears inside version responses, so adding tags there would blur the snapshot boundary.

Reviewed the documentation map, architecture, Gateway/UI READMEs, UI design system, transaction-view spec, controller/service/read repository, migration 009, UI API/editor/history sources. Existing neighboring tests cover editor behavior, API parameters, category saves, repository history, and Liquibase migrations; extend these suites. Another active proposal adds related expenses to the same panel; retain that section when applying either change.

## Goals / Non-Goals

**Goals:** Independent current tag metadata, atomic editor commits, private reusable labels, correct AND/OR filtering and counts, accessible chip interaction within the current React design system.

**Non-Goals:** Tag rename/global deletion screen, tag columns or chips in transaction rows, nested mixed Boolean expressions such as `(A OR B) AND C`, tag-specific sorting, automatic imported tags, parser/event changes. AND/OR means one mode applied to all selected tags.

## Decisions

### Gateway owns dictionary and assignments

Add `personal_transaction_tags(id, user_id, name, normalized_name)` with a unique `(user_id, normalized_name)` constraint, and `transaction_tag_assignments(user_id, document_id, transaction_id, tag_id)` unique on `(user_id, transaction_id, tag_id)`. Use foreign keys to gateway-owned users/documents and an owner-consistent composite reference to tags; no FK to parser-owned transactions, preserving gateway startup independence. Index owner/tag/transaction for search and owner/transaction for reads/replacement. Check ownership through the transaction/document join on every operation. No changes to parser migrations or transaction_versions.

Alternative: tags on snapshots would violate non-versioned metadata; JSON labels on imported rows would cross parser ownership and complicate reuse and filtering. Dictionary rows survive assignment removal intentionally.

### Add separate metadata reads and a unified atomic editor write

- `GET /transactions/tags` returns the owner's reusable `{id,name}` tags, sorted by name.
- `GET /transactions/{id}/tags` returns current assignments after ownership verification; no historical variant.
- Add `PUT /transactions/{id}` for the shared editor with optional `category` or `newCategoryName` (mutually exclusive) and required full desired `tags` set, each entry containing either `id` or `name`. Omitted category fields preserve category, enabling tag-only saves for uncategorized transactions. Empty tags clears assignments. Reject ambiguous/malformed entries.
- Return `{transaction, tags}` with the existing tag-free transaction shape. Existing category endpoints remain compatible; migrate the UI editor to the unified endpoint.

Use one gateway transaction for category creation/change, tag creation/reuse, and assignment replacement. Refactor category mutation into a reusable transactional operation preserving snapshot rules. Serialize edits to a transaction by locking its owned document row before reading latest state (gateway-owned row exists independently of parser schema); apply the same locking discipline to legacy category writes. Tag dictionary unique constraints arbitrate simultaneous creation on different documents: resolve known existing names first and retry a unique-key collision in a fresh transaction, then reuse the winning row. Never continue in an aborted database transaction. Assignment saves use last successful full-set save for competing tag edits; optimistic conflict UI is out of scope.

Alternative: sequential category/tag HTTP calls permit partial saves behind one Save changes action. A generic unified write preserves the user's expected all-or-nothing editor behavior. Separate metadata DTOs keep tags out of version responses and avoid list payload growth.

### Define name and selection semantics

Trim names and normalize comparison with `Locale.ROOT` lowercase, matching existing personal-category conventions; retain first saved spelling. Maximum 255 characters; reject blank/overlong names. Resolve IDs against owner scope, reuse normalized names and deduplicate assignments. Validate everything before commit. Create only new names that remain in the desired saved set. Proposed minor defaults: no tag colors, no arbitrary per-transaction tag-count limit, AND is the initial search mode.

### Filter with owner-scoped membership predicates

Extend `GET /transactions` with repeated `tagIds` query parameters and `tagMode=AND|OR` (default AND). UI serializes repeated bare keys explicitly. Validate IDs and mode; invalid/foreign IDs produce a generic 400 with no identity information. Deduplicate IDs. Empty selection ignores tag restriction; unsupported modes still return 400.

Add correlated membership predicates against assignments to the shared history WHERE fragment: OR uses EXISTS for any requested ID; AND compares the matching distinct tag count with the deduplicated selection size. Parameterize values and preserve owner scope. Apply the identical condition to count and page queries, avoiding fan-out joins and duplicate transactions. Other filters still combine with AND. Existing text search remains description/counterparty search; tags have a dedicated multi-select control.

### Extend the existing panel and filter controls

Render plain text chips under Tags in read mode and nothing in the chip area when none are assigned. Edit transaction activates tag multi-selection with searchable personal choices, inline new-name entry, and individually named remove buttons; no removal control in read mode. Save changes/Cancel govern category and tag drafts together; close discards drafts. Detect changes to either category or tag set, including clearing the last tag, without requiring an existing category. Failed saves retain drafts and show recoverable errors; successful saves refresh metadata/history and choices.

Tags are a separate "Current tags" region when inspecting an older snapshot; keep historical views read-only and never add tags to comparisonFields. Fetch assignments on each panel opening rather than using version snapshots as a tag cache. Refresh history after saves so active tag predicates take effect while preserving filter/sort state; if the current page becomes out of range, clamp to the last available page. Keep the open panel usable if its row leaves the filtered result, and provide a stable focus fallback on close when the opener is gone.

History filters use a multi-select plus "All selected tags (AND)" / "Any selected tag (OR)" mode; selection/mode changes reset page to zero. Clear filters clears tags and restores AND. Chips in filter controls represent selection only; table rows remain unchanged. Mixed Boolean expression builders are deliberately excluded.

Use [Material chips examples](https://material.angular.dev/components/chips/examples) as the user's interaction reference, implemented in existing React/CSS without adding Angular or a component-library dependency. Document a chip/multi-select pattern in DESIGN_SYSTEM.md using semantic tokens, keyboard-operable named removal, visible focus, predictable focus after removal, announced updates, wrapping long names, and responsive contained layout. Verify wide, tablet, narrow, contrast, and reduced motion.

## Risks / Trade-offs

- Concurrent dictionary creation or category edits → unique constraints, document-row serialization for editor and legacy writes, bounded fresh-transaction retry and conflict response without partial state.
- Document-row locking serializes different transaction edits in one document → acceptable initial simplicity; narrow locking can be revisited if contention is observed.
- Many selected tags increase query work → indexed membership predicates; verify pagination/count correctness on realistic datasets before introducing a product limit.
- Current tags alongside historical fields could imply old tag values → separate clearly labeled current metadata and no tag comparison.
- Both active changes touch the panel → reconcile shared loading/error/editor state without removing related-expense behavior.

## Migration Plan

1. Add the next available Gateway Liquibase changeset after 009, including rollback and migration tests; no backfill, all existing transactions start untagged.
2. Deploy additive schema/backend APIs before the UI that uses them; preserve legacy category endpoints and current clients.
3. Deploy editor/filter UI, update architecture ownership/routes and module docs, and update map references when the new spec becomes current.
4. Roll back UI/backend first; additive tables can remain for data retention. Running migration rollback drops tag data and must be an explicit operational choice.
