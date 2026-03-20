# 02_TEST_SPEC (API Gateway)

## 1. 개요

이 문서는 `jym-api-gateway`의 단위 테스트 명세를 정의합니다.

게이트웨이는 **비즈니스 로직을 포함하지 않으며**, 주요 책임은 다음과 같습니다:
- JWT 서명 검증 (`JwtValidator`)
- 사용자 컨텍스트 헤더 주입 (`JwtVerificationFilter` — `GlobalFilter`)
- CORS 정책 적용 (`SecurityConfig`)
- 경로 기반 라우팅 (`GatewayRouteConfig`)

라우팅 테스트(다운스트림 서비스로의 실제 HTTP 전달)는 실제 서비스가 필요하므로
`00_architecture.md §2.3`에 따라 통합 테스트로 보류합니다.

---

## 2. 테스트 레이어 및 도구

| 레이어 | 어노테이션 / 도구 | 범위 |
| :--- | :--- | :--- |
| JwtValidator | `@ExtendWith(MockitoExtension.class)` | 토큰 서명 검증 |
| JwtVerificationFilter | `WebTestClient` + `@SpringBootTest(webEnvironment = RANDOM_PORT)` 또는 `MockServerWebExchange` | GlobalFilter 동작 검증 |
| CORS 설정 | `@SpringBootTest` + `WebTestClient` | Preflight 요청에 대한 CORS 헤더 검증 |

---

## 3. JwtValidator 테스트

### `JwtValidatorTest`

생성자가 `Resource`로부터 PEM 공개키를 읽습니다. 테스트에서는 RSA 키쌍을
프로그래밍 방식으로 생성하고 `ByteArrayResource`로 주입합니다 — 실제 키 파일 불필요.

> `JwtValidator`는 순수 Java 유틸리티이므로, WebMvc/WebFlux 전환에 따른 테스트 변경은 없습니다.

#### `validateToken(String token)`

| # | 시나리오 | 입력 | 기대값 |
| :--- | :--- | :--- | :--- |
| JV-01 | 유효한 토큰 | 미래 만료 시간을 가진 RS256 서명 JWT | `true` 반환 |
| JV-02 | 만료된 토큰 | 과거 만료 시간을 가진 JWT | `false` 반환 |
| JV-03 | 다른 키로 서명된 토큰 | 다른 RSA 키쌍으로 서명된 JWT | `false` 반환 |
| JV-04 | 위·변조된 토큰 문자열 | 페이로드를 수동으로 수정한 JWT | `false` 반환 |
| JV-05 | null 또는 빈 문자열 | `null`, `""` | `false` 반환 |

#### `getClaims(String token)`

| # | 시나리오 | 입력 | 기대값 |
| :--- | :--- | :--- | :--- |
| JC-01 | 유효한 토큰 — claims 추출 | `userId`, `role`, `sub`를 포함한 유효한 JWT | 올바른 필드 값을 가진 `Claims` 객체 반환 |
| JC-02 | 유효하지 않은 토큰 — 예외 발생 | 위·변조 또는 만료 토큰 | `Exception` 발생 |

---

## 4. JwtVerificationFilter 테스트 (GlobalFilter)

### `JwtVerificationFilterTest`

`WebTestClient`와 `@SpringBootTest(webEnvironment = RANDOM_PORT)`를 사용하여
실제 Netty 서버에서의 `GlobalFilter` 동작을 검증합니다.

또는 경량 단위 테스트를 위해 `MockServerWebExchange`와 `MockGatewayFilterChain`을 사용할 수 있습니다.
`JwtValidator`는 `@Mock`으로 주입합니다.

#### 경로 제외 로직 (shouldSkip)

