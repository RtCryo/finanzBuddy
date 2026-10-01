## 1. Scaffolding & dependencies

- [x] 1.1 Create package tree under `security/`: `domain/model`, `domain/exception`, `port/in`, `port/out`, `adapter/in/web`, `adapter/out/token`
- [x] 1.2 Add ArchUnit test-scoped dependency (if not already added by a prior slice)
- [x] 1.3 Confirm `jakarta.inject`/`jakarta.validation` API available to the domain

## 2. Domain model & exceptions

- [x] 2.1 Add framework-free domain types `Principal`, `TokenValidationResult`
- [x] 2.2 Add domain exceptions `InvalidTokenException`, `ExpiredTokenException`

## 3. Ports

- [x] 3.1 Define inbound ports `AuthenticateRequestUseCase`, `AuthenticateInternalUseCase`
- [x] 3.2 Define outbound ports `AccessTokenPort` (issue/parse/validate), `ApiKeyPort` (constant-time compare), `PrincipalLookupPort`

## 4. Outbound adapters

- [x] 4.1 Implement `JwtAccessTokenAdapter` (`AccessTokenPort`) from `JwtService`, holding the only `io.jsonwebtoken` imports; translate library exceptions to domain results
- [x] 4.2 Implement `ApiKeyPort` adapter with constant-time comparison, preserving current matching exactly
- [x] 4.3 Implement `PrincipalLookupPort` backed by the shared user source (`UserRepositoryPort`)

## 5. Domain authenticators

- [x] 5.1 Implement `RequestAuthenticator` (`AuthenticateRequestUseCase`): validate token via `AccessTokenPort`, resolve principal via `PrincipalLookupPort`
- [x] 5.2 Implement `InternalAuthenticator` (`AuthenticateInternalUseCase`): verify api-key via `ApiKeyPort`
- [x] 5.3 Verify no forbidden imports remain in the security domain layer

## 6. Inbound filter adapters

- [x] 6.1 Slim `JwtAuthenticationFilter` to call `AuthenticateRequestUseCase` and populate `SecurityContextHolder`; map failures to current 401
- [x] 6.2 Slim `InternalApiKeyAuthFilter` to call `AuthenticateInternalUseCase` and populate the authentication token
- [x] 6.3 Update `SecurityConfig` to wire the new beans without behavior change

## 7. Tests & enforcement

- [x] 7.1 Re-point existing security/filter tests to new packages; confirm behavior unchanged
- [x] 7.2 Add domain unit tests for token-valid/invalid/expired/wrong-key and api-key match/mismatch using fake ports (no servlet/Spring)
- [x] 7.3 Add ArchUnit tests for domain isolation, filter→inbound-port usage, `io.jsonwebtoken` confined to the token adapter
- [x] 7.4 Run the full api-gateway test suite + architecture suite; fix violations

## 8. Cleanup & docs

- [x] 8.1 Delete `JwtService` and dead filter glue
- [x] 8.2 Update `api-gateway/README.md` and `docs/ARCHITECTURE.md` for the security slice structure
