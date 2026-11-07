# transaction-deduplication Specification

## Purpose
Prevents the same bank transaction from being stored twice for one user when a statement is uploaded again or when exported statements overlap, while keeping legitimately identical payments.

## Requirements

### Requirement: Already stored transactions are skipped
The system SHALL NOT save an incoming transaction when the same owner already has an identical stored transaction. Two transactions are identical when booking date, value date, amount, currency, counterparty, and description are equal after trimming surrounding whitespace, with a missing value equal to an empty value. Transactions of different owners SHALL never be treated as identical, and category and tag edits SHALL NOT affect identity.

#### Scenario: Same file uploaded again
- **WHEN** a user uploads a CSV statement whose transactions were all stored earlier from another document of the same user
- **THEN** no transaction is saved for the new document and every line is counted as a skipped duplicate

#### Scenario: Overlapping statements
- **WHEN** a user uploads a statement covering August to December after one covering August to November
- **THEN** only the transactions that are not yet stored are saved and the August to November lines are skipped

#### Scenario: Different owners
- **WHEN** two users upload the same statement
- **THEN** each user keeps all of its own transactions and neither upload skips lines because of the other

#### Scenario: Edited transaction still matches
- **WHEN** a user changed the category or tags of a stored transaction and then uploads the same statement again
- **THEN** the corresponding line is skipped as a duplicate and the edited transaction is unchanged

### Requirement: Identical lines are matched by quantity
The system SHALL compare the number of identical transactions in an upload with the number already stored for the owner and SHALL save only the surplus. Identical lines inside one file SHALL be kept when the owner has none of them yet.

#### Scenario: Two equal payments on one day
- **WHEN** a first upload contains two identical coffee payments on the same day and the owner has none stored
- **THEN** both are saved

#### Scenario: Re-upload of two equal payments
- **WHEN** the same file is uploaded again after both payments were saved
- **THEN** both lines are skipped and no payment is saved a third time

#### Scenario: Surplus occurrence
- **WHEN** the owner has one stored transaction and the new upload contains three identical ones
- **THEN** two are saved and one is skipped

### Requirement: A document reports its deduplication outcome
The system SHALL record for every successfully parsed document how many transactions were saved and how many were skipped as duplicates, and these numbers SHALL add up to the number of transactions parsed from the file. A document in which every line is a duplicate SHALL end in status `PROCESSED`, not `FAILED`. A file with no parseable transactions SHALL still fail as before.

#### Scenario: Partial duplicates
- **WHEN** a parsed file has 328 lines and 300 of them are already stored
- **THEN** 28 transactions are saved, 300 are reported as skipped, and the document is `PROCESSED`

#### Scenario: All duplicates
- **WHEN** every line of a parsed file is already stored
- **THEN** the document is `PROCESSED` with 0 saved and all lines skipped, and the owner's transaction count is unchanged

#### Scenario: Empty file
- **WHEN** a file contains no transactions
- **THEN** the document ends in `FAILED` as before and no counts are reported

### Requirement: Deduplication is safe under repetition and concurrency
The system SHALL give the same result when a document event is redelivered and SHALL NOT save duplicates when the same owner's uploads are processed at the same time. A redelivered event SHALL NOT change the saved and skipped numbers already recorded for the document.

#### Scenario: Redelivered event
- **WHEN** the parser receives the same document event again after the document was processed
- **THEN** nothing is saved again and the recorded saved and skipped numbers stay the same

#### Scenario: Concurrent identical uploads
- **WHEN** a user uploads the same file twice at nearly the same time
- **THEN** after both are processed the owner has the transactions of one copy only

### Requirement: Existing stored data is not changed
The system SHALL NOT delete, merge, or rewrite transactions stored before this behavior exists. Documents processed before this behavior exists SHALL report no saved and skipped numbers.

#### Scenario: Historic duplicates
- **WHEN** an owner already has duplicate transactions from earlier uploads
- **THEN** they remain, and identical new lines are skipped against the quantity already stored

#### Scenario: Historic document
- **WHEN** a client reads a document that was processed before this behavior exists
- **THEN** its saved and skipped numbers are absent rather than zero

## Related implementation and history

- Documentation: [subsystem map](../../../docs/README.md#subsystem-map), [Parser README](../../../parser-service/README.md#duplicate-handling), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`TransactionPersistenceService.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/service/TransactionPersistenceService.java), [`OwnerLock.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/service/OwnerLock.java), [`StoredTransactionKey.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/repository/StoredTransactionKey.java)
- History: [skip duplicate transactions](../../changes/archive/2026-11-07-skip-duplicate-transactions/)
