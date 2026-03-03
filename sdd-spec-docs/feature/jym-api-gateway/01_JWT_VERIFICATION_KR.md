#완성

# 03_JWT_VERIFICATION (게이트웨이 보안 설계)

## 1. 목적

수신된 JWT를 검증하고 사용자 정보를 하위 마이크로서비스로 전파하는 로직을 정의합니다.

## 2. 핵심 컴포넌트: `JwtValidator`

- **책임**: **공개키 (Public Key)**를 사용하여 액세스 토큰의 RS256 서명을 검증합니다.
- **위치**: `jym-api-gateway`
- **입력**: `Authorization: Bearer <token>` 헤더
- **출력**: 유효성 결과(Boolean) 및 추출된 클레임 정보

## 3. 글로벌 필터 로직

1. 수신된 모든 요청을 가로챕니다.
2. 인증이 필요한 경로인지 확인합니다 (로그인/회원가입 등 제외).
3. `Authorization` 헤더에서 JWT를 추출합니다.
4. 서명 및 만료 여부를 검증합니다.
5. 유효한 경우, `userId`, `username`, `role`을 추출합니다.
6. **헤더 주입 (Header Injection)**:
   - `X-User-Id`: 사용자의 내부 고유 ID
   - `X-User-Name`: 사용자의 아이디
   - `X-User-Role`: 사용자의 권한 수준
7. 수정된 요청을 대상 서비스(Member, Catalog 등)로 전달합니다.

## 4. 오류 처리

- **401 Unauthorized**: 토큰이 없거나, 만료되었거나, 서명이 유효하지 않은 경우 발생
- `GlobalExceptionHandler`를 통해 통합된 오류 응답 형식을 반환합니다.
