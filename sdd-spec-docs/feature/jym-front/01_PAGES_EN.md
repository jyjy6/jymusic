# jym-front: Detailed Page Design

---

## 1. Main Page `/`

**File**: `pages/index.vue`

### UI Layout
- Hero section: service name (Jymusic), short tagline
- CTA button: "Get Started" → navigate to `/auth/register`
- If logged in: "View My Profile" → navigate to `/me`

### Notes
- No authentication required; accessible to everyone

---

## 2. Register Page `/auth/register`

**File**: `pages/auth/register.vue`

### Connected API
`POST /api/v1/auth/register`

### Request Body
```json
{
  "username": "string",
  "password": "string (min 4 chars)",
  "nickname": "string",
  "email": "string (optional)"
}
```

### Success Response `201`
→ Redirect to `/auth/login` + success toast message

### UI Layout
| Field | Type | Validation |
|---|---|---|
| Username | text | Required |
| Password | password | Required, min 4 chars |
| Nickname | text | Required |
| Email | email | Optional |
| Register button | submit | - |

### Error Handling
- `400` → display `message` field inline below the form

---

## 3. Login Page `/auth/login`

**File**: `pages/auth/login.vue`

### Connected API
`POST /api/v1/auth/login`

### Request Body
```json
{ "username": "string", "password": "string" }
```

### Success Response `200`
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer" }
```
- `accessToken` → stored in Pinia `useAuthStore`
- Refresh Token → automatically set by server as HttpOnly Cookie
- → redirect to `/me`

### UI Layout
| Field | Type | Validation |
|---|---|---|
| Username | text | Required |
| Password | password | Required |
| Login button | submit | - |
| Register link | link | `/auth/register` |

### Error Handling
- `401` → display "Invalid username or password." inline

---

## 4. My Profile Page `/me`

**File**: `pages/me.vue`

### Connected API
`GET /api/v1/members/me`
- Header: `Authorization: Bearer <accessToken>` (auto-injected by Axios interceptor)

### Success Response `200`
```json
{
  "id": 1,
  "username": "hong",
  "nickname": "Hong",
  "email": "hong@example.com",
  "role": "ROLE_USER"
}
```

### UI Layout
- Profile card: display nickname, username, email, role
- Logout button

### Logout Flow
```
POST /api/v1/auth/logout  (Bearer header + cookie auto-sent)
  → Regardless of success/failure: clear Pinia store (clearAuth())
  → navigate to `/`
```

### Error Handling
- `401` → redirect to `/auth/login` (handled in advance by middleware)

---

## 5. Common Components / Utilities

### `plugins/axios.ts`

**Request Interceptor**
- In SSR environment (`import.meta.server`): pass through without token injection
- In client environment: inject `accessToken` from Pinia store as `Authorization: Bearer <token>` header

**Response Interceptor - 401 Handling (Token Refresh Queue Pattern)**

```
Receive 401 response
  ├─ If the refresh-token request itself returns 401 → force logout
  ├─ If already refreshing (isRefreshing = true) → add to failedQueue and wait
  └─ If not refreshing:
        isRefreshing = true
        POST /api/v1/auth/refresh-token (cookie auto-sent)
          ├─ Success: new accessToken → save to Pinia store
          │           retry all requests in failedQueue with new token
          │           retry original request
          └─ Failure: reject all requests in failedQueue
                      force logout (clear store + navigate to /auth/login)
```

Module-level variables for preventing duplicate refreshes:
- `isRefreshing: boolean`
- `failedQueue: Array<{ resolve, reject }>`
- `isLoggingOut: boolean` (prevents duplicate logout)

**Axios Global Settings**
- `baseURL`: `http://localhost:8080`
- `withCredentials: true` (auto-send Refresh Token Cookie)

---

### `stores/auth.ts` (Pinia) — Current Definition
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
> **Architecture compliance**: Composition API style required. Options API (`state`, `getters`, `actions` objects) is prohibited.

---

### `middleware/auth.ts`
- On entry to auth-required pages like `/me`, check `isLoggedIn`
- If `false`, redirect to `/auth/login`

---

## 6. Products List Page `/products`

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

### Type Definitions (`types/catalog.ts`)

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

### State (`composables/useCatalog.ts`)

```typescript
// useProducts: fetch product list
const products = ref<ProductSummary[]>([])
const totalPages = ref(0)
const currentPage = ref(0)
const selectedCategoryId = ref<number | null>(null)
const isLoading = ref(false)

async function fetchProducts(): Promise<void> { ... }

// useCategories: fetch category list (cached)
const categories = ref<Category[]>([])
async function fetchCategories(): Promise<void> { ... }
```

> Export `useProducts` and `useCategories` from `useCatalog.ts` as named exports.
> No Pinia store — manage as page-level local state.

### URL Sync
- Category filter and current page stay in sync with query params (`?page=0&categoryId=2`)
- Back button restores previous filter/page via `useRoute` and `useRouter`

---

## 7. Product Detail Page `/products/[id]`

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

### Type Definition (add to `types/catalog.ts`)

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
| "Order" button | On click: check login; current phase: show toast "Order feature is coming soon." |
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

## 8. Shared Components (product-related)

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

### `composables/useCatalog.ts`
- `useProducts()`: product list, pagination, category filter
- `useCategories()`: category list (memoized inside composable)
- `useProductDetail(id: number)`: product detail fetch
