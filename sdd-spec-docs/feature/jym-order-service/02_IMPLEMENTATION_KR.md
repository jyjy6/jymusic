# 02_IMPLEMENTATION — 주문 서비스

> **대상 서비스**: `jym-order-service`  
> **포트**: 8083  
> **DB**: `jym_order_db` (MySQL)  
> **역할**: 장바구니 관리, 주문 생성, 주문 내역 조회 및 상태 관리. 상품 정보는 `jym-catalog-service`에서 실시간 조회.

---

## 1. 프로젝트 설정

| 항목 | 값 |
|---|---|
| 모듈명 | `jym-order-service` |
| 그룹 | `jymusic` |
| Java | 21 |
| Spring Boot | 4.0.3 |
| 서버 포트 | 8083 |
| 데이터베이스 | `jym_order_db` |

### `build.gradle`

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'jymusic'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5'
    compileOnly 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    runtimeOnly 'com.mysql:mysql-connector-j'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

---

## 2. 패키지 구조

```
src/main/java/jymusic/jym_order_service/
├── JymOrderServiceApplication.java
├── client/
│   └── CatalogClient.java              # catalog-service HTTP 클라이언트
├── common/
│   └── GlobalErrorHandler/
│       ├── GlobalException.java
│       └── GlobalExceptionHandler.java
├── config/
│   ├── AppConfig.java                  # RestClient 빈 등록
│   ├── JpaConfig.java                  # JPA Auditing 활성화
│   └── SecurityConfig.java
├── controller/
│   ├── CartController.java
│   └── OrderController.java
├── domain/
│   ├── entity/
│   │   ├── BaseTimeEntity.java
│   │   ├── Cart.java
│   │   ├── CartItem.java
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── OrderStatus.java
│   └── repository/
│       ├── CartItemRepository.java
│       ├── CartRepository.java
│       └── OrderRepository.java
├── dto/
│   ├── request/
│   │   ├── AddToCartRequest.java
│   │   ├── OrderCreateRequest.java
│   │   ├── OrderItemRequest.java
│   │   └── UpdateCartItemRequest.java
│   └── response/
│       ├── CartItemResponse.java
│       ├── CartResponse.java
│       ├── OrderDetailResponse.java
│       ├── OrderItemDetailResponse.java
│       └── OrderResponse.java
├── filter/
│   └── GatewayAuthenticationFilter.java
└── service/
    ├── CartService.java
    └── OrderService.java
```

---

## 3. 도메인 엔티티

### `BaseTimeEntity.java`

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

### `Cart.java`

```java
@Entity
@Table(name = "cart")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Cart extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();
}
```

### `CartItem.java`

```java
@Entity
@Table(name = "cart_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class CartItem extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    public void updateQuantity(int quantity) {
        this.quantity = quantity;
    }
}
```

> **UNIQUE KEY (cart_id, product_id)**: 동일 상품 재담기 시 수량 누적 (UPSERT).

### `Order.java`

```java
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Order extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }
}
```

### `OrderItem.java`

```java
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class OrderItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 255)
    private String productTitle;       // 주문 시점 스냅샷

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal unitPrice;      // 주문 시점 스냅샷

    @Column(nullable = false)
    private int quantity;
}
```

### `OrderStatus.java`

```java
public enum OrderStatus {
    PENDING,    // 주문 생성, 결제 대기
    PAID,       // 결제 완료
    SHIPPED,    // 발송 완료
    COMPLETED,  // 구매 확정
    CANCELLED   // 취소됨
}
```

| 값 | 전환 가능 다음 상태 |
|---|---|
| PENDING | PAID, CANCELLED |
| PAID | SHIPPED, CANCELLED |
| SHIPPED | COMPLETED |
| COMPLETED | (없음) |
| CANCELLED | (없음) |

---

## 4. DTO 설계

### Request DTO

