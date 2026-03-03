#완성
# 00_OAS_PLAN (API 게이트웨이)

## 1. 목적
`jym-api-gateway`의 라우팅 전략과 글로벌 보안 필터를 정의합니다. 모든 클라이언트 요청의 단일 진입점 역할을 수행합니다.

## 2. 라우팅 규칙 (경로 전달)
- `/api/v1/auth/**` -> `jym-member-auth-service`
- `/api/v1/members/**` -> `jym-member-auth-service`
- `/api/v1/products/**` -> `jym-catalog-service`
- `/api/v1/categories/**` -> `jym-catalog-service`
- `/api/v1/orders/**` -> `jym-order-service`
- `/api/v1/payments/**` -> `jym-payment-service`

## 3. 전역 책임 (Global Responsibilities)
- **인증**: 보호된 경로에 대해 헤더의 JWT 토큰을 검증합니다.
- **CORS 설정**: Nuxt 4 프론트엔드에 대한 교차 출처 리소스 공유를 관리합니다.
- **오류 처리**: 하위 서비스가 응답하지 않을 경우 `GlobalExceptionHandler`를 사용하여 통합된 오류 응답을 반환합니다.
