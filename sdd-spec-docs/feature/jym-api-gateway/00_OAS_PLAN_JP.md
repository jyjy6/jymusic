############## Complete #################

# 00_OAS_PLAN (API ゲートウェイ)

## 1. 目的
`jym-api-gateway`のルーティング戦略とグローバルセキュリティフィルタを定義します。すべてのクライアントリクエストの単一エントリポイントとして機能します。

## 2. ルーティングルール (パスフォワーディング)
- `/api/v1/auth/**` -> `jym-member-auth-service`
- `/api/v1/members/**` -> `jym-member-auth-service`
- `/api/v1/products/**` -> `jym-catalog-service`
- `/api/v1/categories/**` -> `jym-catalog-service`
- `/api/v1/orders/**` -> `jym-order-service`
- `/api/v1/payments/**` -> `jym-payment-service`

## 3. グローバル責任 (Global Responsibilities)
- **認証**: 保護されたルートに対して、ヘッダーのJWTトークンを検証します。
- **CORS設定**: Nuxt 4フロントエンドのためのクロスオリジンリソース共有を管理します。
- **エラー処理**: 下流のサービスが応答しない場合、`GlobalExceptionHandler`を使用して統一されたエラーレスポンスを返します。