```java
// AddToCartRequest.java
@Getter @NoArgsConstructor
public class AddToCartRequest {
    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    @Min(value = 1, message = "수량은 최소 1 이상이어야 합니다.")
    private int quantity;
}

// UpdateCartItemRequest.java
@Getter @NoArgsConstructor
public class UpdateCartItemRequest {
    @Min(value = 0, message = "수량은 0 이상이어야 합니다.")
    private int quantity;
    // 수량 0 → 해당 아이템 삭제 처리
}

// OrderCreateRequest.java
@Getter @NoArgsConstructor
public class OrderCreateRequest {
    @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다.")
    @Valid
    private List<OrderItemRequest> items;
}

// OrderItemRequest.java
@Getter @NoArgsConstructor
public class OrderItemRequest {
    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    @Min(value = 1, message = "수량은 최소 1 이상이어야 합니다.")
    private int quantity;
}
```

### Response DTO

```java
// CartItemResponse.java
@Getter @Builder
public class CartItemResponse {
    private Long cartItemId;
    private Long productId;
    private String title;
    private String artist;
    private String thumbnailUrl;
    private BigDecimal price;
    private int quantity;
    private int stockQuantity;   // catalog-service에서 실시간 조회한 재고 (수량 상한 검증용)

    public static CartItemResponse of(CartItem item, String title, String artist,
                                       String thumbnailUrl, BigDecimal price, int stockQuantity) { ... }
}

// CartResponse.java
@Getter @Builder
public class CartResponse {
    private Long cartId;
    private List<CartItemResponse> items;

    public static CartResponse of(Cart cart, List<CartItemResponse> items) { ... }
}

// OrderResponse.java  (목록 조회용 요약)
@Getter @Builder
public class OrderResponse {
    private Long id;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;

    public static OrderResponse from(Order order) { ... }
}

// OrderDetailResponse.java  (상세 조회용)
@Getter @Builder
public class OrderDetailResponse {
    private Long id;
    private BigDecimal totalAmount;
    private String status;
    private List<OrderItemDetailResponse> items;
    private LocalDateTime createdAt;

    public static OrderDetailResponse from(Order order) { ... }
}

// OrderItemDetailResponse.java
@Getter @Builder
public class OrderItemDetailResponse {
    private Long productId;
    private String productTitle;
    private BigDecimal price;
    private int quantity;

    public static OrderItemDetailResponse from(OrderItem item) { ... }
}
```

---

## 5. Repository

```java
// CartRepository.java
public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByMemberId(Long memberId);
}

// CartItemRepository.java
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
}

// OrderRepository.java
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
```

---

## 6. Service 레이어

### CartService 메서드 목록

| 메서드 | 설명 | 트랜잭션 |
|---|---|---|
| `getCart(memberId)` | 장바구니 조회 (없으면 빈 응답) | readOnly |
| `addItem(memberId, request)` | 상품 담기 (UPSERT, 장바구니 자동 생성) | write |
| `updateItem(memberId, cartItemId, request)` | 수량 변경 (수량 0 → 아이템 삭제) | write |
| `removeItem(memberId, cartItemId)` | 아이템 개별 삭제 | write |
| `clearCart(memberId)` | 장바구니 전체 비우기 | write |

### OrderService 메서드 목록

| 메서드 | 설명 | 트랜잭션 |
|---|---|---|
| `createOrder(memberId, request)` | 주문 생성 (재고 검증, 가격 스냅샷, 장바구니 비우기) | write |
| `getMyOrders(memberId)` | 내 주문 목록 조회 (최신순) | readOnly |
| `getOrderDetail(memberId, orderId)` | 주문 상세 조회 (소유자 검증) | readOnly |
| `updateOrderStatus(orderId, newStatus)` | 주문 상태 변경 (payment-service 내부 호출용) | write |

