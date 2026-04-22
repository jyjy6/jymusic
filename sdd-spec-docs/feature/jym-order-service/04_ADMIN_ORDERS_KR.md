# 04_ADMIN_ORDERS — 운영자 주문 관리 API

> **대상 서비스**: `jym-order-service` (확장)
> **역할**: 모든 주문을 페이지 단위로 조회(유저명/닉네임/상품명/날짜/상태 검색)하고, 운영자가 허용된 상태 전이를 수동 수행하는 관리자 전용 API.
> **권한**: `ROLE_ADMIN` 만 접근 가능.

---

## 1. 기능 요약

| 기능 | 메서드 · 경로 | 설명 |
|---|---|---|
| 주문 목록(검색·페이징) | `GET /api/v1/admin/orders` | 유저/상품/기간/상태별 검색 |
| 주문 상세 | `GET /api/v1/admin/orders/{orderId}` | 소유자 제약 없이 조회 |
| 주문 상태 변경 | `PATCH /api/v1/admin/orders/{orderId}/status` | 허용된 전이만 수행 (Order 엔티티 `transitionTo()` 재사용) |
| 주문 요약 통계 | `GET /api/v1/admin/orders/stats` | (옵션) 상태별 카운트 |

모든 엔드포인트는 Gateway에서 `ROLE_ADMIN` 검증 후 `X-User-Role=ROLE_ADMIN` 헤더를 주입한다.

---

## 2. 패키지 구조 (신규)

```
src/main/java/jymusic/jym_order_service/
├── admin/
│   ├── controller/
│   │   └── AdminOrderController.java
│   ├── service/
│   │   └── AdminOrderService.java
│   └── dto/
│       ├── request/
│       │   ├── AdminOrderSearchRequest.java
│       │   └── AdminStatusUpdateRequest.java
│       └── response/
│           ├── AdminOrderSummaryResponse.java
│           └── AdminOrderDetailResponse.java
├── domain/repository/
│   ├── OrderRepository.java                    # (수정) Custom 인터페이스 상속 추가
│   ├── OrderRepositoryCustom.java              # 신규 — QueryDSL 확장 인터페이스
│   └── OrderRepositoryImpl.java                # 신규 — JPAQueryFactory 기반 구현
├── client/
│   └── MemberClient.java                       # username/nickname → memberId 역조회
```

> **빌드 산출물**: `build/generated/sources/annotationProcessor/java/main/` 아래에 `QOrder`, `QOrderItem` 등의 Q 메타모델이 자동 생성된다. Git에는 커밋하지 않는다 (`.gitignore`에 `build/` 이미 포함됨).

---

## 2.5. build.gradle — QueryDSL 설정 (Spring Boot 4.0.3 / Jakarta / Java 21)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

dependencies {
    // ── 기존 ──
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    // ...

    // ── QueryDSL (Jakarta 분류자 필수, Spring Boot 3.x/4.x 호환) ──
    implementation 'com.querydsl:querydsl-jpa:5.1.0:jakarta'
    annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'
    annotationProcessor 'jakarta.annotation:jakarta.annotation-api'
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api'
}

// Gradle이 Q 클래스를 정리할 수 있도록 명시
tasks.named('clean') {
    delete file('build/generated/sources/annotationProcessor')
}
```

> **주의**: `querydsl-jpa` 단독이 아닌 **`:jakarta` 분류자**가 반드시 필요하다. 스프링 부트 3.x 부터 `javax.persistence` → `jakarta.persistence` 로 이동했기 때문. 이를 빠뜨리면 Q 클래스가 `javax.*` import로 생성되어 컴파일 실패한다.

### JPAQueryFactory 빈 등록 — `config/QuerydslConfig.java`

```java
@Configuration
@RequiredArgsConstructor
public class QuerydslConfig {

