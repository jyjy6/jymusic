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

| パス | ページ | 認証必要 | 目的 |
|---|---|---|---|
| `/` | メイン（ホーム） | 不要 | サービス紹介、ナビゲーション |
| `/auth/register` | 会員登録 | 不要 | POST `/api/v1/auth/register` |
| `/auth/login` | ログイン | 不要 | POST `/api/v1/auth/login` |
| `/me` | マイプロフィール | **必要** | GET `/api/v1/members/me` |

## 4. 認証フロー

```
[ログイン成功]
  → アクセストークン → Pinia store（メモリ）
  → リフレッシュトークン → HttpOnly Cookie（サーバーが自動設定）

[認証必要ページへのアクセス]
  → PiniaにアクセストークンなしI → /auth/login にリダイレクト

[APIリクエスト]
  → Axiosインターセプターが Authorization: Bearer <token> を自動注入
```

## 5. レイアウト

- **共通レイアウト** (`layouts/default.vue`): トップナビゲーションバーを含む
  - ログイン状態: ユーザー名表示 + ログアウトボタン
  - 未ログイン状態: ログイン / 会員登録リンク

## 6. エラーハンドリング

- APIレスポンス `401` → アクセストークン期限切れとみなす → ログインページにリダイレクト
- APIレスポンス `400` / `409` など → 該当フォーム下部にエラーメッセージをインライン表示
- 共通エラーレスポンス構造（`GlobalExceptionHandler`基準）:
  ```json
  { "status": 400, "code": "ERR_XXX", "message": "..." }
  ```
