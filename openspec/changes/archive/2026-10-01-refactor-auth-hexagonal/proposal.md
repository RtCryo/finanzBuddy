## Why

The `auth` slice tangles business logic with Spring Security and web transport. `AuthService` imports `AuthenticationManager`, `UsernamePasswordAuthenticationToken`, `PasswordEncoder`, `UserDetails`, Spring DAO exceptions, `ResponseStatusException`/`HttpStatus`, and `@Transactional`; `DatabaseUserDetailsService` is coupled to `UserDetailsService`/`UserDetails`. Registration and login rules (credential validation, password hashing, token issuance, duplicate-email handling) cannot be tested or reasoned about without Spring Security, and the auth framework cannot be swapped without editing core logic.

This is the second slice of the api-gateway hexagonal refactor, applying the pattern proven by [refactor-document-hexagonal] (vertical slice, pragmatic framework-free core). It should be implemented after the document pilot so its learnings carry over.

## What Changes

- Introduce a framework-free **auth domain**: registration and login business logic depending only on `java.*`, domain types, and (pragmatically) `jakarta.inject`/`jakarta.validation`. No `org.springframework.*` or web types.
- Define **inbound ports**: `RegisterUserUseCase`, `LoginUseCase`.
- Define **outbound ports**: `UserRepositoryPort`, `PasswordHasherPort`, `CredentialAuthenticatorPort` (verify credentials), `AccessTokenIssuerPort` (issue JWT — backed by the security slice's token port).
- Move framework code into **adapters**: Spring Security `PasswordEncoder`/`AuthenticationManager` wrapped behind `PasswordHasherPort`/`CredentialAuthenticatorPort`; `DatabaseUserDetailsService` becomes a Spring Security adapter over `UserRepositoryPort`; `AuthController` stays as the inbound web adapter.
- Replace `ResponseStatusException`/`BadCredentialsException`/`DataIntegrityViolationException` in the core with domain exceptions (`EmailAlreadyRegisteredException`, `InvalidCredentialsException`), mapped to HTTP in `ApiExceptionHandler`.
- Add architecture (ArchUnit) tests enforcing the auth domain-isolation and port rules.
- **No external behavior change**: `/auth/register` and `/auth/login` contracts, status codes, and token format are identical.

## Capabilities

### New Capabilities
- `auth-architecture`: Structural requirements for the auth slice — domain isolation, mandatory inbound/outbound ports, adapter responsibilities, and automated enforcement. Verifiable constraints, not functional behavior.

### Modified Capabilities
<!-- None. The user-authentication functional spec and its observable behavior are unchanged. -->

## Impact

- **Code (api-gateway only)**: `auth/service/AuthService.java`, `auth/service/DatabaseUserDetailsService.java`, `auth/controller/*`, `auth/dto/*`, `auth/validation/*` reorganized into `auth/domain`, `auth/port/{in,out}`, `auth/adapter/{in/web,out/security,out/persistence}`.
- **Dependency on other slices**: `AccessTokenIssuerPort` is backed by the token port introduced in [refactor-security-hexagonal]; sequence security before or alongside auth, or stub the token port.
- **APIs**: unchanged. **DB schema**: unchanged (`users` table).
- **Out of scope**: Spring Security wiring in `SecurityConfig` (owned by the security slice), document/cleanup slices.
- **Tests**: existing auth tests re-pointed; new domain unit tests (no Spring) and ArchUnit tests added.
