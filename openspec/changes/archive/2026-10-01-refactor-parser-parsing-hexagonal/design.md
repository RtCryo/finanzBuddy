## Context

The parser strategies are already partially hexagonal: `DocumentParser` is a port, `DocumentParserRouter` selects by type, and `CsvAmountParser`/`CsvDateParser` are pure framework-free Java. The gap is `CsvTransactionParser`, which fuses domain extraction/normalization with OpenCSV (`CSVReader`/`CSVReaderBuilder`/`CSVParserBuilder`), commons-io (`BOMInputStream`), Jackson (`ObjectMapper`), and Spring (`@Component`, `@Value`). `PdfTransactionParser`/`XmlTransactionParser` are MVP stubs that will later gain PDFBox/Jackson-XML and repeat the coupling. The `parse` methods also return the JPA `Transaction` entity.

This mirrors the api-gateway hexagonal refactor and applies the pattern from [refactor-document-hexagonal] to the parsing slice. The processing slice ([refactor-parser-processing-hexagonal]) consumes the parsing port.

## Goals / Non-Goals

**Goals:**
- The CSV row→transaction mapping and value normalization live in a framework-free domain.
- Format libraries (OpenCSV now; PDFBox/Jackson-XML later) are confined to format adapters behind `DocumentParser`.
- The port returns domain transactions, not the JPA entity.
- Behavior preserved (valid/malformed CSV, locale dates, PDF/XML unsupported errors); existing tests pass; ArchUnit enforces boundaries.

**Non-Goals:**
- Not implementing PDF/XML extraction (separate feature changes beyond the MVP) — only relocating the stubs as adapters.
- No change to the processing orchestration/consumer (its own slice).
- No change to parsed fields, error semantics, or `parser.formats.csv-locale`.

## Decisions

### D1: Package structure under `com.finanzbuddy.parser.parsing`
```
parsing/
  domain/
    DocumentParserRouter        (framework-free selection)
    normalization/  CsvRowMapper (row -> domain Transaction), CsvAmountParser,
                    CsvDateParser (moved unchanged — already pure)
    model/          domain Transaction (shared with processing slice)
  port/
    DocumentParser  (supports(type) + parse(...) -> domain transactions)
  adapter/
    csv/   CsvParserAdapter (OpenCSV, commons-io, Jackson; reads rows, delegates to CsvRowMapper)
    pdf/   PdfParserAdapter (MVP unsupported; future PDFBox home)
    xml/   XmlParserAdapter (MVP unsupported; future Jackson-XML home)
```
Rationale: keep the existing port + router; split only `CsvTransactionParser` into an adapter (file reading) + domain mapper (field extraction/normalization). `CsvAmountParser`/`CsvDateParser` move as-is since they are already framework-free.

### D2: Split "read rows" from "map/normalize rows"
`CsvParserAdapter` owns OpenCSV reading (BOM handling, CSV dialect, header handling) and produces a list of raw string rows/records; `CsvRowMapper` (domain) turns each row into a domain `Transaction` using `CsvAmountParser`/`CsvDateParser`. Rationale: the library-specific reading is the technical detail; the field mapping and normalization are the business rules. Swapping OpenCSV touches only the adapter. Trade-off: a defined row/record shape crossing the adapter→domain boundary — a simple `List<String>`-based record, no library types.

### D3: Port returns domain transactions
`DocumentParser.parse` returns the domain `Transaction` type (shared with the processing slice). The JPA `Transaction` entity is mapped in the processing persistence adapter. Rationale: keeps `jakarta.persistence` out of parsing; aligns both parser-service slices on one domain type.

### D4: Jackson usage in CSV re-examined
`CsvTransactionParser` currently imports Jackson (`ObjectMapper`) — likely for a JSON column or config. It stays in `CsvParserAdapter` (a library detail), not the domain. Rationale: if Jackson is only for reading embedded JSON values, that is parsing I/O; the mapped result handed to the domain is plain.

### D5: PDF/XML stubs become adapters, behavior unchanged
`PdfParserAdapter`/`XmlParserAdapter` keep throwing the explicit unsupported-operation error and are the designated homes for future PDFBox/Jackson-XML. Rationale: preserves the MVP contract while placing the eventual library code on the correct side of the boundary.

### D6: Enforcement with ArchUnit (test scope)
Encode: normalization domain may not import OpenCSV/PDFBox/Jackson/commons-io/Spring; format libraries only in `adapter/*`; `DocumentParser.parse` returns the domain type, not a persistence entity.

## Risks / Trade-offs

- **CSV dialect/BOM/locale edge cases could regress during the read/map split** → Move the reading logic verbatim into the adapter; keep `CsvAmountParser`/`CsvDateParser` untouched; rely on existing CSV/date/amount tests plus new domain mapper tests before merging.
- **Jackson's real purpose in CSV must be understood before relocating** → Confirm what `ObjectMapper` parses in `CsvTransactionParser`; keep it in the adapter regardless, but verify the domain receives plain values.
- **Shared domain `Transaction` type coupling two slices** → Intentional; define it once and let both parser-service slices use it.
- **Low overall risk** — the port/router already exist and two of three helpers are already pure.

## Migration Plan

1. Introduce the `parsing` packages, the domain `Transaction` type, and the `CsvRowMapper` domain class; move `CsvAmountParser`/`CsvDateParser` unchanged.
2. Create `CsvParserAdapter` holding OpenCSV/commons-io/Jackson, delegating mapping/normalization to the domain.
3. Relocate the PDF/XML stubs as adapters, unchanged behavior.
4. Change `DocumentParser.parse` to return the domain transaction type; keep `DocumentParserRouter` framework-free.
5. Re-point existing parser tests; add framework-free mapper/normalization unit tests and ArchUnit tests.
6. Delete the old coupled `CsvTransactionParser`.

Rollback: additive until step 6, isolated to the parsing package tree; revert the PR. No data migration.

## Open Questions

- Is `ObjectMapper` in `CsvTransactionParser` used for an embedded-JSON column or for configuration? Confirm before step 2 (changes whether it is read-time I/O or removable).
- Should the row/record boundary type be a positional `List<String>` or a header-keyed map? Default: whatever matches the current OpenCSV usage most directly, to minimize behavior change.
