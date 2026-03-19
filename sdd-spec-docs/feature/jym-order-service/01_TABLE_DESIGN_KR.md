# 01_TABLE_DESIGN (주문 서비스)

> **대상 서비스**: `jym-order-service`  
> **데이터베이스**: `jym_order_db` (MySQL)  
> **원칙**: Database-per-service — 타 서비스의 DB에 직접 접근 금지

---

## 1. ERD 개요

```
cart (1) ──< cart_items (N)
orders (1) ──< order_items (N)
```

> 장바구니(cart)와 주문(order)은 별도 테이블로 분리.  
> 주문 생성 시 cart의 데이터를 복사하며, 이후 cart는 비움.

---

## 2. `cart` 테이블

사용자 한 명당 1개의 활성 장바구니를 보유.

```sql
CREATE TABLE cart (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    member_id   BIGINT       NOT NULL COMMENT '장바구니 소유자 (member-service의 member.id 참조, 직접 FK 없음)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cart_member (member_id)
) COMMENT = '사용자 장바구니 (사용자 1명당 1개)';
```

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 장바구니 고유 ID |
| `member_id` | BIGINT | NOT NULL, UNIQUE | 회원 ID (타 서비스 참조, 물리 FK 없음) |
| `created_at` | DATETIME | NOT NULL | 생성 일시 |
| `updated_at` | DATETIME | NOT NULL | 마지막 수정 일시 |

---

## 3. `cart_items` 테이블

장바구니에 담긴 개별 상품.

```sql
CREATE TABLE cart_items (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    cart_id     BIGINT   NOT NULL COMMENT 'cart.id 참조',
    product_id  BIGINT   NOT NULL COMMENT '상품 ID (catalog-service의 product.id 참조, 직접 FK 없음)',
    quantity    INT      NOT NULL DEFAULT 1 COMMENT '최소 1, 최대 재고 수량',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cart_product (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES cart(id) ON DELETE CASCADE
) COMMENT = '장바구니 아이템';
```

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 아이템 고유 ID |
| `cart_id` | BIGINT | NOT NULL, FK → `cart.id` | 소속 장바구니 ID |
| `product_id` | BIGINT | NOT NULL | 상품 ID (catalog-service 참조) |
| `quantity` | INT | NOT NULL, DEFAULT 1 | 담긴 수량 (≥1) |
| `created_at` | DATETIME | NOT NULL | 담은 일시 |
| `updated_at` | DATETIME | NOT NULL | 수량 변경 일시 |

> **UNIQUE KEY (cart_id, product_id)**: 동일 상품이 장바구니에 중복 삽입될 경우 수량만 증가(UPSERT).

---

## 4. `orders` 테이블

최종 주문 정보.

```sql
CREATE TABLE orders (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    member_id    BIGINT         NOT NULL COMMENT '주문자 (member-service의 member.id 참조, 직접 FK 없음)',
    total_amount DECIMAL(12, 0) NOT NULL COMMENT '총 결제 예정 금액 (주문 시점 스냅샷)',
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | PAID | SHIPPED | COMPLETED | CANCELLED',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_orders_member (member_id),
    INDEX idx_orders_status (status)
) COMMENT = '주문 마스터';
```

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 주문 고유 ID |
| `member_id` | BIGINT | NOT NULL, INDEX | 주문자 ID |
| `total_amount` | DECIMAL(12,0) | NOT NULL | 총 금액 (주문 생성 시점 확정) |
| `status` | VARCHAR(20) | NOT NULL | 주문 상태 |
| `created_at` | DATETIME | NOT NULL | 주문 생성 일시 |
| `updated_at` | DATETIME | NOT NULL | 상태 변경 일시 |

#### `status` Enum 값 정의

| 값 | 설명 | 전환 가능 다음 상태 |
|---|---|---|
| `PENDING` | 주문 생성, 결제 대기 | `PAID`, `CANCELLED` |
| `PAID` | 결제 완료 | `SHIPPED`, `CANCELLED` |
| `SHIPPED` | 발송 완료 | `COMPLETED` |
| `COMPLETED` | 구매 확정 | (없음) |
| `CANCELLED` | 취소됨 | (없음) |

---

## 5. `order_items` 테이블

주문에 포함된 개별 상품 정보 (주문 시점 가격 스냅샷 포함).

```sql
CREATE TABLE order_items (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    order_id      BIGINT         NOT NULL COMMENT 'orders.id 참조',
    product_id    BIGINT         NOT NULL COMMENT '상품 ID (catalog-service 참조, 직접 FK 없음)',
    product_title VARCHAR(255)   NOT NULL COMMENT '주문 시점 상품명 스냅샷',
    unit_price    DECIMAL(12, 0) NOT NULL COMMENT '주문 시점 개당 가격 스냅샷',
    quantity      INT            NOT NULL COMMENT '주문 수량',
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_order_items_order (order_id)
) COMMENT = '주문 상세 아이템 (주문 시점 스냅샷)';
```

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 아이템 고유 ID |
| `order_id` | BIGINT | NOT NULL, FK → `orders.id` | 소속 주문 ID |
| `product_id` | BIGINT | NOT NULL | 상품 ID |
| `product_title` | VARCHAR(255) | NOT NULL | 주문 당시 상품명 (스냅샷) |
| `unit_price` | DECIMAL(12,0) | NOT NULL | 주문 당시 개당 가격 (스냅샷) |
| `quantity` | INT | NOT NULL | 주문 수량 |

> **스냅샷 원칙**: `product_title`, `unit_price`는 주문 생성 시 catalog-service로부터 받아 복사 저장.  
> 이후 상품 가격이 변동되어도 기존 주문 내역은 영향을 받지 않음.

---

## 6. 서비스 간 데이터 참조 정책

| 참조 대상 | 참조 방법 | 비고 |
|---|---|---|
| `member_id` | API 호출 (JWT 클레임에서 추출) | 직접 DB 참조 금지 |
| `product_id`, `product_title`, `unit_price` | 주문 생성 시 catalog-service에 API 호출 후 스냅샷 저장 | 직접 DB 참조 금지 |

---

## 7. Java Entity 대응 (참고)

```java
// Cart.java
@Entity @Table(name = "cart")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Builder
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long memberId;
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();
    // ...
}

// CartItem.java
@Entity @Table(name = "cart_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Builder
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false)
    private int quantity;
    // ...
}

// Order.java
@Entity @Table(name = "orders")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @Builder
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long memberId;
    @Column(nullable = false)
    private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    // ...
}
```
