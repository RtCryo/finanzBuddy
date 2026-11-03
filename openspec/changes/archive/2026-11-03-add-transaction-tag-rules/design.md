# Design

## Context

See [proposal](proposal.md) for motivation and the [rule](specs/transaction-tag-rules/spec.md) and [tag](specs/transaction-tags/spec.md) deltas for behavior. The parser owns imported `transactions` and writes them before calling Gateway `PUT /internal/documents/{id}/status` with `PROCESSED`. Gateway owns personal tags, assignments, document status, and user-facing APIs. `DocumentService.updateDocumentStatus` already runs in a database transaction and treats duplicate status reports as idempotent. Gateway reads parser rows through JDBC and currently presents the latest category snapshot when available. The transaction editor sends a complete desired tag set through `PUT /transactions/{id}`; tag-only edits do not create versions. The UI has an authenticated workspace shell but no rule route.

The shared PostgreSQL database permits Gateway to read committed imported rows at completion time. Gateway migrations must not add a foreign key to the parser-owned table or make Gateway startup depend on its existence. UI patterns and tests are governed by `ui/DESIGN_SYSTEM.md` and `ui/AGENTS.md`.

## Goals / Non-Goals

**Goals:** Keep rule evaluation in Gateway, make document completion and import tagging atomic, preserve the complete-set editor contract, and reconcile old contributions without disturbing independent tag sources. Keep all rule mutations and their optional retroactive work owner-scoped and atomic.

**Non-Goals:** Nested Boolean expressions, regular expressions, rule priorities, negative conditions, changing categories through rules, automatic reruns after manual transaction edits, parser-owned rule state, or a background rules engine.

## Decisions

### Store rules and provenance in Gateway-owned tables

Add a Gateway migration for `transaction_tag_rules` (owner, target tag, AND/OR mode), ordered `transaction_tag_rule_conditions` (field, exact/contains operator, value), and `transaction_tag_rule_contributions` (owner, rule, document, transaction, tag). Use owner-consistent references to Gateway-owned users/tags/rules/documents and a unique contribution key. Do not reference parser-owned `transactions` with a foreign key. Add `manual_assignment` to existing `transaction_tag_assignments`, defaulting existing rows to true. An assignment row is the materialized effective tag; it remains while `manual_assignment` is true or at least one rule contribution exists. This preserves current tag reads and history-filter queries.

The rule contribution retains its tag ID even after the rule's target changes. Editing without retroactive reconciliation leaves old contributions intact; future imports use the new target. Editing with reconciliation removes all old contributions from that rule and recomputes them from its new definition. Deleting without cleanup promotes affected effective assignments to manual, then removes the rule's contributions. Deleting with cleanup removes its contributions and prunes only assignments with no manual or other rule source. This makes the user's keep/remove choice durable without keeping a deleted rule. Existing assignment rows migrate as manual so old tags survive rule operations.

Alternative: store only the resulting tag set. That cannot safely distinguish a manually chosen tag from a rule-applied one during editing or deletion. Storing every tag solely as separate source rows would require replacing the existing read/filter contract; the materialized row is a smaller migration.

### Evaluate from a validated flat rule model

Create and update accept exactly one of `tagId` or `tagName`. Resolve a supplied name in the owner's tag dictionary within the rule mutation transaction, reusing a case-insensitive match. A failed rule mutation rolls back a newly created tag. The editor allows choosing an existing tag or entering a new name, including when the dictionary is empty.

For Category conditions, the editor loads the owner's available category list, offers the existing values and a create-category option, and preserves an edited rule's value even when it is absent from that list. The mutation request carries `newCategoryNames` only for values entered through the create option. Gateway validates each name against a Category condition and inserts it into the owner's personal category dictionary in the same rule transaction, reusing an existing personal name case-insensitively. This does not edit any transaction category or version.

Expose owner-scoped `GET /transactions/rules`, `POST /transactions/rules`, `PUT /transactions/rules/{id}`, and `DELETE /transactions/rules/{id}`. Create and update bodies carry exactly one of `tagId` or `tagName`, plus `mode`, `conditions`, and `applyExisting` (false by default); delete uses an explicit `removeExisting` choice. Accept Counterparty, Description, and Category as condition fields, each with exact or contains matching against text. Reject unknown modes, fields, operators, blank values, and foreign tags through generic invalid-rule responses. Require at least one condition. Use the same evaluator for import and historical processing, with `Locale.ROOT` case folding and trimmed rule/field text. Import candidates use the parser's category; historical candidates use the latest saved category from the transaction snapshot projection, as transaction history does. Category is match input only and rule application never writes a category version. Return a concise rule DTO with condition order retained for the editor.

