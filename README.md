# Jymusic

**음악 앨범 판매 이커머스 플랫폼** — Microservices Architecture (MSA) 기반

---

## 프로젝트 개요

Jymusic은 음악 앨범을 판매하는 이커머스 서비스입니다.  
모든 도메인이 독립적인 마이크로서비스로 분리되어 있으며, 단일 API 게이트웨이를 통해 클라이언트 요청을 라우팅합니다.

---

## 기술 스택

| 구분        | 기술                                                                                                |
| ----------- | --------------------------------------------------------------------------------------------------- |
| Frontend    | Nuxt 4 (Vue 3, TypeScript, Tailwind CSS)                                                            |
| Backend     | Spring Boot 3.x / 4.x (Java 21), JPA(CUD), MyBatis(R), Spring Cloud, Kafka, Redis, LangChain4j, RAG |
| API Gateway | Spring Cloud Gateway (WebMvc->WebFlux 비동기 분산처리)                                              |
| Database    | MySQL (서비스별 독립 DB), Pinecone                                                                  |
| 인증        | JWT (Stateless), OAuth2(Google, Kakao)                                                              |
| 인프라      | Docker / Docker Compose                                                                             |

---

## 서비스 구조

```
jymusic/
├── jym-front/              # Nuxt 4 프론트엔드
├── jym-api-gateway/        # API 게이트웨이 (단일 진입점)
├── jym-member-auth-service/ # 회원 가입 / 로그인 / JWT 인증
├── jym-catalog-service/    # 음악 앨범 카탈로그 (상품 목록/상세)
├── jym-order-service/      # 주문 처리
├── jym-payment-service/    # 결제 처리
├── sdd-spec-docs/          # OpenAPI Spec 문서 (SDD 원칙)
└── docker/                 # Docker 환경 설정
```

### 각 서비스 역할

| 서비스                    | 역할                                                         |
| ------------------------- | ------------------------------------------------------------ |
| `jym-api-gateway`         | 모든 클라이언트 요청의 단일 진입점. 라우팅 및 인증 필터 처리 |
| `jym-member-auth-service` | 회원 가입, 로그인, JWT 발급 및 검증                          |
| `jym-catalog-service`     | 음악 앨범 상품 등록, 조회, 관리                              |
| `jym-order-service`       | 장바구니 및 주문 생성/조회                                   |
| `jym-payment-service`     | 결제 요청 및 결과 처리                                       |
| `jym-front`               | 사용자 대면 웹 UI                                            |

---

## 아키텍처 원칙

- **Database-per-service**: 각 서비스는 자신의 DB만 접근 (직접 크로스 DB 접근 금지)
- **Spec-Driven Development (SDD)**: 모든 API 변경은 OpenAPI Spec 작성에서 시작
- **Stateless**: 서버에 세션 저장 없음, JWT 기반 인가
- **단위 테스트 커버리지 70% 이상** 유지

---

## 시스템 복원력 및 분산 트랜잭션 (MSA Resilience)

마이크로서비스 환경에서의 데이터 일관성과 시스템 장애 격리를 위해 다음과 같은 패턴들이 구현되어 있습니다.

- **비동기 이벤트 스트리밍 (Kafka)**: 서비스 간 결합도를 낮추고 처리량을 높이기 위해 Kafka를 통한 이벤트 기반 통신을 수행합니다. 처리 실패 메시지는 DLT(Dead Letter Topic)로 분리되어 안전하게 관리됩니다.
- **Saga 패턴 (Choreography)**: 주문, 결제, 재고(Catalog) 서비스 등 여러 서비스에 걸친 분산 트랜잭션의 데이터 일관성을 보장합니다. 결제 실패나 재고 부족 등 에러 발생 시 Kafka 이벤트를 발행하고 소비하여 보상 트랜잭션(주문 취소, 재고 복구)을 자동으로 실행합니다.
- **Circuit Breaker & Retry (Resilience4j)**: 불가피한 동기적 REST API 호출(상품 정보, 주문 금액 조회 등) 지점에 적용되어 있습니다. 타 서비스로의 장애 전파(Cascading Failure)를 차단하고, 빠른 실패(Fast Failure) 및 Fallback 처리를 통해 시스템 전체의 안정성을 확보합니다.

### 핵심 동작 단계별 흐름 (Saga & Circuit Breaker)

**[Phase 1] 주문 준비 (⚡ 동기 호출 & Circuit Breaker)**

1. **Frontend** ➔ `Order Service` : 주문 생성 요청
2. `Order Service` ➔ `Catalog Service` : 상품 유효성/단가 확인 (REST API)
   - ⚡ **Circuit Breaker 보호 구간**: Catalog 장애 시 빠른 에러(Fast Fail)를 반환하여 Order의 스레드 고갈 방지
3. `Order Service` : 주문 임시 생성 및 저장 (`PENDING` 상태)

**[Phase 2] 재고 예약 (🔄 비동기 Kafka 이벤트 통신)** 4. `Order Service` ➔ **Kafka** : `ORDER_CREATED` 이벤트 발행 5. **Kafka** ➔ `Catalog Service` : 이벤트 소비 및 재고 차감 수행 6. `Catalog Service` ➔ **Kafka** : `STOCK_RESERVED` 이벤트 발행 (재고 확보 완료) 7. **Kafka** ➔ `Order Service` : 이벤트 소비 및 상태 업데이트 (`STOCK_RESERVED`)

**[Phase 3] 결제 완료 (⚡ 동기 호출 + 🔄 비동기 Kafka 통신)** 8. **Frontend** ➔ `Payment Service` : 결제 준비 요청 9. `Payment Service` ➔ `Order Service` : 결제할 주문 금액 검증 (REST API)

- ⚡ **Circuit Breaker 보호 구간**: 결제 금액 검증과 같은 필수 교차 통신을 보호

10. `Payment Service` : 외부 PG(Toss) 결제 최종 승인 및 결제 내역 DB 저장 완료
11. `Payment Service` ➔ **Kafka** : `PAYMENT_COMPLETED` 이벤트 발행
12. **Kafka** ➔ `Order Service` : 이벤트 소비 및 최종 주문 상태 업데이트 (`PAID`)

**[Phase 4] 보상 트랜잭션 (⚠️ 장애 상황에서의 자동 롤백 / Saga)**
Kafka를 활용하여 어느 한쪽의 로직이 실패해도 다른 도메인에 롤백 이벤트가 자연스럽게 전파되도록 구현되어 있습니다.

- **상황 A (재고 부족 등 취소)**
  - `Catalog Service`가 재고 부족 감지 시 ➔ **Kafka**에 `STOCK_RESERVATION_FAILED` 이벤트 발행
  - `Order Service`가 이를 소비하여 생성되었던 주문을 `CANCELLED` 처리
- **상황 B (결제 실패 또는 취소)**
  - `Payment Service`가 승인 실패 시 ➔ **Kafka**에 `PAYMENT_FAILED` 이벤트 발행
  - `Order Service`가 이벤트를 소비하여 주문 최종 **취소 처리 (`CANCELLED`)**
  - `Catalog Service`가 이벤트를 소비하여 이미 차감했던 **재고 원상 복구 (+)**

---

## 로컬 실행

```bash
# 전체 서비스 Docker Compose로 실행
docker-compose -f docker-compose-dev.yml up
```