    private final EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
```

---

## 3. 검색 조건

### Query Parameters

| 파라미터 | 타입 | 예시 | 설명 |
|---|---|---|---|
| `keyword` | String | `johndoe` | 유저 username OR nickname 부분 일치 |
| `productTitle` | String | `Nocturne` | 주문 상품명 부분 일치 |
| `status` | OrderStatus | `PAID` | 단일 상태 (미지정 시 전체) |
| `statuses` | List&lt;OrderStatus&gt; | `PAID,SHIPPED` | 다중 상태 (OR) — 탭 UI 대응 |
| `startDate` | `yyyy-MM-dd` | `2026-04-01` | `createdAt >= startDate 00:00:00` |
| `endDate` | `yyyy-MM-dd` | `2026-04-30` | `createdAt <= endDate 23:59:59` |
| `minAmount` | Long | `10000` | 총액 하한 (옵션) |
| `maxAmount` | Long | `500000` | 총액 상한 (옵션) |
| `page` | int | `0` | 0-based |
| `size` | int | `20` | 기본 20, 최대 100 |
| `sort` | String | `createdAt,desc` | 정렬 — 기본 `createdAt,desc` |

### 검색 흐름 — `keyword` 처리

`jym-order-service`는 member DB에 직접 접근하지 않는다(헌법 §3 Database-per-service). 따라서:

```
keyword → MemberClient.searchMemberIds(keyword) → List<Long> memberIds
                                                  ↓
         OrderSpecifications.memberIdIn(memberIds)
```

### `MemberClient.java` 설계

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberClient {

    private final RestClient memberAuthRestClient;  // AppConfig 빈

    public List<Long> searchMemberIds(String keyword) {
        try {
            MemberSearchResponse[] results = memberAuthRestClient.get()
                .uri(uri -> uri.path("/api/v1/members/search")
                               .queryParam("keyword", keyword).build())
                .retrieve()
                .body(MemberSearchResponse[].class);
            return Arrays.stream(Objects.requireNonNullElse(results, new MemberSearchResponse[0]))
                         .map(MemberSearchResponse::memberId).toList();
        } catch (Exception e) {
            log.error("member-service 검색 실패: keyword={}", keyword, e);
            throw new GlobalException("회원 정보를 조회할 수 없습니다.",
                "ERR_MEMBER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** 주문 목록에 표시할 회원 정보(단건) */
    public MemberSummary getMember(Long memberId) { /* ... GET /api/v1/members/{id} ... */ }

    public record MemberSearchResponse(Long memberId, String username, String nickname) {}
    public record MemberSummary(Long memberId, String username, String nickname) {}
}
```

> **전제**: `jym-member-auth-service`에 `GET /api/v1/members/search?keyword=` 및 `GET /api/v1/members/{id}` (서비스 간 내부 호출) 엔드포인트 추가가 필요. 별도 스펙 항목으로 분리한다 (Section 8).

---

## 4. DTO 설계

### `AdminOrderSearchRequest.java`

```java
@Getter @Setter @NoArgsConstructor
public class AdminOrderSearchRequest {
    private String keyword;
    private String productTitle;
    private OrderStatus status;
    private List<OrderStatus> statuses;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private Long minAmount;
    private Long maxAmount;
}
```

### `AdminStatusUpdateRequest.java`

```java
@Getter @NoArgsConstructor
public class AdminStatusUpdateRequest {
    @NotNull(message = "변경할 상태는 필수입니다.")
    private OrderStatus status;

    /** 선택: 취소 사유 등 감사 로그용 */
    @Size(max = 255)
    private String reason;
}
```

### `AdminOrderSummaryResponse.java` (목록용)

```java
@Getter @Builder
public class AdminOrderSummaryResponse {
    private Long orderId;
    private Long memberId;
    private String username;
    private String nickname;
    private BigDecimal totalAmount;
    private String status;
    private int itemCount;
    private String firstItemTitle;   // "○○○ 외 N건" UI
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminOrderSummaryResponse of(Order o, MemberClient.MemberSummary m) { /* ... */ }
}
```

### `AdminOrderDetailResponse.java`

```java
@Getter @Builder
public class AdminOrderDetailResponse {
    private Long orderId;
    private Long memberId;
    private String username;
    private String nickname;
    private String email;           // 고객 문의용 (MemberClient 확장 필드)
    private BigDecimal totalAmount;
    private String status;
    private List<OrderItemDetailResponse> items;
    private List<OrderStatus> allowedNextStatuses;  // UI 드롭다운 구성용
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

> `allowedNextStatuses` 는 `Order#isValidTransition` 기반으로 계산해 응답에 포함한다 → 프론트가 상태 드롭다운을 동적으로 구성.

---

## 5. Repository — QueryDSL Custom Repository

### 5.1 기존 `OrderRepository.java` 수정

```java
public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {
    List<Order> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    /** 통계용 — 상태별 카운트 (/admin/orders/stats) */
    @Query("SELECT o.status AS status, COUNT(o) AS cnt FROM Order o GROUP BY o.status")
    List<StatusCount> countByStatusGrouped();

    interface StatusCount { OrderStatus getStatus(); Long getCnt(); }
}
```

### 5.2 `OrderRepositoryCustom.java` — 확장 인터페이스

```java
public interface OrderRepositoryCustom {
    Page<Order> searchAdmin(AdminOrderSearchCriteria criteria, Pageable pageable);
}
```

### 5.3 검색 조건 DTO — `AdminOrderSearchCriteria.java`

Service ↔ Repository 경계에서만 쓰는 **내부 값 객체**. Controller의 `AdminOrderSearchRequest` 와 분리해 회원 ID 배치 조회 결과(`memberIds`)까지 담아 전달한다.

```java
public record AdminOrderSearchCriteria(
        List<Long> memberIds,          // keyword → MemberClient 조회 결과 (null=미적용)
        String productTitle,
        OrderStatus status,
        List<OrderStatus> statuses,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long minAmount,
        Long maxAmount
) {}
```

### 5.4 `OrderRepositoryImpl.java` — QueryDSL 구현

```java
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Order> searchAdmin(AdminOrderSearchCriteria c, Pageable pageable) {
        QOrder order = QOrder.order;
        QOrderItem item = QOrderItem.orderItem;

        BooleanBuilder where = new BooleanBuilder()
                .and(memberIdIn(c.memberIds()))
                .and(statusEq(c.status()))
                .and(statusIn(c.statuses()))
                .and(createdBetween(c.startAt(), c.endAt()))
                .and(totalBetween(c.minAmount(), c.maxAmount()));

        // 컨텐츠 쿼리 — 상품명 검색이 있을 때만 items JOIN + DISTINCT
        JPAQuery<Order> contentQuery = queryFactory
                .selectFrom(order)
                .where(where);

        if (hasText(c.productTitle())) {
            contentQuery
                    .join(order.items, item)
                    .where(item.productTitle.lower().contains(c.productTitle().toLowerCase()))
                    .distinct();
        }

        List<Order> content = applySort(contentQuery, pageable, order)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 카운트 쿼리 — 페이지 크기보다 컨텐츠가 적으면 스킵 (PageableExecutionUtils)
        JPAQuery<Long> countQuery = queryFactory
                .select(hasText(c.productTitle()) ? order.countDistinct() : order.count())
                .from(order)
                .where(where);

        if (hasText(c.productTitle())) {
            countQuery.join(order.items, item)
                    .where(item.productTitle.lower().contains(c.productTitle().toLowerCase()));
        }

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // ───────── 동적 조건 헬퍼 (null-safe) ─────────

    private BooleanExpression memberIdIn(List<Long> ids) {
        return (ids == null || ids.isEmpty()) ? null : QOrder.order.memberId.in(ids);
    }

    private BooleanExpression statusEq(OrderStatus s) {
        return s == null ? null : QOrder.order.status.eq(s);
    }

    private BooleanExpression statusIn(List<OrderStatus> list) {
        return (list == null || list.isEmpty()) ? null : QOrder.order.status.in(list);
    }

    private BooleanExpression createdBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) return null;
        DateTimePath<LocalDateTime> createdAt = QOrder.order.createdAt;
        if (from != null && to != null) return createdAt.between(from, to);
        if (from != null)                return createdAt.goe(from);
        return createdAt.loe(to);
    }

    private BooleanExpression totalBetween(Long min, Long max) {
        if (min == null && max == null) return null;
        NumberPath<BigDecimal> total = QOrder.order.totalAmount;
        if (min != null && max != null)
            return total.between(BigDecimal.valueOf(min), BigDecimal.valueOf(max));
        if (min != null) return total.goe(BigDecimal.valueOf(min));
        return total.loe(BigDecimal.valueOf(max));
    }

    private boolean hasText(String s) { return s != null && !s.isBlank(); }

    // ───────── 정렬 — Pageable.Sort → OrderSpecifier 변환 ─────────

    private JPAQuery<Order> applySort(JPAQuery<Order> query, Pageable pageable, QOrder order) {
        if (pageable.getSort().isUnsorted()) {
            return query.orderBy(order.createdAt.desc());
        }
        for (Sort.Order s : pageable.getSort()) {
            com.querydsl.core.types.Order dir =
                    s.isAscending() ? com.querydsl.core.types.Order.ASC
                                    : com.querydsl.core.types.Order.DESC;
            switch (s.getProperty()) {
                case "createdAt"   -> query.orderBy(new OrderSpecifier<>(dir, order.createdAt));
                case "totalAmount" -> query.orderBy(new OrderSpecifier<>(dir, order.totalAmount));
                case "status"      -> query.orderBy(new OrderSpecifier<>(dir, order.status));
                case "id"          -> query.orderBy(new OrderSpecifier<>(dir, order.id));
                default -> { /* 미지원 필드는 무시 — 화이트리스트 전략 (SQL 인젝션 방지) */ }
            }
        }
        return query;
    }
}
```

> **핵심 포인트**
> - **타입 안전**: `QOrder.order.memberId` 는 컴파일 타임 검증. 엔티티 필드명 변경 시 즉시 컴파일 에러.
> - **N+1 방지**: 컨텐츠 쿼리에서 `order.items` 는 지연로딩. 응답 매핑 단계에서 첫 아이템만 필요하면 `fetchJoin()` 추가 고려. 대량 조회 시에는 `@BatchSize` 또는 `in-clause` 로도 처리 가능.
> - **DISTINCT 조건부 적용**: 상품명 검색이 있을 때만 `distinct()` — 불필요한 DISTINCT로 인한 성능 하락 회피.
> - **카운트 쿼리 최적화**: `PageableExecutionUtils.getPage()` 로 **마지막 페이지/단일 페이지에서 카운트 쿼리를 아예 스킵**.
> - **정렬 화이트리스트**: Pageable의 `sort` 파라미터는 사용자가 보내는 문자열이므로, 허용된 필드만 반영해 SQL 인젝션 공격면을 제거.

---

## 6. Service 레이어

### `AdminOrderService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final MemberClient memberClient;
    private final ApplicationEventPublisher eventPublisher;  // 상태 변경 도메인 이벤트

