# jym-front: 페이지별 상세 설계

---

## 1. 메인 페이지 `/`

**파일**: `pages/index.vue`

### UI 구성
- 히어로 섹션: 서비스명(Jymusic), 짧은 소개 문구
- CTA 버튼: "지금 시작하기" → `/auth/register` 이동
- 로그인 상태면 "내 프로필 보기" → `/me` 이동

### 특이사항
- 인증 불필요, 누구나 접근 가능

---

## 2. 회원가입 페이지 `/auth/register`

**파일**: `pages/auth/register.vue`

### 연결 API
`POST /api/v1/auth/register`

### 요청 Body
```json
{
  "username": "string",
  "password": "string (min 4자)",
  "nickname": "string",
  "email": "string (optional)"
}
```

### 성공 응답 `201`
→ `/auth/login` 페이지로 리다이렉트 + 성공 토스트 메시지

### UI 구성
| 필드 | 타입 | 유효성 검사 |
|---|---|---|
| 아이디 (username) | text | 필수 |
| 비밀번호 (password) | password | 필수, 최소 4자 |
| 닉네임 (nickname) | text | 필수 |
| 이메일 (email) | email | 선택 |
| 가입하기 버튼 | submit | - |

### 에러 처리
- `400` → 폼 하단에 `message` 필드 인라인 표시

---

## 3. 로그인 페이지 `/auth/login`

**파일**: `pages/auth/login.vue`

### 연결 API
`POST /api/v1/auth/login`

### 요청 Body
```json
{ "username": "string", "password": "string" }
```

### 성공 응답 `200`
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer" }
```
- `accessToken` → Pinia `useAuthStore`에 저장
- Refresh Token → 서버가 HttpOnly Cookie로 자동 설정
- → `/me` 페이지로 리다이렉트

### UI 구성
| 필드 | 타입 | 유효성 검사 |
|---|---|---|
| 아이디 (username) | text | 필수 |
| 비밀번호 (password) | password | 필수 |
| 로그인 버튼 | submit | - |
| 회원가입 링크 | link | `/auth/register` |

### 에러 처리
- `401` → "아이디 또는 비밀번호가 올바르지 않습니다." 인라인 표시

---

## 4. 내 프로필 페이지 `/me`

**파일**: `pages/me.vue`

### 연결 API
`GET /api/v1/members/me`
- Header: `Authorization: Bearer <accessToken>` (Axios 인터셉터 자동 주입)

### 성공 응답 `200`
```json
{
  "id": 1,
  "username": "hong",
  "nickname": "홍길동",
  "email": "hong@example.com",
  "role": "ROLE_USER"
}
```

### UI 구성
- 프로필 카드: 닉네임, 아이디, 이메일, 권한(role) 표시
- 로그아웃 버튼

### 로그아웃 흐름
```
POST /api/v1/auth/logout  (Bearer 헤더 + 쿠키 자동 전송)
  → 성공/실패 무관하게 Pinia store 초기화 (clearAuth())
  → `/` 이동
```

### 에러 처리
- `401` → `/auth/login` 리다이렉트 (미들웨어에서 사전 처리)

---

## 5. 공통 컴포넌트 / 유틸

### `stores/auth.ts` (Pinia)
```
// Composition API 스타일 (Options API 금지)
const accessToken = ref<string | null>(null)
const user = ref<AuthUser | null>(null)

const isLoggedIn = computed(() => accessToken.value !== null)

const setAuth(token, authUser)  → 로그인 성공 시 호출
const setToken(token)           → 토큰 갱신 성공 시 호출
const clearAuth()               → 로그아웃 / 갱신 실패 시 호출
```

### `plugins/axios.ts`

**요청 인터셉터**
- SSR 환경(`import.meta.server`)에서는 토큰 주입 없이 통과
- 클라이언트 환경: Pinia store의 `accessToken`을 `Authorization: Bearer <token>` 헤더에 주입

**응답 인터셉터 - 401 처리 (토큰 갱신 큐 패턴)**

```
401 응답 수신
  ├─ refresh-token 요청 자체가 401이면 → 강제 로그아웃
  ├─ 이미 갱신 중(isRefreshing = true)이면 → failedQueue에 추가하고 대기
  └─ 갱신 중이 아니면:
        isRefreshing = true
        POST /api/v1/auth/refresh-token (쿠키 자동 전송)
          ├─ 성공: 새 accessToken → Pinia store 저장
          │         failedQueue의 모든 요청 새 토큰으로 재시도
          │         원래 요청도 재시도
          └─ 실패: failedQueue 전부 reject
                    강제 로그아웃 (store 초기화 + /auth/login 이동)
