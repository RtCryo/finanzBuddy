# Parser Parsing Architecture Specification

## Purpose

Defines the hexagonal-architecture rules for the document-parsing capability so that the parsing normalization domain stays free of framework dependencies, exposes format parsing through the `DocumentParser` port selected by a framework-free router, confines format libraries (OpenCSV, commons-io, Jackson, and future PDFBox/Jackson-XML) to adapters, returns domain transaction types across the port boundary, preserves the observable parsing behavior, and enforces these rules automatically in the build.

## Requirements

### Requirement: Parsing normalization domain is free of framework dependencies
The parsing normalization domain (field mapping, amount parsing, date parsing) SHALL depend only on `java.*`, project domain types, and `jakarta.inject.*`/`jakarta.validation.*`. It SHALL NOT import `com.opencsv.*`, `org.apache.pdfbox.*`, `com.fasterxml.*`, `org.apache.commons.io.*`, or `org.springframework.*`.

#### Scenario: Normalization domain class imports a format library
- **WHEN** a class in the parsing normalization domain imports `com.opencsv.*`, `org.apache.pdfbox.*`, `com.fasterxml.*`, `org.apache.commons.io.*`, or `org.springframework.*`
- **THEN** the architecture test suite fails the build and names the offending class and import

#### Scenario: Normalization helper uses only allowed packages
- **WHEN** the amount/date normalization helpers import only `java.*` and project domain types
- **THEN** the architecture test suite passes for those classes

### Requirement: Parsing is exposed through the DocumentParser port
Format parsing SHALL be defined behind the `DocumentParser` port (`supports(type)` + `parse(...)`), selected by `DocumentParserRouter`. Each concrete format parser SHALL be an adapter implementing the port; the router SHALL remain framework-free.

#### Scenario: Router selects a format adapter
- **WHEN** a document of a given type is parsed
- **THEN** `DocumentParserRouter` selects the `DocumentParser` adapter whose `supports(type)` returns true, with no format library imported in the router

### Requirement: Format libraries are confined to adapters
OpenCSV, commons-io, Jackson, and (future) PDFBox / Jackson-XML SHALL appear only in format adapter classes. The adapter SHALL read the file into rows/records and delegate row→transaction mapping and value normalization to the domain.

#### Scenario: CSV adapter holds the only OpenCSV imports
- **WHEN** the CSV format adapter is defined
- **THEN** it is the only class importing `com.opencsv.*`/`org.apache.commons.io.*`, and it delegates field mapping and normalization to the domain

#### Scenario: Normalization domain references a format library
- **WHEN** a normalization-domain class references `com.opencsv.*` or `org.apache.pdfbox.*`
- **THEN** the architecture test suite fails the build

### Requirement: Parsing returns domain transactions
The `DocumentParser` port SHALL return domain transaction types, not the JPA `Transaction` entity. Mapping domain transactions to the persistence entity SHALL happen in the processing slice's persistence adapter.

#### Scenario: Port signature uses the domain transaction type
- **WHEN** the `DocumentParser.parse` signature is inspected
- **THEN** it returns the domain transaction type and does not reference a `jakarta.persistence` entity

### Requirement: Observable parsing behavior is preserved
The refactor SHALL NOT change observable behavior. Valid-CSV extraction (all required fields), malformed-CSV failure with no partial data, locale-aware date parsing, and the PDF/XML unsupported-operation errors SHALL remain identical.

#### Scenario: Valid CSV after refactor
- **WHEN** a CSV in the expected format is parsed
- **THEN** the same transaction records with the same fields are produced as before

#### Scenario: PDF/XML after refactor
- **WHEN** a PDF or XML document is parsed
- **THEN** the parser throws the same explicit unsupported-operation error as before

### Requirement: Parsing architecture rules are enforced automatically
The build SHALL include ArchUnit (or equivalent) tests verifying the normalization domain-isolation, the `DocumentParser` port boundary, and format-library confinement, running in the standard test phase.

#### Scenario: Parsing architecture test runs in CI
- **WHEN** the test phase executes locally or in CI
- **THEN** the parsing architecture suite runs and any violation fails the build
