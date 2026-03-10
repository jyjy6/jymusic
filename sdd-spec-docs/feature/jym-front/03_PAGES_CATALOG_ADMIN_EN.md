# jym-front: Product Admin Pages — Detailed Design

> **Domain**: Product creation, Product edit, Presigned URL file upload common component
> **Auth requirement**: `ROLE_ADMIN` required — all pages protected by `middleware/admin.ts`
> **Related service**: `jym-catalog-service`

---

## 1. Admin Middleware (`middleware/admin.ts`)

```
Incoming request
  ├─ isLoggedIn = false → redirect to /auth/login
  └─ isLoggedIn = true
        ├─ user.role = 'ROLE_ADMIN' → pass through
        └─ user.role ≠ 'ROLE_ADMIN' → redirect to / + toast("Access denied.")
```

> `user.role` is read from the Pinia `useAuthStore`'s `user` field, populated at login.

---

## 2. Admin Layout (`layouts/admin.vue`)

- Extends `layouts/default.vue`; adds an **admin navigation menu** to the top bar
- Additional items: "Products" → `/admin/products` (future), "Add Product" → `/admin/products/new`
- Active page highlight: `text-indigo-600 border-b-2 border-indigo-600`

---

## 3. Presigned URL File Upload API

### Endpoint
`POST /api/v1/media/presigned-url`
- Header: `Authorization: Bearer <accessToken>` (auto-injected by Axios interceptor)

### Request Body
```json
{
  "filename": "abbey-road.jpg",
  "contentType": "image/jpeg"
}
```

### Success Response (`200`)
```json
{
  "presignedUrl": "https://jymusic-bucket.s3.ap-northeast-2.amazonaws.com/products/uuid-abbey-road.jpg?X-Amz-...",
  "objectKey": "products/uuid-abbey-road.jpg"
}
```

### Full Upload Flow
```
① File selected (input[type=file])
   └─ Client-side validation (file type, max size)

② POST /api/v1/media/presigned-url { filename, contentType }
   └─ Response: { presignedUrl, objectKey }

③ PUT {presignedUrl} (direct S3 upload)
   └─ Content-Type header required
   └─ Upload progress: axios onUploadProgress → progress bar update

④ emit('uploaded', objectKey)
   └─ Parent form stores objectKey in form.imageKey

⑤ Save button clicked → POST/PUT /api/v1/products { ..., imageKey }
   └─ Backend constructs the final S3 URL from objectKey and saves to DB
```

> **Important**: Use a separate `axios.create()` instance or `fetch` for the S3 PUT request.
> The global Axios interceptor must NOT inject the Authorization header into S3 presigned URLs.

---

## 4. Shared File Upload Component (`components/common/FileUpload.vue`)

### Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `accept` | `string` | `'image/*'` | Allowed MIME types |
| `maxSizeMb` | `number` | `5` | Maximum file size in MB |
| `currentImageUrl` | `string \| null` | `null` | Existing image preview for edit forms |
| `disabled` | `boolean` | `false` | Disabled state |

### Emits

| Event | Payload | Description |
|---|---|---|
| `uploaded` | `objectKey: string` | S3 upload successful, passes objectKey |
| `cleared` | — | Image removed/reset |
| `error` | `message: string` | Validation or upload failure |

### Internal State

```typescript
const file = ref<File | null>(null)
const previewUrl = ref<string | null>(props.currentImageUrl ?? null)
const uploadProgress = ref(0)   // 0–100
const status = ref<'idle' | 'uploading' | 'done' | 'error'>('idle')
const errorMessage = ref<string | null>(null)
```

### UI Structure

```
┌─────────────────────────────────────────┐
│  [Preview area]                          │
│  - No image: dashed border + 📷 icon    │
│  - Image present: <img> preview          │
│              [✕ Remove] button          │
├─────────────────────────────────────────┤
│  [Choose File] button                   │
│  Supported: JPG, PNG, WEBP · Max 5MB   │
├─────────────────────────────────────────┤
│  [Upload progress bar] (uploading only) │
│  ████████░░░░ 67%                       │
└─────────────────────────────────────────┘
```

### Validation Rules

| Rule | Handling |
|---|---|
| Unsupported file type | `emit('error', 'Only image files are allowed.')` |
| Exceeds `maxSizeMb` | `emit('error', 'File size exceeds {n}MB.')` |
| Presigned URL API failure | `emit('error', 'Failed to prepare upload.')` |
| S3 PUT failure | `emit('error', 'File upload failed.')` |

### Usage in Parent Form

```vue
<FileUpload
  accept="image/*"
  :max-size-mb="5"
  :current-image-url="form.imageUrl"
  @uploaded="form.imageKey = $event"
  @cleared="form.imageKey = null"
  @error="showToast($event)"
/>
```

### Architecture Compliance
- MUST use `<script setup lang="ts">`
- Tailwind CSS classes only; no `<style>` block
- S3 upload MUST use a separate axios instance to bypass the global interceptor

---

## 5. Product Creation Page `/admin/products/new`

**File**: `pages/admin/products/new.vue`
**Layout**: `layout: 'admin'`
**Middleware**: `middleware: ['admin']`