    public Page<AdminOrderSummaryResponse> search(AdminOrderSearchRequest req, Pageable pageable) {
        // 1) keyword → memberIds 역조회 (MSA: member DB 직접 접근 금지)
        List<Long> memberIds = (req.getKeyword() == null || req.getKeyword().isBlank())
                ? null : memberClient.searchMemberIds(req.getKeyword());

        // keyword를 넣었는데 매칭 회원 0명이면 DB 조회 없이 빈 페이지 반환 (최적화)
        if (req.getKeyword() != null && !req.getKeyword().isBlank()
                && (memberIds == null || memberIds.isEmpty())) {
            return Page.empty(pageable);
        }

        // 2) Controller DTO → Repository 내부 Criteria 변환
        AdminOrderSearchCriteria criteria = new AdminOrderSearchCriteria(
                memberIds,
                req.getProductTitle(),
                req.getStatus(),
                req.getStatuses(),
                req.getStartDate() == null ? null : req.getStartDate().atStartOfDay(),
                req.getEndDate()   == null ? null : req.getEndDate().atTime(LocalTime.MAX),
                req.getMinAmount(),
                req.getMaxAmount()
        );

        // 3) QueryDSL 기반 동적 검색 실행
        Page<Order> page = orderRepository.searchAdmin(criteria, pageable);

        // 4) 회원 정보 배치 조회 (N+1 방지) — page당 1회 HTTP 호출
        Set<Long> ids = page.getContent().stream().map(Order::getMemberId).collect(Collectors.toSet());
        Map<Long, MemberClient.MemberSummary> memberMap =
                ids.isEmpty() ? Map.of() : memberClient.getMembers(ids);

        return page.map(o -> AdminOrderSummaryResponse.of(o,
                memberMap.getOrDefault(o.getMemberId(),
                                       MemberClient.MemberSummary.unknown(o.getMemberId()))));
    }

