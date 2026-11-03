# Spec Delta

## MODIFIED Requirements

### Requirement: Transactions have a persistent personal tag set
The system SHALL allow an authenticated owner to assign zero, one, or multiple distinct personal tags to a transaction. The visible tag set SHALL be the union of manual assignments and rule contributions, with each tag shown once. Assignments SHALL persist across closing and reopening details and new sessions. Removing a tag through the transaction editor SHALL remove that tag's manual assignment and current rule contributions for that transaction; the personal tag SHALL remain available for reuse. A later explicit retroactive rule application or reconciliation MAY assign the tag again if the rule still matches.

#### Scenario: Save several tags and reopen details
- **WHEN** an owner saves two tags on a transaction and later reopens its details
- **THEN** both tags are returned as the transaction's current tag set

#### Scenario: Remove the last assignment
- **WHEN** the owner removes a transaction's last tag and saves
- **THEN** the transaction has no tags and the removed tag remains in the owner's reusable list

#### Scenario: Manually remove a rule-assigned tag
- **WHEN** the owner removes a tag currently contributed by a rule and saves the transaction
- **THEN** that tag is absent from the transaction's current tag set, other tags remain, and no transaction version is created
