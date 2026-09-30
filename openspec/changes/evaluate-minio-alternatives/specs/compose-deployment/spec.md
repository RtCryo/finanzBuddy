## ADDED Requirements

### Requirement: Object storage uses a maintained, supported backend
The Compose deployment's object storage SHALL be provided by a maintained, upstream-supported, S3-compatible backend whose image is publicly obtainable or reproducibly built from source. It SHALL NOT depend long-term on a frozen or unmaintained image that no longer receives updates or security patches. The choice of backend SHALL be recorded with its rationale.

#### Scenario: Object-storage backend is selected
- **WHEN** the object-storage backend for the stack is chosen or reaffirmed
- **THEN** it is an S3-compatible backend that is either published as a currently-maintained image or reproducibly built from source, and the decision and its rationale are recorded in the repository

#### Scenario: Backend depends on an unmaintained image
- **WHEN** the object-storage service would otherwise rely on a frozen or end-of-life image that no longer receives updates
- **THEN** that reliance is treated as a defect to resolve, not an acceptable long-term state, and is either replaced or explicitly recorded as a time-bound stopgap with a follow-up

### Requirement: Object-storage backend changes preserve document behavior and are verified
When the object-storage backend is changed (replaced, upgraded, or moved to a managed endpoint), the change SHALL preserve document upload and download behavior through the existing S3 client, and SHALL be verified against a running stack before being considered complete.

#### Scenario: Backend is replaced
- **WHEN** the `minio` object-storage service is repointed to a different S3-compatible backend or a managed endpoint
- **THEN** the API Gateway and parser service reach it through their existing `MINIO_*` endpoint/credentials configuration without application source changes, and the previously required bucket is available

#### Scenario: Stack is verified after a backend change
- **WHEN** the object-storage backend has been changed
- **THEN** `docker compose config` is valid, all services reach a healthy state, and an end-to-end document upload, parse, and download round-trip succeeds against the new backend before the change is considered done
