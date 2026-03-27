# jym-front: 상품 검색 기능 상세 설계

> **목적**: 상품 목록 페이지(`/products`)에 검색 UI를 추가하여 키워드·가격 범위·카테고리·정렬 복합 조건 검색을 지원한다.
> **연관 서비스**: `jym-catalog-service` → `GET /api/v1/products/search`
> **연관 스펙**: `jym-catalog-service/04_MYBATIS_QUERYDSL_SEARCH_KR.md`, `02_PAGES_CATALOG_KR.md`
> **헌법 준수**: `00_architecture.md` — Composition API, Axios, Tailwind CSS

---

## 1. 개요

### 1.1 배경

현재 상품 목록 페이지(`/products`)는 카테고리 필터와 페이지네이션만 지원한다.
사용자가 특정 앨범명이나 아티스트명으로 상품을 찾거나, 가격 범위를 지정하여 탐색할 수 없다.
백엔드에 MyBatis 동적 SQL 기반 검색 API(`GET /api/v1/products/search`)가 추가되므로, 이를 활용하는 프론트엔드 검색 UI를 구현한다.

### 1.2 변경 범위

| 파일 | 변경 내용 |
|---|---|
| `types/catalog.ts` | `ProductSearchParams` 타입 추가 |
| `composables/useCatalog.ts` | `useProductSearch` composable 추가 |
| `pages/products/index.vue` | 검색 바 UI 추가, 검색/탐색 모드 분기, URL 동기화 |

### 1.3 설계 원칙

| 원칙 | 설명 |
|---|---|
| **두 가지 모드** | 일반 탐색(browse) 모드와 검색(search) 모드를 `isSearchMode` 플래그로 분기 |
| **URL 동기화** | 검색 상태를 query string에 반영하여 새로고침·뒤로가기·URL 공유 지원 |
| **기존 UI 무파괴** | 검색 바를 카테고리 탭 상단에 추가할 뿐, 기존 카테고리 탭·카드 그리드·페이지네이션은 그대로 유지 |
| **카테고리 연동** | 검색 모드에서도 카테고리 탭을 활용하여 "카테고리 내 검색" 지원 |

---

## 2. 연결 API

| 메서드 | 경로 | 용도 | 모드 |
|---|---|---|---|
| `GET /api/v1/products?page=&size=&categoryId=` | 상품 목록 | 일반 탐색 | browse |
| `GET /api/v1/categories` | 카테고리 목록 | 공통 (1회 로드) | 공통 |
| `GET /api/v1/products/search?keyword=&...` | 상품 검색 | 검색 | search |

### 2.1 검색 API 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `keyword` | string | N | - | 제목/아티스트 LIKE 검색 |
| `categoryId` | integer | N | - | 카테고리 필터 |
| `minPrice` | number | N | - | 최소 가격 (이상) |
| `maxPrice` | number | N | - | 최대 가격 (이하) |
| `page` | integer | N | 0 | 페이지 번호 (0-based) |
| `size` | integer | N | 12 | 페이지 크기 |
| `sort` | string | N | `createdAt,desc` | 정렬 기준 |

### 2.2 검색 API 응답 (기존 `ProductListResponse` 동일)

```json
{
  "content": [
    {
      "id": 1,
      "title": "Abbey Road",
      "artist": "The Beatles",
      "price": 25000.00,
      "thumbnailUrl": "https://..."
    }
  ],
  "totalElements": 3,
  "totalPages": 1
}
```

---

## 3. 타입 정의 — `types/catalog.ts`

기존 타입에 `ProductSearchParams`를 추가한다.

```typescript
export interface ProductSearchParams {
  keyword?: string
  categoryId?: number | null
  minPrice?: number
  maxPrice?: number
  page?: number
  size?: number
  sort?: string
}
```

---

## 4. 상태 관리 — `composables/useCatalog.ts`

### 4.1 `useProductSearch` composable 추가

기존 `useProducts`, `useCategories`, `useProductDetail`과 동일 패턴의 named export로 추가한다.