### CartService.java

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogClient catalogClient;

    public CartResponse getCart(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .map(this::toCartResponse)
                .orElseGet(() -> CartResponse.builder().cartId(null).items(List.of()).build());
    }

    @Transactional
    public CartResponse addItem(Long memberId, AddToCartRequest request) {
        CatalogClient.ProductInfo productInfo = catalogClient.getProductInfo(request.getProductId());

        if (productInfo.stockQuantity() < request.getQuantity()) {
            throw new GlobalException(
                "재고가 부족합니다. 최대 " + productInfo.stockQuantity() + "개까지 구매 가능합니다.",
                "ERR_INSUFFICIENT_STOCK"
            );
        }

        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseGet(() -> cartRepository.save(Cart.builder().memberId(memberId).build()));

        cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId())
                .ifPresentOrElse(
                        existing -> existing.updateQuantity(existing.getQuantity() + request.getQuantity()),
                        () -> cart.getItems().add(CartItem.builder()
                                .cart(cart).productId(request.getProductId())
                                .quantity(request.getQuantity()).build())
                );

        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItem(Long memberId, Long cartItemId, UpdateCartItemRequest request) {
        Cart cart = getCartOrThrow(memberId);
        CartItem item = getCartItemOrThrow(cartItemId);
        verifyCartOwnership(cart, item);

        if (request.getQuantity() == 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            CatalogClient.ProductInfo info = catalogClient.getProductInfo(item.getProductId());
            if (request.getQuantity() > info.stockQuantity()) {
                throw new GlobalException(
                    "재고가 부족합니다. 최대 " + info.stockQuantity() + "개까지 구매 가능합니다.",
                    "ERR_INSUFFICIENT_STOCK"
                );
            }
            item.updateQuantity(request.getQuantity());
        }
        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public void removeItem(Long memberId, Long cartItemId) {
        Cart cart = getCartOrThrow(memberId);
        CartItem item = getCartItemOrThrow(cartItemId);
        verifyCartOwnership(cart, item);
        cart.getItems().remove(item);
        cartItemRepository.delete(item);
    }

    @Transactional
    public void clearCart(Long memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });
    }

    // -- 내부 헬퍼 --
    private Cart getCartOrThrow(Long memberId) {
        return cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new GlobalException("장바구니를 찾을 수 없습니다.", "ERR_CART_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private CartItem getCartItemOrThrow(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new GlobalException("장바구니 아이템을 찾을 수 없습니다.", "ERR_CART_ITEM_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private void verifyCartOwnership(Cart cart, CartItem item) {
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new GlobalException("접근 권한이 없습니다.", "ERR_FORBIDDEN", HttpStatus.FORBIDDEN);
        }
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> {
                    CatalogClient.ProductInfo info = catalogClient.getProductInfo(item.getProductId());
                    return CartItemResponse.of(item, info.title(), info.artist(),
                            info.thumbnailUrl(), info.price(), info.stockQuantity());
                })
                .toList();
        return CartResponse.of(cart, itemResponses);
    }
}
```

### OrderService.java

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CatalogClient catalogClient;

    @Transactional
    public OrderResponse createOrder(Long memberId, OrderCreateRequest request) {
        record ItemInfo(CatalogClient.ProductInfo info, int quantity) {}

        List<ItemInfo> infos = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 1. 재고 검증 + 금액 계산 (catalog-service API 호출)
        for (OrderItemRequest itemReq : request.getItems()) {
            CatalogClient.ProductInfo info = catalogClient.getProductInfo(itemReq.getProductId());
            if (info.stockQuantity() < itemReq.getQuantity()) {
                throw new GlobalException("상품 '" + info.title() + "'의 재고가 부족합니다.", "ERR_INSUFFICIENT_STOCK");
            }
            totalAmount = totalAmount.add(info.price().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            infos.add(new ItemInfo(info, itemReq.getQuantity()));
        }

        // 2. 주문 + 주문 아이템 생성 (가격 스냅샷 저장)
        Order order = Order.builder()
                .memberId(memberId).totalAmount(totalAmount).status(OrderStatus.PENDING).build();
        for (ItemInfo i : infos) {
            order.getItems().add(OrderItem.builder()
                    .order(order).productId(i.info().productId()).productTitle(i.info().title())
                    .unitPrice(i.info().price()).quantity(i.quantity()).build());
        }
        Order savedOrder = orderRepository.save(order);

        // 3. 장바구니 비우기
        cartRepository.findByMemberId(memberId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });

        return OrderResponse.from(savedOrder);
    }

    public List<OrderResponse> getMyOrders(Long memberId) {
        return orderRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                .stream().map(OrderResponse::from).toList();
    }

    public OrderDetailResponse getOrderDetail(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new GlobalException("접근 권한이 없습니다.", "ERR_FORBIDDEN", HttpStatus.FORBIDDEN);
        }
        return OrderDetailResponse.from(order);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new GlobalException("주문을 찾을 수 없습니다.", "ERR_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND));
        order.updateStatus(newStatus);
    }
}
```

