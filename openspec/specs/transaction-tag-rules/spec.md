# transaction-tag-rules Specification

## Purpose

Let each user define text-based rules that add personal tags to imported transactions, with explicit control over changes to older assignments.

## Requirements

### Requirement: Users can manage private tag rules
The system SHALL let an authenticated user list, create, edit, and delete only their own rules. Each rule SHALL target an existing personal tag or a tag created by name in the rule editor, and contain at least one condition. New tag names SHALL be trimmed, nonblank, at most 255 characters, and unique per user ignoring case after trimming. A same-name tag SHALL be reused. Tag creation and the rule mutation SHALL be atomic. Every condition SHALL choose Counterparty, Description, or Category, exact match or contains, and a nonblank text value. A rule SHALL use one AND or OR operator across all its conditions; nested groups SHALL NOT be accepted. Invalid or foreign tag references and malformed conditions SHALL reject a mutation without changing rules, tags, or assignments.

The rule editor SHALL show the AND/OR choice only when a rule has more than one condition. A new rule with one condition SHALL default to AND; hiding the choice after removing conditions SHALL preserve the selected mode if another condition is added again.
For a Category condition, the editor SHALL offer the user's available categories and an option to create a personal category by name, as in transaction editing. An existing rule value absent from the available list SHALL remain selectable while editing. A newly named category SHALL be trimmed, nonblank, at most 255 characters, and saved for the owner atomically with the rule mutation. Canceling or failing the mutation SHALL leave no new category. Creating a category through a rule SHALL NOT assign it to a transaction or create a transaction version. The condition SHALL retain its selected exact or contains operator.

#### Scenario: Create a rule with AND conditions
- **WHEN** a user creates a rule targeting their tag 123 with Counterparty exactly X AND Description containing YUPI
- **THEN** the rule is saved for that user and matches only transactions satisfying both conditions

#### Scenario: Create a rule with OR conditions
- **WHEN** a user creates a rule with two conditions joined by OR
- **THEN** a transaction satisfying either condition matches the rule once

#### Scenario: Create a rule with a new tag
- **WHEN** a user enters a new tag name while creating a rule
- **THEN** the personal tag and rule targeting it are saved together with the selected choice about existing transactions

#### Scenario: Reuse a same-name tag
- **WHEN** a user enters a tag name that already exists for them with different case or surrounding whitespace
- **THEN** the rule targets the existing tag without creating a duplicate

#### Scenario: Select or create a category while authoring a rule
- **WHEN** a user selects Category for a condition and chooses an available category or enters a new personal category name
- **THEN** the condition uses that category text, and a new personal category is saved with the rule only after successful submission

#### Scenario: Keep a previous free-text category condition editable
- **WHEN** a user edits a Category condition whose value is not in the available category list
- **THEN** the editor still shows and preserves that value until the user selects another category or creates one

#### Scenario: Reject a foreign tag or invalid rule
- **WHEN** a user submits another user's tag, an empty condition value, or a nested condition group
- **THEN** the mutation is rejected without revealing the foreign tag or changing data

### Requirement: Text matching is predictable
Exact match SHALL compare the whole field and contains SHALL find the value anywhere in the field. Both modes SHALL compare without case sensitivity after trimming leading and trailing whitespace from the rule value and transaction field. A missing or blank transaction field SHALL NOT satisfy a condition. Category conditions SHALL compare the transaction's category text; they SHALL NOT change its category. Existing transactions SHALL be matched using their latest saved Counterparty, Description, and Category values. New imports SHALL be matched using their imported values. Multiple matching rules SHALL add their distinct target tags without replacing existing tags.

#### Scenario: Match text ignoring case
- **WHEN** Counterparty is ` x ` and Description is `Payment yupi 42` for a rule requiring exact `X` AND contains `YUPI`
- **THEN** the rule matches

#### Scenario: Missing field
- **WHEN** a rule tests Description and the transaction has no Description
- **THEN** that condition is false for both exact and contains matching

#### Scenario: Match the latest category
- **WHEN** a processed transaction's imported Category is `Other`, its latest saved Category is `Travel`, and a rule requires Category exactly `travel`
- **THEN** applying the rule to existing transactions matches that transaction and adds its target tag without changing the category

#### Scenario: Match an imported category
- **WHEN** a new transaction is imported with Category `Groceries` and a rule requires Category containing `grocer`
- **THEN** the rule matches during document completion and adds its target tag

