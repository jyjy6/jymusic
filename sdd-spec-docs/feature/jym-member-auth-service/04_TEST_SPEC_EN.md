# 04_TEST_SPEC (Member & Auth Service)

## 1. Overview

This document defines the unit test specification for `jym-member-auth-service`.
Tests are written **after** business code, using the OAS spec as the source of truth — not the implementation.
All tests are unit tests. Integration tests are deferred per `00_architecture.md §2.3`.

---

## 2. Test Layers & Tools

| Layer        | Annotation / Tool                     | Scope                          |
| :----------- | :------------------------------------ | :----------------------------- |
| Controller   | `@WebMvcTest` + `MockMvc`             | HTTP request/response contract |
| Service      | `@ExtendWith(MockitoExtension.class)` | Business logic isolation       |
| JwtProvider  | `@ExtendWith(MockitoExtension.class)` | Token generation & claims      |
| RedisService | `@ExtendWith(MockitoExtension.class)` | Refresh token lifecycle        |

---

## 3. Controller Layer Tests

### 3.1 `MemberAuthControllerTest`

#### `POST /api/v1/auth/register`

| #    | Scenario                            | Input                             | Expected Status   | Expected Response                           |
| :--- | :---------------------------------- | :-------------------------------- | :---------------- | :------------------------------------------ |
| R-01 | Happy path — valid registration     | Valid `MemberRegistrationRequest` | `201 Created`     | `MemberProfileResponse` with correct fields |
| R-02 | Duplicate username                  | Already-existing `username`       | `409 Conflict`    | `GlobalException` error response            |
| R-03 | Duplicate email                     | Already-existing `email`          | `409 Conflict`    | `GlobalException` error response            |
| R-04 | Missing required field (`nickname`) | Request without `nickname`        | `400 Bad Request` | Bean Validation error                       |
| R-05 | Missing required field (`username`) | Request without `username`        | `400 Bad Request` | Bean Validation error                       |

#### `POST /api/v1/auth/login`

| #    | Scenario                         | Input                            | Expected Status    | Expected Response                                         |
| :--- | :------------------------------- | :------------------------------- | :----------------- | :-------------------------------------------------------- |
| L-01 | Happy path — correct credentials | Valid `MemberLoginRequest`       | `200 OK`           | `AuthTokenResponse` with `accessToken` and `refreshToken` |
| L-02 | Wrong password                   | Correct username, wrong password | `401 Unauthorized` | `GlobalException` error response                          |
| L-03 | Non-existent username            | Unknown `username`               | `401 Unauthorized` | `GlobalException` error response                          |
| L-04 | Inactive account                 | `is_active = false` member       | `403 Forbidden`    | `GlobalException` error response                          |
| L-05 | Missing required field           | Request without `password`       | `400 Bad Request`  | Bean Validation error                                     |

---

### 3.2 `MemberControllerTest`

#### `GET /api/v1/members/me`

| #    | Scenario                           | Input                                                         | Expected Status    | Expected Response                           |
| :--- | :--------------------------------- | :------------------------------------------------------------ | :----------------- | :------------------------------------------ |
| M-01 | Happy path — authenticated request | Valid `X-User-Id` / `X-User-Role` headers injected by gateway | `200 OK`           | `MemberProfileResponse` with correct fields |
| M-02 | Missing user identity headers      | No `X-User-Id` header                                         | `401 Unauthorized` | `GlobalException` error response            |
| M-03 | User not found in DB               | Valid headers but member deleted                              | `404 Not Found`    | `GlobalException` error response            |

> **Note**: JWT verification is handled by `jym-api-gateway`. This service only trusts the `X-User-Id` / `X-User-Role` headers forwarded by the gateway.

---

## 4. Service Layer Tests

### 4.1 `MemberAuthServiceTest`

#### `register(MemberRegistrationRequest)`