```typescript
export const useProductSearch = () => {
  const { $axios } = useNuxtApp()

  const searchResults = useState<ProductSummary[]>('search-results', () => [])
  const totalElements = useState<number>('search-total-elements', () => 0)
  const totalPages = useState<number>('search-total-pages', () => 0)
  const currentPage = useState<number>('search-current-page', () => 0)
  const isLoading = useState<boolean>('search-loading', () => false)
  const errorMessage = useState<string>('search-error', () => '')

  const searchProducts = async (params: ProductSearchParams) => {
    isLoading.value = true
    errorMessage.value = ''

    try {
      const queryParams: Record<string, string | number> = {}
      if (params.keyword) queryParams.keyword = params.keyword
      if (params.categoryId != null) queryParams.categoryId = params.categoryId
      if (params.minPrice != null) queryParams.minPrice = params.minPrice
      if (params.maxPrice != null) queryParams.maxPrice = params.maxPrice
      queryParams.page = params.page ?? 0
      queryParams.size = params.size ?? 12
      if (params.sort) queryParams.sort = params.sort

      const response = await ($axios as AxiosInstance).get<ProductListResponse>(
        '/api/v1/products/search',
        { params: queryParams },
      )

      searchResults.value = response.data.content
      totalElements.value = response.data.totalElements
      totalPages.value = response.data.totalPages
      currentPage.value = params.page ?? 0
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      searchResults.value = []
      totalElements.value = 0
      totalPages.value = 0
      errorMessage.value =
        error.response?.data?.message ?? '검색 결과를 불러오지 못했습니다.'
    } finally {
      isLoading.value = false
    }
  }

  const resetSearch = () => {
    searchResults.value = []
    totalElements.value = 0
    totalPages.value = 0
    currentPage.value = 0
    errorMessage.value = ''
  }

  return {
    searchResults,
    totalElements,
    totalPages,
    currentPage,
    isLoading,
    errorMessage,
    searchProducts,
    resetSearch,
  }
}
```

### 4.2 기존 composable — 변경 없음

`useProducts`, `useCategories`, `useCategoryAdmin`, `useProductDetail`은 수정하지 않는다.
일반 탐색 모드에서 기존 `useProducts`를, 검색 모드에서 `useProductSearch`를 사용한다.

---

## 5. UI 구성 — `pages/products/index.vue`

### 5.1 전체 레이아웃 구조

```
[페이지 헤더: "상품 목록" + 설명]

[에러 배너 (조건부)]

[검색 바 섹션]  ← NEW
  ┌─────────────────────────────────────────────────────────┐
  │ [키워드 입력] [최소가격] [최대가격] [정렬 드롭다운] [검색] [초기화] │
  └─────────────────────────────────────────────────────────┘

[카테고리 탭 섹션]  ← 기존 유지
  전체 | Rock | Pop | Jazz | Classical | ...

[상품 목록 정보]
  총 58개의 상품  |  1 / 5 페이지

[상품 카드 그리드]  ← 기존 유지 (데이터 소스만 분기)
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ 썸네일    │  │ 썸네일    │  │ 썸네일    │
  │ 앨범명    │  │ 앨범명    │  │ 앨범명    │
  │ 아티스트  │  │ 아티스트  │  │ 아티스트  │
  │ ₩ 가격   │  │ ₩ 가격   │  │ ₩ 가격   │
  └──────────┘  └──────────┘  └──────────┘

[페이지네이션]  ← 기존 유지
```

### 5.2 검색 바 상세

#### 입력 요소

| 요소 | 타입 | v-model | placeholder | 설명 |
|---|---|---|---|---|
| 키워드 | `text` | `searchKeyword` | "앨범명 또는 아티스트명으로 검색..." | Enter 키로도 검색 실행 |
| 최소 가격 | `number` | `searchMinPrice` | "₩ 0" | `min="0"`, `w-28` |
| 최대 가격 | `number` | `searchMaxPrice` | "₩ ∞" | `min="0"`, `w-28` |
| 정렬 | `select` | `searchSort` | - | 4개 옵션 (아래 참고) |

#### 정렬 드롭다운 옵션

| value | 표시 텍스트 |
|---|---|
| `createdAt,desc` | 최신순 |
| `price,asc` | 가격 낮은순 |
| `price,desc` | 가격 높은순 |
| `title,asc` | 이름순 |

#### 버튼

| 버튼 | 조건 | 동작 |
|---|---|---|
| **검색** | 항상 표시, 로딩 중 `disabled` | `handleSearch()` 실행 |
| **초기화** | `isSearchMode === true`일 때만 표시 | `handleSearchReset()` 실행, 탐색 모드로 복귀 |

