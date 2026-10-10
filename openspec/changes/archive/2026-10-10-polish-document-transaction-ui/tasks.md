# Tasks

## 1. Transaction history API

- [x] 1.1 Change transaction-history date bounds to use Value date and add validated, allowlisted sort-field and direction parameters with stable null and tie-break ordering; verify repository tests cover inclusive bounds, supported sorts, and paginated ordering.
- [x] 1.2 Update API controller/service tests for default sorting, explicit sorting, and invalid sort input; verify the API Gateway test suite passes.

## 2. Transaction history UI

- [x] 2.1 Pass sort parameters through the UI API client and add accessible sortable column headers, active direction state, and first-page reset behavior; verify component tests assert sort requests and pagination reset.
- [x] 2.2 Show Value date instead of Booking date in transaction history and label the From/To filter as Value date; verify page tests cover the displayed column and date-range request behavior.
- [x] 2.3 Expand filters across the card, preserve wrapping on narrow screens, and style positive/negative amounts with soft semantic palette colors while keeping neutral values neutral; verify UI tests and wide/narrow viewport behavior.

## 3. Workspace and documents UI

- [x] 3.1 Remove the Upload a file sidebar link and duplicate sidebar privacy statement while retaining the footer reassurance and Documents-page upload action; verify shell tests cover navigation links, upload access, and one reassurance statement.
- [x] 3.2 Remove the manual Refresh control and its dedicated state from the documents list while retaining automatic polling; verify documents-page tests confirm there is no manual control and status updates still refresh automatically.
- [x] 3.3 Remove the manual Refresh control from document details while retaining polling during processing and the initial fetch on route entry; verify detail-page tests cover processing updates, no manual control, and reopening the page.

## 4. Integration verification

- [x] 4.1 Run the relevant API Gateway and UI test suites and production builds together; verify sorting, Value date filtering, responsive transaction layout, both document polling flows, and shell navigation work across the integrated change.
