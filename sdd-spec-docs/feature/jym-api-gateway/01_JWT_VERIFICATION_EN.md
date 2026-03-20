# 01_JWT_VERIFICATION (Gateway Security)

## 1. Objective

Define the logic for validating incoming JWTs and propagating user information to downstream microservices.

## 2. Key Component: `JwtValidator`

- **Responsibility**: Verify the RS256 signature of the Access Token using the **Public Key**.
- **Location**: `jym-api-gateway`
- **Input**: `Authorization: Bearer <token>`
- **Output**: Boolean (validity) and extracted Claims.
- **Note**: Pure Java utility class — works identically in both Servlet and Reactive environments.

## 3. Global Filter Logic (`GlobalFilter`-based)

> **Technology Stack**: Spring Cloud Gateway Reactive — implements `GlobalFilter` + `Ordered`

1. Intercept incoming request via `ServerWebExchange`.
2. Check if the path requires authentication (exclude login/register).
3. Extract JWT from the `Authorization` header of `ServerHttpRequest`.
4. Validate signature and expiration.
5. If valid, extract `userId`, `username`, and `role`.
6. **Header Injection** — using `ServerHttpRequest.mutate().header(...)`:
   - `X-User-Id`: User's internal ID.
   - `X-User-Name`: User's username.
   - `X-User-Role`: User's authority level.
7. Forward the mutated `ServerWebExchange` through `GatewayFilterChain` to the target service.

### 3.1 Filter Execution Order

- `getOrder()` returns: `-1` (executes after Security filter chain, before routing)

### 3.2 Excluded Paths

| Path Pattern | Reason |
| :--- | :--- |
| `/api/v1/auth/**` | Login / Register / Token refresh |
| `/swagger-ui/**` | API documentation UI |
| `/v3/api-docs/**` | OpenAPI spec |
| `/openapi.yaml` | OpenAPI definition file |

## 4. Error Handling

- **401 Unauthorized**: If token is missing, expired, or signature is invalid.
- Response method: Write JSON directly to `ServerHttpResponse` via `DataBuffer`.
- Response format: `{"status":401,"code":"ERR_UNAUTHORIZED","message":"...","timestamp":"..."}`
- Request is terminated immediately by returning `Mono<Void>` without invoking `GatewayFilterChain`.
