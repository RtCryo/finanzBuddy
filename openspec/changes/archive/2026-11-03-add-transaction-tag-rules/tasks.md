# Tasks

## 1. Rule and assignment persistence

- [x] 1.1 Add the next Gateway Liquibase changeset for rules, ordered conditions, contributions, and `manual_assignment` with ownership constraints and rollback; verify migration and entity/schema parity tests pass without a parser-owned transaction foreign key.
- [x] 1.2 Add Gateway repositories for owner-scoped rule CRUD, latest transaction iteration, contribution writes, and effective assignment maintenance; verify repository tests cover distinct tags, old manual assignments, and shared contributions.
- [x] 1.3 Update `docs/ARCHITECTURE.md` and the Gateway README for new Gateway-owned tables and their parser-table boundary; verify descriptions against the migration and run the documentation-map checker.

## 2. Rule behavior and authenticated API

- [x] 2.1 Implement validation and flat AND/OR evaluation for Counterparty/Description with exact/contains matching; verify unit tests cover casing, whitespace, nulls, one condition, multiple conditions, and invalid input.
- [x] 2.2 Implement owner-scoped list/create/update/delete endpoints with atomic optional historical application or cleanup; verify API and service tests cover foreign tags/rules, create yes/no, edit yes/no including target changes, delete yes/no, failure rollback, and no cross-owner changes.
- [x] 2.3 Adapt the complete-set transaction editor to maintain manual and rule sources while preserving current read/filter and category-version behavior; verify service tests cover manual removal, unchanged saves, multiple sources, and atomic category/tag edits.
- [x] 2.4 Document rule endpoints, request choices, matching, and editor interaction in the Gateway README; verify examples against controller DTOs and run the documentation-map checker.
- [x] 2.5 Add Category as a validated exact/contains condition field in the rule API and evaluator. Include imported category in new-document candidates and latest saved category in historical candidates; verify matching, missing/blank values, ownership, and no category-version writes in tests. Update the Gateway README's supported fields.

## 3. Automatic import application

- [x] 3.1 Apply current owner rules in Gateway on the first `PROCESSING -> PROCESSED` transition, committing contributions with status; verify integration tests cover matching imports, duplicate completion, application failure rollback, and successful retry.
- [x] 3.2 Serialize completion and rule mutations with consistent owner/document lock order and source-aware assignment writes; verify concurrent integration cases for import versus edit/delete and manual tag save versus reconciliation.
- [x] 3.3 Update `docs/ARCHITECTURE.md` and Gateway/Parser READMEs for the completion flow and retry behavior; verify the documented boundary matches code and run the documentation-map checker.

## 4. Rules management UI

- [x] 4.1 Add the authenticated `/rules` route, workspace navigation, API client, and owner rule list; verify UI tests cover navigation, loading, empty, and error states.
- [x] 4.2 Build a flat rule editor with repeatable condition rows and existing-tag selection; verify UI tests cover AND/OR, exact/contains, validation, keyboard interaction, and create/edit persistence.
- [x] 4.3 Add explicit existing-transaction choices to create/edit/delete flows and send each choice with its mutation; verify UI tests cover yes/no paths, cancellation, failure recovery, and refreshed tags/rules after success.
- [x] 4.4 Update the UI README, applicable design-system pattern if needed, and documentation map for the new route and implementation area; verify wide/tablet/narrow layouts, focus, contrast, reduced motion, relevant UI tests, build, lint, and documentation-map check.
- [x] 4.5 Add Category to the rule editor's field selector and condition summaries; verify create/edit persistence and rendering in UI tests, and update the UI README's supported fields.

## 5. Integration verification

- [x] 5.1 Run the Gateway and parser test suites, relevant UI tests/build/lint, `openspec validate add-transaction-tag-rules --strict`, and `rtk proxy python scripts/check_documentation_map.py`; verify every command succeeds or record a concrete failure to fix before completion.
- [x] 5.2 Verify Category condition coverage across Gateway and UI, rerun the affected suites/build/lint, `openspec validate add-transaction-tag-rules --strict`, and `rtk proxy python scripts/check_documentation_map.py` after implementation.
## 6. Create a tag while authoring a rule

- [x] 6.1 Extend rule create/update to accept one of `tagId` or `tagName`, resolve a new name atomically, and cover validation, reuse, and rollback.
- [x] 6.2 Allow a new tag in the rule editor, including an empty personal tag list, and cover the UI flow.
- [x] 6.3 Update the change artifacts and module documentation, then run affected tests, build, lint, OpenSpec validation, and the documentation-map checker.

## 7. Category picker and condition action polish

- [x] 7.1 Offer available categories and new personal category entry for Category conditions while preserving older values absent from the list.
- [x] 7.2 Create requested personal categories atomically with rule create/update, validating category references and rollback; cover Gateway and UI behavior.
- [x] 7.3 Align condition removal with the shared secondary action pattern and verify responsive layout, tests, build, lint, OpenSpec, and documentation integrity.
