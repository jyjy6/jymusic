############## Complete #################

# 03_JWT_DESIGN (認証戦略仕様)

## 1. 概要
MSA環境に最適化された非対称鍵 (RS256) ベースのJWT戦略およびRedisベースのリフレッシュトークンローテーション (RTR) を定義します。

## 2. 鍵管理 (Key Management)
- **アルゴリズム**: RS256 (RSA Signature with SHA-256)
- **秘密鍵 (Private Key)**: `jym-member-auth-service` に配置。トークンの **発行 (署名)** に使用。
- **公開鍵 (Public Key)**: `jym-api-gateway` と共有。トークンの **検証** に使用。

## 3. トークンのライフサイクル
### 3.1 アクセストークン (Access Token)
- **有効期限**: 15 ~ 30分
- **ペイロード**: `sub` (ユーザー名), `userId`, `role` (権限)
- **検証**: `jym-api-gateway` で一括処理

### 3.2 リフレッシュトークン (Refresh Token - RTR)
- **有効期限**: 7日間
- **ストレージ**: Redis (Key: `RT:{username}`, Value: `{token}`)
- **ローテーションポリシー**: アクセストークンの再発行のたびにリフレッシュトークンも更新し、セキュリティを強化

## 4. 主要コンポーネント
- **JwtProvider**: トークン発行専用ユーティリティ (秘密鍵使用)
- **JwtValidator**: ゲートウェイ専用トークン検証ユーティリティ (公開鍵使用)
- **RedisService**: リフレッシュトークン管理およびブラックリスト処理

## 5. MSAワークフロー
1. クライアントのログイン要求 -> **Auth Service** が AT/RT を発行
2. クライアントのリソース要求 -> **Gateway** が公開鍵で AT を検証
3. **Gateway** が検証済みユーザー情報を HTTP ヘッダー (`X-User-Id`, `X-User-Role`) に注入してバックエンドに伝達
