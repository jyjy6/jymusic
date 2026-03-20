# 01_JWT_VERIFICATION (게이트웨이 보안 설계)

## 1. 목적

수신된 JWT를 검증하고 사용자 정보를 하위 마이크로서비스로 전파하는 로직을 정의합니다.

## 2. 핵심 컴포넌트: `JwtValidator`

- **책임**: **공개키 (Public Key)**를 사용하여 액세스 토큰의 RS256 서명을 검증합니다.
- **위치**: `jym-api-gateway`
- **입력**: `Authorization: Bearer <token>` 헤더
- **출력**: 유효성 결과(Boolean) 및 추출된 클레임 정보
- **특이 사항**: 순수 Java 유틸리티 클래스로, Servlet/Reactive 환경에 무관하게 동작합니다.

## 3. 글로벌 필터 로직 (`GlobalFilter` 기반)

> **기술 스택**: Spring Cloud Gateway Reactive — `GlobalFilter` + `Ordered` 인터페이스 구현

1. 수신된 모든 요청을 `ServerWebExchange`를 통해 가로챕니다.
2. 인증이 필요한 경로인지 확인합니다 (로그인/회원가입 등 제외).
3. `ServerHttpRequest`의 `Authorization` 헤더에서 JWT를 추출합니다.
4. 서명 및 만료 여부를 검증합니다.
5. 유효한 경우, `userId`, `username`, `role`을 추출합니다.
6. **헤더 주입 (Header Injection)** — `ServerHttpRequest.mutate().header(...)` 사용:
   - `X-User-Id`: 사용자의 내부 고유 ID
   - `X-User-Name`: 사용자의 아이디
   - `X-User-Role`: 사용자의 권한 수준
7. 변경된 `ServerWebExchange`를 `GatewayFilterChain`에 전달하여 대상 서비스로 라우팅합니다.

### 3.1 필터 실행 순서

- `getOrder()` 반환값: `-1` (Security 필터 체인 이후, 라우팅 이전 실행)

### 3.2 제외 경로

| 경로 패턴 | 사유 |
| :--- | :--- |
| `/api/v1/auth/**` | 로그인/회원가입/토큰 갱신 |
| `/swagger-ui/**` | API 문서 UI |
| `/v3/api-docs/**` | OpenAPI 스펙 |
| `/openapi.yaml` | OpenAPI 정의 파일 |

## 4. 오류 처리

- **401 Unauthorized**: 토큰이 없거나, 만료되었거나, 서명이 유효하지 않은 경우 발생
- 응답 방식: `ServerHttpResponse`에 `DataBuffer`를 통해 JSON 형식으로 직접 작성
- 응답 형식: `{"status":401,"code":"ERR_UNAUTHORIZED","message":"...","timestamp":"..."}`
- `GatewayFilterChain`을 호출하지 않고 `Mono<Void>`를 즉시 반환하여 요청을 종료합니다.
