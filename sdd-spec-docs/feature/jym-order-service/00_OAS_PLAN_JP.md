# 00_OAS_PLAN (注文サービス)

## 1. 目的
`jym-order-service`における注文作成、履歴追跡、およびステータス管理のためのAPI仕様を定義します。

## 2. コアエンドポイント
- `POST /api/v1/orders`: 新規注文の作成。
- `GET /api/v1/orders`: ユーザーの注文履歴取得。
- `GET /api/v1/orders/{id}`: 特定の注文の詳細取得。

## 3. 技術スタック要件
- バックエンド: Spring Boot
- データベース: MySQL (注文/注文項目テーブル)
- エラー処理: `GlobalErrorHandler.GlobalException`
