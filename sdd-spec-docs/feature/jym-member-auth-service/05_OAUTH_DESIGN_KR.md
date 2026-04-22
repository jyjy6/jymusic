# 05_OAUTH_DESIGN (소셜 로그인 설계 명세)

## 1. 개요

본 문서는 `jym-member-auth-service`에 **OAuth 2.0 Authorization Code Flow** 기반의
소셜 로그인(Google, Kakao)을 추가하기 위한 설계 명세를 정의합니다.

### 1.1 지원 Provider

이미 `AuthProvider` enum에 정의되어 있으며, 다음 두 가지를 구현합니다.

| Provider | enum 값 | Description |
|:---------|:---------|:------------|
| Google   | `GOOGLE` | 구글 로그인  |
| Kakao    | `KAKAO`  | 카카오 로그인 |

> **참고**: `LOCAL`은 기존 ID/PW 가입이며 OAuth 대상이 아닙니다.

### 1.2 핵심 원칙 (헌법 준수)

- **Statelessness**: 세션 없음. OAuth 성공 후에도 기존 JWT(RS256) 발급 흐름 동일.
- **DTO 원칙**: Entity 직접 노출 금지. 모든 응답은 DTO를 통해 반환.
- **GlobalException**: 모든 비즈니스 예외는 `GlobalErrorHandler.GlobalException` 사용.
- **Gateway**: 클라이언트는 반드시 `jym-api-gateway`를 통해서만 접근.

---

## 2. OAuth 2.0 인증 흐름 (Authorization Code Flow)

### 2.1 시퀀스 다이어그램

```
Client (Nuxt)                Gateway              Auth Service            OAuth Provider
     │                          │                       │                       │
     │  1. "구글 로그인" 클릭    │                       │                       │
     │ ──────────────────────► │                       │                       │
     │                          │  2. GET /auth/oauth2/ │                       │
     │                          │     {provider}        │                       │
     │                          │ ──────────────────►   │                       │
     │                          │                       │  3. 302 Redirect      │
     │                          │   ◄────────────────── │  → Provider 인가 URL  │
     │  ◄────────────────────── │                       │                       │
     │                          │                       │                       │
     │  4. 사용자 동의 (Provider 화면)                  │                       │
     │ ─────────────────────────────────────────────────────────────────────►   │
     │                          │                       │                       │
     │  5. Redirect callback    │                       │                       │
     │  ◄───────────────────────────────────────────────────────────────────    │
     │                          │                       │                       │
     │  6. GET /auth/oauth2/    │                       │                       │
     │     callback/{provider}  │                       │                       │
     │     ?code=xxx&state=yyy  │                       │                       │
     │ ──────────────────────► │ ──────────────────►   │                       │
     │                          │                       │  7. code → token 교환 │
     │                          │                       │ ──────────────────►   │
     │                          │                       │  ◄────────────────    │
     │                          │                       │  8. token → 사용자   │
     │                          │                       │     정보 조회         │
     │                          │                       │ ──────────────────►   │
     │                          │                       │  ◄────────────────    │
     │                          │                       │                       │
     │                          │  9. JWT(AT/RT) 발급   │                       │
     │                          │  ◄────────────────── │                       │
     │  10. AT 반환 + RT Cookie │                       │                       │
     │  ◄────────────────────── │                       │                       │
```

### 2.2 흐름 상세

| 단계 | 설명 |
|:-----|:-----|
| 1~3  | 프론트에서 소셜 로그인 버튼 클릭 → Auth Service가 Provider 인가 URL로 302 Redirect |
| 4    | 사용자가 Provider 화면에서 동의 |
| 5~6  | Provider가 `redirect_uri`로 `code`(Authorization Code)와 `state`를 전달 |
| 7    | Auth Service가 `code`를 Provider의 Token Endpoint로 교환하여 Access Token 취득 |
| 8    | 취득한 Access Token으로 Provider의 UserInfo API 호출하여 사용자 정보 확보 |
| 9    | DB에서 `(authProvider, providerId)` 조합으로 기존 회원 조회. 없으면 자동 가입 |
| 10   | 기존 로컬 로그인과 동일한 JWT(AT + RT) 발급. RT는 Redis 저장(RTR) + HttpOnly Cookie |

---

## 3. API 엔드포인트

### 3.1 소셜 로그인 시작

```
GET /api/v1/auth/oauth2/{provider}
```

| 항목 | 값 |
|:-----|:---|
| Path Variable | `provider`: `google` 또는 `kakao` (소문자) |
| 응답 | `302 Found` — Provider 인가 URL로 리다이렉트 |
| 인증 | 불필요 |

**리다이렉트 URL 구성 (Google 예시)**:
```
https://accounts.google.com/o/oauth2/v2/auth
  ?client_id={CLIENT_ID}
  &redirect_uri={REDIRECT_URI}
  &response_type=code
  &scope=openid email profile
  &state={CSRF_TOKEN}
```

