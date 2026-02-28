# 03_JWT_DESIGN (Authentication Strategy)

## 1. Overview
Asymmetric (RS256) JWT strategy with Redis-based Refresh Token Rotation (RTR) for MSA environment.

## 2. Key Management
- **Algorithm**: RS256 (RSA Signature with SHA-256)
- **Private Key**: Located in `jym-member-auth-service` resources. Used for **signing** tokens.
- **Public Key**: Shared with `jym-api-gateway`. Used for **verifying** tokens.

## 3. Token Lifecycle
### 3.1 Access Token
- **Validity**: 15 ~ 30 minutes.
- **Payload**: `sub` (username), `userId`, `role`.
- **Verification**: Performed by `jym-api-gateway`.

### 3.2 Refresh Token (RTR)
- **Validity**: 7 days.
- **Storage**: Redis (Key: `RT:{username}`, Value: `{token}`).
- **Rotation**: Every time a new Access Token is requested, a new Refresh Token is also issued and the old one is invalidated.

## 4. Components
- **JwtProvider**: Core utility for token issuance (Sign with Private Key).
- **JwtValidator**: Gateway-side utility for token verification (Verify with Public Key).
- **RedisService**: Manages Refresh Tokens and Blacklisted Access Tokens.

## 5. MSA Workflow
1. Client requests login -> **Auth Service** issues AT/RT.
2. Client requests protected resource -> **Gateway** verifies AT using Public Key.
3. **Gateway** injects `X-User-Id` and `X-User-Role` headers into downstream requests.
