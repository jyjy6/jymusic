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
