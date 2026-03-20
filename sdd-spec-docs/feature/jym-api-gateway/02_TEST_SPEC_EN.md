# 02_TEST_SPEC (API Gateway)

## 1. Overview

This document defines the unit test specification for `jym-api-gateway`.

The gateway has **no business logic** — its sole responsibilities are:
- JWT signature verification (via `JwtValidator`)
- User context header injection (via `JwtVerificationFilter` — `GlobalFilter`)
- CORS policy enforcement (via `SecurityConfig`)
- Path-based routing (via `GatewayRouteConfig`)

Routing tests (actual HTTP forwarding to downstream services) require live services
and are deferred as integration tests per `00_architecture.md §2.3`.

---

## 2. Test Layers & Tools

| Layer | Annotation / Tool | Scope |
| :--- | :--- | :--- |
| JwtValidator | `@ExtendWith(MockitoExtension.class)` | Token signature verification |
| JwtVerificationFilter | `WebTestClient` + `@SpringBootTest(webEnvironment = RANDOM_PORT)` or `MockServerWebExchange` | GlobalFilter behavior verification |
| CORS Config | `@SpringBootTest` + `WebTestClient` | CORS headers on preflight requests |

---

## 3. JwtValidator Tests

### `JwtValidatorTest`

The constructor reads a PEM public key from a `Resource`. Tests generate an RSA key pair
programmatically and inject it via `ByteArrayResource` — no real key files required.

> `JwtValidator` is a pure Java utility — no test changes required due to WebMvc/WebFlux migration.

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

## 4. JwtVerificationFilter Tests (GlobalFilter)

### `JwtVerificationFilterTest`

Uses `WebTestClient` with `@SpringBootTest(webEnvironment = RANDOM_PORT)` to verify
`GlobalFilter` behavior on an actual Netty server.

Alternatively, for lightweight unit tests, use `MockServerWebExchange` and `MockGatewayFilterChain`.
`JwtValidator` is injected as a `@Mock`.

#### Path Exclusion Logic (shouldSkip)

| # | Path | Expected |
| :--- | :--- | :--- |
| SF-01 | `/api/v1/auth/login` | Filter skipped — `chain.filter()` invoked immediately |
| SF-02 | `/api/v1/auth/register` | Filter skipped |
| SF-03 | `/api/v1/auth/refresh-token` | Filter skipped |
| SF-04 | `/swagger-ui/index.html` | Filter skipped |
| SF-05 | `/api/v1/members/me` | Filter applied |
| SF-06 | `/api/v1/orders` | Filter applied |
| SF-07 | `/api/v1/products` | Filter applied |

#### JWT Validation & Header Injection (`filter()`)

| # | Scenario | Setup | Expected |
| :--- | :--- | :--- | :--- |
| FI-01 | Missing `Authorization` header | No `Authorization` header | Status 401, body contains `ERR_UNAUTHORIZED` |
| FI-02 | Header without `Bearer ` prefix | `Authorization: JustToken abc` | Status 401 |
| FI-03 | Invalid token | `validateToken` returns `false` | Status 401 |
| FI-04 | Valid token — filter passes | `validateToken` returns `true`, valid claims | `chain.filter()` called; downstream request carries `X-User-Id`, `X-User-Name`, `X-User-Role` |
| FI-05 | Header injection accuracy | Claims: `userId=1`, `sub=testuser`, `role=ROLE_USER` | Downstream `X-User-Id=1`, `X-User-Name=testuser`, `X-User-Role=ROLE_USER` |

### Test Implementation Reference

```java
// Lightweight unit test example
MockServerHttpRequest request = MockServerHttpRequest
        .get("/api/v1/orders")
        .header("Authorization", "Bearer " + validToken)
        .build();
MockServerWebExchange exchange = MockServerWebExchange.from(request);

StepVerifier.create(filter.filter(exchange, mockChain))
        .verifyComplete();
```

---

## 5. CORS Configuration Tests

### `CorsConfigTest`

Uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`.
Verifies the `SecurityWebFilterChain` CORS behavior.

> **Note**: `application-test.properties` must provide dummy `services.*` URLs to prevent
> `@Value` resolution failures in `GatewayRouteConfig`.

| # | Scenario | Request | Expected |
| :--- | :--- | :--- | :--- |
| CO-01 | Allowed origin — preflight returns CORS headers | `OPTIONS` + `Origin: http://localhost:3000` | `Access-Control-Allow-Origin: http://localhost:3000` |
| CO-02 | Disallowed origin — no CORS headers | `OPTIONS` + `Origin: http://evil.com` | No `Access-Control-Allow-Origin` header |
| CO-03 | Credentials allowed | Valid CORS preflight | `Access-Control-Allow-Credentials: true` |
| CO-04 | Allowed methods include DELETE | Preflight with `Access-Control-Request-Method: DELETE` | `Access-Control-Allow-Methods` includes `DELETE` |

### Test Implementation Reference

```java
// WebTestClient-based CORS test example
webTestClient.options()
        .uri("/api/v1/products")
        .header("Origin", "http://localhost:3000")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000")
        .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
```

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
