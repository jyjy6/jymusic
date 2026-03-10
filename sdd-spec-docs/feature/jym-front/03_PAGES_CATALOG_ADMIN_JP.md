# jym-front: 商品管理ページ詳細設計（管理者）

> **対象ドメイン**: 商品登録、商品編集、Presigned URLファイルアップロード共通コンポーネント
> **認証要件**: `ROLE_ADMIN` 必須 — `middleware/admin.ts` で全ページを保護
> **関連サービス**: `jym-catalog-service`

---

## 1. 管理者ミドルウェア (`middleware/admin.ts`)

```
リクエスト受信
  ├─ isLoggedIn = false → /auth/login にリダイレクト
  └─ isLoggedIn = true
        ├─ user.role = 'ROLE_ADMIN' → 通過
        └─ user.role ≠ 'ROLE_ADMIN' → / にリダイレクト + toast("アクセス権限がありません。")
```

> `user.role` はログイン時に Pinia `useAuthStore` の `user` フィールドに保存された値を参照。

---

## 2. 管理者レイアウト (`layouts/admin.vue`)

- `layouts/default.vue` を継承し、トップナビゲーションに**管理者メニュー**を追加
- 追加項目: 「商品管理」→ `/admin/products`（将来）、「商品登録」→ `/admin/products/new`
- 現在ページのハイライト: `text-indigo-600 border-b-2 border-indigo-600`

---

## 3. Presigned URL ファイルアップロード API

### エンドポイント
`POST /api/v1/media/presigned-url`
- ヘッダー: `Authorization: Bearer <accessToken>`（Axiosインターセプターが自動注入）

### リクエストボディ
```json
{
  "filename": "abbey-road.jpg",
  "contentType": "image/jpeg"
}
```

### 成功レスポンス（`200`）
```json
{
  "presignedUrl": "https://jymusic-bucket.s3.ap-northeast-2.amazonaws.com/products/uuid-abbey-road.jpg?X-Amz-...",
  "objectKey": "products/uuid-abbey-road.jpg"
}
```

### アップロード全体フロー
```
① ファイル選択（input[type=file]）
   └─ クライアント側バリデーション（ファイルタイプ、最大サイズ）

② POST /api/v1/media/presigned-url { filename, contentType }
   └─ レスポンス: { presignedUrl, objectKey }

③ PUT {presignedUrl}（S3 直接アップロード）
   └─ Content-Type ヘッダー必須
   └─ 進捗表示: axios onUploadProgress → プログレスバー更新

④ emit('uploaded', objectKey)
   └─ 親フォームで objectKey を form.imageKey に保存

⑤ 保存ボタンクリック → POST/PUT /api/v1/products { ..., imageKey }
   └─ バックエンドが objectKey から最終S3 URLを組み立ててDB保存
```

> **注意**: S3への PUT リクエストには `$axios` インスタンスではなく、別途 `axios.create()` または `fetch` を使用すること。
> グローバル Axios インターセプターが S3 Presigned URL に Authorization ヘッダーを注入しないよう分離必須。

---

## 4. 共通ファイルアップロードコンポーネント (`components/common/FileUpload.vue`)

### Props

| Prop | 型 | デフォルト | 説明 |
|---|---|---|---|
| `accept` | `string` | `'image/*'` | 許可MIMEタイプ |
| `maxSizeMb` | `number` | `5` | 最大ファイルサイズ（MB） |
| `currentImageUrl` | `string \| null` | `null` | 編集フォームでの既存画像プレビュー |
| `disabled` | `boolean` | `false` | 無効化状態 |

### Emits

| イベント | ペイロード | 説明 |
|---|---|---|
| `uploaded` | `objectKey: string` | S3アップロード成功、objectKeyを渡す |
| `cleared` | — | 画像削除/リセット |
| `error` | `message: string` | バリデーションまたはアップロード失敗 |

### 内部状態

```typescript
const file = ref<File | null>(null)
const previewUrl = ref<string | null>(props.currentImageUrl ?? null)
const uploadProgress = ref(0)   // 0–100
const status = ref<'idle' | 'uploading' | 'done' | 'error'>('idle')
const errorMessage = ref<string | null>(null)
```

### UI構成

```
┌─────────────────────────────────────────┐
│  [プレビュー領域]                         │
│  - 画像なし: 点線ボーダー + 📷 アイコン   │
│  - 画像あり: <img> プレビュー             │
│              [✕ 削除] ボタン             │
├─────────────────────────────────────────┤
│  [ファイルを選択] ボタン                  │
│  対応形式: JPG, PNG, WEBP · 最大5MB    │
├─────────────────────────────────────────┤
│  [アップロード進捗バー]（uploading時のみ） │
│  ████████░░░░ 67%                       │
└─────────────────────────────────────────┘
```

### バリデーションルール

| ルール | 処理 |
|---|---|
| 非対応ファイルタイプ | `emit('error', '画像ファイルのみアップロードできます。')` |
| `maxSizeMb` 超過 | `emit('error', 'ファイルサイズが{n}MBを超えています。')` |
| Presigned URL API 失敗 | `emit('error', 'アップロードの準備に失敗しました。')` |
| S3 PUT 失敗 | `emit('error', 'ファイルのアップロードに失敗しました。')` |

### 親フォームでの使用例

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

