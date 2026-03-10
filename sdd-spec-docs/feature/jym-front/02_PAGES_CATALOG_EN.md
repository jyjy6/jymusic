# jym-front: Product Catalog Pages — Detailed Design

> **Domain**: Product list, Product detail, Catalog common components/types/composables
> **Related service**: `jym-catalog-service` → `GET /api/v1/products`, `GET /api/v1/categories`

---

## 1. Products List Page `/products`

**File**: `pages/products/index.vue`

### API Integration

| Method | Path | Purpose |
|---|---|---|
| `GET /api/v1/categories` | Category list | Filter tab rendering (once on mount) |
| `GET /api/v1/products?page=&size=&categoryId=` | Product list | Card grid rendering |

### Query Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | integer | `0` | Page number (0-indexed) |
| `size` | integer | `12` | Items per page |
| `categoryId` | integer | (none) | Category filter; omit for all |

### Success Response (`200`) — Product List

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

### UI Structure

#### Layout
```
[Category filter tab bar]
  All | Rock | Pop | Jazz | Classical | ...

[Product card grid]  ← 3 columns (1 on mobile, 2 on tablet, 3 on PC)
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Thumbnail│  │ Thumbnail│  │ Thumbnail│
  │ Album    │  │ Album    │  │ Album    │
  │ Artist   │  │ Artist   │  │ Artist   │
  │ ₩ Price  │  │ ₩ Price  │  │ ₩ Price  │
  └──────────┘  └──────────┘  └──────────┘

[Pagination]  ← Prev / 1 2 3 ... / Next
```

#### Product Card Component (`components/products/ProductCard.vue`)

| Element | Description |
|---|---|
| Thumbnail image | `thumbnailUrl`; fallback to default album image if missing |
| Album title (`title`) | Single-line ellipsis |
| Artist (`artist`) | Secondary gray text |
| Price (`price`) | Format: `₩ 29,000` (KRW) |
| Card click | `navigateTo('/products/{id}')` |
| Hover | Card shadow lift (`hover:shadow-lg`) |

#### Category Filter Tabs
- "All" tab selected by default (removes `categoryId` when selected)
- On tab change, reset `page=0` and refetch product list
- Selected tab: `border-b-2 border-indigo-600 text-indigo-600`

#### Pagination
- Hidden when `totalPages` ≤ 1
- Show up to 5 page numbers with Prev/Next buttons
- Current page: `bg-indigo-600 text-white`

### Loading / Error States

| State | Handling |
|---|---|
| API loading | Skeleton UI in card grid (12 placeholders) |
| Zero products | Empty state message: "No products registered." |
| `400` | Error banner at top of page |

### URL Sync
- Category filter and current page stay in sync with query params (`?page=0&categoryId=2`)
- Back button restores previous filter/page via `useRoute` and `useRouter`

---

## 2. Product Detail Page `/products/[id]`

**File**: `pages/products/[id].vue`

### API Integration

`GET /api/v1/products/{productId}`

### Success Response (`200`)

```json
{
  "id": 1,
  "title": "Abbey Road",
  "artist": "The Beatles",
  "description": "The Beatles' landmark 1969 release...",
  "price": 29000,
  "stockQuantity": 15,
  "imageUrl": "https://..."
}
```

### UI Structure

#### Layout
```
[← Back to list]

┌─────────────────────┬──────────────────────────────┐
│                     │  Album title (title)          │
│    Product image    │  Artist (artist)              │
│    (imageUrl)       │  ─────────────────────────── │
│                     │  ₩ 29,000                    │
│                     │  In stock: 15 left            │
│                     │                              │
│                     │  [Order]                      │
└─────────────────────┴──────────────────────────────┘

[Product description]
description text area (multi-line, whitespace-pre-wrap)
```

#### UI Element Rules

| Element | Rule |
|---|---|
| Product image | `imageUrl`; fallback to default album cover if missing |
| Album title | `text-2xl font-bold` |
| Artist | `text-gray-500 text-lg` |
| Price | `text-2xl font-semibold text-indigo-600`, `₩` + thousands separator |
| Stock | `stockQuantity > 0` → "In stock: {n} left" (green); `0` → "Out of stock" (red, order button disabled) |
| "Order" button | On click: check login status (see flow below) |
| "← Back to list" | `navigateTo('/products')` or `router.back()` |

#### Loading / Error States

| State | Handling |
|---|---|
| API loading | Skeleton for image + text area |
| `404` | "Product not found." + button to go back to list |
| Other errors | Error banner |

### "Order" Button Flow (current phase)

```
[Order] click
  ├─ Not logged in → toast("Please log in.") + go to /auth/login
  └─ Logged in → toast("Order feature is coming soon.") [extend when order-service is wired]
```

> When `jym-order-service` is integrated, replace this with `POST /api/v1/orders` call.

---

## 3. Type Definitions (`types/catalog.ts`)

```typescript
// Summary type for product list
export interface ProductSummary {
  id: number
  title: string
  artist: string
  price: number
  thumbnailUrl: string | null
}

// Paginated list API response type
export interface ProductListResponse {
  content: ProductSummary[]
  totalElements: number
  totalPages: number
}

// Category type
export interface Category {
  id: number
  name: string
}

// Product detail type
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

## 4. State Management (`composables/useCatalog.ts`)

```typescript
// useProducts: fetch product list
const products = ref<ProductSummary[]>([])
const totalPages = ref(0)
const currentPage = ref(0)
const selectedCategoryId = ref<number | null>(null)
const isLoading = ref(false)

async function fetchProducts(): Promise<void> { ... }

// useCategories: fetch category list (memoized inside composable)
const categories = ref<Category[]>([])
async function fetchCategories(): Promise<void> { ... }

// useProductDetail: fetch product detail
async function useProductDetail(id: number): Promise<ProductDetail> { ... }
```

> Export `useProducts`, `useCategories`, and `useProductDetail` from `useCatalog.ts` as named exports.
> No Pinia store — manage as page-level local state.

---

## 5. Shared Components (product-related)

### `components/products/ProductCard.vue`
- Props: `product: ProductSummary`
- Used in product list grid

### `components/products/CategoryTabs.vue`
- Props: `categories: Category[]`, `modelValue: number | null`
- Emit: `update:modelValue`
- Renders tabs including "All"

### `components/products/Pagination.vue`
- Props: `currentPage: number`, `totalPages: number`
- Emit: `change(page: number)`
