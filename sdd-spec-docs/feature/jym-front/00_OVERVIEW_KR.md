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

| 경로 | 페이지 | 인증 필요 | 목적 | 상세 스펙 |
|---|---|---|---|---|
| `/` | 메인(홈) | 불필요 | 서비스 소개, 네비게이션 | `01_PAGES_AUTH_KR.md` |
| `/auth/register` | 회원가입 | 불필요 | POST `/api/v1/auth/register` | `01_PAGES_AUTH_KR.md` |
| `/auth/login` | 로그인 | 불필요 | POST `/api/v1/auth/login` | `01_PAGES_AUTH_KR.md` |
| `/me` | 내 프로필 | **필요** | GET `/api/v1/members/me` | `01_PAGES_AUTH_KR.md` |
| `/products` | 상품 목록 | 불필요 | GET `/api/v1/products`, GET `/api/v1/categories` | `02_PAGES_CATALOG_KR.md` |
| `/products/[id]` | 상품 상세 | 불필요 | GET `/api/v1/products/{id}` | `02_PAGES_CATALOG_KR.md` |
| `/admin/products/new` | 상품 등록 | **ROLE_ADMIN** | POST `/api/v1/products` | `03_PAGES_CATALOG_ADMIN_KR.md` |
| `/admin/products/[id]/edit` | 상품 수정 | **ROLE_ADMIN** | PUT `/api/v1/products/{id}` | `03_PAGES_CATALOG_ADMIN_KR.md` |
| `/me/orders` | 내 주문 목록 | **필요** | GET `/api/v1/orders` + SSE 구독 | `07_PAGES_MY_ORDERS_KR.md` |
| `/me/orders/[id]` | 내 주문 상세 | **필요** | GET `/api/v1/orders/{id}` + SSE 구독 | `07_PAGES_MY_ORDERS_KR.md` |
| `/admin/orders` | 전체 주문 검색·목록 | **ROLE_ADMIN** | GET `/api/v1/admin/orders` + SSE 구독 | `08_PAGES_ADMIN_ORDERS_KR.md` |
| `/admin/orders/[id]` | 주문 상세·상태 변경 | **ROLE_ADMIN** | GET/PATCH `/api/v1/admin/orders/{id}` | `08_PAGES_ADMIN_ORDERS_KR.md` |

## 4. 스펙 파일 구조

| 파일 | 포함 도메인 |
|---|---|
| `01_PAGES_AUTH_KR.md` | 메인, 회원가입, 로그인, 내 프로필, Axios/Pinia/미들웨어 공통 설정 |
| `02_PAGES_CATALOG_KR.md` | 상품 목록, 상품 상세, 카탈로그 타입/컴포저블/컴포넌트 |
| `03_PAGES_CATALOG_ADMIN_KR.md` | 상품 등록/수정, Presigned URL 업로드, FileUpload 컴포넌트, 관리자 미들웨어 |
| `07_PAGES_MY_ORDERS_KR.md` | 내 주문 목록/상세, OrderStatusStepper, 상태 탭 필터, 주문 취소 |
| `08_PAGES_ADMIN_ORDERS_KR.md` | 운영자 주문 검색/페이징, 상태 변경 패널, Pagination 컴포넌트 |
| `09_SSE_NOTIFICATION_CLIENT_KR.md` | EventSource 싱글턴 매니저, NotificationBell, Pinia notifications 스토어 |

## 5. 인증 흐름

```
[로그인 성공]
  → Access Token → Pinia store (메모리)
  → Refresh Token → HttpOnly Cookie (서버가 자동 설정)

[인증 필요 페이지 접근]
  → Pinia에 Access Token 없음 → /auth/login 리다이렉트

[API 요청]
  → Axios 인터셉터가 Authorization: Bearer <token> 자동 주입
```

## 6. 레이아웃

- **공통 레이아웃** (`layouts/default.vue`): 상단 네비게이션 바 포함
  - 로그인 상태: 사용자명 표시 + 로그아웃 버튼
  - 비로그인 상태: 로그인 / 회원가입 링크

## 7. 에러 처리

- API 응답 `401` → Access Token 만료로 간주 → 로그인 페이지 리다이렉트
- API 응답 `400` / `409` 등 → 해당 폼 하단에 에러 메시지 인라인 표시
- 공통 에러 응답 구조 (`GlobalExceptionHandler` 기준):
  ```json
  { "status": 400, "code": "ERR_XXX", "message": "..." }
  ```
