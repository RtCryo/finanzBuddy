# Auth Architecture Specification

## Purpose

Defines the hexagonal-architecture rules for the auth capability so that the auth domain stays free of framework dependencies, exposes use cases through inbound ports, reaches security and persistence only through outbound ports, signals failures with domain exceptions, preserves observable behavior, and enforces these rules automatically in the build.

## Requirements

### Requirement: Auth domain is free of framework dependencies
The auth domain layer SHALL depend only on `java.*`, project domain types, and the packages `jakarta.inject.*` and `jakarta.validation.*`. It SHALL NOT import `org.springframework.*` (including Spring Security), `jakarta.persistence.*`, `com.fasterxml.*`, or any web type.

#### Scenario: Auth domain class imports a forbidden framework package
- **WHEN** a class in the auth domain package imports `org.springframework.*`, `jakarta.persistence.*`, `com.fasterxml.*`, or a web type
- **THEN** the architecture test suite fails the build and names the offending class and import

#### Scenario: Auth domain class uses only allowed packages
- **WHEN** an auth domain class imports only `java.*`, project domain types, `jakarta.inject.*`, or `jakarta.validation.*`
- **THEN** the architecture test suite passes for that class

### Requirement: Auth use cases are exposed through inbound ports
Registration and login SHALL each be defined as an inbound port (`RegisterUserUseCase`, `LoginUseCase`) implemented by the domain. `AuthController` SHALL depend on these ports, never on a concrete domain class.

#### Scenario: Controller depends on an inbound auth port
- **WHEN** `AuthController` handles `/auth/register` or `/auth/login`
- **THEN** it invokes the use case through its inbound port interface, not a concrete domain service type

### Requirement: Security and persistence are reached only through outbound ports
The auth domain SHALL access user persistence, password hashing, credential verification, and token issuance exclusively through outbound ports (`UserRepositoryPort`, `PasswordHasherPort`, `CredentialAuthenticatorPort`, `AccessTokenIssuerPort`). Spring Security types (`PasswordEncoder`, `AuthenticationManager`, `UserDetails`) and Spring Data repositories SHALL appear only in adapters implementing those ports.

#### Scenario: Domain references a Spring Security type directly
- **WHEN** an auth domain class references `PasswordEncoder`, `AuthenticationManager`, `UserDetails`, or a Spring Data repository
- **THEN** the architecture test suite fails the build

#### Scenario: Adapter implements an outbound auth port
- **WHEN** a security or persistence adapter is defined
- **THEN** it implements the corresponding outbound port and is the only place the Spring Security / Spring Data type is imported

### Requirement: Auth domain signals failures with domain exceptions
The auth domain SHALL raise domain exceptions (for example `EmailAlreadyRegisteredException`, `InvalidCredentialsException`) rather than `ResponseStatusException`, `BadCredentialsException`, or `DataIntegrityViolationException`. The web adapter SHALL translate them into HTTP responses.

#### Scenario: Duplicate email during registration
- **WHEN** registration is attempted for an email that already exists
- **THEN** the domain throws a domain exception and the web adapter maps it to the current HTTP status, with no framework exception type present in the domain layer

#### Scenario: Wrong password during login
- **WHEN** login is attempted with invalid credentials
- **THEN** the domain throws a domain exception and the web adapter maps it to HTTP 401

### Requirement: Observable auth behavior is preserved
The refactor SHALL NOT change the externally observable behavior of the auth capability. `/auth/register` and `/auth/login` paths, request/response payloads, HTTP status codes, password hashing scheme, and issued token format SHALL remain identical.

#### Scenario: Existing auth API contract after refactor
- **WHEN** the pre-refactor auth tests run against the refactored code
- **THEN** they pass without modification to the asserted request/response contracts

### Requirement: Auth architecture rules are enforced automatically
The build SHALL include ArchUnit (or equivalent) tests that verify the auth domain-isolation, inbound-port, outbound-port, and domain-exception rules, running as part of the standard test phase.

#### Scenario: Auth architecture test runs in CI
- **WHEN** the test phase executes locally or in CI
- **THEN** the auth architecture suite runs and any violation fails the build
