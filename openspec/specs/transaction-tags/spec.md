# transaction-tags Specification

## Purpose

Enable private reusable labels on transactions and precise multi-tag searches without recording tag assignments in transaction version history.

## Requirements

### Requirement: Transactions have a persistent personal tag set
The system SHALL allow an authenticated owner to assign zero, one, or multiple distinct personal tags to a transaction. Assignments SHALL persist across closing and reopening details and new sessions. Removing a tag from a transaction SHALL remove only that assignment; the personal tag SHALL remain available for reuse.

#### Scenario: Save several tags and reopen details
- **WHEN** an owner saves two tags on a transaction and later reopens its details
- **THEN** both tags are returned as the transaction's current tag set

#### Scenario: Remove the last assignment
- **WHEN** the owner removes a transaction's last tag and saves
- **THEN** the transaction has no tags and the removed tag remains in the owner's reusable list

### Requirement: Users can create and reuse private tags
The system SHALL offer the user's existing tags and allow creating a tag while editing a transaction. Names SHALL be trimmed, nonblank, and at most 255 characters. Names SHALL be unique per user ignoring case after trimming; entering an existing name SHALL reuse that tag without a duplicate assignment. Invalid names SHALL reject the save without changes. Unassigned draft tags SHALL NOT be created on cancel.

#### Scenario: Reuse a differently cased name
- **WHEN** a user with tag "Travel" adds " travel " to a transaction and saves
- **THEN** the existing "Travel" tag is assigned once and no duplicate tag is created

#### Scenario: Invalid tag name
- **WHEN** an owner saves a blank or overlong new tag name
- **THEN** the save is rejected and no tag, assignment, category change, or version is committed

### Requirement: Tags are independent of transaction snapshots
The system SHALL store tags as current transaction metadata outside transaction version history. Tag-only saves SHALL NOT create or alter snapshots. Tags SHALL NOT appear in historical snapshot payloads or changed-field comparisons. Viewing an older snapshot SHALL show the current tags in a separately identified current-metadata section, read-only. A combined category/tag save SHALL commit all edits atomically and create a version only if the category changes. An unchanged save SHALL have no persistent side effects.

#### Scenario: Edit only tags
- **WHEN** an owner changes only tags and saves
- **THEN** the tag set changes and the version count and existing snapshots remain unchanged

#### Scenario: Save category and tags together
- **WHEN** an owner changes category and tags in one successful save
- **THEN** both changes persist and one new category snapshot is created, preserving the original snapshot when this is the first category edit

#### Scenario: Combined save fails
- **WHEN** any part of a combined save fails
- **THEN** the category, tag dictionary, assignments, and snapshots remain unchanged

#### Scenario: Inspect an older version
- **WHEN** the owner selects an older snapshot after changing tags
- **THEN** current tags are identified as current metadata and no historical tag values or tag differences are shown

### Requirement: Transaction searches support all or any selected tags
The system SHALL filter the owner's transactions by multiple selected tag identities with one explicit AND/all or OR/any mode. AND SHALL require every selected tag; OR SHALL require at least one. The default SHALL be AND. Empty tag selection SHALL impose no tag restriction. Duplicate selections SHALL be treated as one. The tag condition SHALL combine with other filters using AND and apply before sorting, pagination, and result counting. Untagged transactions SHALL NOT match a nonempty tag selection. Unknown or foreign tag identities SHALL reject the request with a generic invalid-selection error without revealing their existence or owner.

#### Scenario: Require all selected tags
- **WHEN** AND search selects "Travel" and "Work"
- **THEN** only transactions carrying both tags match, including transactions with additional tags

#### Scenario: Require any selected tag
- **WHEN** OR search selects "Travel" and "Work"
- **THEN** transactions carrying either or both match once each

#### Scenario: Combine tag and date filters across pages
- **WHEN** a tag condition and date range are applied
- **THEN** all returned transactions satisfy both and pagination totals count matching transactions without duplicate rows

#### Scenario: Clear tag selection
- **WHEN** selected tags are cleared
- **THEN** tagged and untagged transactions are eligible subject to the remaining filters

### Requirement: Tag operations are owner-scoped
The system SHALL require authentication and verify transaction ownership and tag ownership for reads and writes. Personal tag lists SHALL expose only the current user's tags. A failed authorization SHALL disclose no other user's transaction or tag data and SHALL make no changes.

#### Scenario: Attempt to assign another owner's tag
- **WHEN** an authenticated user submits a foreign tag identity for an owned transaction
- **THEN** the save is rejected without data disclosure or persistent changes

#### Scenario: Access another owner's transaction tags
- **WHEN** an authenticated user reads or edits another owner's transaction tags
- **THEN** the system responds as for an unavailable transaction without disclosing its tags

#### Scenario: Unauthenticated access
- **WHEN** a request for tag data or editing has no valid authentication
- **THEN** the request is rejected without disclosing or changing data

## Related implementation and history

- Documentation: [documentation map](../../../docs/README.md#subsystem-map), [Gateway README](../../../api-gateway/README.md), [UI README](../../../ui/README.md), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [tag service](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/TransactionTagService.java), [tag repository](../../../api-gateway/src/main/java/com/finanzbuddy/api/repository/TransactionTagRepository.java), [detail editor](../../../ui/src/components/TransactionDetailsDialog.tsx), [history filter](../../../ui/src/components/HistoryTagFilter.tsx)
- History: [add transaction tags](../../changes/archive/2026-11-01-add-transaction-tags/)