```

중복 갱신 방지를 위한 모듈 레벨 변수:
- `isRefreshing: boolean`
- `failedQueue: Array<{ resolve, reject }>`
- `isLoggingOut: boolean` (로그아웃 중복 방지)

**axios 전역 설정**
- `baseURL`: `http://localhost:8080`
- `withCredentials: true` (Refresh Token Cookie 자동 전송)

---

### `stores/auth.ts` (Pinia) — 최신 정의
```typescript
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const user = ref<AuthUser | null>(null)

  const isLoggedIn = computed(() => accessToken.value !== null)

  const setAuth = (token: string, authUser: AuthUser) => { ... }
  const setToken = (token: string) => { ... }
  const clearAuth = () => { ... }

  return { accessToken, user, isLoggedIn, setAuth, setToken, clearAuth }
})
```
> **헌법 준수**: `<script setup lang="ts">` 기반 Composition API 스타일. Options API(`state`, `getters`, `actions` 객체) 사용 금지.

---

### `middleware/auth.ts`
- `/me` 등 인증 필요 페이지 진입 시 `isLoggedIn` 확인
- `false`이면 `/auth/login` 리다이렉트

---

## 6. 상품 목록 페이지 `/products`

**파일**: `pages/products/index.vue`

### 연결 API

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET /api/v1/categories` | 카테고리 목록 | 필터 탭 렌더링 (페이지 마운트 시 1회) |
| `GET /api/v1/products?page=&size=&categoryId=` | 상품 목록 | 카드 그리드 렌더링 |

### 쿼리 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `page` | integer | `0` | 페이지 번호 (0-indexed) |
| `size` | integer | `12` | 한 페이지 상품 수 |
| `categoryId` | integer | (없음) | 카테고리 필터, 미선택 시 전체 조회 |

### 성공 응답 (`200`) — 상품 목록

```json
{
  "content": [
    {
      "id": 1,
      "title": "Abbey Road",
      "artist": "The Beatles",
      "price": 29000,
      "thumbnailUrl": "https://..."
    }
  ],
  "totalElements": 58,
  "totalPages": 5
}
```

### UI 구성

#### 레이아웃 구조
```
[카테고리 필터 탭 바]
  전체 | Rock | Pop | Jazz | Classical | ...

[상품 카드 그리드]  ← 3열(모바일 1열, 태블릿 2열, PC 3열)
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ 썸네일    │  │ 썸네일    │  │ 썸네일    │
  │ 앨범명    │  │ 앨범명    │  │ 앨범명    │
  │ 아티스트  │  │ 아티스트  │  │ 아티스트  │
  │ ₩ 가격   │  │ ₩ 가격   │  │ ₩ 가격   │
  └──────────┘  └──────────┘  └──────────┘

[페이지네이션]  ← 이전 / 1 2 3 ... / 다음
```

#### 상품 카드 컴포넌트 (`components/products/ProductCard.vue`)

| 요소 | 설명 |
|---|---|
| 썸네일 이미지 | `thumbnailUrl`, 없을 경우 기본 앨범 이미지 표시 |
| 앨범명(`title`) | 텍스트 1줄 말줄임 |
| 아티스트(`artist`) | 회색 보조 텍스트 |
| 가격(`price`) | `₩ 29,000` 형식 (한국 원화 포맷) |
| 카드 전체 클릭 | `navigateTo('/products/{id}')` |
| 호버 효과 | 카드 그림자 상승 (`hover:shadow-lg`) |

#### 카테고리 필터 탭
- "전체" 탭 기본 선택 (선택 시 `categoryId` 파라미터 제거)
- 탭 선택 시 `page=0` 으로 초기화하며 상품 목록 재조회
- 선택된 탭: `border-b-2 border-indigo-600 text-indigo-600`

#### 페이지네이션
- `totalPages`가 1 이하이면 미표시
- 최대 5개 페이지 번호 표시, 앞/뒤 "이전" / "다음" 버튼
- 현재 페이지: `bg-indigo-600 text-white`

### 로딩 / 에러 상태

| 상태 | 처리 |
|---|---|
| API 호출 중 | 카드 그리드 영역에 스켈레톤 UI 표시 (12개 placeholder) |
| 상품 0개 | "등록된 상품이 없습니다." 빈 상태 메시지 |
| `400` | 페이지 상단 에러 배너 표시 |

### 타입 정의 (`types/catalog.ts`)

```typescript
export interface ProductSummary {
  id: number
  title: string
  artist: string
  price: number
  thumbnailUrl: string | null
}

export interface ProductListResponse {
  content: ProductSummary[]
  totalElements: number
  totalPages: number
}

