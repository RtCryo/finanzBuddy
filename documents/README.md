# Test Documents

This folder is intended for local test documents such as bank statements, PDF reports, and XML exports.

**Do not commit files in this folder.** They often contain private financial data.

Only this README and the `.gitignore` are tracked by Git. All other files placed here are ignored.

## Usage

Place your sample documents here for local parsing and integration tests, e.g.:

- `sample-statement.csv`
- `bank-export.xml`
- `invoice.pdf`

The parser service can read these files from this folder during local development.
