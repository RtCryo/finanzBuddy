# Proposal

## Why

Users currently repeat the same tag edits for every imported transaction. Personal rules will apply tags consistently as new statements arrive and let users decide whether rule changes should affect older transactions.

## What Changes

- Add owner-scoped rules that assign an existing or newly created personal tag when Counterparty, Description, or Category meets one or more text conditions. A rule combines its conditions with one AND or OR operator; each condition supports exact or contains matching.
- Apply active rules automatically to transactions after a successful import, before the processed document becomes visible as complete.
- Provide a rules management screen to create, inspect, edit, and delete rules. Creating a rule offers application to existing transactions; editing or deleting one asks whether to update existing assignments.
- Let Category conditions select an available category or create a personal category within the rule save, using the transaction editor's category-choice pattern.
- Track which rule contributed each tag assignment so retroactive updates can remove only that rule's contribution while preserving manual assignments and contributions from other rules.

## Capabilities

### New Capabilities

- `transaction-tag-rules`: Rule authoring, matching, automatic import application, optional retroactive application and reconciliation, ownership, and UI behavior.

### Modified Capabilities

- `transaction-tags`: Clarify the effective tag set and manual edit behavior when a tag is also assigned by a rule.

## Impact

- API Gateway: rule and assignment storage, owner-scoped rule APIs, automatic processing at the internal document-completion boundary, and tag editing/reading behavior.
- Parser Service: no new rule ownership; its existing completion notification triggers Gateway processing.
- Web UI: authenticated rules route, navigation, rule editor, and confirmation flows.
- Gateway Liquibase migration and tests; architecture, module, design-system, and documentation-map updates during implementation.
