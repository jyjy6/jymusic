# 04_TEST_SPEC（Member & Auth Service）

## 1. 概要
本ドキュメントは `jym-member-auth-service` の単体テスト仕様を定義する。
テストはビジネスコードの**後**に記述し、実装ではなく OAS 仕様を正とする。
すべてのテストは単体テストとする。統合テストは `00_architecture.md §2.3` に従い後回しとする。

---

## 2. テストレイヤーとツール

| レイヤー | アノテーション / ツール | スコープ |
| :--- | :--- | :--- |
| Controller | `@WebMvcTest` + `MockMvc` | HTTP リクエスト/レスポンス契約 |
| Service | `@ExtendWith(MockitoExtension.class)` | ビジネスロジックの分離 |
| JwtProvider | `@ExtendWith(MockitoExtension.class)` | トークン生成とクレーム |
| RedisService | `@ExtendWith(MockitoExtension.class)` | リフレッシュトークンのライフサイクル |

---

## 3. Controller レイヤーテスト

### 3.1 `MemberAuthControllerTest`

#### `POST /api/v1/auth/register`

| # | シナリオ | 入力 | 期待ステータス | 期待レスポンス |
| :--- | :--- | :--- | :--- | :--- |
| R-01 | 正常系 — 有効な登録 | 有効な `MemberRegistrationRequest` | `201 Created` | 正しいフィールドを持つ `MemberProfileResponse` |
| R-02 | ユーザー名重複 | 既存の `username` | `409 Conflict` | `GlobalException` エラーレスポンス |
| R-03 | メール重複 | 既存の `email` | `409 Conflict` | `GlobalException` エラーレスポンス |
| R-04 | 必須フィールド欠落（`nickname`） | `nickname` なしのリクエスト | `400 Bad Request` | Bean Validation エラー |
| R-05 | 必須フィールド欠落（`username`） | `username` なしのリクエスト | `400 Bad Request` | Bean Validation エラー |

#### `POST /api/v1/auth/login`

| # | シナリオ | 入力 | 期待ステータス | 期待レスポンス |
| :--- | :--- | :--- | :--- | :--- |
| L-01 | 正常系 — 正しい認証情報 | 有効な `MemberLoginRequest` | `200 OK` | `accessToken` と `refreshToken` を持つ `AuthTokenResponse` |
| L-02 | パスワード誤り | 正しい username、誤った password | `401 Unauthorized` | `GlobalException` エラーレスポンス |
| L-03 | 存在しないユーザー名 | 未知の `username` | `401 Unauthorized` | `GlobalException` エラーレスポンス |
| L-04 | 非アクティブアカウント | `is_active = false` のメンバー | `403 Forbidden` | `GlobalException` エラーレスポンス |
| L-05 | 必須フィールド欠落 | `password` なしのリクエスト | `400 Bad Request` | Bean Validation エラー |

---

### 3.2 `MemberControllerTest`

#### `GET /api/v1/members/me`

| # | シナリオ | 入力 | 期待ステータス | 期待レスポンス |
| :--- | :--- | :--- | :--- | :--- |
| M-01 | 正常系 — 認証済みリクエスト | ゲートウェイが注入した有効な `X-User-Id` / `X-User-Role` ヘッダー | `200 OK` | 正しいフィールドを持つ `MemberProfileResponse` |
| M-02 | ユーザー識別ヘッダー欠落 | `X-User-Id` ヘッダーなし | `401 Unauthorized` | `GlobalException` エラーレスポンス |
| M-03 | DB にユーザー不在 | 有効なヘッダーだがメンバー削除済み | `404 Not Found` | `GlobalException` エラーレスポンス |

> **Note**: JWT 検証は `jym-api-gateway` で行う。本サービスはゲートウェイが転送する `X-User-Id` / `X-User-Role` ヘッダーのみを信頼する。

---

## 4. Service レイヤーテスト

### 4.1 `MemberAuthServiceTest`

#### `register(MemberRegistrationRequest)`

