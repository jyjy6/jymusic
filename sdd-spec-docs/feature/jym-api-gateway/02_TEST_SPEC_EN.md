# 02_TEST_SPEC (API Gateway)

## 1. Overview

This document defines the unit test specification for `jym-api-gateway`.

The gateway has **no business logic** — its sole responsibilities are:
- JWT signature verification (via `JwtValidator`)
- User context header injection (via `JwtVerificationFilter`)
- CORS policy enforcement (via `SpringSecurityConfig`)
- Path-based routing (via `GatewayRoutingConfig`)

Routing tests (actual HTTP forwarding to downstream services) require live services
and are deferred as integration tests per `00_architecture.md §2.3`.

---

## 2. Test Layers & Tools

| Layer | Annotation / Tool | Scope |
| :--- | :--- | :--- |
| JwtValidator | `@ExtendWith(MockitoExtension.class)` | Token signature verification |
| JwtVerificationFilter | `MockHttpServletRequest` / `MockFilterChain` | Filter behavior without Spring context |
| CORS Config | `@SpringBootTest` + `MockMvc` | CORS headers on preflight requests |

---

## 3. JwtValidator Tests

### `JwtValidatorTest`

The constructor reads a PEM public key from a `Resource`. Tests generate an RSA key pair
programmatically and inject it via `ByteArrayResource` — no real key files required.

#### `validateToken(String token)`

| # | Scenario | Input | Expected |
| :--- | :--- | :--- | :--- |
| JV-01 | Valid token | RS256-signed JWT with future expiration | Returns `true` |
| JV-02 | Expired token | JWT with past expiration date | Returns `false` |
| JV-03 | Token signed with a different key | JWT signed with a different RSA key pair | Returns `false` |
| JV-04 | Tampered token string | Manually modified JWT payload | Returns `false` |
| JV-05 | Null or blank string | `null`, `""` | Returns `false` |

#### `getClaims(String token)`

| # | Scenario | Input | Expected |
| :--- | :--- | :--- | :--- |
| JC-01 | Valid token — extract claims | Valid JWT with `userId`, `role`, `sub` | Returns `Claims` with correct field values |
| JC-02 | Invalid token — throw exception | Tampered or expired token | Throws `Exception` |

---

## 4. JwtVerificationFilter Tests

### `JwtVerificationFilterTest`

Uses `MockHttpServletRequest`, `MockHttpServletResponse`, and `MockFilterChain` directly.
No Spring context needed. `JwtValidator` is injected as a `@Mock`.

#### `shouldNotFilter()` — Path Exclusion Logic

| # | Path | Expected (`shouldNotFilter`) |
| :--- | :--- | :--- |
| SF-01 | `/api/v1/auth/login` | `true` — filter skipped |
| SF-02 | `/api/v1/auth/register` | `true` — filter skipped |
| SF-03 | `/api/v1/auth/refresh-token` | `true` — filter skipped |
| SF-04 | `/swagger-ui/index.html` | `true` — filter skipped |
| SF-05 | `/api/v1/members/me` | `false` — filter applied |
| SF-06 | `/api/v1/orders` | `false` — filter applied |
| SF-07 | `/api/v1/products` | `false` — filter applied |

#### `doFilterInternal()` — JWT Validation & Header Injection

| # | Scenario | Setup | Expected |
| :--- | :--- | :--- | :--- |
| FI-01 | Missing `Authorization` header | No `Authorization` header | Status 401, body contains `ERR_UNAUTHORIZED` |
| FI-02 | Header without `Bearer ` prefix | `Authorization: JustToken abc` | Status 401 |
| FI-03 | Invalid token | `validateToken` returns `false` | Status 401 |
| FI-04 | Valid token — filter passes | `validateToken` returns `true`, valid claims | `filterChain.doFilter()` called; downstream request carries `X-User-Id`, `X-User-Name`, `X-User-Role` |
| FI-05 | Header injection accuracy | Claims: `userId=1`, `sub=testuser`, `role=ROLE_USER` | Downstream `X-User-Id=1`, `X-User-Name=testuser`, `X-User-Role=ROLE_USER` |

---

## 5. CORS Configuration Tests

### `CorsConfigTest`

Uses `@SpringBootTest` + `MockMvc`. Verifies the `CorsConfigurationSource` bean behavior.

> **Note**: `application-test.properties` must provide dummy `services.*` URLs to prevent
> `@Value` resolution failures in `GatewayRoutingConfig`.

| # | Scenario | Request | Expected |
| :--- | :--- | :--- | :--- |
| CO-01 | Allowed origin — preflight returns CORS headers | `OPTIONS` + `Origin: http://localhost:3000` | `Access-Control-Allow-Origin: http://localhost:3000` |
| CO-02 | Disallowed origin — no CORS headers | `OPTIONS` + `Origin: http://evil.com` | No `Access-Control-Allow-Origin` header |
| CO-03 | Credentials allowed | Valid CORS preflight | `Access-Control-Allow-Credentials: true` |
| CO-04 | Allowed methods include DELETE | Preflight with `Access-Control-Request-Method: DELETE` | `Access-Control-Allow-Methods` includes `DELETE` |

---

## 6. Naming Conventions

- Test class: `{TargetClass}Test.java`
- Test method: `{method}_{scenario}_{expectedResult}`
- Use `@DisplayName` for human-readable test descriptions.

---

## 7. Out of Scope (Deferred)

- Routing tests (actual HTTP forwarding to downstream services)
- Load balancing or circuit breaker tests
- Rate limiting tests (no implementation yet)
