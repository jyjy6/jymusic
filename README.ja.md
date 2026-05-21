# Jymusic

🌐 Language: [한국어](README.md) | [日本語](README.ja.md)

**音楽アルバム販売向け E コマースプラットフォーム** — Microservices Architecture (MSA) ベース

---

## プロジェクト概要

Jymusic は音楽アルバムを販売する E コマースサービスです。  
すべてのドメインは独立したマイクロサービスとして分離されており、単一の API ゲートウェイを通じてクライアントリクエストをルーティングします。

---

## 技術スタック

| 区分        | 技術                                                                                                |
| ----------- | --------------------------------------------------------------------------------------------------- |
| Frontend    | Nuxt 4 (Vue 3, TypeScript, Tailwind CSS)                                                            |
| Backend     | Spring Boot 3.x / 4.x (Java 21), JPA(CUD), MyBatis(R), Spring Cloud, Kafka, Redis, LangChain4j, RAG |
| API Gateway | Spring Cloud Gateway (WebMvc->WebFlux 非同期分散処理)                                               |
| Database    | MySQL (サービスごとの独立 DB), Pinecone                                                             |
| 認証        | JWT (Stateless), OAuth2(Google, Kakao)                                                              |
| インフラ    | Docker / Docker Compose                                                                             |

---

## サービス構成

```
jymusic/
├── jym-front/              # Nuxt 4 フロントエンド
├── jym-api-gateway/        # API ゲートウェイ (単一エントリーポイント)
├── jym-member-auth-service/ # 会員登録 / ログイン / JWT 認証
├── jym-catalog-service/    # 音楽アルバムカタログ (商品一覧/詳細)
├── jym-order-service/      # 注文処理
├── jym-payment-service/    # 決済処理
├── sdd-spec-docs/          # OpenAPI Spec ドキュメント (SDD 原則)
└── docker/                 # Docker 環境設定
```

### 各サービスの役割

| サービス                  | 役割                                                               |
| ------------------------- | ------------------------------------------------------------------ |
| `jym-api-gateway`         | すべてのクライアントリクエストの単一エントリーポイント。ルーティングおよび認証フィルター処理 |
| `jym-member-auth-service` | 会員登録、ログイン、JWT 発行および検証                              |
| `jym-catalog-service`     | 音楽アルバム商品の登録、照会、管理                                  |
| `jym-order-service`       | カートおよび注文の作成/照会                                         |
| `jym-payment-service`     | 決済リクエストおよび結果処理                                        |
| `jym-front`               | ユーザー向け Web UI                                                 |

---

## アーキテクチャ原則

- **Database-per-service**: 各サービスは自身の DB のみにアクセス (直接的なクロス DB アクセスは禁止)
- **Spec-Driven Development (SDD)**: すべての API 変更は OpenAPI Spec の作成から開始
- **Stateless**: サーバーにセッションを保存せず、JWT ベースで認可
- **単体テストカバレッジ 70%以上** を維持

---

## システム回復性および分散トランザクション (MSA Resilience)

マイクロサービス環境におけるデータ整合性とシステム障害の分離のため、次のようなパターンを実装しています。

- **非同期イベントストリーミング (Kafka)**: サービス間の結合度を下げ、スループットを高めるために Kafka を通じたイベント駆動通信を行います。処理に失敗したメッセージは DLT(Dead Letter Topic) に分離され、安全に管理されます。
- **Saga パターン (Choreography)**: 注文、決済、在庫(Catalog)サービスなど、複数サービスにまたがる分散トランザクションのデータ整合性を保証します。決済失敗や在庫不足などのエラー発生時には Kafka イベントを発行・消費し、補償トランザクション(注文キャンセル、在庫復旧)を自動実行します。
- **Transactional Outbox / Inbox パターン**: DB 保存と Kafka 発行が同一トランザクションに含まれないことで発生する Dual-Write 問題を解決します。ドメインデータと発行イベントを同じトランザクションで `outbox_event` に INSERT した後、別途ポーリングパブリッシャーが Kafka に発行します(Exponential Backoff リトライを含む)。コンシューマー側では `inbox_event` の `(eventId, consumerGroup)` unique 制約により重複消費を防ぎ、**effectively exactly-once** 処理を保証します。
- **Circuit Breaker & Retry (Resilience4j)**: やむを得ない同期 REST API 呼び出し(商品情報、注文金額照会など)の箇所に適用されています。他サービスへの障害伝播(Cascading Failure)を遮断し、Fast Failure および Fallback 処理によってシステム全体の安定性を確保します。

### 主要な動作フロー (Saga & Circuit Breaker)

**[Phase 1] 注文準備 (⚡ 同期呼び出し & Circuit Breaker)**

1. **Frontend** ➔ `Order Service` : 注文作成リクエスト
2. `Order Service` ➔ `Catalog Service` : 商品の有効性/単価確認 (REST API)
   - ⚡ **Circuit Breaker 保護区間**: Catalog 障害時に Fast Fail を返し、Order のスレッド枯渇を防止
3. `Order Service` : 注文を一時作成および保存 (`PENDING` 状態)

**[Phase 2] 在庫予約 (🔄 非同期 Kafka イベント通信)** 4. `Order Service` ➔ **Kafka** : `ORDER_CREATED` イベント発行 5. **Kafka** ➔ `Catalog Service` : イベント消費および在庫差し引き実行 6. `Catalog Service` ➔ **Kafka** : `STOCK_RESERVED` イベント発行 (在庫確保完了) 7. **Kafka** ➔ `Order Service` : イベント消費および状態更新 (`STOCK_RESERVED`)

**[Phase 3] 決済完了 (⚡ 同期呼び出し + 🔄 非同期 Kafka 通信)** 8. **Frontend** ➔ `Payment Service` : 決済準備リクエスト 9. `Payment Service` ➔ `Order Service` : 決済対象注文の金額検証 (REST API)

- ⚡ **Circuit Breaker 保護区間**: 決済金額検証のような必須のサービス間通信を保護

10. `Payment Service` : 外部 PG(Toss) 決済の最終承認および決済履歴 DB 保存完了
11. `Payment Service` ➔ **Kafka** : `PAYMENT_COMPLETED` イベント発行
12. **Kafka** ➔ `Order Service` : イベント消費および最終注文状態更新 (`PAID`)

**[Phase 4] 補償トランザクション (⚠️ 障害状況での自動ロールバック / Saga)**
Kafka を活用し、どちらか一方のロジックが失敗しても、他ドメインへロールバックイベントが自然に伝播されるように実装しています。

- **状況 A (在庫不足などによるキャンセル)**
  - `Catalog Service` が在庫不足を検知した場合 ➔ **Kafka** に `STOCK_RESERVATION_FAILED` イベントを発行
  - `Order Service` がこれを消費し、作成済み注文を `CANCELLED` に処理
- **状況 B (決済失敗またはキャンセル)**
  - `Payment Service` が承認失敗時 ➔ **Kafka** に `PAYMENT_FAILED` イベントを発行
  - `Order Service` がイベントを消費し、注文を最終的に **キャンセル処理 (`CANCELLED`)**
  - `Catalog Service` がイベントを消費し、すでに差し引いた **在庫を元に戻す (+)**

---

## ローカル実行

```bash
# Docker Compose で全サービスを起動
docker-compose -f docker-compose-dev.yml up
```
