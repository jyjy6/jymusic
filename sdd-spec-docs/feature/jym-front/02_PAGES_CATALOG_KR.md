# jym-front: 상품 카탈로그 페이지 상세 설계

> **포함 도메인**: 상품 목록, 상품 상세, 카탈로그 공통 컴포넌트/타입/컴포저블
> **연관 서비스**: `jym-catalog-service` → `GET /api/v1/products`, `GET /api/v1/categories`

---

## 1. 상품 목록 페이지 `/products`

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

### URL 동기화
- 카테고리 필터, 현재 페이지는 URL 쿼리 파라미터(`?page=0&categoryId=2`)와 동기화
- 뒤로가기 시 이전 필터/페이지 상태 복원 (`useRoute`, `useRouter` 활용)

---

## 2. 상품 상세 페이지 `/products/[id]`

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
| "주문하기" 버튼 | 클릭 시 로그인 여부 확인 후 처리 (아래 흐름 참고) |
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

## 3. 타입 정의 (`types/catalog.ts`)

```typescript
// 상품 목록용 요약 타입
export interface ProductSummary {
  id: number
  title: string
  artist: string
  price: number
  thumbnailUrl: string | null
}

// 목록 API 응답 페이지 타입
export interface ProductListResponse {
  content: ProductSummary[]
  totalElements: number
  totalPages: number
}

// 카테고리 타입
export interface Category {
  id: number
  name: string
}

// 상품 상세 타입
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

---

## 4. 상태 관리 (`composables/useCatalog.ts`)

```typescript
// useProducts: 상품 목록 조회
const products = ref<ProductSummary[]>([])
const totalPages = ref(0)
const currentPage = ref(0)
const selectedCategoryId = ref<number | null>(null)
const isLoading = ref(false)

async function fetchProducts(): Promise<void> { ... }

// useCategories: 카테고리 목록 조회 (컴포저블 내 메모이제이션)
const categories = ref<Category[]>([])
async function fetchCategories(): Promise<void> { ... }

// useProductDetail: 상품 상세 조회
async function useProductDetail(id: number): Promise<ProductDetail> { ... }
```

> `useProducts`, `useCategories`, `useProductDetail`을 각각 named export로 분리.
> Pinia store 불필요 — 페이지 단위 로컬 상태로 관리.

---

## 5. 공통 컴포넌트 (상품 관련)

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
