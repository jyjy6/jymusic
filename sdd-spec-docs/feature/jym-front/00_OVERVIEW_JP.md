# jym-front: フロントエンド概要とページ設計

## 1. 技術スタック

| 項目 | 技術 |
|---|---|
| フレームワーク | Nuxt 4 (Vue 3, TypeScript) |
| スタイリング | Tailwind CSS |
| API通信 | Axios |
| 状態管理 | Pinia |
| 認証方式 | JWT (アクセストークン: メモリ/Pinia, リフレッシュトークン: HttpOnly Cookie) |

## 2. API接続先

すべてのAPIリクエストは**APIゲートウェイ**の単一エンドポイントにのみ送信します。

```
http://localhost:8080  ←  jym-api-gateway
```

フロントエンドは各下流サービス（member-auth、catalogなど）のポートを直接知ってはいけません。

## 3. ページ一覧

| パス | ページ | 認証必要 | 目的 | 詳細スペックファイル |
|---|---|---|---|---|
| `/` | メイン（ホーム） | 不要 | サービス紹介、ナビゲーション | `01_PAGES_AUTH_JP.md` |
| `/auth/register` | 会員登録 | 不要 | POST `/api/v1/auth/register` | `01_PAGES_AUTH_JP.md` |
| `/auth/login` | ログイン | 不要 | POST `/api/v1/auth/login` | `01_PAGES_AUTH_JP.md` |
| `/me` | マイプロフィール | **必要** | GET `/api/v1/members/me` | `01_PAGES_AUTH_JP.md` |
| `/products` | 商品一覧 | 不要 | GET `/api/v1/products`, GET `/api/v1/categories` | `02_PAGES_CATALOG_JP.md` |
| `/products/[id]` | 商品詳細 | 不要 | GET `/api/v1/products/{id}` | `02_PAGES_CATALOG_JP.md` |
| `/admin/products/new` | 商品登録 | **ROLE_ADMIN** | POST `/api/v1/products` | `03_PAGES_CATALOG_ADMIN_JP.md` |
| `/admin/products/[id]/edit` | 商品編集 | **ROLE_ADMIN** | PUT `/api/v1/products/{id}` | `03_PAGES_CATALOG_ADMIN_JP.md` |

## 4. スペックファイル構成

| ファイル | 対象ドメイン |
|---|---|
| `01_PAGES_AUTH_JP.md` | メイン、会員登録、ログイン、マイプロフィール、Axios/Pinia/ミドルウェア共通設定 |
| `02_PAGES_CATALOG_JP.md` | 商品一覧、商品詳細、カタログ型/コンポーザブル/コンポーネント |
| `03_PAGES_CATALOG_ADMIN_JP.md` | 商品登録/編集、Presigned URLアップロード、FileUploadコンポーネント、管理者ミドルウェア |

## 5. 認証フロー

```
[ログイン成功]
  → アクセストークン → Pinia store（メモリ）
  → リフレッシュトークン → HttpOnly Cookie（サーバーが自動設定）

[認証必要ページへのアクセス]
  → PiniaにアクセストークンなしI → /auth/login にリダイレクト

[APIリクエスト]
  → Axiosインターセプターが Authorization: Bearer <token> を自動注入
```

## 6. レイアウト

- **共通レイアウト** (`layouts/default.vue`): トップナビゲーションバーを含む
  - ログイン状態: ユーザー名表示 + ログアウトボタン
  - 未ログイン状態: ログイン / 会員登録リンク

## 7. エラーハンドリング

- APIレスポンス `401` → アクセストークン期限切れとみなす → ログインページにリダイレクト
- APIレスポンス `400` / `409` など → 該当フォーム下部にエラーメッセージをインライン表示
- 共通エラーレスポンス構造（`GlobalExceptionHandler`基準）:
  ```json
  { "status": 400, "code": "ERR_XXX", "message": "..." }
  ```
