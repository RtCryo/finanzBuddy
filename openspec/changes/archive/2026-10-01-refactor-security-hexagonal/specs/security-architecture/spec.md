## ADDED Requirements

### Requirement: Security domain is free of framework dependencies
The security domain layer (token and api-key decisions) SHALL depend only on `java.*`, project domain types, and `jakarta.inject.*`/`jakarta.validation.*`. It SHALL NOT import `io.jsonwebtoken.*`, `org.springframework.*`, `jakarta.servlet.*`, or any web/servlet type.

#### Scenario: Security domain class imports a forbidden package
- **WHEN** a class in the security domain package imports `io.jsonwebtoken.*`, `org.springframework.*`, or `jakarta.servlet.*`
- **THEN** the architecture test suite fails the build and names the offending class and import

#### Scenario: Security domain class uses only allowed packages
- **WHEN** a security domain class imports only `java.*`, project domain types, `jakarta.inject.*`, or `jakarta.validation.*`
- **THEN** the architecture test suite passes for that class

### Requirement: Authentication decisions are exposed through inbound ports
Resolving a principal from a bearer token and verifying an internal api-key SHALL each be defined as an inbound port (`AuthenticateRequestUseCase`, `AuthenticateInternalUseCase`) implemented by the domain. The servlet filters SHALL depend on these ports.

#### Scenario: Filter depends on an inbound port
- **WHEN** `JwtAuthenticationFilter` or `InternalApiKeyAuthFilter` processes a request
- **THEN** it resolves authentication through an inbound port, not by embedding token/api-key logic in the filter

### Requirement: Token and lookup concerns are reached only through outbound ports
The security domain SHALL perform token issue/parse/validate, constant-time api-key comparison, and principal lookup exclusively through outbound ports (`AccessTokenPort`, `ApiKeyPort`, `PrincipalLookupPort`). The `io.jsonwebtoken` library SHALL appear only in the `AccessTokenPort` adapter.

#### Scenario: Domain references the JWT library directly
- **WHEN** a security domain class imports `io.jsonwebtoken.*`
- **THEN** the architecture test suite fails the build

#### Scenario: Token adapter implements the outbound port
- **WHEN** the JWT adapter is defined
- **THEN** it implements `AccessTokenPort` and is the only place `io.jsonwebtoken` is imported

### Requirement: Token failures are domain results, mapped at the filter edge
The security domain SHALL represent token validation failures with domain results or exceptions (for example `InvalidTokenException`, `ExpiredTokenException`) rather than leaking `JwtException`/`ExpiredJwtException`. The inbound servlet adapter SHALL translate them into the current 401 behavior.

#### Scenario: Expired bearer token
- **WHEN** a request presents an expired token
- **THEN** the domain reports an expired-token result and the filter responds with the current 401 behavior, with no `io.jsonwebtoken` exception type present in the domain layer

### Requirement: Observable authentication behavior is preserved
The refactor SHALL NOT change observable behavior. Bearer-token authentication, internal api-key authentication, 401 responses, the `SecurityContext` population, and the token format SHALL remain identical.

#### Scenario: Existing security/filter tests after refactor
- **WHEN** the pre-refactor security and filter tests run against the refactored code
- **THEN** they pass without modification to the asserted behavior

### Requirement: Security architecture rules are enforced automatically
The build SHALL include ArchUnit (or equivalent) tests verifying the security domain-isolation, inbound-port, outbound-port, and domain-result rules, running in the standard test phase.

#### Scenario: Security architecture test runs in CI
- **WHEN** the test phase executes locally or in CI
- **THEN** the security architecture suite runs and any violation fails the build
