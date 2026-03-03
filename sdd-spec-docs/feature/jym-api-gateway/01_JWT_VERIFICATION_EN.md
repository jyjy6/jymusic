#Complete

# 03_JWT_VERIFICATION (Gateway Security)

## 1. Objective

Define the logic for validating incoming JWTs and propagating user information to downstream microservices.

## 2. Key Component: `JwtValidator`

- **Responsibility**: Verify the RS256 signature of the Access Token using the **Public Key**.
- **Location**: `jym-api-gateway`
- **Input**: `Authorization: Bearer <token>`
- **Output**: Boolean (validity) and extracted Claims.

## 3. Global Filter Logic

1. Intercept incoming request.
2. Check if the path requires authentication (exclude login/register).
3. Extract JWT from the `Authorization` header.
4. Validate signature and expiration.
5. If valid, extract `userId`, `username`, and `role`.
6. **Header Injection**:
   - `X-User-Id`: User's internal ID.
   - `X-User-Name`: User's username.
   - `X-User-Role`: User's authority level.
7. Forward the modified request to the target service.

## 4. Error Handling

- **401 Unauthorized**: If token is missing, expired, or signature is invalid.
- Return unified error response using `GlobalExceptionHandler`.
