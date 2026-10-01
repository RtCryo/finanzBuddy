## Why

The parser strategies are already partially hexagonal — a `DocumentParser` port and a `DocumentParserRouter` select a parser by type, and the normalization helpers `CsvAmountParser`/`CsvDateParser` are pure framework-free Java. But `CsvTransactionParser` fuses the domain extraction/normalization rules with library and framework concerns: it imports OpenCSV (`CSVReader`/`CSVReaderBuilder`/`CSVParserBuilder`), Jackson (`ObjectMapper`), commons-io (`BOMInputStream`), and Spring (`@Component`, `@Value`). Swapping the CSV library — or testing the field-mapping/normalization rules — forces changes inside the parsing logic. `PdfTransactionParser`/`XmlTransactionParser` are MVP stubs that will eventually gain PDFBox/Jackson-XML, repeating the same coupling.

This mirrors the api-gateway hexagonal refactor and applies the pattern from [refactor-document-hexagonal] to the parser-service's parsing slice. The processing slice ([refactor-parser-processing-hexagonal]) consumes the parsing port, so this slice should land first or alongside it.

## What Changes

- Keep `DocumentParser` as the domain **port** (`supports(type)` + `parse(...) -> domain transactions`) and keep `DocumentParserRouter` as framework-free selection.
- Make the parsing **domain** framework-free: the field mapping and normalization rules (column→field mapping, `CsvAmountParser`, `CsvDateParser`, locale-aware date/amount handling) depend only on `java.*`, domain types, and (pragmatically) `jakarta.inject`/`jakarta.validation`.
- Move library/framework code into **format adapters** that implement `DocumentParser`:
  - `CsvParserAdapter` holds the only OpenCSV / commons-io / Jackson imports — it reads the file into rows and delegates row→transaction mapping and normalization to the domain.
  - `PdfParserAdapter` / `XmlParserAdapter` keep the MVP unsupported-operation behavior and are the designated homes for future PDFBox / Jackson-XML code.
- Return **domain transactions** from the port rather than the JPA `Transaction` entity; the processing slice's persistence adapter maps domain→entity.
- Add architecture (ArchUnit) tests enforcing parsing domain-isolation and the port boundary (OpenCSV/PDFBox/Jackson only in adapters, not in the normalization domain).
- **No external behavior change**: valid-CSV parsing, malformed-CSV failure, locale-aware date parsing, and the PDF/XML unsupported-operation errors are identical.

## Capabilities

### New Capabilities
- `parser-parsing-architecture`: Structural requirements for the parsing slice — framework-free normalization domain, `DocumentParser` port boundary, format libraries confined to adapters, and automated enforcement. Verifiable constraints, not functional behavior.

### Modified Capabilities
<!-- None. The document-parsing and transaction-normalization functional specs and their observable behavior are unchanged. -->

## Impact

- **Code (parser-service only)**: `parser/csv/CsvTransactionParser.java` split into a framework-free CSV row→transaction mapper (domain) and a `CsvParserAdapter` (OpenCSV/commons-io/Jackson); `parser/csv/CsvAmountParser.java` and `CsvDateParser.java` move into the normalization domain unchanged; `parser/pdf/*`, `parser/xml/*` become format adapters; `DocumentParser`/`DocumentParserRouter` stay as the port + selection.
- **Cross-slice**: exposes the parsing port consumed by [refactor-parser-processing-hexagonal]; the domain `Transaction` type is shared with the processing persistence adapter.
- **Contracts**: unchanged — same parsed fields, same error semantics, same `parser.formats.csv-locale` configuration.
- **Out of scope**: the processing orchestration/consumer (own change); actually implementing PDF/XML extraction (separate feature changes beyond the MVP).
- **Tests**: existing CSV/date/amount parser tests re-pointed; new framework-free normalization unit tests and ArchUnit tests added.
