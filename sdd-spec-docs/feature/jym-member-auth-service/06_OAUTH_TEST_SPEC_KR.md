# 06_OAUTH_TEST_SPEC (OAuth 소셜 로그인 테스트 명세)

## 1. 개요

본 문서는 `jym-member-auth-service`의 OAuth 소셜 로그인 관련 단위 테스트 명세를 정의합니다.
테스트는 비즈니스 코드 **이후**에 작성하며, `05_OAUTH_DESIGN_KR.md` 스펙을 기준으로 합니다.
모든 테스트는 단위 테스트이며, `00_architecture.md §2.3`에 따라 통합 테스트는 보류합니다.

---

## 2. 테스트 레이어 및 도구

| 레이어 | 어노테이션 / 도구 | 범위 |
|:-------|:------------------|:-----|
| Controller | `@WebMvcTest` + `MockMvc` | OAuth HTTP 요청/응답 계약 |
| OAuthService | `@ExtendWith(MockitoExtension.class)` | OAuth 비즈니스 로직 격리 |
| OAuthProviderClient | `@ExtendWith(MockitoExtension.class)` | Provider API 호출 로직 |

---

## 3. Controller 레이어 테스트

### 3.1 `OAuthControllerTest`

#### `GET /api/v1/auth/oauth2/{provider}` — 소셜 로그인 시작

| # | 시나리오 | 입력 | 기대 상태 | 기대 응답 |
|:--|:---------|:-----|:----------|:----------|
| OA-01 | 정상 — Google 로그인 리다이렉트 | `provider = google` | `302 Found` | Location 헤더에 Google 인가 URL 포함 |
| OA-02 | 정상 — Kakao 로그인 리다이렉트 | `provider = kakao` | `302 Found` | Location 헤더에 Kakao 인가 URL 포함 |
| OA-03 | 미지원 Provider | `provider = naver` | `400 Bad Request` | `ERR_UNSUPPORTED_PROVIDER` 에러 응답 |

#### `GET /api/v1/auth/oauth2/callback/{provider}` — OAuth 콜백

| # | 시나리오 | 입력 | 기대 상태 | 기대 응답 |
|:--|:---------|:-----|:----------|:----------|
| OC-01 | 정상 — 신규 사용자 자동 가입 + 로그인 | 유효한 `code`, `state` | `302 Found` | 프론트 success URL로 리다이렉트 + HttpOnly Cookie 설정 |
| OC-02 | 정상 — 기존 사용자 로그인 | 이미 가입된 사용자의 `code` | `302 Found` | 프론트 success URL로 리다이렉트 + HttpOnly Cookie 설정 |
| OC-03 | State 불일치 | 유효한 `code`, 잘못된 `state` | `401 Unauthorized` | `ERR_OAUTH_INVALID_STATE` |
| OC-04 | code 누락 | `state`만 존재, `code` 없음 | `400 Bad Request` | Bean Validation 에러 |

---

## 4. Service 레이어 테스트

### 4.1 `OAuthServiceTest`

#### `processOAuthCallback(AuthProvider, String code)`