### 3.2 OAuth 콜백

```
GET /api/v1/auth/oauth2/callback/{provider}?code={code}&state={state}
```

| 항목 | 값 |
|:-----|:---|
| Path Variable | `provider`: `google` 또는 `kakao` |
| Query Params | `code` (Authorization Code), `state` (CSRF 검증용) |
| 성공 응답 | `200 OK` — `AuthTokenResponse` (accessToken + tokenType) |
| 실패 응답 | `401 Unauthorized` — OAuth 인증 실패 시 `GlobalException` |
| RT 전달 | `Set-Cookie: refreshToken=eyJ...; Path=/; HttpOnly; Max-Age=604800` |

**성공 응답 Body**:
```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer"
}
```

> **Note**: 콜백 성공 시 프론트엔드로의 토큰 전달은 두 가지 전략 중 택일:
> - **전략 A (Redirect)**: 콜백 처리 후 프론트 URL로 302 리다이렉트 (`/auth/oauth2/success?token=xxx`)
> - **전략 B (Direct Response)**: 콜백 API가 직접 JSON 응답 반환
>
> 본 프로젝트에서는 **전략 A (Redirect)**를 채택합니다. SPA 특성상 서버가 프론트 URL로
> 리다이렉트하면서 Access Token을 query parameter로 전달하고, RT는 HttpOnly Cookie로 설정합니다.

### 3.3 콜백 성공 후 리다이렉트

```
302 Found
Location: {FRONT_BASE_URL}/auth/oauth2/success?accessToken={JWT}
Set-Cookie: refreshToken=eyJ...; Path=/; HttpOnly; Max-Age=604800
```

---

## 4. Provider별 설정

### 4.1 Google OAuth 2.0

| 항목 | 값 |
|:-----|:---|
| Authorization Endpoint | `https://accounts.google.com/o/oauth2/v2/auth` |
| Token Endpoint | `https://oauth2.googleapis.com/token` |
| UserInfo Endpoint | `https://www.googleapis.com/oauth2/v3/userinfo` |
| Scopes | `openid`, `email`, `profile` |
| 사용 필드 | `sub` → `providerId`, `email` → `email`, `name` → `nickname` |

### 4.2 Kakao OAuth 2.0

| 항목 | 값 |
|:-----|:---|
| Authorization Endpoint | `https://kauth.kakao.com/oauth/authorize` |
| Token Endpoint | `https://kauth.kakao.com/oauth/token` |
| UserInfo Endpoint | `https://kapi.kakao.com/v2/user/me` |
| Scopes | `profile_nickname`, `account_email` |
| 사용 필드 | `id` → `providerId`, `kakao_account.email` → `email`, `properties.nickname` → `nickname` |

### 4.3 application.yml 설정 구조

```yaml
oauth2:
  providers:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
      redirect-uri: http://localhost:8080/api/v1/auth/oauth2/callback/google
      authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
      token-uri: https://oauth2.googleapis.com/token
      user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
      scopes: openid,email,profile
    kakao:
      client-id: ${KAKAO_CLIENT_ID}
      client-secret: ${KAKAO_CLIENT_SECRET}
      redirect-uri: http://localhost:8080/api/v1/auth/oauth2/callback/kakao
      authorization-uri: https://kauth.kakao.com/oauth/authorize
      token-uri: https://kauth.kakao.com/oauth/token
      user-info-uri: https://kapi.kakao.com/v2/user/me
      scopes: profile_nickname,account_email

app:
  front-base-url: http://localhost:3000
```

> **보안**: `client-id`, `client-secret`은 반드시 환경변수로 관리합니다. application.yml에 하드코딩 금지.

---

## 5. 컴포넌트 설계

### 5.1 신규 클래스 목록

| 패키지 | 클래스 | 역할 |
|:-------|:-------|:-----|
| `config` | `OAuth2Properties` | `@ConfigurationProperties` 기반 Provider 설정 바인딩 |
| `controller.member` | `OAuthController` | OAuth 시작/콜백 엔드포인트 |
| `service.member` | `OAuthService` | OAuth 흐름 오케스트레이션 (code 교환 → 사용자 조회/생성 → JWT 발급) |
| `service.member` | `OAuthProviderClient` | 인터페이스. Provider별 HTTP 통신 추상화 |
| `service.member` | `GoogleOAuthClient` | Google Token/UserInfo API 호출 구현체 |
| `service.member` | `KakaoOAuthClient` | Kakao Token/UserInfo API 호출 구현체 |
| `dto` | `OAuthUserInfo` | Provider에서 가져온 사용자 정보 DTO |
| `dto` | `OAuthTokenResponse` | Provider로부터 받은 Token 응답 DTO |

### 5.2 `OAuthProviderClient` 인터페이스

```java
public interface OAuthProviderClient {
    AuthProvider getProvider();
    String getAuthorizationUrl(String state);
    OAuthTokenResponse getAccessToken(String code);
    OAuthUserInfo getUserInfo(String accessToken);
}
```

