# 01_TABLE_DESIGN (会員・認証サービス)

## 1. 概要
このドキュメントは、`openapi.yaml` 仕様に基づいて `jym-member-auth-service` のデータベーススキーマを定義します。

## 2. テーブル: `members`
ユーザーアカウント情報および認証データを保存します。

| カラム名 | 데이터型 | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, 自動増分 | ユーザー固有の識別子 |
| `username` | VARCHAR(50) | Unique, Not Null | 固有のログイン識別子 (ID) |
| `password` | VARCHAR(255) | | BCryptでハッシュ化されたパスワード (OAuthログイン時はNULL可) |
| `email` | VARCHAR(100) | | ユーザー連絡用メールアドレス (任意) |
| `nickname` | VARCHAR(50) | Not Null | サービス内で表示されるニックネーム |
| `role` | VARCHAR(20) | Not Null | ユーザー権한 (ROLE_USER, ROLE_ADMIN) |
| `auth_provider` | VARCHAR(20) | Not Null | 認証元 (LOCAL, GOOGLE, NAVER, KAKAO) |
| `provider_id` | VARCHAR(255) | | ソーシャルサービスの固有識別子 |
| `is_active` | BOOLEAN | デフォルト: TRUE | アカウントの有効状態 |
| `created_at` | DATETIME | デフォルト: 현재時刻 | レコード作成日時 |
| `updated_at` | DATETIME | デフォルト: 현재時刻 | 最終更新日時 |



## 3. 実装上の注意事項
- **Lombok**: エンティティクラスで `@Builder`, `@Getter`, `@NoArgsConstructor` を積極的に活用します。
- **セキュリティ**: パスワードを平文で保存してはいけません。Spring Securityの `PasswordEncoder` を使用します。
- **インデックス**: 高速な検索のために `email` と `username` にユニークインデックスを適用します。
