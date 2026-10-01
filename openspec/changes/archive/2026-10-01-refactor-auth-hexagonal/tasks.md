## 1. Scaffolding & dependencies

- [x] 1.1 Create package tree under `auth/`: `domain/model`, `domain/exception`, `port/in`, `port/out`, `adapter/in/web`, `adapter/out/security`, `adapter/out/persistence`
- [x] 1.2 Add ArchUnit test-scoped dependency (if not already added by a prior slice)
- [x] 1.3 Confirm `jakarta.inject`/`jakarta.validation` API available to the domain

## 2. Domain model & exceptions

- [x] 2.1 Add framework-free domain types: `Registration`, `Credentials`, `AuthenticatedUser`
- [x] 2.2 Add domain exceptions `EmailAlreadyRegisteredException`, `InvalidCredentialsException`

## 3. Ports

- [x] 3.1 Define inbound ports `RegisterUserUseCase`, `LoginUseCase` (domain-typed signatures)
- [x] 3.2 Define outbound ports `UserRepositoryPort`, `PasswordHasherPort`, `CredentialAuthenticatorPort`, `AccessTokenIssuerPort`

## 4. Outbound adapters

- [x] 4.1 Implement `JpaUserRepositoryAdapter` over the Spring Data `UserRepository` with domain↔entity mapping; translate `DataIntegrityViolationException` → `EmailAlreadyRegisteredException`
- [x] 4.2 Implement `SpringPasswordHasherAdapter` (`PasswordEncoder`) and `SpringCredentialAuthenticatorAdapter` (`AuthenticationManager`)
- [x] 4.3 Keep `DatabaseUserDetailsService` as a Spring Security adapter sourced from `UserRepositoryPort`
- [x] 4.4 Implement `AccessTokenIssuerPort` adapter delegating to the security slice `AccessTokenPort` (or temporarily wrapping existing `JwtService`)

## 5. Domain service

- [x] 5.1 Rewrite `AuthService` to implement both inbound ports, depending only on outbound ports + domain types
- [x] 5.2 Replace `ResponseStatusException`/`BadCredentialsException`/`DataIntegrityViolationException` with domain exceptions
- [x] 5.3 Replace `@Transactional` with `TransactionRunnerPort` for the register flow, mirroring current boundaries
- [x] 5.4 Verify no forbidden imports remain in the auth domain layer

## 6. Inbound web adapter

- [x] 6.1 Re-point `AuthController` to inbound ports; map DTOs ↔ domain types; keep `ByteLength` validation in the web adapter
- [x] 6.2 Extend `ApiExceptionHandler` to map auth domain exceptions → current HTTP statuses, preserving response bodies

## 7. Tests & enforcement

- [x] 7.1 Re-point existing auth tests to new packages; confirm contract assertions pass unchanged
- [x] 7.2 Add domain unit tests for register/login using fake ports (no Spring context)
- [x] 7.3 Add ArchUnit tests for domain isolation, inbound-port usage, Spring Security/Data confined to adapters, and domain-exception rule
- [x] 7.4 Run the full api-gateway test suite + architecture suite; fix violations

## 8. Cleanup & docs

- [x] 8.1 Delete dead coupled code
- [x] 8.2 Update `api-gateway/README.md` and `docs/ARCHITECTURE.md` for the auth slice structure
