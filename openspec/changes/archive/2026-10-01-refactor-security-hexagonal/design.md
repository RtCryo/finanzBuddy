## Context

The `security` slice is `JwtService` (token create/validate, coupled to `io.jsonwebtoken` + Spring `@Value`/`@Service`/`UserDetails`), `JwtAuthenticationFilter` and `InternalApiKeyAuthFilter` (servlet filters embedding token-extraction and api-key-matching logic in `jakarta.servlet` + Spring Security web plumbing), and `SecurityConfig` (pure Spring Security wiring). The decision of whether a request is authenticated is entangled with the JWT library and the servlet stack.

This is the third slice of the api-gateway hexagonal refactor, applying the pattern from [refactor-document-hexagonal]. The auth slice depends on the token port introduced here.

## Goals / Non-Goals

**Goals:**
- Token issue/parse/validate and api-key verification live in a framework-free domain behind ports.
- Servlet filters become thin inbound adapters calling those ports and populating the `SecurityContext`.
- The JWT library (`io.jsonwebtoken`) and the web stack become replaceable details.
- Expose `AccessTokenPort` for reuse by the auth slice.
- Behavior preserved; existing filter tests pass; ArchUnit enforces boundaries.

**Non-Goals:**
- `SecurityConfig` stays Spring wiring (explicitly a configuration adapter), not domain.
- No change to the auth contract, token format, or env (`JWT_SECRET`, `INTERNAL_API_KEY`).
- No new DI container; isolation by package + enforced by tests.

## Decisions

### D1: Package structure under `com.finanzbuddy.api.security`
```
security/
  domain/
    RequestAuthenticator (implements AuthenticateRequestUseCase)
    InternalAuthenticator (implements AuthenticateInternalUseCase)
    model/      Principal, TokenValidationResult ...
    exception/  InvalidTokenException, ExpiredTokenException
  port/
    in/         AuthenticateRequestUseCase, AuthenticateInternalUseCase
    out/        AccessTokenPort, ApiKeyPort, PrincipalLookupPort
  adapter/
    in/web/     JwtAuthenticationFilter, InternalApiKeyAuthFilter (thin servlet adapters)
    out/token/  JwtAccessTokenAdapter (io.jsonwebtoken; replaces JwtService)
  SecurityConfig  (retained Spring Security wiring / configuration adapter)
```
Rationale: same single-module ArchUnit-enforced boundary as prior slices.

### D2: `AccessTokenPort` is the reusable token contract
`AccessTokenPort` exposes issue(subject, claims) / parse+validate(token) → `TokenValidationResult`. The JWT adapter holds all `io.jsonwebtoken` code. Rationale: both the auth slice (issuing on login) and the filter (validating on each request) need it; one port, one adapter. Alternative — duplicate token logic in auth and security; rejected (duplication + divergence risk).

### D3: Filters stay as inbound adapters, not domain
Filters remain `OncePerRequestFilter` subclasses but delegate the decision to the inbound ports, then populate `SecurityContextHolder`. Rationale: `SecurityContext` population and servlet mechanics are web concerns; the *decision* (valid token? which principal? valid api-key?) is domain. Trade-off: the filter keeps a little Spring Security glue — acceptable, it is explicitly an adapter.

### D4: Constant-time api-key comparison behind `ApiKeyPort`
The current `InternalApiKeyAuthFilter` matching logic moves into the domain `InternalAuthenticator` using `ApiKeyPort` for the comparison primitive (constant-time). Rationale: the comparison is security-sensitive and must be preserved exactly; isolating it makes it testable.

### D5: Token failures as domain results
Replace leaked `JwtException`/`ExpiredJwtException` with `TokenValidationResult` / domain exceptions; the filter maps to the current 401. Rationale: library exceptions are an implementation detail.

### D6: Enforcement with ArchUnit (test scope)
Encode: domain may not import `io.jsonwebtoken`/`org.springframework`/`jakarta.servlet`; filters depend on inbound ports; `io.jsonwebtoken` only in the token adapter.

## Risks / Trade-offs

- **Security-sensitive code (token validation, constant-time compare) must not change behavior** → Move, don't rewrite; cover with the existing filter tests plus new domain tests for expiry/invalid-signature/wrong-key before merging.
- **`PrincipalLookupPort` overlaps with auth's user source** → Back it by the same `UserRepositoryPort`/user source; avoid a second user-loading path.
- **Filter still touches Spring Security** → Intentional; the filter is an adapter. ArchUnit allows Spring in `adapter/in/web`, forbids it in `domain`.
- **Sequencing with auth** → Land security first (or alongside) so `AccessTokenPort` exists before auth depends on it.

## Migration Plan

1. Add domain authenticators, inbound/outbound ports, domain results/exceptions (additive).
2. Implement `JwtAccessTokenAdapter` from `JwtService`; move `io.jsonwebtoken` into it.
3. Move api-key matching into the domain via `ApiKeyPort`.
4. Slim the two filters to call inbound ports + populate `SecurityContext`.
5. Re-point tests; add domain unit tests (no servlet/Spring) and ArchUnit tests.
6. Delete `JwtService` and dead glue; keep `SecurityConfig` wiring updated to new beans.

Rollback: additive until step 6, isolated to the `security` package tree; revert the PR. No data/config migration.

## Open Questions

- Should `Principal`/`TokenValidationResult` be shared with the auth slice's `AuthenticatedUser`, or kept security-local? Default: keep local until a concrete need.
- Keep both filters, or unify behind a single authentication entry filter delegating by scheme? Default: keep two (minimal change, matches current behavior).