    public AdminOrderDetailResponse getDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.",
                        "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));
        MemberClient.MemberSummary m = memberClient.getMember(order.getMemberId());
        return AdminOrderDetailResponse.of(order, m, allowedNextStatusesOf(order.getStatus()));
    }

    @Transactional
    public AdminOrderDetailResponse updateStatus(Long orderId, AdminStatusUpdateRequest req, Long adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.",
                        "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));

        OrderStatus previous = order.getStatus();
        order.transitionTo(req.getStatus());  // 유효성 검증 — 실패 시 ERR_INVALID_ORDER_TRANSITION
        orderRepository.save(order);

        // 도메인 이벤트 발행 — domain/event/OrderStatusChangedDomainEvent (03_SSE 스펙 5.6 참조)
        // OrderNotificationListener(@TransactionalEventListener AFTER_COMMIT)가 수신하여 Push 발행
        eventPublisher.publishEvent(OrderStatusChangedDomainEvent.of(
                order.getId(), order.getMemberId(), previous, order.getStatus(),
                order.getTotalAmount(),
                order.getItems().isEmpty() ? "" : order.getItems().get(0).getProductTitle(),
                order.getItems().size()));

        log.info("ADMIN status update: orderId={}, adminId={}, {}→{}, reason={}",
                orderId, adminId, previous, req.getStatus(), req.getReason());

        return getDetail(orderId);
    }

    public Map<OrderStatus, Long> statusCounts() {
        // JPQL projection → EnumMap 으로 변환 (UI 정렬 안정성을 위해 EnumMap 사용)
        Map<OrderStatus, Long> result = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) result.put(s, 0L);   // 0 기본값 채움
        for (OrderRepository.StatusCount row : orderRepository.countByStatusGrouped()) {
            result.put(row.getStatus(), row.getCnt());
        }
        return result;
    }

    private List<OrderStatus> allowedNextStatusesOf(OrderStatus current) {
        return switch (current) {
            case PENDING        -> List.of(OrderStatus.STOCK_RESERVED, OrderStatus.CANCELLED);
            case STOCK_RESERVED -> List.of(OrderStatus.PAID, OrderStatus.CANCELLED);
            case PAID           -> List.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED);
            case SHIPPED        -> List.of(OrderStatus.COMPLETED);
            case COMPLETED, CANCELLED -> List.of();
        };
    }
}
```

> **보안 정합성**: `Order#transitionTo` 는 이미 유효하지 않은 전이 시 `ERR_INVALID_ORDER_TRANSITION` 을 던지므로, 관리자라 해도 임의 상태로 점프할 수 없다 (예: `PENDING` → `SHIPPED` 차단).

