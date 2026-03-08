# jym-front: 商品カタログページ詳細設計

> **対象ドメイン**: 商品一覧、商品詳細、カタログ共通コンポーネント/型/コンポーザブル
> **関連サービス**: `jym-catalog-service` → `GET /api/v1/products`, `GET /api/v1/categories`

---

## 1. 商品一覧ページ `/products`

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

### URL同期
- カテゴリフィルター・現在ページはURLクエリ（`?page=0&categoryId=2`）と同期
- 戻る操作で前のフィルター/ページ状態を復元（`useRoute`、`useRouter` 利用）

---

## 2. 商品詳細ページ `/products/[id]`

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
| 「注文する」ボタン | クリック時はログイン確認後に処理（下記フロー参照） |
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

## 3. 型定義（`types/catalog.ts`）

```typescript
// 商品一覧用サマリー型
export interface ProductSummary {
  id: number
  title: string
  artist: string
  price: number
  thumbnailUrl: string | null
}

// 一覧APIレスポンスのページ型
export interface ProductListResponse {
  content: ProductSummary[]
  totalElements: number
  totalPages: number
}

// カテゴリ型
export interface Category {
  id: number
  name: string
}

// 商品詳細型
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

## 4. 状態管理（`composables/useCatalog.ts`）

```typescript
// useProducts: 商品一覧取得
const products = ref<ProductSummary[]>([])
const totalPages = ref(0)
const currentPage = ref(0)
const selectedCategoryId = ref<number | null>(null)
const isLoading = ref(false)

async function fetchProducts(): Promise<void> { ... }

// useCategories: カテゴリ一覧取得（コンポーザブル内でメモ化）
const categories = ref<Category[]>([])
async function fetchCategories(): Promise<void> { ... }

// useProductDetail: 商品詳細取得
async function useProductDetail(id: number): Promise<ProductDetail> { ... }
```

> `useProducts`、`useCategories`、`useProductDetail` をそれぞれ named export で分離。
> Pinia store は不要 — ページ単位のローカル状態で管理。

---

## 5. 共通コンポーネント（商品関連）

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