| # | シナリオ | モック動作 | 期待結果 |
| :--- | :--- | :--- | :--- |
| RS-01 | 正常系 | `repo.existsByUsername` → false、`repo.existsByEmail` → false、`repo.save` → 保存されたエンティティ | `MemberProfileResponse` を返す |
| RS-02 | ユーザー名既存 | `repo.existsByUsername` → true | `GlobalException`（CONFLICT）をスロー |
| RS-03 | メール既存 | `repo.existsByEmail` → true | `GlobalException`（CONFLICT）をスロー |
| RS-04 | パスワードエンコード | BCryptPasswordEncoder が生パスワードで呼ばれる | 保存されたパスワードは生パスワードと一致しない |

#### `login(MemberLoginRequest)`

| # | シナリオ | モック動作 | 期待結果 |
| :--- | :--- | :--- | :--- |
| LS-01 | 正常系 | メンバー発見、パスワード一致、アカウント有効 | 空でないトークンを持つ `AuthTokenResponse` を返す |
| LS-02 | ユーザー名未発見 | `repo.findByUsername` → `Optional.empty()` | `GlobalException`（UNAUTHORIZED）をスロー |
| LS-03 | パスワード不一致 | `passwordEncoder.matches` → false | `GlobalException`（UNAUTHORIZED）をスロー |
| LS-04 | アカウント非アクティブ | メンバー発見だが `is_active = false` | `GlobalException`（FORBIDDEN）をスロー |
| LS-05 | リフレッシュトークンを Redis に保存 | ログイン成功 | `redisService.save(RT:{username}, refreshToken)` が1回呼ばれる |

#### `getMyProfile(Long userId)`

| # | シナリオ | モック動作 | 期待結果 |
| :--- | :--- | :--- | :--- |
| MP-01 | 正常系 | `repo.findById(userId)` → メンバー存在 | `MemberProfileResponse` を返す |
| MP-02 | メンバー未発見 | `repo.findById(userId)` → `Optional.empty()` | `GlobalException`（NOT_FOUND）をスロー |

---

## 5. JWT コンポーネントテスト

### 5.1 `JwtProviderTest`

| # | シナリオ | 期待結果 |
| :--- | :--- | :--- |
| J-01 | アクセストークン生成 | 空でない JWT 文字列を返す |
| J-02 | アクセストークンに正しいクレームを含む | `sub` = username、`userId`、`role` がすべて正しい値で存在 |
| J-03 | アクセストークン有効期限が仕様内 | 発行から約 15〜30 分に設定 |
| J-04 | リフレッシュトークン生成 | 空でないトークン文字列を返す |
| J-05 | リフレッシュトークン有効期限が仕様内 | 発行から約 7 日に設定 |

---

## 6. RedisService テスト

### 6.1 `RedisServiceTest`

| # | シナリオ | 期待結果 |
| :--- | :--- | :--- |
| RD-01 | リフレッシュトークン保存 | キー `RT:{username}` に正しい値で保存 |
| RD-02 | リフレッシュトークン取得 — 存在 | 保存されたトークン文字列を返す |
| RD-03 | リフレッシュトークン取得 — 不存在 | `null` または `Optional.empty()` を返す |
| RD-04 | リフレッシュトークン削除 | キー `RT:{username}` を削除 |
| RD-05 | RTR — 再発行時に旧トークン無効化 | 旧 RT 削除、新 RT を同一キーで保存 |

---

## 7. 命名規則

- テストクラス: `{TargetClass}Test.java`
- テストメソッド: `{method}_{scenario}_{expectedResult}`（例: `login_wrongPassword_throwsUnauthorized`）
- 人間が読める説明には `@DisplayName` で日本語を使用する。

---

## 8. 対象外（後回し）

- 統合テスト（DB、Redis コンテナベース）
- セキュリティフィルターチェーンテスト
- ソーシャルログイン（`GOOGLE`、`NAVER`、`KAKAO`）パス
