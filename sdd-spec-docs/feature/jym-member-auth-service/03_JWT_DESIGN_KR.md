# 03_JWT_DESIGN (인증 전략 명세)

## 1. 개요
MSA 환경에 최적화된 비대칭 키(RS256) 기반 JWT 전략 및 Redis 기반 리프레시 토큰 로테이션(RTR)을 정의합니다.

## 2. 키 관리 (Key Management)
- **알고리즘**: RS256 (RSA Signature with SHA-256)
- **비밀키 (Private Key)**: `jym-member-auth-service`에 위치. 토큰 **발급(서명)**에 사용.
- **공개키 (Public Key)**: `jym-api-gateway`와 공유. 토큰 **검증**에 사용.

## 3. 토큰 생명주기
### 3.1 액세스 토큰 (Access Token)
- **유효 기간**: 15 ~ 30분
- **페이로드**: `sub` (사용자 아이디), `userId` (고유번호), `role` (권한)
- **검증**: `jym-api-gateway`에서 일괄 처리

### 3.2 리프레시 토큰 (Refresh Token - RTR)
- **유효 기간**: 7일
- **저장소**: Redis (키: `RT:{username}`, 값: `{token}`)
- **로테이션 정책**: 액세스 토큰 재발급 시마다 리프레시 토큰도 함께 갱신하여 보안성 강화

## 4. 주요 컴포넌트
- **JwtProvider**: 토큰 발급 전용 유틸리티 (비밀키 사용)
- **JwtValidator**: 게이트웨이 전용 토큰 검증 유틸리티 (공개키 사용)
- **RedisService**: 리프레시 토큰 관리 및 블랙리스트 처리

## 5. MSA 워크플로우
1. 클라이언트 로그인 요청 -> **Auth Service**가 AT/RT 발급
2. 클라이언트 자원 요청 -> **Gateway**가 공개키로 AT 검증
3. **Gateway**가 검증된 사용자 정보를 HTTP 헤더(`X-User-Id`, `X-User-Role`)에 주입하여 백엔드에 전달