### アーキテクチャ準拠事項
- `<script setup lang="ts">` 必須
- Tailwind CSSクラスのみ使用、`<style>` ブロック禁止
- S3アップロード時はグローバルインターセプターをバイパスするため別axiosインスタンスを使用

---

## 5. 商品登録ページ `/admin/products/new`

**ファイル**: `pages/admin/products/new.vue`
**レイアウト**: `layout: 'admin'`
**ミドルウェア**: `middleware: ['admin']`

### 接続API

| メソッド | パス | 用途 |
|---|---|---|
| `GET /api/v1/categories` | カテゴリ一覧 | カテゴリ選択ドロップダウン |
| `POST /api/v1/media/presigned-url` | Presigned URL | S3画像アップロード |
| `POST /api/v1/products` | 商品登録 | フォーム送信 |

### リクエストボディ（`POST /api/v1/products`）

```json
{
  "title": "Abbey Road",
  "artist": "The Beatles",
  "description": "1969年リリースのビートルズの名盤...",
  "price": 29000,
  "stockQuantity": 100,
  "categoryId": 2,
  "imageKey": "products/uuid-abbey-road.jpg"
}
```

### 成功レスポンス `201`
```json
{ "id": 42, "title": "Abbey Road", ... }
```
→ toast("商品を登録しました。") + `/products/42` へ遷移

### UI構成

```
[← 商品一覧へ]

─── 商品登録 ────────────────────────────

[画像アップロード]
  <FileUpload @uploaded="form.imageKey = $event" />

[アルバム名 *]       [アーティスト *]
[─────────────────] [─────────────────]

[価格 (₩) *]        [在庫数 *]
[─────────────────] [─────────────────]

[カテゴリ *]
[ドロップダウン ▾]

[詳細説明]
[─────────────────────────────────────]
[                                     ]
[_____________________________________]

                  [キャンセル]  [商品登録]
```

### フォームフィールド定義

| フィールド | 入力タイプ | バリデーション |
|---|---|---|
| `title` | text | 必須、最大100文字 |
| `artist` | text | 必須、最大100文字 |
| `price` | number | 必須、最小0 |
| `stockQuantity` | number | 必須、最小0、整数 |
| `categoryId` | select | 必須 |
| `description` | textarea | 任意、最大2000文字 |
| `imageKey` | —（FileUploadコンポーネント） | 任意 |

### エラーハンドリング

| コード | 処理 |
|---|---|
| `400` | 各フィールド下部にインラインエラーメッセージ |
| `401` | `/auth/login` にリダイレクト（Axiosインターセプター処理） |
| `403` | toast("アクセス権限がありません。") + `/` へ遷移 |

### 型定義追加（`types/catalog.ts`）

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

## 6. 商品編集ページ `/admin/products/[id]/edit`

**ファイル**: `pages/admin/products/[id]/edit.vue`
**レイアウト**: `layout: 'admin'`
**ミドルウェア**: `middleware: ['admin']`

### 接続API

| メソッド | パス | 用途 |
|---|---|---|
| `GET /api/v1/products/{id}` | 商品詳細 | フォーム初期値ロード |
| `GET /api/v1/categories` | カテゴリ一覧 | ドロップダウン |
| `POST /api/v1/media/presigned-url` | Presigned URL | 画像差し替え時 |
| `PUT /api/v1/products/{id}` | 商品更新 | フォーム送信 |

### リクエストボディ（`PUT /api/v1/products/{id}`）

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
> 画像未変更の場合は既存のobjectKeyをそのまま送信。画像削除時は `null` を送信。

### 成功レスポンス `200`
→ toast("商品を更新しました。") + `/products/{id}` へ遷移

### UI構成

- 登録フォームと同一構造、既存商品データで初期値を設定
- 画像領域: `FileUpload` の `currentImageUrl` prop に既存の `imageUrl` を渡す
- ページタイトル: 「商品登録」→「商品編集」
- 保存ボタン: 「商品登録」→「変更を保存」

### ページマウント時の初期化フロー

```
GET /api/v1/products/{id}
  ├─ 成功: form = { title, artist, description, price, stockQuantity, categoryId }
  │         currentImageUrl = product.imageUrl
  └─ 404: toast("商品が見つかりません。") + /admin/products へ遷移
```

### エラーハンドリング

| コード | 処理 |
|---|---|
| `400` | 各フィールド下部にインラインエラーメッセージ |
| `403` | toast("アクセス権限がありません。") + `/` へ遷移 |
| `404` | toast("商品が見つかりません。") + `/admin/products` へ遷移 |
| `409` | toast("競合が発生しました。再度お試しください。") |

### 型定義追加（`types/catalog.ts`）

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

## 7. 共通コンポーザブル (`composables/useCatalogAdmin.ts`)

```typescript
// useProductForm: 登録・編集フォームの共通ロジック
const form = ref<ProductCreateRequest | ProductUpdateRequest>({ ... })
const errors = ref<Record<string, string>>({})
const isSubmitting = ref(false)

function validate(): boolean { ... }
async function submitCreate(): Promise<void> { ... }
async function submitUpdate(id: number): Promise<void> { ... }
```

> `new.vue`と`[id]/edit.vue`が同じコンポーザブルを共有。
> `isEditMode: boolean` パラメータで内部分岐。