---

## 7. Controller 레이어

### `AdminOrderController.java`

```java
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    public ResponseEntity<Page<AdminOrderSummaryResponse>> search(
            @ModelAttribute AdminOrderSearchRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new GlobalException("페이지 크기는 최대 100입니다.", "ERR_PAGE_SIZE_EXCEEDED");
        }
        return ResponseEntity.ok(adminOrderService.search(request, pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDetailResponse> detail(@PathVariable Long orderId) {
        return ResponseEntity.ok(adminOrderService.getDetail(orderId));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<AdminOrderDetailResponse> updateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody AdminStatusUpdateRequest request,
            @AuthenticationPrincipal String adminId) {
        return ResponseEntity.ok(adminOrderService.updateStatus(
                orderId, request, Long.parseLong(adminId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(adminOrderService.statusCounts().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
    }
}
```

---

## 8. 외부 의존 — member-auth-service 추가 API (스펙 참조)

본 기능은 다음 두 엔드포인트를 `jym-member-auth-service`에 추가로 요구한다. 해당 서비스 스펙 폴더(`sdd-spec-docs/feature/jym-member-auth-service/`)에 별도 문서로 반영 필요.

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET` | `/api/v1/members/search?keyword=` | username/nickname 부분 일치 검색. 서비스 간 내부 호출용 — 결과 최대 50건 제한, 민감정보 제외 |
| `GET` | `/api/v1/members/batch?ids=1,2,3` | memberId 배치 조회 (N+1 방지). 서비스 간 내부 호출용 |

Gateway는 이 두 경로를 **내부 통신 전용**으로 인가 정책을 분리하거나, 서비스 간 시크릿 헤더 (`X-Internal-Call`) 를 검증해야 한다.

---

## 9. OpenAPI Specification 추가분

```yaml
paths:
  /admin/orders:
    get:
      tags: [관리자-주문]
      summary: 전체 주문 검색 (페이징)
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: keyword,      in: query, schema: { type: string } }
        - { name: productTitle, in: query, schema: { type: string } }
        - { name: status,       in: query, schema: { $ref: '#/components/schemas/OrderStatus' } }
        - { name: statuses,     in: query, schema: { type: array, items: { $ref: '#/components/schemas/OrderStatus' } }, style: form, explode: false }
        - { name: startDate,    in: query, schema: { type: string, format: date } }
        - { name: endDate,      in: query, schema: { type: string, format: date } }
        - { name: minAmount,    in: query, schema: { type: integer, format: int64 } }
        - { name: maxAmount,    in: query, schema: { type: integer, format: int64 } }
        - { name: page,         in: query, schema: { type: integer, default: 0 } }
        - { name: size,         in: query, schema: { type: integer, default: 20, maximum: 100 } }
        - { name: sort,         in: query, schema: { type: string, default: "createdAt,desc" } }
      responses:
        '200':
          description: 주문 페이지
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AdminOrderPage' }
        '403': { $ref: '#/components/responses/ErrorResponse' }

  /admin/orders/{orderId}:
    get:
      tags: [관리자-주문]
      summary: 주문 상세 조회 (관리자)
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: orderId, in: path, required: true, schema: { type: integer, format: int64 } }
      responses:
        '200':
          description: 주문 상세
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AdminOrderDetailResponse' }
        '404': { $ref: '#/components/responses/ErrorResponse' }

  /admin/orders/{orderId}/status:
    patch:
      tags: [관리자-주문]
      summary: 주문 상태 변경 (관리자)
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: orderId, in: path, required: true, schema: { type: integer, format: int64 } }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/AdminStatusUpdateRequest' }
      responses:
        '200':
          description: 변경 결과
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AdminOrderDetailResponse' }
        '400': { description: 유효하지 않은 상태 전이 (ERR_INVALID_ORDER_TRANSITION) }

  /admin/orders/stats:
    get:
      tags: [관리자-주문]
      summary: 상태별 주문 수
      security: [{ bearerAuth: [] }]
      responses:
        '200':
          description: 상태별 카운트
          content:
            application/json:
              schema:
                type: object
                additionalProperties: { type: integer, format: int64 }

