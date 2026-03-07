# jym-front: ページ詳細設計

---

## 1. メインページ `/`

**ファイル**: `pages/index.vue`

### UI構成
- ヒーローセクション: サービス名（Jymusic）、短い紹介文
- CTAボタン: 「今すぐ始める」→ `/auth/register` へ遷移
- ログイン状態の場合: 「マイプロフィールを見る」→ `/me` へ遷移

### 備考
- 認証不要。誰でもアクセス可能

---

## 2. 会員登録ページ `/auth/register`

**ファイル**: `pages/auth/register.vue`

### 接続API
`POST /api/v1/auth/register`

### リクエストボディ
```json
{
  "username": "string",
  "password": "string（最小4文字）",
  "nickname": "string",
  "email": "string（任意）"
}
```

### 成功レスポンス `201`
→ `/auth/login` ページにリダイレクト + 成功トーストメッセージ

### UI構成
| フィールド | タイプ | バリデーション |
|---|---|---|
| ユーザーID (username) | text | 必須 |
| パスワード (password) | password | 必須、最小4文字 |
| ニックネーム (nickname) | text | 必須 |
| メールアドレス (email) | email | 任意 |
| 登録ボタン | submit | - |

### エラーハンドリング
- `400` → フォーム下部に `message` フィールドをインライン表示

---

## 3. ログインページ `/auth/login`

**ファイル**: `pages/auth/login.vue`

### 接続API
`POST /api/v1/auth/login`

### リクエストボディ
```json
{ "username": "string", "password": "string" }
```

### 成功レスポンス `200`
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer" }
```
- `accessToken` → Pinia `useAuthStore` に保存
- リフレッシュトークン → サーバーがHttpOnly Cookieとして自動設定
- → `/me` ページにリダイレクト

### UI構成
| フィールド | タイプ | バリデーション |
|---|---|---|
| ユーザーID (username) | text | 必須 |
| パスワード (password) | password | 必須 |
| ログインボタン | submit | - |
| 会員登録リンク | link | `/auth/register` |

### エラーハンドリング
- `401` → 「ユーザーIDまたはパスワードが正しくありません。」をインライン表示

---

## 4. マイプロフィールページ `/me`

**ファイル**: `pages/me.vue`

### 接続API
`GET /api/v1/members/me`
- ヘッダー: `Authorization: Bearer <accessToken>`（Axiosインターセプターが自動注入）

### 成功レスポンス `200`
```json
{
  "id": 1,
  "username": "hong",
  "nickname": "홍길동",
  "email": "hong@example.com",
  "role": "ROLE_USER"
}
```

### UI構成
- プロフィールカード: ニックネーム、ユーザーID、メールアドレス、権限（role）を表示
- ログアウトボタン

### ログアウトフロー
```
POST /api/v1/auth/logout  (Bearerヘッダー + Cookie自動送信)
  → 成功・失敗に関わらず Pinia store を初期化（clearAuth()）
  → `/` へ遷移
```

### エラーハンドリング
- `401` → `/auth/login` にリダイレクト（ミドルウェアで事前処理）

---

## 5. 共通コンポーネント / ユーティリティ

### `plugins/axios.ts`

**リクエストインターセプター**
- SSR環境（`import.meta.server`）: トークン注入なしで通過
- クライアント環境: Pinia storeの `accessToken` を `Authorization: Bearer <token>` ヘッダーに注入

**レスポンスインターセプター - 401処理（トークン更新キューパターン）**

```
401レスポンス受信
  ├─ refresh-tokenリクエスト自体が401の場合 → 強制ログアウト
  ├─ すでに更新中（isRefreshing = true）の場合 → failedQueueに追加して待機
  └─ 更新中でない場合:
        isRefreshing = true
        POST /api/v1/auth/refresh-token（Cookie自動送信）
          ├─ 成功: 新アクセストークン → Pinia storeに保存
          │         failedQueue内の全リクエストを新トークンで再試行
          │         元のリクエストも再試行
          └─ 失敗: failedQueue内の全リクエストをreject
                    強制ログアウト（store初期化 + /auth/loginへ遷移）
