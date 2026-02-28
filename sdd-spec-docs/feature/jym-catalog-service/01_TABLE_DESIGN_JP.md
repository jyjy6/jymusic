# 01_TABLE_DESIGN (商品カタログサービス)

## 1. 概要
このドキュメントは、`openapi.yaml` 仕様に基づいて `jym-catalog-service` のデータベーススキーマを定義します。商品（アルバム）情報とカテゴリを管理します。

## 2. テーブル: `categories`
音楽ジャンルなどの商品カテゴリを保存します。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, 自動増分 | カテゴリ固有の識別子 |
| `name` | VARCHAR(50) | Unique, Not Null | カテゴリ名 (例: Rock, Pop, Jazz) |
| `created_at` | DATETIME | デフォルト: 現在時刻 | レコード作成日時 |

## 3. テーブル: `products`
音楽アルバムの商品情報を保存します。

| カラム名 | データ型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, 自動増分 | 商品固有の識別子 |
| `category_id` | BIGINT | FK (categories.id) | カテゴリ参照外部キー |
| `title` | VARCHAR(100) | Not Null | アルバムタイトル |
| `artist` | VARCHAR(100) | Not Null | アーティスト名 |
| `description` | TEXT | | 商品の詳細説明 |
| `price` | DECIMAL(10,2) | Not Null | 商品価格 |
| `stock_quantity` | INT | Not Null, デフォルト: 0 | 現在の在庫数 |
| `thumbnail_url` | VARCHAR(255) | | リスト表示用サムネイルURL |
| `image_url` | VARCHAR(255) | | 詳細表示用メイン画像URL |
| `is_available` | BOOLEAN | デフォルト: TRUE | 販売可能状態 |
| `created_at` | DATETIME | デフォルト: 現在時刻 | レコード作成日時 |
| `updated_at` | DATETIME | デフォルト: 現在時刻 | 最終更新日時 |

## 4. 実装上の注意事項
- **Lombok**: 全てのエンティティクラスで `@Builder`, `@Getter`, `@NoArgsConstructor` を使用します。
- **金額の扱い**: 金額の正確性を保つため、Javaコードでは `BigDecimal` 型を使用します。
- **リレーション**: `Product` エンティティは `Category` エンティティと 多対一 (Many-to-One) の関係を持ちます。