| # | 시나리오 | Mock 동작 | 기대 결과 |
|:--|:---------|:----------|:----------|
| OS-01 | 정상 — 신규 사용자 | `providerClient.getAccessToken` → 유효한 토큰, `getUserInfo` → 유효한 사용자 정보, `repo.findByAuthProviderAndProviderId` → `Optional.empty()` | 신규 Member 저장 + `AuthTokenResponse` 반환 |
| OS-02 | 정상 — 기존 사용자 | `repo.findByAuthProviderAndProviderId` → 기존 Member | 기존 Member로 JWT 발급, `repo.save` 호출 없음 |
| OS-03 | Token 교환 실패 | `providerClient.getAccessToken` → 예외 발생 | `GlobalException`(UNAUTHORIZED, `ERR_OAUTH_TOKEN_EXCHANGE`) 발생 |
| OS-04 | UserInfo 조회 실패 | `providerClient.getUserInfo` → 예외 발생 | `GlobalException`(UNAUTHORIZED, `ERR_OAUTH_USER_INFO`) 발생 |
| OS-05 | 비활성 계정 | `repo.findByAuthProviderAndProviderId` → `isActive = false`인 Member | `GlobalException`(FORBIDDEN) 발생 |
| OS-06 | JWT 토큰 발급 확인 | 정상 흐름 | `jwtProvider.generateAccessToken`, `jwtProvider.generateRefreshToken` 각 1회 호출 |
| OS-07 | Redis RT 저장 확인 | 정상 흐름 | `redisService.save(RT:{username}, refreshToken)` 1회 호출 |
| OS-08 | 자동 가입 시 username 형식 | 신규 사용자 (Google, providerId=12345) | 저장된 Member의 username이 `google_12345` |
| OS-09 | 자동 가입 시 password null | 신규 사용자 | 저장된 Member의 password가 `null` |
| OS-10 | 자동 가입 시 role 기본값 | 신규 사용자 | 저장된 Member의 role이 `ROLE_USER` |

### 4.2 State 검증 테스트

| # | 시나리오 | Mock 동작 | 기대 결과 |
|:--|:---------|:----------|:----------|
| ST-01 | 정상 — 유효한 state | Redis에 `OAUTH_STATE:{state}` 존재 | 검증 통과, Redis에서 해당 키 삭제 |
| ST-02 | 만료/존재하지 않는 state | Redis에 해당 키 없음 | `GlobalException`(UNAUTHORIZED, `ERR_OAUTH_INVALID_STATE`) 발생 |

---

## 5. OAuthProviderClient 테스트

### 5.1 `GoogleOAuthClientTest`

| # | 시나리오 | Mock 동작 | 기대 결과 |
|:--|:---------|:----------|:----------|
| GC-01 | 인가 URL 생성 | `state = "test-state"` | URL에 `client_id`, `redirect_uri`, `response_type=code`, `scope`, `state` 포함 |
| GC-02 | Access Token 취득 성공 | RestClient mock → 유효한 token JSON 응답 | `OAuthTokenResponse`에 `accessToken` 포함 |
| GC-03 | Access Token 취득 실패 | RestClient mock → 4xx 응답 | 예외 발생 |
| GC-04 | UserInfo 조회 성공 | RestClient mock → 유효한 userinfo JSON | `OAuthUserInfo`에 `providerId`, `email`, `nickname` 포함 |
| GC-05 | getProvider 반환값 | - | `AuthProvider.GOOGLE` 반환 |

### 5.2 `KakaoOAuthClientTest`

| # | 시나리오 | Mock 동작 | 기대 결과 |
|:--|:---------|:----------|:----------|
| KC-01 | 인가 URL 생성 | `state = "test-state"` | URL에 `client_id`, `redirect_uri`, `response_type=code`, `scope`, `state` 포함 |
| KC-02 | Access Token 취득 성공 | RestClient mock → 유효한 token JSON 응답 | `OAuthTokenResponse`에 `accessToken` 포함 |
| KC-03 | Access Token 취득 실패 | RestClient mock → 4xx 응답 | 예외 발생 |
| KC-04 | UserInfo 조회 성공 | RestClient mock → Kakao 형식 userinfo JSON | `OAuthUserInfo`에 `providerId`(id), `email`(kakao_account.email), `nickname`(properties.nickname) 포함 |
| KC-05 | getProvider 반환값 | - | `AuthProvider.KAKAO` 반환 |

---

## 6. 명명 규칙

- 테스트 클래스: `{TargetClass}Test.java`
- 테스트 메서드: `{method}_{scenario}_{expectedResult}` (예: `processOAuthCallback_newUser_createsMemberAndReturnsToken`)
- 사람이 읽기 쉬운 설명에는 `@DisplayName`으로 한국어를 사용한다.

---

## 7. 범위 외 (보류)

- 통합 테스트 (실제 Provider API 호출)
- Gateway 라우팅 테스트
- 프론트엔드 OAuth 리다이렉트 E2E 테스트
