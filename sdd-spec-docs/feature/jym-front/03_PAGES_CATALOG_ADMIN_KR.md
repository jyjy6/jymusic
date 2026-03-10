# jym-front: 상품 관리 페이지 상세 설계 (관리자)

> **포함 도메인**: 상품 등록, 상품 수정, Presigned URL 파일 업로드 공통 컴포넌트
> **인증 요건**: `ROLE_ADMIN` 필수 — `middleware/admin.ts`로 전 페이지 보호
> **연관 서비스**: `jym-catalog-service`

---

## 1. 관리자 미들웨어 (`middleware/admin.ts`)

```
진입 요청
  ├─ isLoggedIn = false → /auth/login 리다이렉트
  └─ isLoggedIn = true
        ├─ user.role = 'ROLE_ADMIN' → 통과
        └─ user.role ≠ 'ROLE_ADMIN' → / 리다이렉트 + toast("접근 권한이 없습니다.")
```

> `user.role`은 로그인 시 Pinia `useAuthStore`의 `user` 필드에 저장된 값을 참조.

---

## 2. 관리자 레이아웃 (`layouts/admin.vue`)

- 기본 `layouts/default.vue`를 상속하되, 상단 네비게이션에 **관리자 메뉴** 추가
- 추가 항목: "상품 관리" → `/admin/products` (향후), "상품 등록" → `/admin/products/new`
- 현재 페이지 하이라이트: `text-indigo-600 border-b-2 border-indigo-600`

---

## 3. Presigned URL 파일 업로드 API

### 엔드포인트
`POST /api/v1/media/presigned-url`
- Header: `Authorization: Bearer <accessToken>` (Axios 인터셉터 자동 주입)

### 요청 Body
```json
{
  "filename": "abbey-road.jpg",
  "contentType": "image/jpeg"
}
```

### 성공 응답 (`200`)
```json
{
  "presignedUrl": "https://jymusic-bucket.s3.ap-northeast-2.amazonaws.com/products/uuid-abbey-road.jpg?X-Amz-...",
  "objectKey": "products/uuid-abbey-road.jpg"
}
```

### 업로드 전체 흐름
```
① 파일 선택 (input[type=file])
   └─ 클라이언트 유효성 검사 (파일 타입, 최대 크기)

② POST /api/v1/media/presigned-url { filename, contentType }
   └─ 응답: { presignedUrl, objectKey }

③ PUT {presignedUrl} (S3 직접 업로드)
   └─ Content-Type 헤더 필수
   └─ 업로드 진행률: axios onUploadProgress → progress bar 표시

④ emit('uploaded', objectKey)
   └─ 부모 폼에서 objectKey를 form.imageKey에 저장

⑤ 폼 저장 버튼 클릭 → POST/PUT /api/v1/products { ..., imageKey }
   └─ 백엔드에서 objectKey로 최종 S3 URL을 조합하여 DB 저장
```

> **주의**: S3 직접 업로드 시 `$axios` 인스턴스가 아닌 별도의 `axios.create()` 또는 `fetch`를 사용.
> Axios 인터셉터가 S3 URL에 Authorization 헤더를 주입하지 않도록 분리 필수.

---

## 4. 공통 파일 업로드 컴포넌트 (`components/common/FileUpload.vue`)

### Props

| Prop | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `accept` | `string` | `'image/*'` | 허용 파일 타입 (MIME type) |
| `maxSizeMb` | `number` | `5` | 최대 파일 크기 (MB) |
| `currentImageUrl` | `string \| null` | `null` | 수정 폼에서 기존 이미지 미리보기 |
| `disabled` | `boolean` | `false` | 비활성화 상태 |

### Emits

| Event | 페이로드 | 설명 |
|---|---|---|
| `uploaded` | `objectKey: string` | S3 업로드 성공, objectKey 전달 |
| `cleared` | — | 이미지 삭제/초기화 |
| `error` | `message: string` | 유효성 검사 또는 업로드 실패 |

### 내부 상태

```typescript
const file = ref<File | null>(null)
const previewUrl = ref<string | null>(props.currentImageUrl ?? null)
const uploadProgress = ref(0)   // 0–100
const status = ref<'idle' | 'uploading' | 'done' | 'error'>('idle')
const errorMessage = ref<string | null>(null)
```

### UI 구성

```
┌─────────────────────────────────────────┐
│  [미리보기 영역]                          │
│  - 이미지 없음: 점선 박스 + 📷 아이콘     │
│  - 이미지 있음: <img> 미리보기            │
│            [✕ 제거] 버튼                 │
├─────────────────────────────────────────┤
│  [파일 선택] 버튼                        │
│  지원 형식: JPG, PNG, WEBP · 최대 5MB   │
├─────────────────────────────────────────┤
│  [업로드 진행바]  (uploading 상태에서만) │
│  ████████░░░░ 67%                       │
└─────────────────────────────────────────┘
```

### 유효성 검사 규칙

| 규칙 | 처리 |
|---|---|
| 허용 타입 외 파일 | `emit('error', '이미지 파일만 업로드할 수 있습니다.')` |
| `maxSizeMb` 초과 | `emit('error', '파일 크기가 {n}MB를 초과합니다.')` |
| Presigned URL API 실패 | `emit('error', '업로드 준비에 실패했습니다.')` |
| S3 PUT 실패 | `emit('error', '파일 업로드에 실패했습니다.')` |

### 부모 폼에서 사용 예시

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

