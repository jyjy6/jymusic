# 02_CONTROLLER_DESIGN (会員・認証サービス)

## 1. 概要
このドキュメントは、`openapi.yaml` 仕様に基づいて `jym-member-auth-service` の REST API コントローラーを定義します。

## 2. コントローラー: `MemberAuthController`
ユーザー登録および認証ワークフローを処理します。

| エンドポイント | メソッド | リクエストDTO | レスポンスDTO | 説明 |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/auth/register` | `POST` | `MemberRegistrationRequest` | `MemberProfileResponse` | 新規ユーザーアカウントの作成 |
| `/api/v1/auth/login` | `POST` | `MemberLoginRequest` | `AuthTokenResponse` | ユーザー認証およびJWT発行 |

## 3. コントローラー: `MemberController`
プロフィール管理およびユーザー固有のクエリを処理します。

| エンドポイント | メソッド | リクエストDTO | レスポンスDTO | 説明 |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/members/me` | `GET` | (なし) | `MemberProfileResponse` | 現在のユーザープロフィールの取得 |

## 4. 実装の詳細
- **バリデーション**: リクエストDTOに `@Valid` を使用してバリデーションを実行します。
- **成功レスポンス**: 登録時は `HttpStatus.CREATED` (201)、プロフィール照会時は `HttpStatus.OK` (200) を返します。
- **サービス連携**: コントローラーはサービスレイヤーのメソッドのみを呼び出す必要があります。
