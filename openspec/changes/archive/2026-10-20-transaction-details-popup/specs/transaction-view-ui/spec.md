# Spec Delta

## ADDED Requirements

### Requirement: Users can open transaction details from transaction lists
The system SHALL let users open a popup with the selected transaction's available normalized fields from both the transaction-history list and a processed document's transaction list. The popup SHALL show booking date, value date, amount, currency, counterparty, description, and category, representing unavailable values consistently with the corresponding list. Opening and closing the popup SHALL preserve the current list, filters, sorting, pagination, and scroll position.

#### Scenario: Open transaction details from history
- **WHEN** the user activates a transaction in transaction history
- **THEN** a popup opens for that transaction and displays its booking date, value date, amount, currency, counterparty, description, and category

#### Scenario: Open transaction details from a processed document
- **WHEN** the user activates a transaction in a processed document's transaction list
- **THEN** a popup opens for that transaction and displays its booking date, value date, amount, currency, counterparty, description, and category

#### Scenario: Close transaction details and continue browsing
- **WHEN** the user closes the popup using its close control or the Escape key
- **THEN** the popup closes, focus returns to the transaction that opened it, and the current list state and scroll position are preserved

#### Scenario: Open transaction details using the keyboard
- **WHEN** the user navigates to a transaction in either list using the keyboard and activates it
- **THEN** the same transaction-details popup opens with an accessible name and keyboard-operable close behavior
