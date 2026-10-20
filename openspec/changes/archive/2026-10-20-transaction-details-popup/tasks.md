# Tasks

## 1. Shared Transaction Details Popup

- [x] 1.1 Create a reusable native modal dialog for a selected `TransactionRecord`, showing all normalized fields with existing missing-value and date/amount formatting conventions; verify its component tests cover all fields, unavailable values, Escape dismissal, and accessible dialog naming.
- [x] 1.2 Add responsive dialog styling and an explicit close control with focus restoration to its opener; verify UI tests cover close-button dismissal and keyboard focus returning to the activating transaction.

## 2. Transaction History Integration

- [x] 2.1 Make each history transaction operable by pointer and keyboard and open the shared popup for the selected record; verify history-page tests open the correct transaction and show booking date alongside the other normalized fields.
- [x] 2.2 Verify closing the popup preserves the history filters, sort order, current page, and scroll position in transaction-history tests.

## 3. Document Transaction Integration

- [x] 3.1 Make each processed-document transaction operable by pointer and keyboard and open the shared popup for the selected record; verify document-detail tests open the correct transaction and show all normalized fields.
- [x] 3.2 Verify closing the popup returns focus to the selected transaction and preserves the existing document transaction list in document-detail tests.

## 4. Integration Verification

- [x] 4.1 Run the UI test suite with `npm test` from `ui/` and verify it passes across both transaction list contexts.
- [x] 4.2 Run `npm run build` from `ui/` and verify TypeScript compilation and the production build succeed.
