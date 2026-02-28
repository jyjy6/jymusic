# 00_OAS_PLAN (商品カタログサービス)

## 1. 目的
`jym-catalog-service`における商品の一覧取得、詳細表示、およびカテゴリ管理のためのAPI仕様を定義します。

## 2. コアエンドポイント
- `GET /api/v1/products`: 商品一覧取得（ページング対応）。
- `GET /api/v1/products/{id}`: 商品詳細情報の取得。
- `GET /api/v1/categories`: 商品カテゴリの一覧取得。

## 3. 技術スタック要件
- バックエンド: Spring Boot
- データベース: MySQL (商品/カテゴリ/在庫テーブル)
- エラー処理: `GlobalErrorHandler.GlobalException`
