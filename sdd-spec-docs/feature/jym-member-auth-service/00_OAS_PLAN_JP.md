# 00_OAS_PLAN (会員・認証サービス)

## 1. 目的
`jym-member-auth-service`の認証、会員登録、プロフィール管理のためのAPI仕様を定義します。

## 2. コアエンドポイント
- `POST /api/v1/auth/login`: JWTトークンの発行。
- `POST /api/v1/auth/register`: 新規ユーザー登録。
- `GET /api/v1/members/me`: 現在のユーザープロフィールの取得。

## 3. 技術スタック要件
- バックエンド: Spring Boot (Spring Security)
- データベース: MySQL (ユーザー/ロールテーブル)
- エラー処理: `GlobalErrorHandler.GlobalException`