#### 검색 실행 조건

키워드·최소가격·최대가격 중 **하나 이상** 입력되어야 검색이 실행된다.
세 가지 모두 비어있으면 검색 버튼 클릭을 무시한다.

#### 스타일

```
검색 바 전체: rounded-2xl border border-gray-200 bg-white p-4 shadow-sm
입력 필드:    rounded-lg border border-gray-300 px-3 py-2 text-sm
             focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500
검색 버튼:    rounded-lg bg-indigo-600 text-white hover:bg-indigo-700
초기화 버튼:  rounded-lg border border-gray-300 bg-white text-gray-700 hover:bg-gray-50
```

#### 반응형

| 뷰포트 | 레이아웃 |
|---|---|
| `sm` 이상 | 가로 한 줄 배치 (`flex-row items-end`) |
| `sm` 미만 | 세로 쌓임 (`flex-col gap-3`) |

### 5.3 두 가지 모드 분기

페이지는 `isSearchMode` ref로 모드를 구분하고, 모든 표시 데이터를 `computed`로 분기한다.

```typescript
const isSearchMode = ref(false)

const displayProducts = computed(() =>
  isSearchMode.value ? searchResults.value : products.value,
)
const displayTotalElements = computed(() =>
  isSearchMode.value ? searchTotalElements.value : totalElements.value,
)
const displayTotalPages = computed(() =>
  isSearchMode.value ? searchTotalPages.value : totalPages.value,
)
const displayCurrentPage = computed(() =>
  isSearchMode.value ? searchCurrentPage.value : currentPage.value,
)
const displayLoading = computed(() =>
  isSearchMode.value ? isSearchLoading.value : isProductLoading.value,
)
```

기존 `ProductCard`, `Pagination`, `CategoryTabs` 컴포넌트는 `display*` computed를 데이터 소스로 사용하므로 **변경 불필요**.

### 5.4 빈 결과 메시지

| 모드 | 상태 | 메시지 |
|---|---|---|
| 탐색 | 상품 0개 | "No products registered." + "선택한 조건에 맞는 상품이 아직 없습니다." |
| **검색** | **결과 0개** | **"검색 결과가 없습니다."** + **"다른 검색어나 조건으로 다시 시도해 보세요."** |

### 5.5 카테고리 탭 동작

| 모드 | 카테고리 탭 클릭 시 동작 |
|---|---|
| 탐색 (기존) | `page=0`으로 초기화 후 해당 카테고리 상품 조회 |
| **검색** | `selectedCategoryId` 변경 후 현재 검색 조건으로 **재검색** (카테고리 내 검색) |

---

## 6. URL 동기화

### 6.1 URL 형식

| 모드 | URL 예시 |
|---|---|
| 탐색 | `/products?page=0&categoryId=2` |
| 검색 | `/products?mode=search&keyword=Beatles&minPrice=10000&sort=price,asc&page=0` |

### 6.2 `mode=search` 파라미터

- URL에 `mode=search`가 있으면 검색 모드로 진입
- 없으면 일반 탐색 모드

### 6.3 검색 URL 동기화 함수

```typescript
const pushSearchQuery = async (page: number) => {
  const query: Record<string, string> = {
    mode: 'search',
    page: String(page),
    sort: searchSort.value,
  }
  if (searchKeyword.value) query.keyword = searchKeyword.value
  if (selectedCategoryId.value != null) query.categoryId = String(selectedCategoryId.value)
  if (searchMinPrice.value != null) query.minPrice = String(searchMinPrice.value)
  if (searchMaxPrice.value != null) query.maxPrice = String(searchMaxPrice.value)
  await router.push({ query })
}
```

### 6.4 URL → 상태 복원 (새로고침/뒤로가기)

페이지 마운트 시 `route.query.mode === 'search'`이면:
1. URL 파라미터에서 검색 조건 파싱
2. `isSearchMode = true` 설정
3. 각 검색 필드 ref에 값 복원
4. `searchProducts()` 호출