### Requirement: Successful imports receive rule tags automatically
After new transactions are persisted for a document, the system SHALL evaluate that owner's current rules against each new transaction and commit matching tag contributions before the document becomes visible as `PROCESSED`. A failed rule-application step SHALL NOT expose the document as successfully processed without its required rule tags. Repeated completion notifications SHALL NOT duplicate contributions or reapply changed rules to an already processed document.

#### Scenario: Complete an import with matching rules
- **WHEN** the parser completes an import containing a transaction that matches two rules
- **THEN** the transaction receives both target tags before the document is shown as processed

#### Scenario: Duplicate completion notification
- **WHEN** the completion notification for an already processed document is delivered again
- **THEN** no duplicate tag contribution is created and prior assignments remain unchanged

### Requirement: Creating a rule offers application to existing transactions
When creating a rule, the UI SHALL offer an explicit choice to apply it to all the user's already processed transactions. Choosing application SHALL evaluate every eligible transaction and add contributions for matches; declining SHALL leave existing assignments unchanged. The saved rule SHALL apply to future imports in either case. A failed retroactive application SHALL report failure without leaving a partially applied result.

#### Scenario: Apply a new rule to history
- **WHEN** the user chooses to apply a new rule to existing transactions
- **THEN** every matching transaction in the user's processed documents gains the target tag once

#### Scenario: Decline retroactive application
- **WHEN** the user saves a new rule without applying it to existing transactions
- **THEN** existing transaction tags stay unchanged and later imports use the rule

### Requirement: Editing a rule offers reconciliation of existing assignments
When editing a rule, the UI SHALL ask whether to update existing transactions. If chosen, the system SHALL reevaluate all of the user's processed transactions against the new definition, remove prior contributions from that rule that no longer apply, and add its new contributions. If declined, existing assignments SHALL remain unchanged, while future imports use the new definition. A failed reconciliation SHALL leave the rule and existing contributions unchanged.

#### Scenario: Edit with reconciliation
- **WHEN** a user changes a rule's conditions or target tag and chooses to update existing transactions
- **THEN** the rule's previous contributions are replaced according to the new definition across the user's processed transactions

#### Scenario: Edit without reconciliation
- **WHEN** a user changes a rule and declines the update of existing transactions
- **THEN** existing tag assignments remain as they were and future imports use the updated rule

### Requirement: Deleting a rule offers cleanup of existing assignments
When deleting a rule, the UI SHALL ask whether to remove its effects from existing transactions. If chosen, the system SHALL remove that rule's contributions and delete the rule. If declined, existing effective tag assignments SHALL remain and the rule SHALL be deleted; retained assignments SHALL no longer depend on the deleted rule. In both cases, manual assignments and other rules' contributions SHALL remain. A failed cleanup SHALL leave the rule and existing assignments unchanged.

#### Scenario: Delete and remove prior effects
- **WHEN** a user deletes a rule and chooses cleanup
- **THEN** only that rule's contributions are removed and the rule stops applying to new imports

#### Scenario: Delete and retain prior effects
- **WHEN** a user deletes a rule and declines cleanup
- **THEN** current tag assignments remain visible and the rule stops applying to new imports

### Requirement: Rule operations preserve ownership and independent tag sources
Rule evaluation and retroactive operations SHALL use only the authenticated owner's rules, tags, and transactions. A transaction's effective tag set SHALL contain each tag once even if manual edits or multiple rules contribute it. Removing a rule contribution SHALL NOT remove a tag that has another contribution. Rule changes SHALL NOT create transaction category versions or put rule metadata in historical snapshots.

#### Scenario: Shared tag from two rules
- **WHEN** two rules contribute the same tag to one transaction and one rule is deleted with cleanup
- **THEN** the tag remains assigned through the other rule

#### Scenario: Cross-owner isolation
- **WHEN** one user applies or reconciles a rule
- **THEN** no rule or transaction assignment belonging to another user changes

## Related implementation and history

- Documentation: [documentation map](../../../docs/README.md#subsystem-map), [Gateway README](../../../api-gateway/README.md#personal-tag-rules), [UI README](../../../ui/README.md), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [rule service](../../../api-gateway/src/main/java/com/finanzbuddy/api/document/service/TransactionTagRuleService.java), [rule repository](../../../api-gateway/src/main/java/com/finanzbuddy/api/repository/TransactionTagRuleRepository.java), [rules page](../../../ui/src/pages/RulesPage.tsx)
- History: [add transaction tag rules](../../changes/archive/2026-11-03-add-transaction-tag-rules/)