---

## 7. Controller 레이어

### CartController.java

```java
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal String memberId) {
        return ResponseEntity.ok(cartService.getCart(Long.parseLong(memberId)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addItem(Long.parseLong(memberId), request));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(Long.parseLong(memberId), cartItemId, request));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long cartItemId) {
        cartService.removeItem(Long.parseLong(memberId), cartItemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal String memberId) {
        cartService.clearCart(Long.parseLong(memberId));
        return ResponseEntity.noContent().build();
    }
}
```

### OrderController.java

```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal String memberId,
            @Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(Long.parseLong(memberId), request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal String memberId) {
        return ResponseEntity.ok(orderService.getMyOrders(Long.parseLong(memberId)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @AuthenticationPrincipal String memberId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderDetail(Long.parseLong(memberId), orderId));
    }

    // payment-service가 결제 완료·취소 후 주문 상태를 동기적으로 업데이트하기 위한 내부 엔드포인트
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {
        orderService.updateOrderStatus(orderId, OrderStatus.valueOf(body.get("status")));
        return ResponseEntity.noContent().build();
    }
}
```

---

## 8. 외부 서비스 클라이언트

### CatalogClient.java

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogClient {

    private final RestClient catalogRestClient;  // AppConfig에서 baseUrl 설정된 빈

    @SuppressWarnings("unchecked")
    public ProductInfo getProductInfo(Long productId) {
        try {
            Map<String, Object> response = catalogRestClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new GlobalException("상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND);
                    })
                    .body(Map.class);

            return new ProductInfo(
                    productId,
                    (String) response.get("title"),
                    (String) response.get("artist"),
                    (String) response.get("imageUrl"),
                    new BigDecimal(response.get("price").toString()),
                    (Integer) response.get("stockQuantity")
            );
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("catalog-service 호출 실패: productId={}", productId, e);
            throw new GlobalException("상품 정보를 가져오는 중 오류가 발생했습니다.", "ERR_CATALOG_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public record ProductInfo(
            Long productId, String title, String artist,
            String thumbnailUrl, BigDecimal price, int stockQuantity
    ) {}
}
```

**호출 흐름:**
```
CartService / OrderService
  → CatalogClient.getProductInfo(productId)
      → GET {catalog-url}/api/v1/products/{productId}
          ← { title, artist, imageUrl, price, stockQuantity }
```

---

## 9. 보안 설정

### GatewayAuthenticationFilter.java

```java
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String userRole = request.getHeader("X-User-Role");

        if (userId != null && userRole != null) {
            var authorities = AuthorityUtils.createAuthorityList(userRole);
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
```

> API Gateway가 JWT 검증 후 `X-User-Id` / `X-User-Role` 헤더를 주입하면, 이 필터가 SecurityContext에 인증 정보를 등록합니다.  
> 컨트롤러의 `@AuthenticationPrincipal String memberId`는 `X-User-Id` 값을 주입받습니다.

### SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/openapi.yaml").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

---

## 10. 예외 처리

### GlobalException.java

```java
public class GlobalException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    public GlobalException(String message, String errorCode, HttpStatus httpStatus) { ... }
    public GlobalException(String message, String errorCode) {
        this(message, errorCode, HttpStatus.BAD_REQUEST);
    }
}
```

### GlobalExceptionHandler.java

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(GlobalException ex) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(...) { ... }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(...) { ... }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(...) { ... }
}
```

### 오류 응답 포맷

```json
{
  "status": 404,
  "code": "ERR_ORDER_NOT_FOUND",
  "message": "주문을 찾을 수 없습니다.",
  "timestamp": "2026-03-18T10:30:00"
}
```

### 에러 코드 목록

| 코드 | HTTP | 설명 |
|---|---|---|
| `ERR_CART_NOT_FOUND` | 404 | 장바구니를 찾을 수 없음 |
| `ERR_CART_ITEM_NOT_FOUND` | 404 | 장바구니 아이템을 찾을 수 없음 |
| `ERR_ORDER_NOT_FOUND` | 404 | 주문을 찾을 수 없음 |
| `ERR_INSUFFICIENT_STOCK` | 400 | 재고 부족 |
| `ERR_FORBIDDEN` | 403 | 접근 권한 없음 (타인 주문 조회 시도) |
| `ERR_CATALOG_UNAVAILABLE` | 503 | catalog-service 연결 오류 |
| `ERR_VALIDATION_FAILED` | 400 | Bean Validation 실패 |

---

## 11. 설정 파일

### `application.properties`

```properties
spring.application.name=jym-order-service
spring.profiles.active=dev
```

### `application-dev.properties`

```properties
server.port=8083
spring.application.name=jym-order-service

# Database
spring.datasource.url=jdbc:mysql://218.38.160.152:3306/jym_order_db?serverTimezone=UTC&characterEncoding=UTF-8
spring.datasource.username=jyjymysql
spring.datasource.password=1234!@
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Swagger UI
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.url=/openapi.yaml
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/api-docs

# Catalog Service URL
services.catalog.url=http://localhost:8082

# Logging
logging.level.jymusic.jym_order_service=DEBUG
server.error.include-message=always
```

---

## 12. 단위 테스트 가이드

### 테스트 대상 우선순위

| 우선순위 | 대상 클래스 | 테스트 유형 |
|---|---|---|
| 1 | `CartService` | Mockito 단위 테스트 |
| 2 | `OrderService` | Mockito 단위 테스트 |
| 3 | `CartController` | MockMvc 슬라이스 테스트 (선택) |

### CartServiceTest — 핵심 케이스

| 테스트 메서드 | 검증 내용 |
|---|---|
| `getCart_returnsEmptyWhenNoCart` | 장바구니 없으면 빈 응답 반환 |
| `addItem_createsNewCart` | 신규 상품 담기 → 장바구니 자동 생성 |
| `addItem_throwsWhenStockInsufficient` | 재고 초과 시 `GlobalException` 발생 |
| `removeItem_throwsWhenItemNotFound` | 없는 아이템 삭제 시 404 예외 |
| `updateItem_deletesWhenQuantityIsZero` | 수량 0 업데이트 → 아이템 삭제 |

### OrderServiceTest — 핵심 케이스

| 테스트 메서드 | 검증 내용 |
|---|---|
| `createOrder_success` | 정상 주문 생성 시 PENDING 상태 반환 |
| `createOrder_throwsWhenStockInsufficient` | 재고 부족 시 예외 |
| `getOrderDetail_throwsWhenNotOwner` | 타인 주문 조회 시 FORBIDDEN |
| `getOrderDetail_throwsWhenNotFound` | 없는 주문 조회 시 404 |
| `getMyOrders_returnsOrders` | 회원의 주문 목록 반환 |

---