Alternative: generate SQL predicates directly from user input. A bounded, validated evaluator is easier to share across import and retroactive paths and avoids dialect differences in case folding and escaping. Query candidate transactions in stable ID order and page through them, rather than loading the user's full history at once. A future scale threshold can justify set-based SQL or a job system without changing rule semantics.

### Apply rules at Gateway document completion

When a `PROCESSING -> PROCESSED` status request arrives, Gateway obtains the owning document and evaluates active rules against its already committed imported transactions before committing the status transition. Insert rule contributions and materialized assignments in that same Gateway transaction. On failure, roll back both tags and status, return an error to the parser, and let its existing status retry path retry the completion call. A repeated `PROCESSED` report for an already processed document is a no-op; it must not re-evaluate rules. This preserves the existing parser event and service-key boundary.

Serialize rule create/edit/delete and document completion for a user through a Gateway-owned user row lock, so an import sees a coherent rule set relative to retroactive mutations. Keep a consistent lock order and use uniqueness constraints/upserts for duplicate contribution protection. Document-row locking already serializes manual editor saves for a transaction; coordinate assignment updates so an editor save and a rule operation cannot leave an assignment without its sources.

Alternative: have the parser read rules or publish a new event. That would cross current data ownership and require new event/retry machinery. A completion hook uses the existing committed-row and status boundary.

### Preserve manual editor semantics

Keep the existing `PUT /transactions/{id}` complete desired set and response shape. For tags newly selected by a user, create a manual source; for tags already visible and still selected, retain their existing source flags without writing an unnecessary manual source. For removed tags, delete their assignment and rule contributions for that transaction. A subsequent explicit retroactive application may restore a matching rule tag. Keep assignment updates and category/version edits in the existing atomic transaction. Read APIs and history filters continue to use the effective assignment table; no rule metadata enters transaction versions.

Alternative: make rule-assigned tags read-only. That would conflict with the current generic tag editor and prevent users from correcting individual transactions.

### Ask for retroactive effects in the rules UI

Add a protected `/rules` page and workspace navigation entry. The page lists rules with readable condition summaries and offers create/edit/delete actions. The editor uses one mode selector and repeatable condition rows with field, operator, and text input; show the mode selector only when there are multiple conditions, keeping its value when the form returns to one condition. Its field selector includes Counterparty, Description, and Category, and rule summaries name Category conditions correctly. It does not render nested groups. Before the final create/edit/delete submission, show an accessible explicit choice about existing transactions, with a clear default of leaving them unchanged. Send that choice with the mutation in one request, not as a later best-effort request. Show progress and recoverable failure; refresh the list and relevant tag views after success. Reuse existing semantic tokens, focus, validation, responsive form, and dialog patterns. Add the pattern to `DESIGN_SYSTEM.md` only if existing patterns cannot express it.

Alternative: apply a rule now and then call a second retroactive endpoint. That can leave a saved rule with only some historical effects after a client interruption and makes the confirmation misleading.

## Risks / Trade-offs

- Large historical reconciliation can hold a transaction and owner lock for too long -> page through rows within one transaction, retain all-or-nothing behavior, and measure on realistic import volumes before considering an asynchronous job.
- Parser may retry a completion request after a timeout -> unique contribution keys plus the existing idempotent `PROCESSED` transition prevent duplicate or changed-rule reapplication.
- Import completion may race with rule edits or manual saves -> consistent lock order, owner/document serialization, and transactional source/assignment updates; test both orders.
- Removing a rule tag manually can be undone by a later explicit retroactive run -> the UI choice and rule description make that behavior clear; ordinary reads and imports never re-evaluate old transactions.
- Migration rollback would remove rules and provenance -> retain the additive schema on application rollback where possible; any schema rollback that drops these tables requires explicit acceptance of data loss.

## Migration Plan

1. Add the Gateway Liquibase schema and migration parity tests. Backfill existing assignments as manual; no parser migration or Kafka contract changes.
2. Deploy Gateway rule APIs and import-completion hook before the rules UI. Existing transaction and tag clients continue using their current shapes.
3. Deploy the UI route and confirmations. Update `docs/ARCHITECTURE.md`, Gateway/UI READMEs, and `docs/README.md` with the new owner, endpoint, route, capability, and implementation references when applying; add current spec backlinks at sync/archive time.
4. Roll back UI and Gateway code first if needed, keeping additive data. A destructive schema rollback explicitly loses rule definitions/contributions and must not be treated as routine rollback.