```typescript
const hydrateSearchFromRoute = async () => {
  if (route.query.mode !== 'search') return

  const keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  const minP = parseOptionalNumber(route.query.minPrice)
  const maxP = parseOptionalNumber(route.query.maxPrice)
  const sort = typeof route.query.sort === 'string' ? route.query.sort : 'createdAt,desc'
  const page = parsePage(route.query.page)

  if (!keyword.trim() && minP == null && maxP == null) return

  isSearchMode.value = true
  searchKeyword.value = keyword
  searchMinPrice.value = minP ?? undefined
  searchMaxPrice.value = maxP ?? undefined
  searchSort.value = sort
  selectedCategoryId.value = parseCategoryId(route.query.categoryId)

  await searchProducts({
    keyword: keyword.trim() || undefined,
    categoryId: selectedCategoryId.value,
    minPrice: searchMinPrice.value,
    maxPrice: searchMaxPrice.value,
    page,
    size: pageSize,
    sort: searchSort.value,
  })
}
```

### 6.5 `watch` — route 변경 감지

```typescript
watch(
  () => route.fullPath,
  async (path, prev) => {
    if (path === prev) return
    if (route.query.mode === 'search') {
      await hydrateSearchFromRoute()
    } else {
      isSearchMode.value = false
      await syncProductsFromRoute()
    }
  },
)
```

---

## 7. 이벤트 핸들러

### 7.1 `handleSearch`

```
[검색 버튼 클릭 or Enter]
  ├─ 키워드·최소가격·최대가격 모두 비어있음 → 무시 (return)
  └─ 하나라도 있음 → isSearchMode = true → pushSearchQuery(0)
```

### 7.2 `handleSearchReset`

```
[초기화 버튼 클릭]
  → isSearchMode = false
  → 모든 검색 필드 초기화 (keyword='', minPrice=undefined, maxPrice=undefined, sort='createdAt,desc')
  → resetSearch() (composable 상태 초기화)
  → router.push({ query: { page: '0', ...categoryId } })  (탐색 모드 URL로 복귀)
```

### 7.3 `handleCategoryChange` (변경)

```
[카테고리 탭 클릭]
  ├─ 탐색 모드 → 기존 동작 (pushBrowseQuery(0, nextCategoryId))
  └─ 검색 모드 → selectedCategoryId 변경 후 pushSearchQuery(0) (카테고리 내 재검색)
```

### 7.4 `handlePageChange` (변경)

```
[페이지 번호 클릭]
  ├─ 탐색 모드 → 기존 동작 (pushBrowseQuery(nextPage, selectedCategoryId))
  └─ 검색 모드 → pushSearchQuery(nextPage)
```

---

## 8. 라이프사이클

### 8.1 `onMounted`

```typescript
onMounted(async () => {
  if (route.query.mode === 'search') {
    await fetchCategories()
    await hydrateSearchFromRoute()
    return
  }

  syncBrowseStateFromRoute()
  await fetchCategories()
  await syncProductsFromRoute()
})
```

### 8.2 에러 표시

에러 배너는 세 가지 소스를 `||`로 합산:
- `categoryErrorMessage` — 카테고리 로드 실패
- `productErrorMessage` — 탐색 모드 상품 로드 실패
- `searchErrorMessage` — 검색 모드 검색 실패 (검색 모드일 때만)

---

## 9. 체크리스트

- [ ] `types/catalog.ts` — `ProductSearchParams` 타입 추가
- [ ] `composables/useCatalog.ts` — `useProductSearch` composable 추가
- [ ] `pages/products/index.vue` — 검색 바 UI (키워드, 가격 범위, 정렬, 검색/초기화 버튼)
- [ ] `pages/products/index.vue` — `isSearchMode` + `display*` computed 분기
- [ ] `pages/products/index.vue` — `handleSearch`, `handleSearchReset` 핸들러
- [ ] `pages/products/index.vue` — `handleCategoryChange`, `handlePageChange` 검색 모드 분기 추가
- [ ] `pages/products/index.vue` — URL 동기화 (`mode=search` query param)
- [ ] `pages/products/index.vue` — `onMounted` 검색 모드 hydration
- [ ] `pages/products/index.vue` — `watch(route.fullPath)` 검색/탐색 모드 분기
- [ ] 검색 결과 빈 상태 메시지 ("검색 결과가 없습니다.")
- [ ] 반응형 레이아웃 (모바일 세로 쌓임, 데스크탑 가로 배치)