### API Integration

| Method | Path | Purpose |
|---|---|---|
| `GET /api/v1/categories` | Category list | Category select dropdown |
| `POST /api/v1/media/presigned-url` | Presigned URL | S3 image upload |
| `POST /api/v1/products` | Create product | Form submit |

### Request Body (`POST /api/v1/products`)

```json
{
  "title": "Abbey Road",
  "artist": "The Beatles",
  "description": "The Beatles' landmark 1969 release...",
  "price": 29000,
  "stockQuantity": 100,
  "categoryId": 2,
  "imageKey": "products/uuid-abbey-road.jpg"
}
```

### Success Response `201`
```json
{ "id": 42, "title": "Abbey Road", ... }
```
→ toast("Product registered successfully.") + navigate to `/products/42`

### UI Structure

```
[← Back to products]

─── Add Product ────────────────────────────

[Image Upload]
  <FileUpload @uploaded="form.imageKey = $event" />

[Album Title *]     [Artist *]
[───────────────]   [───────────────]

[Price (₩) *]       [Stock Quantity *]
[───────────────]   [───────────────]

[Category *]
[Dropdown ▾]

[Description]
[─────────────────────────────────────]
[                                     ]
[_____________________________________]

                  [Cancel]  [Add Product]
```

### Form Field Definitions

| Field | Input Type | Validation |
|---|---|---|
| `title` | text | Required, max 100 chars |
| `artist` | text | Required, max 100 chars |
| `price` | number | Required, min 0 |
| `stockQuantity` | number | Required, min 0, integer |
| `categoryId` | select | Required |
| `description` | textarea | Optional, max 2000 chars |
| `imageKey` | — (FileUpload component) | Optional |

### Error Handling

| Code | Handling |
|---|---|
| `400` | Inline error message below each field |
| `401` | Redirect to `/auth/login` (handled by Axios interceptor) |
| `403` | toast("Access denied.") + navigate to `/` |

### Type Definitions (add to `types/catalog.ts`)

```typescript
export interface ProductCreateRequest {
  title: string
  artist: string
  description: string
  price: number
  stockQuantity: number
  categoryId: number
  imageKey: string | null
}

export interface PresignedUrlRequest {
  filename: string
  contentType: string
}

export interface PresignedUrlResponse {
  presignedUrl: string
  objectKey: string
}
```

---

## 6. Product Edit Page `/admin/products/[id]/edit`

**File**: `pages/admin/products/[id]/edit.vue`
**Layout**: `layout: 'admin'`
**Middleware**: `middleware: ['admin']`

### API Integration

| Method | Path | Purpose |
|---|---|---|
| `GET /api/v1/products/{id}` | Product detail | Load initial form values |
| `GET /api/v1/categories` | Category list | Dropdown |
| `POST /api/v1/media/presigned-url` | Presigned URL | Image replacement |
| `PUT /api/v1/products/{id}` | Update product | Form submit |

### Request Body (`PUT /api/v1/products/{id}`)

```json
{
  "title": "Abbey Road (Remastered)",
  "artist": "The Beatles",
  "description": "...",
  "price": 32000,
  "stockQuantity": 80,
  "categoryId": 2,
  "imageKey": "products/uuid-abbey-road-new.jpg"
}
```
> Send existing `objectKey` unchanged if the image is not replaced. Send `null` if the image is removed.

### Success Response `200`
→ toast("Product updated successfully.") + navigate to `/products/{id}`

### UI Structure

- Same form layout as the creation page, pre-filled with existing product data
- Image area: pass existing `imageUrl` to `FileUpload`'s `currentImageUrl` prop
- Page title: "Add Product" → "Edit Product"
- Save button: "Add Product" → "Save Changes"

### Page Mount Initialization Flow

```
GET /api/v1/products/{id}
  ├─ Success: form = { title, artist, description, price, stockQuantity, categoryId }
  │           currentImageUrl = product.imageUrl
  └─ 404: toast("Product not found.") + navigate to /admin/products
```

### Error Handling

| Code | Handling |
|---|---|
| `400` | Inline error message below each field |
| `403` | toast("Access denied.") + navigate to `/` |
| `404` | toast("Product not found.") + navigate to `/admin/products` |
| `409` | toast("Conflict detected. Please try again.") |

### Type Definitions (add to `types/catalog.ts`)

```typescript
export interface ProductUpdateRequest {
  title: string
  artist: string
  description: string
  price: number
  stockQuantity: number
  categoryId: number
  imageKey: string | null
}
```

---

## 7. Shared Composable (`composables/useCatalogAdmin.ts`)

```typescript
// useProductForm: shared form logic for create and edit
const form = ref<ProductCreateRequest | ProductUpdateRequest>({ ... })
const errors = ref<Record<string, string>>({})
const isSubmitting = ref(false)

function validate(): boolean { ... }
async function submitCreate(): Promise<void> { ... }
async function submitUpdate(id: number): Promise<void> { ... }
```

> Both `new.vue` and `[id]/edit.vue` share the same composable.
> Branched internally by an `isEditMode: boolean` parameter.