| #     | Scenario                | Mock Behavior                                                                             | Expected Outcome                            |
| :---- | :---------------------- | :---------------------------------------------------------------------------------------- | :------------------------------------------ |
| RS-01 | Happy path              | `repo.existsByUsername` → false, `repo.existsByEmail` → false, `repo.save` → saved entity | Returns `MemberProfileResponse`             |
| RS-02 | Username already exists | `repo.existsByUsername` → true                                                            | Throws `GlobalException` (CONFLICT)         |
| RS-03 | Email already exists    | `repo.existsByEmail` → true                                                               | Throws `GlobalException` (CONFLICT)         |
| RS-04 | Password encoding       | BCryptPasswordEncoder called with raw password                                            | Stored password does NOT equal raw password |

#### `login(MemberLoginRequest)`

| #     | Scenario                      | Mock Behavior                                  | Expected Outcome                                                |
| :---- | :---------------------------- | :--------------------------------------------- | :-------------------------------------------------------------- |
| LS-01 | Happy path                    | Member found, password matches, account active | Returns `AuthTokenResponse` with non-blank tokens               |
| LS-02 | Username not found            | `repo.findByUsername` → `Optional.empty()`     | Throws `GlobalException` (UNAUTHORIZED)                         |
| LS-03 | Password mismatch             | `passwordEncoder.matches` → false              | Throws `GlobalException` (UNAUTHORIZED)                         |
| LS-04 | Account inactive              | Member found but `is_active = false`           | Throws `GlobalException` (FORBIDDEN)                            |
| LS-05 | Refresh token stored in Redis | Login succeeds                                 | `redisService.save(RT:{username}, refreshToken)` is called once |

#### `getMyProfile(Long userId)`

| #     | Scenario         | Mock Behavior                                | Expected Outcome                     |
| :---- | :--------------- | :------------------------------------------- | :----------------------------------- |
| MP-01 | Happy path       | `repo.findById(userId)` → member present     | Returns `MemberProfileResponse`      |
| MP-02 | Member not found | `repo.findById(userId)` → `Optional.empty()` | Throws `GlobalException` (NOT_FOUND) |

---

## 5. JWT Component Tests

### 5.1 `JwtProviderTest`

| #    | Scenario                             | Expected Outcome                                                   |
| :--- | :----------------------------------- | :----------------------------------------------------------------- |
| J-01 | Generate Access Token                | Returns non-blank JWT string                                       |
| J-02 | Access Token contains correct claims | `sub` = username, `userId`, `role` all present with correct values |
| J-03 | Access Token expiry is within spec   | Expiry is set to ~15–30 minutes from issuance                      |
| J-04 | Generate Refresh Token               | Returns non-blank token string                                     |
| J-05 | Refresh Token expiry is within spec  | Expiry is set to ~7 days from issuance                             |

---

## 6. RedisService Tests

### 6.1 `RedisServiceTest`

| #     | Scenario                               | Expected Outcome                              |
| :---- | :------------------------------------- | :-------------------------------------------- |
| RD-01 | Save refresh token                     | Key `RT:{username}` stored with correct value |
| RD-02 | Get refresh token — exists             | Returns stored token string                   |
| RD-03 | Get refresh token — not exists         | Returns `null` or `Optional.empty()`          |
| RD-04 | Delete refresh token                   | Key `RT:{username}` removed                   |
| RD-05 | RTR — old token invalidated on reissue | Old RT deleted, new RT stored under same key  |

---

## 7. Naming Conventions

- Test class: `{TargetClass}Test.java`
- Test method: `{method}_{scenario}_{expectedResult}` (e.g., `login_wrongPassword_throwsUnauthorized`)
- Use `@DisplayName` for Korean-language human-readable descriptions.

---

## 8. Out of Scope (Deferred)

- Integration tests (DB, Redis container-based)
- Security filter chain tests
- Social login (`GOOGLE`, `NAVER`, `KAKAO`) paths
