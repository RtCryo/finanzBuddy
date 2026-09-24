# Spec Delta

## Purpose

Extracts structured transaction data from CSV, PDF, and XML financial documents using format-specific parsers.

## ADDED Requirements

### Requirement: CSV parser extracts transactions
The system SHALL parse CSV bank statements and extract booking date, value date, amount, currency, counterparty, description, and category.

#### Scenario: Valid CSV statement
- **WHEN** a CSV file in the expected bank statement format is parsed
- **THEN** a list of transaction records is produced with all required fields populated

#### Scenario: Malformed CSV
- **WHEN** a CSV file cannot be parsed
- **THEN** parsing fails with a clear error and no partial data is persisted

### Requirement: PDF parser extracts transactions
The system SHALL parse text-based PDF financial documents and extract transaction data.

#### Scenario: Text-based PDF statement
- **WHEN** a PDF containing selectable transaction text is parsed
- **THEN** a list of transaction records is produced

#### Scenario: Scanned PDF
- **WHEN** a PDF contains only images
- **THEN** parsing fails or returns an error indicating OCR is required

### Requirement: XML parser extracts transactions
The system SHALL parse XML financial documents and extract transaction data.

#### Scenario: Valid XML statement
- **WHEN** an XML file in a supported schema is parsed
- **THEN** a list of transaction records is produced

### Requirement: Locale-aware date parsing
The system SHALL parse dates according to the locale associated with the user or document.

#### Scenario: German date format
- **WHEN** a CSV contains dates in `dd.MM.yy` format for a German locale
- **THEN** dates are parsed correctly into UTC/local date objects