### 5.3 `OAuthService` 핵심 로직

```java
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final Map<AuthProvider, OAuthProviderClient> providerClients;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RedisService redisService;

    /**
     * 1. code로 Provider Access Token 취득
     * 2. Access Token으로 사용자 정보 조회
     * 3. DB에서 (authProvider, providerId)로 기존 회원 검색
     *    - 있으면 → 기존 회원으로 로그인
     *    - 없으면 → 신규 회원 자동 생성 (AUTO_REGISTER)
     * 4. JWT(AT + RT) 발급
     */
    public AuthTokenResponse processOAuthCallback(AuthProvider provider, String code) { ... }
}
```

### 5.4 자동 회원 가입 규칙

| 필드 | 값 |
|:-----|:---|
| `username` | `{provider}_{providerId}` (예: `google_1234567890`) |
| `password` | `null` (소셜 로그인 전용이므로 비밀번호 없음) |
| `email` | Provider에서 제공한 이메일 (nullable) |
| `nickname` | Provider에서 제공한 이름/닉네임 |
| `role` | `ROLE_USER` (기본값) |
| `authProvider` | `GOOGLE` 또는 `KAKAO` |
| `providerId` | Provider의 고유 사용자 식별자 |
| `isActive` | `true` (기본값) |

---

## 6. DB 변경 사항

### 6.1 `MemberRepository` 추가 메서드

```java
Optional<Member> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);
```

### 6.2 테이블 스키마 변경

기존 `members` 테이블 변경 없음. 이미 `auth_provider`, `provider_id` 컬럼 존재.

**추가 인덱스**:
```sql
CREATE INDEX idx_members_provider_providerid ON members (auth_provider, provider_id);
```

---

## 7. Gateway 변경 사항

`jym-api-gateway`에서 OAuth 관련 경로를 인증 없이 통과시켜야 합니다.

```yaml
# application.yml (gateway)
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service-oauth
          uri: lb://jym-member-auth-service
          predicates:
            - Path=/api/v1/auth/oauth2/**
          filters:
            - StripPrefix=0
```

**JWT 검증 제외 경로 추가**:
```
/api/v1/auth/oauth2/**  →  JWT 필터 스킵
```

---

## 8. CSRF / State 파라미터

- OAuth 요청 시 `state` 파라미터를 생성하여 CSRF 공격 방지.
- **생성**: Auth Service에서 `UUID.randomUUID()` 기반으로 생성.
- **저장**: Redis에 키 `OAUTH_STATE:{state}` (TTL: 5분).
- **검증**: 콜백 시 `state` 파라미터가 Redis에 존재하는지 확인 후 즉시 삭제.

---

## 9. 에러 처리

| 시나리오 | HTTP Status | Error Code |
|:---------|:------------|:-----------|
| 지원하지 않는 Provider | `400 Bad Request` | `ERR_UNSUPPORTED_PROVIDER` |
| Authorization Code 교환 실패 | `401 Unauthorized` | `ERR_OAUTH_TOKEN_EXCHANGE` |
| 사용자 정보 조회 실패 | `401 Unauthorized` | `ERR_OAUTH_USER_INFO` |
| State 파라미터 불일치 / 만료 | `401 Unauthorized` | `ERR_OAUTH_INVALID_STATE` |
| Provider 서버 통신 장애 | `502 Bad Gateway` | `ERR_OAUTH_PROVIDER_ERROR` |
| 이미 다른 Provider로 가입된 이메일 | `409 Conflict` | `ERR_OAUTH_EMAIL_CONFLICT` |

---

## 10. 보안 고려사항

1. **client-secret 관리**: 환경변수로만 관리. 절대 소스코드/설정파일에 하드코딩 금지.
2. **redirect_uri 검증**: Provider 콘솔에 등록된 redirect_uri만 허용.
3. **State 파라미터**: CSRF 방지를 위해 반드시 생성 및 검증.
4. **HTTPS**: Production 환경에서는 반드시 HTTPS 사용.
5. **Token 노출 최소화**: Access Token은 URL query parameter로 전달되므로 프론트에서 즉시 추출 후 URL 히스토리 정리 (`window.history.replaceState`).

---

## 11. 구현 참고사항

- **Spring Security OAuth2 Client 미사용**: Spring Security의 `spring-boot-starter-oauth2-client`를 사용하지 않고 **수동 구현**합니다. 이는 학습 목적과 MSA 구조에서의 유연성을 위한 결정입니다.
- **RestTemplate / WebClient**: Provider API 호출에 `RestTemplate` 또는 `WebClient` 사용. Spring Boot 3.x 기준 `RestClient` 권장.
- **Lombok 필수**: 모든 DTO, Config 클래스에 Lombok 적극 활용.
- **Builder 패턴**: 회원 자동 생성 시 `Member.builder()` 사용.
