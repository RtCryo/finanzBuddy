# Proposal

## Why

Categories give each transaction only one classification. Personal reusable tags let users organize a transaction in several ways and find transactions by combinations of labels without adding noise to transaction version history.

## What Changes

- Allow zero, one, or several personal tags per transaction, persisted independently of transaction snapshots.
- Display tags as chips in the shared transaction detail panel; add existing tags, create new ones, and remove assignments only after entering "Edit transaction". Use the existing Save changes/Cancel workflow.
- Keep personal tags reusable after their last assignment is removed. Tag changes do not create versions or appear in snapshot comparisons.
- Add a multi-tag history filter with an explicit AND/all or OR/any mode, combinable with existing filters. Do not display tags in transaction table rows.
- Save category and tag edits atomically; retain existing category versioning behavior.

## Capabilities

### New Capabilities

- `transaction-tags`: Owner-scoped reusable tags, transaction assignments, persistence outside version history, and multi-tag matching semantics.

### Modified Capabilities

- `transaction-view-ui`: Extend detail editing to tags and transaction-history filtering to multi-tag AND/OR selection while preserving category editing and snapshot inspection.

## Impact

- API Gateway: new gateway-owned tag/assignment tables and Liquibase migration, owner-scoped read/save endpoints, atomic transaction-edit orchestration, and tag predicates in the history query/count.
- UI: transaction detail editor, reusable accessible chip controls, history filters, API/types, styles and related tests.
- No parser transaction schema, Kafka events, or deployment changes. Keep snapshot DTOs free of tags.
- Implementation updates architecture, Gateway/UI documentation, chip patterns in the design system, and documentation map/current-spec navigation. Planning does not describe these additions as already implemented.
- Coordinate shared detail-panel edits with the active `show-counterparty-expenses-in-transaction-details` change.