components:
  schemas:
    OrderStatus:
      type: string
      enum: [PENDING, STOCK_RESERVED, PAID, SHIPPED, COMPLETED, CANCELLED]

    AdminStatusUpdateRequest:
      type: object
      required: [status]
      properties:
        status: { $ref: '#/components/schemas/OrderStatus' }
        reason: { type: string, maxLength: 255 }

    AdminOrderSummaryResponse:
      type: object
      properties:
        orderId:        { type: integer, format: int64 }
        memberId:       { type: integer, format: int64 }
        username:       { type: string }
        nickname:       { type: string }
        totalAmount:    { type: number }
        status:         { $ref: '#/components/schemas/OrderStatus' }
        itemCount:      { type: integer }
        firstItemTitle: { type: string }
        createdAt:      { type: string, format: date-time }
        updatedAt:      { type: string, format: date-time }

    AdminOrderDetailResponse:
      allOf:
        - $ref: '#/components/schemas/AdminOrderSummaryResponse'
        - type: object
          properties:
            email:                { type: string }
            items:                { type: array, items: { $ref: '#/components/schemas/OrderItemDetailResponse' } }
            allowedNextStatuses:  { type: array, items: { $ref: '#/components/schemas/OrderStatus' } }

    AdminOrderPage:
      type: object
      properties:
        content:          { type: array, items: { $ref: '#/components/schemas/AdminOrderSummaryResponse' } }
        totalElements:    { type: integer, format: int64 }
        totalPages:       { type: integer }
        number:           { type: integer }
        size:             { type: integer }
