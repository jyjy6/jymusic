# jym-front: 프론트엔드 개요 및 페이지 설계

## 1. 기술 스택

| 항목 | 기술 |
|---|---|
| 프레임워크 | Nuxt 4 (Vue 3, TypeScript) |
| 스타일링 | Tailwind CSS |
| API 통신 | Axios |
| 상태 관리 | Pinia |
| 인증 방식 | JWT (Access Token: 메모리/Pinia, Refresh Token: HttpOnly Cookie) |

## 2. API 연결 대상

모든 API 요청은 **API Gateway** 단일 엔드포인트로만 전송합니다.

```
http://localhost:8080  ←  jym-api-gateway
```

프론트에서 백엔드 서비스(member-auth, catalog 등)의 포트를 직접 알아서는 안 됩니다.

## 3. 페이지 목록

| 경로 | 페이지 | 인증 필요 | 목적 |
|---|---|---|---|
| `/` | 메인(홈) | 불필요 | 서비스 소개, 네비게이션 |
| `/auth/register` | 회원가입 | 불필요 | POST `/api/v1/auth/register` |
| `/auth/login` | 로그인 | 불필요 | POST `/api/v1/auth/login` |
| `/me` | 내 프로필 | **필요** | GET `/api/v1/members/me` |
| `/products` | 상품 목록 | 불필요 | GET `/api/v1/products`, GET `/api/v1/categories` |
| `/products/[id]` | 상품 상세 | 불필요 | GET `/api/v1/products/{id}` |

## 4. 인증 흐름

```
[로그인 성공]
  → Access Token → Pinia store (메모리)
  → Refresh Token → HttpOnly Cookie (서버가 자동 설정)

[인증 필요 페이지 접근]
  → Pinia에 Access Token 없음 → /auth/login 리다이렉트

[API 요청]
  → Axios 인터셉터가 Authorization: Bearer <token> 자동 주입
```

## 5. 레이아웃

- **공통 레이아웃** (`layouts/default.vue`): 상단 네비게이션 바 포함
  - 로그인 상태: 사용자명 표시 + 로그아웃 버튼
  - 비로그인 상태: 로그인 / 회원가입 링크

## 6. 에러 처리

- API 응답 `401` → Access Token 만료로 간주 → 로그인 페이지 리다이렉트
- API 응답 `400` / `409` 등 → 해당 폼 하단에 에러 메시지 인라인 표시
- 공통 에러 응답 구조 (`GlobalExceptionHandler` 기준):
  ```json
  { "status": 400, "code": "ERR_XXX", "message": "..." }
  ```
