# MSA 복원력(Resilience) 패턴 가이드 — Kafka · Saga · Circuit Breaker

> **목적**: Jymusic 프로젝트에 Kafka, Saga 패턴, Circuit Breaker를 도입하기 전에 알아야 할 핵심 원리와 도입 사유를 설명합니다.  
> **대상 독자**: MSA 패턴을 처음 학습하는 개발자  
> **관련 스펙**: `01_KAFKA_INFRASTRUCTURE_SPEC_KR.md`, `02_SAGA_ORDER_PAYMENT_SPEC_KR.md`, `03_CIRCUIT_BREAKER_SPEC_KR.md`

---

## 목차

1. [현재 아키텍처의 문제점 분석](#1-현재-아키텍처의-문제점-분석)
2. [메시지 브로커(Kafka) — 왜 필요한가](#2-메시지-브로커kafka--왜-필요한가)
3. [Saga 패턴 — 분산 트랜잭션을 대체하는 방법](#3-saga-패턴--분산-트랜잭션을-대체하는-방법)
4. [Circuit Breaker — 장애 전파를 차단하는 방법](#4-circuit-breaker--장애-전파를-차단하는-방법)
5. [Jymusic에 어떤 패턴 조합을 적용하는가](#5-jymusic에-어떤-패턴-조합을-적용하는가)
6. [현업 베스트 프랙티스 체크리스트](#6-현업-베스트-프랙티스-체크리스트)

---

## 1. 현재 아키텍처의 문제점 분석

### 1.1 현재 서비스 간 통신 구조 (AS-IS)

```
┌──────────┐     REST      ┌──────────────┐     REST      ┌─────────────┐
│  Frontend │─────────────→│  order-svc   │─────────────→│ catalog-svc │
│  (Nuxt)  │              │  (8083)      │  getProduct   │  (8082)     │
└──────────┘              └──────┬───────┘              └─────────────┘
      │                         │
      │                         │ REST (updateOrderStatus)
      │                         ▼
      │    REST           ┌──────────────┐     REST      ┌─────────────┐
      └──────────────────→│ payment-svc  │─────────────→│  Toss API   │
                          │  (8084)      │              │  (외부 PG)   │
                          └──────────────┘              └─────────────┘
```

> **핵심**: 모든 서비스 간 통신이 **동기(Synchronous) REST 호출**로만 이루어져 있습니다.

### 1.2 구체적 문제 시나리오

#### 문제 ① — 결제 성공 후 주문 상태 업데이트 실패 (데이터 불일치)

```
시나리오: 사용자가 결제 승인(confirm)을 요청합니다.

1. payment-service → Toss API 승인 요청     ✅ 성공 (카드 결제 완료)
2. payment-service → DB에 Payment 저장       ✅ 성공
3. payment-service → order-service 상태 PAID  ❌ 실패 (네트워크 타임아웃)

결과:
  - Toss에서는 카드가 결제됨 (돈이 빠져나감)
  - payment DB에는 DONE 상태로 기록됨
  - order DB에는 여전히 PENDING 상태 ← 💀 데이터 불일치!
  - 사용자는 결제했는데 주문이 "결제 대기" 상태로 보임
```

**왜 발생하는가?**
- 3개의 서로 다른 시스템(Toss, payment DB, order DB)에 걸친 작업을 **하나의 트랜잭션으로 묶을 수 없기 때문**입니다.
- 로컬 DB 트랜잭션(`@Transactional`)은 **단일 DB 내에서만** 원자성을 보장합니다.
- Toss API 호출은 HTTP 외부 호출이므로 DB 트랜잭션에 포함되지 않습니다.

#### 문제 ② — 재고 경쟁 조건 (Race Condition)

```
시나리오: 재고 1개 남은 앨범에 2명이 동시에 주문합니다.

사용자 A: createOrder → catalogClient.getProductInfo() → stock=1 ≥ 1 ✅ → 주문 생성
사용자 B: createOrder → catalogClient.getProductInfo() → stock=1 ≥ 1 ✅ → 주문 생성

결과:
  - 재고 1개인데 주문 2개가 생성됨
  - 재고 차감 로직 자체가 없음 (조회만 함)
  - 결제 단계에서야 문제 발생 가능
```

**왜 발생하는가?**
- 현재 코드는 `catalogClient.getProductInfo()`로 **재고를 조회만** 하고, **차감(deduction)하지 않습니다.**
- 조회와 주문 생성 사이에 시간 간격이 있으므로 동시 요청 시 같은 재고를 두 번 사용할 수 있습니다.

#### 문제 ③ — 장애 전파 (Cascading Failure)

```
시나리오: catalog-service가 부하로 응답이 느려집니다 (5초+).

1. 사용자가 장바구니를 열면 → order-service가 catalog-service를 호출
2. catalog-service 응답이 5초 걸림 → order-service 스레드가 5초간 점유
3. 여러 사용자가 동시에 장바구니를 열면 → order-service 스레드 풀 고갈
4. order-service 자체도 느려짐 → API Gateway 타임아웃 → 프론트엔드 에러

결과:
  - catalog-service 하나의 문제가 order-service로 전파
  - order-service의 다른 기능(주문 조회 등)도 영향 받음
  - 시스템 전체가 도미노처럼 무너짐 (Cascading Failure)
```

### 1.3 문제 요약표

| 문제 | 원인 | 해결 기술 |
|---|---|---|
| 결제 후 주문 상태 불일치 | 분산 트랜잭션 부재 | **Saga 패턴** + **Kafka** |
| 재고 경쟁 조건 | 재고 조회만, 선점(Lock) 없음 | **Kafka 이벤트 기반 재고 예약** |
| 장애 전파(Cascading Failure) | 동기 REST 의존, 보호 장치 없음 | **Circuit Breaker** |
| 서비스 다운 시 데이터 유실 | 동기 호출 실패 시 재시도 불가 | **Kafka** (메시지 영속성) |

---

## 2. 메시지 브로커(Kafka) — 왜 필요한가

### 2.1 동기 vs 비동기 통신

#### 동기 통신 (현재 방식)

```
order-service                    catalog-service
     │                                │
     │── GET /products/1 ──────────→ │
     │                                │ (처리 중... 500ms)
     │← { title, price, stock } ────│
     │                                │
     ▼ (다음 로직 실행)
```

- **장점**: 단순, 즉시 결과 확인 가능
- **단점**: 호출 대상이 다운되면 호출자도 실패, 스레드 점유, 강결합

#### 비동기 통신 (Kafka 도입 후)

```
order-service        Kafka Broker        catalog-service
     │                    │                    │
     │── publish ────────→│                    │
     │   "order.created"  │                    │
     │                    │── deliver ────────→│
     ▼ (즉시 응답)        │                    │ (별도 처리)
                          │← "stock.reserved" ─│
                          │                    │
     │← consume ─────────│                    │
     │   "stock.reserved" │                    │
```

- **장점**: 호출자가 대상의 상태에 영향 받지 않음, 자연스런 재시도, 이벤트 영속
- **단점**: 최종 일관성(Eventual Consistency), 복잡도 증가

### 2.2 Kafka를 선택하는 이유

| 특성 | RabbitMQ | Kafka | 선택 이유 |
|---|---|---|---|
| 메시지 영속성 | 설정 필요 | **기본 영속** | 장애 시 메시지 유실 방지 |
| 처리량 | 중 | **초고속** | 향후 확장성 대비 |
| 메시지 순서 보장 | ✗ | **파티션 내 보장** | 주문 이벤트 순서 중요 |
| Consumer Group | ✗ | **✓** | 서비스 인스턴스 확장 시 자동 분산 |
| 재처리(Replay) | ✗ | **✓** | 장애 복구 시 이벤트 재생 가능 |
| Spring 생태계 | ✓ | **✓** | `spring-kafka` 공식 지원 |

> **현업 관점**: 한국 IT 대기업(카카오, 네이버, 쿠팡, 토스)에서 Kafka는 MSA 표준 인프라입니다.  
> 이직/면접에서도 Kafka 경험이 크게 유리합니다.

### 2.3 Kafka 핵심 개념 (5분 요약)

```
                    ┌──────────────────────────────────────┐
                    │          Kafka Cluster                │
                    │                                      │
   Producer         │  Topic: "order-events"               │         Consumer
  (order-svc)       │  ┌─────────────────────────────┐     │       (payment-svc)
      │             │  │ Partition 0: [m1][m2][m3]    │     │           │
      │── push ────→│  │ Partition 1: [m4][m5]        │─────│── pull ──→│
      │             │  │ Partition 2: [m6]            │     │           │
                    │  └─────────────────────────────┘     │
                    │                                      │
                    │  각 메시지는 디스크에 영속 저장         │
                    │  retention.ms 동안 보관 (기본 7일)     │
                    └──────────────────────────────────────┘
```

| 개념 | 설명 | Jymusic에서의 의미 |
|---|---|---|
| **Topic** | 메시지를 분류하는 채널 | `order-events`, `payment-events`, `stock-events` 등 |
| **Partition** | Topic 내 병렬 처리 단위 | `orderId`를 key로 → 같은 주문의 이벤트는 같은 파티션에 → 순서 보장 |
| **Producer** | 메시지를 발행하는 쪽 | order-service가 `OrderCreated` 이벤트 발행 |
| **Consumer** | 메시지를 소비하는 쪽 | payment-service가 `OrderCreated` 이벤트를 수신 |
| **Consumer Group** | 같은 서비스의 여러 인스턴스 | payment-service 2대 → 파티션을 나눠 처리 |
| **Offset** | 소비자가 읽은 위치 | 장애 복구 시 마지막 offset부터 재개 |
| **Dead Letter Topic (DLT)** | 처리 실패 메시지 전용 토픽 | 재시도 횟수 초과 시 DLT로 이동 → 수동 조치 |

### 2.4 이벤트 기반 아키텍처의 핵심 원칙

#### ① 이벤트는 '사실(Fact)'을 기술한다

```java
// ✅ 좋은 이벤트 — "무슨 일이 일어났는지" 기술
OrderCreatedEvent { orderId: 1, memberId: 42, totalAmount: 87000, items: [...] }

// ❌ 나쁜 이벤트 — "무엇을 하라"는 명령
ReserveStockCommand { productId: 5, quantity: 2 }
```

이벤트는 과거형으로 이름짓습니다: `Created`, `Completed`, `Failed`, `Cancelled`

#### ② Idempotency (멱등성) — 같은 이벤트를 여러 번 처리해도 결과가 동일

```java
// 네트워크 이슈로 같은 OrderCreatedEvent가 2번 도착할 수 있음
// → Consumer는 orderId로 중복 체크하여 이미 처리한 이벤트는 무시

@KafkaListener(topics = "order-events")
void handle(OrderCreatedEvent event) {
    if (stockReservationRepository.existsByOrderId(event.getOrderId())) {
        log.info("이미 처리된 이벤트, skip: orderId={}", event.getOrderId());
        return;  // 멱등성 보장
    }
    // ... 정상 처리
}
```

#### ③ Eventual Consistency (최종 일관성)

```
[동기 방식 - 강한 일관성]
  A 서비스 DB 커밋 → B 서비스 DB 커밋 → 응답   (둘 다 동시에 일관됨)

[비동기 방식 - 최종 일관성]  
  A 서비스 DB 커밋 → 응답                        (A만 일관됨)
  ... (수 ms ~ 수 초 후) ...
  B 서비스가 이벤트 소비 → B 서비스 DB 커밋        (이후 B도 일관됨)
```

- 짧은 시간 동안 A와 B의 데이터가 불일치할 수 있지만, **최종적으로는 반드시 일관됩니다.**
- 이것을 거부감 없이 받아들이는 것이 MSA 이벤트 기반 설계의 출발점입니다.
- 현실 세계의 대부분의 비즈니스 프로세스가 실제로 이 방식입니다 (예: 온라인 주문 → 배송 → 수령).

---

## 3. Saga 패턴 — 분산 트랜잭션을 대체하는 방법

### 3.1 왜 분산 트랜잭션(2PC)을 쓰지 않는가?

모놀리식에서는 하나의 DB 트랜잭션으로 모든 작업을 원자적으로 처리할 수 있었습니다:

```java
// 모놀리식 — 하나의 @Transactional로 전부 해결
@Transactional
public void processOrder(OrderRequest req) {
    Order order = orderRepo.save(new Order(...));       // orders 테이블
    stockRepo.decrease(productId, quantity);             // products 테이블 (같은 DB)
    Payment payment = paymentRepo.save(new Payment(...));// payments 테이블 (같은 DB)
    // 셋 중 하나라도 실패하면 전체 롤백 ✅
}
```

MSA에서는 각 서비스가 **별도 DB**를 갖습니다 (Database-per-service 원칙).
따라서 2PC(Two-Phase Commit)가 이론적으로 가능하지만:

| 2PC의 문제 | 설명 |
|---|---|
| **가용성 저하** | 참여 서비스 중 하나만 다운되어도 전체 트랜잭션 블로킹 |
| **성능 저하** | 모든 참여자가 prepare → commit 2단계를 거쳐야 하므로 느림 |
| **NoSQL/외부 API 미지원** | Toss Payments 같은 외부 API는 2PC에 참여 불가 |
| **현업 미사용** | Spring Boot MSA에서 XA 트랜잭션은 사실상 사용하지 않음 |

> **결론**: MSA에서는 2PC 대신 **Saga 패턴**으로 "비즈니스 레벨의 롤백"을 구현합니다.

### 3.2 Saga 패턴이란?

**Saga**는 긴(Long-running) 비즈니스 트랜잭션을 **여러 개의 로컬 트랜잭션 + 보상 트랜잭션(Compensation)**으로 분해하는 패턴입니다.

```
[정상 흐름]
  T1(주문 생성) → T2(재고 예약) → T3(결제 처리) → T4(주문 확정)

[T3에서 실패 시 — 보상 트랜잭션 실행]
  T1(주문 생성) → T2(재고 예약) → T3(결제 실패!) 
                                     ↓
                  C2(재고 복원) ← C1(주문 취소)
```

| 용어 | 설명 |
|---|---|
| **로컬 트랜잭션 (Ti)** | 각 서비스 내부의 DB 트랜잭션. `@Transactional`로 원자적 처리. |
| **보상 트랜잭션 (Ci)** | Ti의 논리적 반대 작업. 실패 시 이전 단계를 되돌림. |
| **피봇 트랜잭션** | 되돌릴 수 없는 단계 (예: 외부 PG 결제 승인). 이 단계 이후엔 반드시 전진(forward recovery). |

### 3.3 Choreography vs Orchestration

Saga 패턴을 구현하는 두 가지 방식이 있습니다:

#### Choreography 방식 (이벤트 기반, 분산)

```
order-svc         Kafka         catalog-svc        payment-svc
    │                │                │                  │
    │─ OrderCreated →│                │                  │
    │                │─ deliver ────→│                  │
    │                │                │─ StockReserved →│
    │                │                │                  │─ consume
    │                │                │                  │
    │← OrderPaid ────│← PaymentDone ─│                  │─ publish
```

- 각 서비스가 **자기가 관심있는 이벤트만 구독**하고, 처리 후 **다음 이벤트를 발행**
- 중앙 조정자(Orchestrator) 없이 서비스들이 자율적으로 협력
- **장점**: 단순함, 서비스 간 느슨한 결합, 단일 장애점 없음
- **단점**: 전체 흐름 파악이 어려움, 서비스 수가 많으면 복잡

#### Orchestration 방식 (중앙 조정자)

```
order-svc (Orchestrator)
    │
    ├─ call → catalog-svc: "재고 예약해줘"
    │         ← OK
    ├─ call → payment-svc: "결제 처리해줘"  
    │         ← FAIL
    ├─ call → catalog-svc: "재고 복원해줘" (보상)
    │
    └─ 주문 CANCELLED
```

- **하나의 서비스(Orchestrator)**가 Saga 전체 흐름을 제어
- **장점**: 전체 흐름을 한 곳에서 파악 가능, 복잡한 분기 처리 쉬움
- **단점**: Orchestrator가 단일 장애점, 서비스 간 결합도 증가

### 3.4 Jymusic의 선택: Choreography

| 판단 기준 | Jymusic 상황 | 결론 |
|---|---|---|
| 서비스 수 | 3개 (order, payment, catalog) | Choreography 충분 |
| 흐름 복잡도 | 직선형 (주문→재고→결제→확정) | Choreography 적합 |
| 향후 확장 | 알림, 포인트 서비스 추가 가능 | 이벤트 발행만으로 확장 용이 |
| 학습 곡선 | Choreography가 입문에 적합 | 학습 목적에 부합 |
| 운영 부담 | Orchestrator 별도 관리 불필요 | 운영 단순화 |

> **현업 팁**: Netflix, Uber는 Orchestration을 주로 사용하지만, 이는 수십~수백 개 서비스 규모입니다.  
> 카카오, 토스 같은 한국 기업에서도 서비스 수가 적은 도메인에서는 Choreography를 먼저 도입합니다.  
> 복잡도가 올라가면 그때 Orchestration으로 전환하는 것이 일반적인 성장 경로입니다.

### 3.5 보상 트랜잭션 설계 원칙

#### ① 보상은 "논리적 되돌림"이지 "물리적 undo"가 아니다

```
[재고 예약]  product.stockQuantity -= 2   (T2)
[재고 복원]  product.stockQuantity += 2   (C2) ← 물리적 undo 아닌 논리적 복원
```

#### ② 보상 트랜잭션도 실패할 수 있다 → 재시도 + DLT

```
C2(재고 복원) 실패 시:
  → Kafka가 자동 재시도 (3회)
  → 그래도 실패 → Dead Letter Topic에 저장
  → 운영팀이 DLT 메시지를 확인하고 수동 처리
```

#### ③ 피봇 트랜잭션 이후에는 전진만 가능

```
Toss API 결제 승인이 피봇 트랜잭션입니다.
  - 승인 전: 재고 복원 + 주문 취소 (보상 가능)
  - 승인 후: 결제 취소 + 재고 복원 + 주문 취소 (Toss 취소 API 별도 호출 필요)
```

---

## 4. Circuit Breaker — 장애 전파를 차단하는 방법

### 4.1 Circuit Breaker란?

전기 회로의 차단기(Circuit Breaker)에서 이름을 빌려온 패턴입니다.

```
[정상 상태]
  회로 닫힘(CLOSED) → 전류(요청)가 정상 흐름

[과부하 감지]  
  회로 열림(OPEN) → 전류(요청)를 차단, 쇼트 방지

[복구 감지]
  반열림(HALF_OPEN) → 일부 전류만 시험적으로 통과
```

### 4.2 상태 머신 (3 States)

```
                   실패율 ≥ 임계치
        ┌─────────────────────────────┐
        │                             ▼
   ┌────────┐                    ┌────────┐
   │ CLOSED │                    │  OPEN  │
   │(정상)   │                    │(차단)   │
   └────────┘                    └───┬────┘
        ▲                            │
        │     시험 요청 성공          │ 대기 시간 경과
        │  ┌───────────────┐         │
        └──│  HALF_OPEN    │←────────┘
           │(시험 통과)     │
           └───────────────┘
                │
                │ 시험 요청 실패
                └──────→ OPEN (다시 차단)
```

| 상태 | 동작 | 설명 |
|---|---|---|
| **CLOSED** | 모든 요청 통과 | 정상 상태. 실패율을 모니터링 중 |
| **OPEN** | 모든 요청 즉시 거부 | 임계치 초과. 대상 서비스에 요청 보내지 않음. Fallback 응답 반환 |
| **HALF_OPEN** | 제한된 수의 요청만 통과 | 대기 시간 후 시험적으로 일부 요청 통과. 성공하면 CLOSED, 실패하면 OPEN |

### 4.3 왜 필요한가? — 구체적 시나리오

```
[Circuit Breaker 없이]
catalog-service 장애 발생 (응답 5초)
  → order-service 스레드 200개 모두 catalog 호출 대기 (5초 × 200)
  → order-service 스레드 풀 고갈
  → order-service의 "주문 조회" 같은 관계없는 API도 점부 응답 불가
  → API Gateway에서 order-service 타임아웃
  → Front에서 전체 서비스 장애로 인식

[Circuit Breaker 있으면]
catalog-service 장애 발생
  → 5회 연속 실패 감지 → Circuit OPEN
  → 이후 요청은 catalog-service에 보내지 않고 즉시 Fallback 응답
  → "상품 정보를 일시적으로 불러올 수 없습니다" 안내
  → order-service의 다른 API(주문 조회)는 정상 동작
  → 30초 후 HALF_OPEN → catalog-service 복구 확인 → CLOSED (정상 복귀)
```

### 4.4 Resilience4j — Spring Boot 표준 라이브러리

Netflix Hystrix가 더 이상 유지보수되지 않으므로(2018년 deprecated), Spring Boot 3.x/4.x에서는 **Resilience4j**가 표준입니다.

Resilience4j는 Circuit Breaker 외에도 여러 복원력 패턴을 제공합니다:

| 패턴 | 설명 | Jymusic 적용 |
|---|---|---|
| **Circuit Breaker** | 장애 전파 차단 | ✅ catalog/order 클라이언트 호출 보호 |
| **Retry** | 일시적 실패 재시도 | ✅ 네트워크 타임아웃 시 자동 재시도 |
| **Rate Limiter** | 초당 요청 수 제한 | 선택 (향후 외부 API 호출 제한) |
| **Bulkhead** | 스레드 풀 격리 | 선택 (스레드 풀 분리로 장애 격리) |
| **Time Limiter** | 타임아웃 설정 | ✅ 외부 호출 시 타임아웃 강제 |

### 4.5 Circuit Breaker + Retry 조합 (Best Practice)

```java
// 올바른 적용 순서: Retry → Circuit Breaker (안쪽부터 바깥으로)
// = "먼저 재시도하고, 재시도가 모두 실패하면 Circuit Breaker가 카운트"

@CircuitBreaker(name = "catalogService", fallbackMethod = "getProductFallback")
@Retry(name = "catalogService")
public ProductInfo getProductInfo(Long productId) {
    return catalogRestClient.get()
            .uri("/api/v1/products/{id}", productId)
            .retrieve()
            .body(ProductDetailResponse.class);
}

// Fallback — Circuit OPEN 상태일 때 호출됨
public ProductInfo getProductFallback(Long productId, Throwable t) {
    log.warn("catalog-service 호출 실패, fallback 응답: productId={}", productId, t);
    throw new GlobalException(
        "상품 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도해주세요.",
        "ERR_CATALOG_TEMPORARILY_UNAVAILABLE",
        HttpStatus.SERVICE_UNAVAILABLE
    );
}
```

> **순서가 중요합니다**: `@Retry`가 먼저 실행되고, 재시도가 모두 소진된 후에야 Circuit Breaker가 해당 호출을 "실패 1회"로 카운트합니다.  
> 반대로 하면 일시적 1회 실패에도 Circuit Breaker가 카운트하므로 너무 쉽게 OPEN 됩니다.

### 4.6 Fallback 전략 유형

| 전략 | 설명 | 적용 예시 |
|---|---|---|
| **에러 응답 반환** | 사용자에게 일시적 장애 안내 | 장바구니 조회 시 catalog 장애 |
| **캐시된 값 반환** | Redis/로컬 캐시에서 이전 값 반환 | 상품 가격 캐시 조회 |
| **기본값 반환** | 하드코딩된 기본 응답 | 카테고리 목록 조회 시 빈 리스트 |
| **대안 서비스 호출** | 다른 서비스/DB에서 조회 | 현 단계에서는 해당없음 |

---

## 5. Jymusic에 어떤 패턴 조합을 적용하는가

### 5.1 TO-BE 아키텍처

```
┌──────────┐                  ┌──────────────┐                ┌─────────────┐
│ Frontend │───REST──────────→│ order-svc    │───REST+CB─────→│ catalog-svc │
│ (Nuxt)   │                  │              │                │             │
└──────────┘                  │   produces → │ Kafka ←consumes│ ← produces  │
      │                       └──────────────┘    ↕           └─────────────┘
      │                              ↑            │
      │    REST                      │       ┌────────────┐
      └──────────────────────→ ┌─────────────┐   │  (Topics)  │
                               │ payment-svc │   │            │
                               │             │   │ order.*    │
                               │ produces → ─│───│ payment.*  │
                               └─────────────┘   │ stock.*    │
                                                  └────────────┘

범례:
  ───REST──→   동기 REST 호출 (변경 없음: 읽기 전용 조회)
  ───REST+CB→  동기 REST + Circuit Breaker (보호된 호출)
  produces →   Kafka 이벤트 발행
  ← consumes   Kafka 이벤트 소비
```

### 5.2 무엇이 바뀌는가?

| AS-IS (현재) | TO-BE (목표) | 변경 이유 |
|---|---|---|
| payment → order REST (상태변경) | payment → Kafka `payment.completed` → order consumes | 상태 업데이트 실패 시 Kafka가 재시도 보장 |
| 재고 조회만 (차감 없음) | order → Kafka `order.created` → catalog stock 예약 | 재고 선점으로 Race Condition 방지 |
| REST 호출 시 보호 없음 | Resilience4j Circuit Breaker + Retry | 장애 전파 차단 |
| catalog 장애 → order 전체 장애 | Fallback 응답 | 부분적 서비스 지속 |

### 5.3 무엇이 그대로인가? (바꾸지 않는 것도 중요)

| 유지 항목 | 이유 |
|---|---|
| order → catalog REST 조회 (상품 정보) | 실시간 가격/재고 정보 필요, 동기 조회가 적절 |
| Frontend → Backend REST API | UI 인터랙션은 동기 요청이 자연스러움 |
| API Gateway 라우팅 구조 | 기존 구조 유지, Kafka는 백엔드 내부 인프라 |
| JWT 인증 흐름 | 변경 불필요 |

### 5.4 구현 우선순위 (추천 로드맵)

```
Phase 1: Circuit Breaker (1~2일)
  ├─ Resilience4j 의존성 추가
  ├─ CatalogClient, OrderClient에 @CircuitBreaker + @Retry 적용
  ├─ Fallback 메서드 구현
  └─ 단위 테스트

Phase 2: Kafka 인프라 (1~2일)  
  ├─ Docker Compose에 Kafka/Zookeeper 추가
  ├─ 각 서비스에 spring-kafka 의존성 및 설정
  ├─ Topic 생성 스크립트
  └─ 기본 Producer/Consumer 연결 확인

Phase 3: Saga - 결제→주문 상태 동기화 (2~3일)
  ├─ payment-service: 결제 완료/취소 이벤트 발행
  ├─ order-service: 이벤트 소비하여 주문 상태 업데이트
  ├─ 기존 OrderClient.updateOrderStatus() REST 호출 제거
  └─ DLT 설정 및 재시도 로직

Phase 4: Saga - 재고 예약 (2~3일)
  ├─ order-service: 주문 생성 시 OrderCreated 이벤트 발행
  ├─ catalog-service: 이벤트 소비하여 재고 예약/차감
  ├─ catalog-service: StockReserved/StockReservationFailed 이벤트 발행
  ├─ 보상 트랜잭션 (재고 복원)
  └─ E2E 흐름 테스트
```

---

## 6. 현업 베스트 프랙티스 체크리스트

### ✅ Kafka

- [ ] **이벤트 스키마에 버전 필드 포함** — 향후 스키마 변경 시 하위 호환성 유지
- [ ] **Consumer Idempotency** — `eventId` 또는 `orderId`를 기반으로 중복 처리 방지
- [ ] **Dead Letter Topic(DLT) 반드시 설정** — 처리 불가 메시지가 무한 재시도하는 것 방지
- [ ] **메시지 Key 설계** — `orderId`를 key로 사용하여 같은 주문의 이벤트 순서 보장
- [ ] **Consumer Group ID를 서비스명으로** — `jym-order-service-group`
- [ ] **Acks 설정: `acks=all`** — 메시지 유실 방지 (금융/결제 도메인 필수)
- [ ] **enable.auto.commit=false** — 수동 offset 커밋으로 메시지 처리 보장
- [ ] **DLT 모니터링** — DLT에 메시지가 쌓이면 알림 (Slack, 이메일 등)

### ✅ Saga

- [ ] **각 단계의 상태값 명확히 정의** — `PENDING → STOCK_RESERVED → PAID → COMPLETED`
- [ ] **모든 로컬 트랜잭션에 보상 트랜잭션 쌍 정의** — 빠트리면 데이터 불일치 발생
- [ ] **피봇 트랜잭션 식별** — 결제 승인 이후에는 보상이 아닌 취소 프로세스 필요
- [ ] **타임아웃 설정** — 주문 생성 후 30분 내 결제 미완료 → 자동 취소 (스케줄러)
- [ ] **이벤트 순서 보장** — Kafka 파티션 key로 순서 보장

### ✅ Circuit Breaker

- [ ] **서비스별 독립 Circuit Breaker 인스턴스** — catalog 장애가 payment circuit에 영향 안 줌
- [ ] **Fallback은 비즈니스 맥락에 맞게** — 단순 에러 반환 vs 캐시 데이터 반환 결정
- [ ] **Retry는 Circuit Breaker 안쪽에** — 재시도 소진 후 circuit에 실패 카운트
- [ ] **헬스체크 엔드포인트 연동** — Actuator + Circuit Breaker 상태 노출
- [ ] **알림 설정** — Circuit OPEN 시 Slack/Grafana 알림

### ✅ 테스트

- [ ] **Circuit Breaker 테스트** — WireMock으로 외부 서비스 장애 시뮬레이션
- [ ] **Kafka Consumer 테스트** — `@EmbeddedKafka` 또는 Testcontainers
- [ ] **보상 트랜잭션 테스트** — 의도적 실패 주입으로 보상 흐름 검증
- [ ] **Idempotency 테스트** — 같은 이벤트 2회 전송 후 결과 동일 확인

---

## 용어 정리

| 용어 | 한국어 | 설명 |
|---|---|---|
| Saga | 사가 | 분산 트랜잭션을 로컬 트랜잭션 조합으로 대체하는 패턴 |
| Choreography | 코레오그래피 | 중앙 조정자 없이 이벤트로 서비스가 자율 협력 |
| Orchestration | 오케스트레이션 | 중앙 조정자가 Saga 흐름을 지휘 |
| Compensation | 보상 트랜잭션 | Saga 실패 시 이전 단계를 논리적으로 되돌리는 작업 |
| Pivot Transaction | 피봇 트랜잭션 | 되돌릴 수 없는 Saga 단계 (예: 외부 PG 결제) |
| Circuit Breaker | 서킷 브레이커 | 장애 전파를 차단하는 보호 패턴 |
| Fallback | 폴백 | 서킷 오픈 시 대체 응답을 제공하는 방법 |
| Idempotency | 멱등성 | 같은 작업을 여러 번 수행해도 결과가 동일한 성질 |
| Eventual Consistency | 최종 일관성 | 즉시는 아니지만 최종적으로 모든 서비스의 데이터가 일관됨 |
| DLT | Dead Letter Topic | 처리 실패 메시지를 보관하는 별도 토픽 |
| Backpressure | 배압 | Consumer 처리 속도보다 Producer 발행이 빠를 때의 압력 |

---

_이 문서는 Jymusic 프로젝트의 MSA 복원력 패턴 도입을 위한 교육 자료입니다._  
_구현 세부 스펙은 동일 폴더의 `01_`, `02_`, `03_` 문서를 참고하세요._
