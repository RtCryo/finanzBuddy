## Context

The `auth` slice is `AuthService` (register + login) and `DatabaseUserDetailsService`, plus `AuthController`, DTOs, and the `ByteLength` validation. `AuthService` imports Spring Security (`AuthenticationManager`, `UsernamePasswordAuthenticationToken`, `PasswordEncoder`, `UserDetails`), Spring DAO (`DataIntegrityViolationException`), Spring Web (`ResponseStatusException`, `HttpStatus`), and `@Transactional`. `DatabaseUserDetailsService` implements Spring Security's `UserDetailsService`. Credential rules cannot be exercised without a Spring Security context.

This is the second slice of the api-gateway hexagonal refactor, applying the pattern from [refactor-document-hexagonal]. Core-purity decision: pragmatic — domain may use `jakarta.inject`/`jakarta.validation`, not Spring.

## Goals / Non-Goals

**Goals:**
- Registration and login business rules live in a framework-free domain behind inbound ports.
- Password hashing, credential verification, token issuance, and user persistence are outbound ports with single adapters.
- Spring Security becomes a replaceable detail confined to adapters.
- Behavior preserved; existing auth tests pass; ArchUnit enforces the boundaries.

**Non-Goals:**
- No change to `SecurityConfig` wiring (owned by [refactor-security-hexagonal]).
- No change to the REST contract, password scheme, token format, or `users` schema.
- No new DI container; isolation is by package + enforced by tests.

## Decisions

### D1: Package structure under `com.finanzbuddy.api.auth`
```
auth/
  domain/
    model/      Registration, Credentials, AuthenticatedUser ...
    AuthService (implements RegisterUserUseCase, LoginUseCase)
    exception/  EmailAlreadyRegisteredException, InvalidCredentialsException
  port/
    in/         RegisterUserUseCase, LoginUseCase
    out/        UserRepositoryPort, PasswordHasherPort,
                CredentialAuthenticatorPort, AccessTokenIssuerPort
  adapter/
    in/web/     AuthController, DTOs, ByteLength validation, request/response mappers
    out/security/     SpringPasswordHasherAdapter (PasswordEncoder),
                      SpringCredentialAuthenticatorAdapter (AuthenticationManager),
                      DatabaseUserDetailsService (UserDetailsService over UserRepositoryPort)
    out/persistence/  JpaUserRepositoryAdapter (Spring Data UserRepository + entity mapping)
```
Rationale: same single-module, ArchUnit-enforced boundary as the document slice. Alternative (Maven module split) rejected for the same reasons.

### D2: Token issuance delegated to the security slice
`AccessTokenIssuerPort` is implemented by an adapter that calls the `AccessTokenPort` from [refactor-security-hexagonal]. Rationale: token creation is a security-slice concern; auth only needs "give me a token for this subject". If security lands later, a temporary adapter wraps the existing `JwtService`.

### D3: `UserDetails` stays out of the domain
`DatabaseUserDetailsService` remains a Spring Security adapter translating a domain user (via `UserRepositoryPort`) into `UserDetails`. The domain models users with its own type. Rationale: `UserDetails` is a Spring Security contract; keeping it at the edge lets the authenticator be swapped.

### D4: Errors as domain exceptions, mapped at the edge
`AuthService` throws `EmailAlreadyRegisteredException`/`InvalidCredentialsException`; `ApiExceptionHandler` maps them to the current HTTP statuses. `DataIntegrityViolationException` from a unique-constraint race is caught in the persistence adapter and re-thrown as `EmailAlreadyRegisteredException`. Rationale: HTTP status and persistence exceptions are transport/infra concerns.

### D5: Transactions via the shared transaction port
Reuse `TransactionRunnerPort` (introduced for documents) for the register flow's transactional boundary instead of `@Transactional` on the domain. Rationale: `@Transactional` is Spring. If documents has not landed, introduce the port here.

### D6: Enforcement with ArchUnit (test scope)
Encode the spec rules: auth domain may not import Spring/persistence/web; web depends on inbound ports; Spring Security/Data types only in `adapter/out/*`.

## Risks / Trade-offs

- **Cross-slice dependency on security's token port** → If sequencing forces auth first, wrap the existing `JwtService` behind `AccessTokenIssuerPort` temporarily; swap to the real port when security lands.
- **Spring Security authentication semantics subtle to wrap** → Mirror the current `AuthenticationManager` call exactly inside the authenticator adapter; verify with existing login tests (including lockout/bad-credential paths) before merging.
- **Mapping boilerplate (domain user ↔ JPA entity ↔ UserDetails)** → Accepted; keep mappers in adapters.
- **Pattern already proven on documents** → Lower risk than the pilot; reuse its ports where shared.

## Migration Plan

1. Add domain model, inbound/outbound ports, domain exceptions (additive).
2. Implement security + persistence adapters wrapping existing Spring Security/Data code.
3. Rewrite `AuthService` to depend only on ports; delete framework imports.
4. Re-point `AuthController` to inbound ports; move exception mapping into `ApiExceptionHandler`.
5. Re-point existing tests; add domain unit tests (no Spring) and ArchUnit tests.
6. Delete dead coupled code.

Rollback: additive until step 6 and isolated to the `auth` package tree; revert the PR. No data migration.

## Open Questions

- Should `AuthenticatedUser`/`Credentials` domain types be shared with the security slice's principal model, or kept auth-local until a second consumer exists? Default: keep local.
- Keep `DatabaseUserDetailsService` as an auth adapter, or move it under the security slice since it serves the authentication filter? Leaning auth (it is the user source of truth).
