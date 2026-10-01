## 1. Scaffolding & dependencies

- [x] 1.1 Create `parsing/` slice packages: `domain/model`, `domain/normalization`, `port`, `adapter/csv`, `adapter/pdf`, `adapter/xml`
- [x] 1.2 Add ArchUnit test-scoped dependency (if not already added by the processing slice)
- [x] 1.3 Confirm `jakarta.inject`/`jakarta.validation` API available to the domain

## 2. Domain model & normalization

- [x] 2.1 Define the domain `Transaction` type (shared with the processing slice)
- [x] 2.2 Move `CsvAmountParser` and `CsvDateParser` into `domain/normalization` unchanged (already framework-free)
- [x] 2.3 Extract `CsvRowMapper` (domain): row/record → domain `Transaction` using the amount/date parsers

## 3. Port & router

- [x] 3.1 Keep `DocumentParser` as the port; change `parse(...)` to return the domain `Transaction` type
- [x] 3.2 Keep `DocumentParserRouter` framework-free; verify no library imports

## 4. Format adapters

- [x] 4.1 Confirm the purpose of `ObjectMapper` in `CsvTransactionParser` before relocating (embedded-JSON column vs config)
- [x] 4.2 Implement `CsvParserAdapter` (`DocumentParser` for CSV): OpenCSV/commons-io/Jackson read rows, delegate to `CsvRowMapper`; preserve BOM/dialect/locale handling verbatim
- [x] 4.3 Relocate `PdfParserAdapter` and `XmlParserAdapter`, keeping the explicit unsupported-operation behavior

## 5. Tests & enforcement

- [x] 5.1 Re-point existing CSV/date/amount parser tests to new packages; confirm fields and error semantics unchanged
- [x] 5.2 Add framework-free unit tests for `CsvRowMapper` and the normalization helpers (no OpenCSV/Spring)
- [x] 5.3 Add ArchUnit tests for normalization domain isolation, `DocumentParser` returning the domain type, format libraries confined to adapters, router framework-free
- [x] 5.4 Run the full parser-service test suite + architecture suite; fix violations

## 6. Cleanup & docs

- [x] 6.1 Delete the old coupled `CsvTransactionParser`
- [x] 6.2 Update `parser-service/README.md` and `docs/ARCHITECTURE.md` for the parsing slice structure
