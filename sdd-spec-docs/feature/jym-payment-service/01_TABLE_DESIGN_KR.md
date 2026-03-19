# 01_TABLE_DESIGN (결제 서비스)

> **대상 서비스**: `jym-payment-service`  
> **데이터베이스**: `jym_payment_db` (MySQL)  
> **원칙**: Database-per-service — 타 서비스의 DB에 직접 접근 금지

---

## 1. 결제 서비스 개요

```
[Frontend]
    │
    ├─ POST /payments/prepare  ← 결제 준비 (서버 사이드 금액 검증)
    │       ↓ { clientKey, paymentKey }
    │
    ├─ [Toss Payments SDK 결제창 호출] ← 클라이언트 직접 호출
    │       ↓ 결제 완료 후 successUrl 리다이렉트
    │
    └─ POST /payments/confirm  ← 서버 사이드 결제 최종 승인
            ↓ Toss API 검증 후 DB 저장
```

---

## 2. `payments` 테이블

결제 트랜잭션 기록.

```sql
CREATE TABLE payments (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    order_id         BIGINT         NOT NULL COMMENT '연관 주문 ID (order-service의 orders.id 참조, 직접 FK 없음)',
    member_id        BIGINT         NOT NULL COMMENT '결제자 ID (member-service 참조, 직접 FK 없음)',
    payment_key      VARCHAR(200)   NOT NULL COMMENT 'PG사(Toss) 결제 고유 키',
    method           VARCHAR(30)    NOT NULL COMMENT 'CARD | VIRTUAL_ACCOUNT | KAKAO_PAY | NAVER_PAY',
    amount           DECIMAL(12, 0) NOT NULL COMMENT '실 결제 금액',
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | SUCCESS | FAILED | CANCELLED',
    pg_provider      VARCHAR(30)    NOT NULL DEFAULT 'TOSS' COMMENT 'PG사 식별자 (추후 다중 PG 지원 대비)',
    pg_transaction_id VARCHAR(200)  NULL COMMENT 'PG사에서 부여한 거래 고유 ID',
    fail_reason      VARCHAR(500)   NULL COMMENT '결제 실패 사유 (실패 시 저장)',
    paid_at          DATETIME       NULL COMMENT '결제 완료 일시',
    cancelled_at     DATETIME       NULL COMMENT '결제 취소 일시',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payment_key (payment_key),
    INDEX idx_payments_order (order_id),
    INDEX idx_payments_member (member_id),
    INDEX idx_payments_status (status)
) COMMENT = '결제 트랜잭션 기록';
```

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 내부 결제 고유 ID |
| `order_id` | BIGINT | NOT NULL, INDEX | 연관 주문 ID |
| `member_id` | BIGINT | NOT NULL, INDEX | 결제자 회원 ID |
| `payment_key` | VARCHAR(200) | NOT NULL, UNIQUE | PG사 결제 키 (Toss `paymentKey`) |
| `method` | VARCHAR(30) | NOT NULL | 결제 수단 |
| `amount` | DECIMAL(12,0) | NOT NULL | 결제 금액 |
| `status` | VARCHAR(20) | NOT NULL | 결제 상태 |
| `pg_provider` | VARCHAR(30) | NOT NULL, DEFAULT 'TOSS' | PG사 구분자 |
| `pg_transaction_id` | VARCHAR(200) | NULL | PG사 내부 거래 ID |
| `fail_reason` | VARCHAR(500) | NULL | 실패/취소 사유 |
| `paid_at` | DATETIME | NULL | 결제 완료 일시 |
| `cancelled_at` | DATETIME | NULL | 취소 완료 일시 |
| `created_at` | DATETIME | NOT NULL | 레코드 생성 일시 |
| `updated_at` | DATETIME | NOT NULL | 마지막 수정 일시 |

#### `status` Enum 값 정의

| 값 | 설명 | 전환 가능 다음 상태 |
|---|---|---|
| `PENDING` | 결제 준비 완료, 승인 대기 | `SUCCESS`, `FAILED` |
| `SUCCESS` | 결제 승인 완료 | `CANCELLED` |
| `FAILED` | 결제 실패 (PG사 거절, 타임아웃 등) | (없음) |
| `CANCELLED` | 결제 취소됨 | (없음) |

#### `method` Enum 값 정의

| 값 | 설명 |
|---|---|
| `CARD` | 신용/체크카드 |
| `VIRTUAL_ACCOUNT` | 계좌이체 |
| `KAKAO_PAY` | 카카오페이 |
| `NAVER_PAY` | 네이버페이 |

---

## 3. `payment_prepare` 테이블 (결제 준비 임시 저장)

결제 시작 전 금액 위변조 방지를 위한 임시 저장.  
PG사의 `successUrl` 콜백 수신 후 금액 검증에 사용 후 삭제 또는 만료.

```sql
CREATE TABLE payment_prepare (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    order_id   BIGINT         NOT NULL COMMENT '주문 ID',
    amount     DECIMAL(12, 0) NOT NULL COMMENT '서버 측 계산 금액 (검증용)',
    expires_at DATETIME       NOT NULL COMMENT '준비 레코드 만료 일시 (생성 후 30분)',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_prepare_order (order_id),
    INDEX idx_prepare_expires (expires_at)
) COMMENT = '결제 준비 임시 저장 (금액 위변조 방지)';
```

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | 고유 ID |
| `order_id` | BIGINT | NOT NULL, UNIQUE | 주문 ID (1 주문 = 1 준비 레코드) |
| `amount` | DECIMAL(12,0) | NOT NULL | 서버에서 계산한 실제 금액 |
| `expires_at` | DATETIME | NOT NULL | 만료 일시 (생성 후 30분) |
| `created_at` | DATETIME | NOT NULL | 생성 일시 |

> **금액 검증 흐름**:  
> 1. `POST /payments/prepare` 호출 시 `payment_prepare` 레코드 생성  
> 2. `POST /payments/confirm` 수신 시 `payment_prepare.amount == request.amount` 검증  
> 3. 검증 통과 후 Toss API로 최종 승인 요청  
> 4. 결과 `payments` 테이블에 저장, `payment_prepare` 레코드 삭제

---

## 4. 서비스 간 데이터 참조 정책

| 참조 대상 | 참조 방법 |
|---|---|
| `order_id` 유효성 | 결제 준비 시 order-service에 API 호출로 주문 존재 및 `PENDING` 상태 확인 |
| `member_id` | JWT 클레임에서 추출 |
| 결제 완료 후 주문 상태 변경 | order-service에 이벤트/API 호출로 `orders.status = PAID` 업데이트 |

---

## 5. Java Entity 대응 (참고)

```java
// Payment.java
@Entity @Table(name = "payments")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Builder
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, length = 30)
    private String pgProvider;

    @Column(length = 200)
    private String pgTransactionId;

    @Column(length = 500)
    private String failReason;

    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    // ...
}
```