export interface Category {
  id: number
  name: string
}
```

### 상태 관리 (`composables/useCatalog.ts`)

```typescript
// useProducts: 상품 목록 조회
const products = ref<ProductSummary[]>([])
const totalPages = ref(0)
const currentPage = ref(0)
const selectedCategoryId = ref<number | null>(null)
const isLoading = ref(false)

async function fetchProducts(): Promise<void> { ... }

// useCategories: 카테고리 목록 조회 (캐싱)
const categories = ref<Category[]>([])
async function fetchCategories(): Promise<void> { ... }
```

> `useCatalog.ts` 내 `useProducts`, `useCategories`를 각각 named export로 분리.
> Pinia store 불필요 — 페이지 단위 로컬 상태로 관리.

### URL 동기화
- 카테고리 필터, 현재 페이지는 URL 쿼리 파라미터(`?page=0&categoryId=2`)와 동기화
- 뒤로가기 시 이전 필터/페이지 상태 복원 (`useRoute`, `useRouter` 활용)

---

## 7. 상품 상세 페이지 `/products/[id]`

**파일**: `pages/products/[id].vue`

### 연결 API

`GET /api/v1/products/{productId}`

### 성공 응답 (`200`)

```json
{
  "id": 1,
  "title": "Abbey Road",
  "artist": "The Beatles",
  "description": "1969년 발매된 비틀즈의 명반...",
  "price": 29000,
  "stockQuantity": 15,
  "imageUrl": "https://..."
}
```

### 타입 정의 (`types/catalog.ts` 추가)

```typescript
export interface ProductDetail {
  id: number
  title: string
  artist: string
  description: string
  price: number
  stockQuantity: number
  imageUrl: string | null
}
```

### UI 구성

#### 레이아웃 구조
```
[← 목록으로 돌아가기]

┌─────────────────────┬──────────────────────────────┐
│                     │  앨범명 (title)                │
│    상품 이미지       │  아티스트 (artist)             │
│    (imageUrl)       │  ─────────────────────────── │
│                     │  ₩ 29,000                    │
│                     │  재고: 15개 남음               │
│                     │                              │
│                     │  [주문하기]                    │
└─────────────────────┴──────────────────────────────┘

[상품 상세 설명]
description 텍스트 영역 (멀티라인, whitespace-pre-wrap)
```

#### 각 UI 요소 상세

| 요소 | 규칙 |
|---|---|
| 상품 이미지 | `imageUrl`, 없으면 기본 앨범 커버 이미지 표시 |
| 앨범명 | `text-2xl font-bold` |
| 아티스트 | `text-gray-500 text-lg` |
| 가격 | `text-2xl font-semibold text-indigo-600`, `₩` + 천 단위 구분 포맷 |
| 재고 표시 | `stockQuantity > 0` → "재고: {n}개 남음" (초록), `0` → "품절" (빨강 + 주문 버튼 비활성화) |
| "주문하기" 버튼 | 클릭 시 주문 서비스 연동 (현재 단계: 클릭 시 로그인 여부 확인 후 미구현 안내 toast) |
| "← 목록으로 돌아가기" | `navigateTo('/products')` 또는 `router.back()` |

#### 로딩 / 에러 상태

| 상태 | 처리 |
|---|---|
| API 호출 중 | 이미지 + 텍스트 영역 스켈레톤 UI 표시 |
| `404` | "상품을 찾을 수 없습니다." 메시지 + 목록으로 이동 버튼 |
| 기타 오류 | 에러 배너 표시 |

### "주문하기" 버튼 동작 흐름 (현 단계)

```
[주문하기] 클릭
  ├─ 비로그인 상태 → toast("로그인이 필요합니다.") + /auth/login 이동
  └─ 로그인 상태 → toast("주문 기능은 준비 중입니다.") [order-service 연동 시 확장]
```

> 향후 `jym-order-service` 연동 시 이 항목을 `POST /api/v1/orders` 호출로 교체.

---

## 8. 공통 컴포넌트 추가분 (상품 관련)

### `components/products/ProductCard.vue`
- Props: `product: ProductSummary`
- 상품 목록 그리드에서 사용

### `components/products/CategoryTabs.vue`
- Props: `categories: Category[]`, `modelValue: number | null`
- Emit: `update:modelValue`
- "전체" 포함한 탭 목록 렌더링

### `components/products/Pagination.vue`
- Props: `currentPage: number`, `totalPages: number`
- Emit: `change(page: number)`

### `composables/useCatalog.ts`
- `useProducts()`: 상품 목록 조회, 페이징, 카테고리 필터 로직
- `useCategories()`: 카테고리 목록 조회 (컴포저블 내 메모이제이션)
- `useProductDetail(id: number)`: 상품 상세 조회