```

重複更新防止のためのモジュールレベル変数:
- `isRefreshing: boolean`
- `failedQueue: Array<{ resolve, reject }>`
- `isLoggingOut: boolean`（重複ログアウト防止）

**axios全体設定**
- `baseURL`: `http://localhost:8080`
- `withCredentials: true`（リフレッシュトークンCookieを自動送信）

---

### `stores/auth.ts`（Pinia）— 現在の定義
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
> **アーキテクチャ準拠**: Composition APIスタイル必須。Options API（`state`、`getters`、`actions`オブジェクト）の使用禁止。

---

### `middleware/auth.ts`
- `/me` などの認証必要ページ進入時に `isLoggedIn` を確認
- `false` の場合は `/auth/login` にリダイレクト

---

## 6. 商品一覧ページ `/products`

**ファイル**: `pages/products/index.vue`

### 接続API

| メソッド | パス | 用途 |
|---|---|---|
| `GET /api/v1/categories` | カテゴリ一覧 | フィルタータブ描画（マウント時1回） |
| `GET /api/v1/products?page=&size=&categoryId=` | 商品一覧 | カードグリッド描画 |

### クエリパラメータ

| パラメータ | 型 | デフォルト | 説明 |
|---|---|---|---|
| `page` | integer | `0` | ページ番号（0始まり） |
| `size` | integer | `12` | 1ページあたりの商品数 |
| `categoryId` | integer | （なし） | カテゴリフィルター、未選択時は全件取得 |

### 成功レスポンス（`200`）— 商品一覧

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

### UI構成

#### レイアウト構造
```
[カテゴリフィルタータブバー]
  すべて | Rock | Pop | Jazz | Classical | ...

[商品カードグリッド]  ← 3列（モバイル1列、タブレット2列、PC 3列）
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ サムネイル │  │ サムネイル │  │ サムネイル │
  │ アルバム名 │  │ アルバム名 │  │ アルバム名 │
  │ アーティスト│  │ アーティスト│  │ アーティスト│
  │ ₩ 価格   │  │ ₩ 価格   │  │ ₩ 価格   │
  └──────────┘  └──────────┘  └──────────┘

[ページネーション]  ← 前 / 1 2 3 ... / 次
```

#### 商品カードコンポーネント（`components/products/ProductCard.vue`）

| 要素 | 説明 |
|---|---|
| サムネイル画像 | `thumbnailUrl`、無い場合はデフォルトアルバム画像を表示 |
| アルバム名（`title`） | 1行省略表示 |
| アーティスト（`artist`） | グレー補助テキスト |
| 価格（`price`） | `₩ 29,000` 形式（韓国ウォン表記） |
| カード全体クリック | `navigateTo('/products/{id}')` |
| ホバー | カードに影を付与（`hover:shadow-lg`） |

#### カテゴリフィルタータブ
- 「すべて」タブをデフォルト選択（選択時は `categoryId` パラメータを削除）
- タブ選択時は `page=0` にリセットして商品一覧を再取得
- 選択中タブ: `border-b-2 border-indigo-600 text-indigo-600`

#### ページネーション
- `totalPages` が1以下の場合は非表示
- 最大5個のページ番号表示、前/次ボタン
- 現在ページ: `bg-indigo-600 text-white`

### ローディング / エラー状態

| 状態 | 対応 |
|---|---|
| API呼び出し中 | カードグリッド領域にスケルトンUI（12個プレースホルダー） |
| 商品0件 | 「登録された商品がありません。」の空状態メッセージ |
| `400` | ページ上部にエラーバナー表示 |

### 型定義（`types/catalog.ts`）

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

### 状態管理（`composables/useCatalog.ts`）