```

---

## 10. 에러 코드 추가

| 코드 | HTTP | 설명 |
|---|---|---|
| `ERR_INVALID_ORDER_TRANSITION` | 400 | 허용되지 않은 상태 전이 (기존 재사용) |
| `ERR_PAGE_SIZE_EXCEEDED`       | 400 | size > 100 |
| `ERR_MEMBER_UNAVAILABLE`       | 503 | member-auth-service 연결 실패 |

---

## 11. 보안 정책

| 항목 | 설정 |
|---|---|
| 클래스 | `SecurityConfig` 는 수정 불필요 — Controller에 `@PreAuthorize("hasAuthority('ROLE_ADMIN')")` 부여 |
| URL 패턴 | `/api/v1/admin/**` 는 Gateway에서도 `ROLE_ADMIN` 선검증 권장 |
| 감사 로그 | `AdminOrderService.updateStatus` 는 항상 `adminId, previous, current, reason` 을 INFO 로깅 |

---

## 12. 단위 테스트 가이드

### `AdminOrderServiceTest`

| 테스트 | 검증 |
|---|---|
| `search_withKeyword_resolvesMemberIds` | keyword → MemberClient 호출 → Criteria.memberIds 에 반영 |
| `search_withKeyword_noMatch_returnsEmptyPage` | 회원 0명 매칭 시 Repository 호출 스킵 (단축 경로) |
| `search_buildsCriteriaFromRequest` | Controller DTO → Repository Criteria 변환 검증 |
| `getDetail_throwsWhenNotFound` | 404 |
| `getDetail_includesAllowedNextStatuses` | 현재 상태별 다음 후보 포함 |
| `updateStatus_success_publishesDomainEvent` | ApplicationEventPublisher 호출 검증 |
| `updateStatus_invalidTransition_throws` | `ERR_INVALID_ORDER_TRANSITION` |
| `statusCounts_fillsMissingStatusesWithZero` | 미발생 상태는 0으로 초기화되어 반환 |

### `AdminOrderControllerTest` (MockMvc 슬라이스)

| 테스트 | 검증 |
|---|---|
| `search_as_admin_returns200` | `X-User-Role=ROLE_ADMIN` 헤더 주입 시 200 |
| `search_as_user_returns403` | `ROLE_USER` 는 403 |
| `updateStatus_validationFails_whenStatusNull` | Bean Validation 400 |
| `search_sizeOver100_returns400` | `ERR_PAGE_SIZE_EXCEEDED` |

### `OrderRepositoryImplTest` (`@DataJpaTest` + QueryDSL 빈 수동 등록)

> **테스트 설정**: `@DataJpaTest` 기본적으로 JPAQueryFactory 빈을 로드하지 않으므로, `@Import(QuerydslConfig.class)` 추가. H2 임베디드 DB로 실제 SQL 생성을 검증한다.

| 테스트 | 검증 |
|---|---|
| `searchAdmin_allConditionsNull_returnsAll` | 모든 조건 null → 전체 반환 + 정렬은 createdAt desc 기본값 |
| `searchAdmin_memberIdIn_filtersCorrectly` | memberIds 리스트에 포함된 것만 반환 |
| `searchAdmin_statusEq_and_statusIn_combineWithAnd` | status + statuses 동시 지정 시 AND |
| `searchAdmin_productTitleContains_joinsItemsWithDistinct` | JOIN 발생, 동일 Order가 중복으로 반환되지 않음 |
| `searchAdmin_createdBetween_inclusive` | 경계값 포함 (both inclusive) |
| `searchAdmin_totalBetween_filtersAmount` | 금액 범위 필터 |
| `searchAdmin_pageable_appliesOffsetLimit` | offset/limit 반영 |
| `searchAdmin_sortByTotalAmountAsc` | 정렬 화이트리스트 동작 |
| `searchAdmin_sortByUnsupportedField_ignored` | 미지원 필드는 조용히 무시되고 기본 정렬 유지 |
| `searchAdmin_countQuerySkippedOnLastPage` | `PageableExecutionUtils` 동작 — 로그/Hibernate 쿼리 어서트 |

**커버리지**: 관리자 API 관련 클래스 70% 이상.

---

## 13. 구현 순서

1. **build.gradle** QueryDSL 의존성 추가 → `./gradlew clean compileJava` 로 Q 클래스 자동 생성 확인
2. `config/QuerydslConfig.java` 로 `JPAQueryFactory` 빈 등록
3. `OrderRepositoryCustom` + `OrderRepositoryImpl` 작성 → `@DataJpaTest` 단위 테스트 (H2) 통과
4. `OrderRepository` 에 `countByStatusGrouped` JPQL 추가
5. DTO (`AdminOrderSearchRequest`, `AdminStatusUpdateRequest`, 응답 DTO) 작성
6. `MemberClient` 작성 + `jym-member-auth-service` 측 `/members/search`, `/members/batch` 스펙 분리 제출
7. `AdminOrderService` → `AdminOrderController` 작성 → MockMvc 슬라이스 테스트
8. 03_SSE 스펙의 `OrderStatusChangedDomainEvent` 리스너와 연동해 관리자 상태 변경 시에도 유저에게 Push 발송 확인
9. OpenAPI YAML 병합 + Swagger UI에서 수동 검증