### 헌법 준수 사항
- `<script setup lang="ts">` 사용
- Tailwind CSS 클래스만 사용, `<style>` 블록 없음
- S3 직접 업로드 시 `$axios` 인터셉터 우회 (별도 axios 인스턴스 사용)

---

## 5. 상품 등록 페이지 `/admin/products/new`

**파일**: `pages/admin/products/new.vue`
**레이아웃**: `layout: 'admin'`
**미들웨어**: `middleware: ['admin']`

### 연결 API

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET /api/v1/categories` | 카테고리 목록 | 카테고리 선택 드롭다운 |
| `POST /api/v1/media/presigned-url` | Presigned URL | 이미지 S3 업로드 |
| `POST /api/v1/products` | 상품 등록 | 폼 저장 |

### 요청 Body (`POST /api/v1/products`)

```json
{
  "title": "Abbey Road",
  "artist": "The Beatles",
  "description": "1969년 발매된 비틀즈의 명반...",
  "price": 29000,
  "stockQuantity": 100,
  "categoryId": 2,
  "imageKey": "products/uuid-abbey-road.jpg"
}
```

### 성공 응답 `201`
```json
{ "id": 42, "title": "Abbey Road", ... }
```
→ toast("상품이 등록되었습니다.") + `/products/42` 로 이동

### UI 구성

```
[← 상품 목록으로]

─── 상품 등록 ───────────────────────────────

[이미지 업로드]
  <FileUpload @uploaded="form.imageKey = $event" />

[앨범명 *]          [아티스트 *]
[─────────────────] [─────────────────]

[가격 (₩) *]        [재고 수량 *]
[─────────────────] [─────────────────]

[카테고리 *]
[드롭다운 ▾]

[상세 설명]
[─────────────────────────────────────]
[                                     ]
[_____________________________________]

                  [취소]  [상품 등록]
```

### 폼 필드 정의

| 필드 | 입력 타입 | 유효성 검사 |
|---|---|---|
| `title` | text | 필수, 최대 100자 |
| `artist` | text | 필수, 최대 100자 |
| `price` | number | 필수, 최소 0 |
| `stockQuantity` | number | 필수, 최소 0, 정수 |
| `categoryId` | select | 필수 |
| `description` | textarea | 선택, 최대 2000자 |
| `imageKey` | — (FileUpload 컴포넌트) | 선택 |

### 에러 처리

| 코드 | 처리 |
|---|---|
| `400` | 각 필드 아래 인라인 에러 메시지 표시 |
| `401` | `/auth/login` 리다이렉트 (Axios 인터셉터 처리) |
| `403` | toast("접근 권한이 없습니다.") + `/` 이동 |

### 타입 정의 추가 (`types/catalog.ts`)

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

## 6. 상품 수정 페이지 `/admin/products/[id]/edit`

**파일**: `pages/admin/products/[id]/edit.vue`
**레이아웃**: `layout: 'admin'`
**미들웨어**: `middleware: ['admin']`

### 연결 API

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET /api/v1/products/{id}` | 상품 상세 | 폼 초기값 로드 |
| `GET /api/v1/categories` | 카테고리 목록 | 드롭다운 |
| `POST /api/v1/media/presigned-url` | Presigned URL | 이미지 교체 시 |
| `PUT /api/v1/products/{id}` | 상품 수정 | 폼 저장 |

### 요청 Body (`PUT /api/v1/products/{id}`)

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
> `imageKey` 미변경 시 기존 objectKey 그대로 전송. 이미지 삭제 시 `null` 전송.

### 성공 응답 `200`
→ toast("상품이 수정되었습니다.") + `/products/{id}` 로 이동

### UI 구성

- 등록 폼과 동일한 구조, 단 초기값이 기존 상품 데이터로 채워짐
- 이미지 영역: `FileUpload`의 `currentImageUrl` prop에 기존 `imageUrl` 전달
- 상단 제목: "상품 등록" → "상품 수정"
- 저장 버튼: "상품 등록" → "수정 저장"

### 페이지 마운트 시 초기화 흐름

```
GET /api/v1/products/{id}
  ├─ 성공: form = { title, artist, description, price, stockQuantity, categoryId }
  │         currentImageUrl = product.imageUrl
  └─ 404: toast("상품을 찾을 수 없습니다.") + /admin/products 이동
```

### 에러 처리

| 코드 | 처리 |
|---|---|
| `400` | 각 필드 아래 인라인 에러 메시지 |
| `403` | toast("접근 권한이 없습니다.") + `/` 이동 |
| `404` | toast("상품을 찾을 수 없습니다.") + `/admin/products` 이동 |
| `409` | toast("동시 수정 충돌이 발생했습니다. 다시 시도해주세요.") |

### 타입 정의 추가 (`types/catalog.ts`)

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

## 7. 공통 컴포저블 (`composables/useCatalogAdmin.ts`)

```typescript
// useProductForm: 등록/수정 폼 공통 로직
const form = ref<ProductCreateRequest | ProductUpdateRequest>({ ... })
const errors = ref<Record<string, string>>({})
const isSubmitting = ref(false)

function validate(): boolean { ... }
async function submitCreate(): Promise<void> { ... }
async function submitUpdate(id: number): Promise<void> { ... }
```

> 등록(`new.vue`)과 수정(`[id]/edit.vue`) 페이지가 동일한 컴포저블 공유.
> `isEditMode: boolean` 파라미터로 분기.