```typescript
// useProducts: 商品一覧取得
const products = ref<ProductSummary[]>([])
const totalPages = ref(0)
const currentPage = ref(0)
const selectedCategoryId = ref<number | null>(null)
const isLoading = ref(false)

async function fetchProducts(): Promise<void> { ... }

// useCategories: カテゴリ一覧取得（キャッシュ）
const categories = ref<Category[]>([])
async function fetchCategories(): Promise<void> { ... }
```

> `useCatalog.ts` 内の `useProducts`、`useCategories` をそれぞれ named export で分離。
> Pinia store は不要 — ページ単位のローカル状態で管理。

### URL同期
- カテゴリフィルター・現在ページはURLクエリ（`?page=0&categoryId=2`）と同期
- 戻る操作で前のフィルター/ページ状態を復元（`useRoute`、`useRouter` 利用）

---

## 7. 商品詳細ページ `/products/[id]`

**ファイル**: `pages/products/[id].vue`

### 接続API

`GET /api/v1/products/{productId}`

### 成功レスポンス（`200`）

```json
{
  "id": 1,
  "title": "Abbey Road",
  "artist": "The Beatles",
  "description": "1969年リリースのビートルズの名盤...",
  "price": 29000,
  "stockQuantity": 15,
  "imageUrl": "https://..."
}
```

### 型定義（`types/catalog.ts` に追加）

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

### UI構成

#### レイアウト構造
```
[← 一覧に戻る]

┌─────────────────────┬──────────────────────────────┐
│                     │  アルバム名 (title)           │
│    商品画像         │  アーティスト (artist)        │
│    (imageUrl)       │  ─────────────────────────── │
│                     │  ₩ 29,000                    │
│                     │  在庫: 15個残り               │
│                     │                              │
│                     │  [注文する]                   │
└─────────────────────┴──────────────────────────────┘

[商品詳細説明]
description テキスト領域（複数行、whitespace-pre-wrap）
```

#### 各UI要素詳細

| 要素 | ルール |
|---|---|
| 商品画像 | `imageUrl`、無い場合はデフォルトアルバムカバー画像を表示 |
| アルバム名 | `text-2xl font-bold` |
| アーティスト | `text-gray-500 text-lg` |
| 価格 | `text-2xl font-semibold text-indigo-600`、`₩` + 千単位区切り |
| 在庫表示 | `stockQuantity > 0` →「在庫: {n}個残り」（緑）、`0` →「品切れ」（赤 + 注文ボタン無効） |
| 「注文する」ボタン | クリック時は注文サービス連携（現段階: ログイン確認後、未実装案内のtoast） |
| 「← 一覧に戻る」 | `navigateTo('/products')` または `router.back()` |

#### ローディング / エラー状態

| 状態 | 対応 |
|---|---|
| API呼び出し中 | 画像 + テキスト領域にスケルトンUI表示 |
| `404` | 「商品が見つかりません。」メッセージ + 一覧へ戻るボタン |
| その他エラー | エラーバナー表示 |

### 「注文する」ボタン動作フロー（現段階）

```
[注文する] クリック
  ├─ 未ログイン → toast("ログインが必要です。") + /auth/login へ遷移
  └─ ログイン済み → toast("注文機能は準備中です。") [order-service 連携時に拡張]
```

> 今後 `jym-order-service` 連携時に、この項目を `POST /api/v1/orders` 呼び出しに差し替え。

---

## 8. 共通コンポーネント追加分（商品関連）

### `components/products/ProductCard.vue`
- Props: `product: ProductSummary`
- 商品一覧グリッドで使用

### `components/products/CategoryTabs.vue`
- Props: `categories: Category[]`, `modelValue: number | null`
- Emit: `update:modelValue`
- 「すべて」を含むタブ一覧を描画

### `components/products/Pagination.vue`
- Props: `currentPage: number`, `totalPages: number`
- Emit: `change(page: number)`

### `composables/useCatalog.ts`
- `useProducts()`: 商品一覧取得、ページネーション、カテゴリフィルター
- `useCategories()`: カテゴリ一覧取得（composable内でメモ化）
- `useProductDetail(id: number)`: 商品詳細取得