| # | 경로 | 기대값 |
| :--- | :--- | :--- |
| SF-01 | `/api/v1/auth/login` | 필터 건너뜀 — `chain.filter()` 즉시 호출 |
| SF-02 | `/api/v1/auth/register` | 필터 건너뜀 |
| SF-03 | `/api/v1/auth/refresh-token` | 필터 건너뜀 |
| SF-04 | `/swagger-ui/index.html` | 필터 건너뜀 |
| SF-05 | `/api/v1/members/me` | 필터 적용 |
| SF-06 | `/api/v1/orders` | 필터 적용 |
| SF-07 | `/api/v1/products` | 필터 적용 |

#### JWT 검증 및 헤더 주입 (`filter()`)

| # | 시나리오 | 사전 조건 | 기대값 |
| :--- | :--- | :--- | :--- |
| FI-01 | `Authorization` 헤더 없음 | `Authorization` 헤더 미존재 | 상태코드 401, 바디에 `ERR_UNAUTHORIZED` 포함 |
| FI-02 | `Bearer ` 접두사 없는 헤더 | `Authorization: JustToken abc` | 상태코드 401 |
| FI-03 | 유효하지 않은 토큰 | `validateToken` → `false` | 상태코드 401 |
| FI-04 | 유효한 토큰 — 필터 통과 | `validateToken` → `true`, 유효한 claims | `chain.filter()` 호출됨; 다운스트림 요청에 `X-User-Id`, `X-User-Name`, `X-User-Role` 헤더 추가됨 |
| FI-05 | 헤더 주입 값 정확성 검증 | claims: `userId=1`, `sub=testuser`, `role=ROLE_USER` | 다운스트림 요청: `X-User-Id=1`, `X-User-Name=testuser`, `X-User-Role=ROLE_USER` |

### 테스트 구현 참고

```java
// 경량 단위 테스트 예시
MockServerHttpRequest request = MockServerHttpRequest
        .get("/api/v1/orders")
        .header("Authorization", "Bearer " + validToken)
        .build();
MockServerWebExchange exchange = MockServerWebExchange.from(request);

StepVerifier.create(filter.filter(exchange, mockChain))
        .verifyComplete();
```

---

## 5. CORS 설정 테스트

### `CorsConfigTest`

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient` 사용.
`SecurityWebFilterChain`의 CORS 동작을 검증합니다.

> **주의**: `GatewayRouteConfig`의 `@Value` 주입 실패를 방지하기 위해
> `application-test.properties`에 더미 `services.*` URL을 반드시 설정해야 합니다.

| # | 시나리오 | 요청 | 기대값 |
| :--- | :--- | :--- | :--- |
| CO-01 | 허용된 Origin의 Preflight → CORS 헤더 반환 | `OPTIONS` + `Origin: http://localhost:3000` | `Access-Control-Allow-Origin: http://localhost:3000` |
| CO-02 | 허용되지 않은 Origin → CORS 헤더 없음 | `OPTIONS` + `Origin: http://evil.com` | `Access-Control-Allow-Origin` 헤더 없음 |
| CO-03 | credentials 허용 여부 확인 | 유효한 CORS Preflight | `Access-Control-Allow-Credentials: true` |
| CO-04 | 허용된 HTTP 메서드 목록 확인 | `Access-Control-Request-Method: DELETE` | `Access-Control-Allow-Methods`에 `DELETE` 포함 |

### 테스트 구현 참고

```java
// WebTestClient 기반 CORS 테스트 예시
webTestClient.options()
        .uri("/api/v1/products")
        .header("Origin", "http://localhost:3000")
        .header("Access-Control-Request-Method", "GET")
        .exchange()
        .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000")
        .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
```

---

## 6. 테스트 네이밍 규칙

- 테스트 클래스: `{대상클래스}Test.java`
- 테스트 메서드: `{메서드명}_{시나리오}_{기대결과}`
- `@DisplayName`으로 한국어 가독성 설명을 작성합니다.

---

## 7. 테스트 범위 외 (보류)

- 라우팅 테스트 (다운스트림 서비스로의 실제 HTTP 전달)
- 로드 밸런싱 또는 서킷 브레이커 테스트
- Rate Limiting 테스트 (현재 미구현)
