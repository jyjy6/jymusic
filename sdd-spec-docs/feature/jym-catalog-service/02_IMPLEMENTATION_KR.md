# jym-catalog-service: 백엔드 구현 스펙

> **목적**: 프론트엔드(jym-front) 카탈로그 기능과 연동하는 Spring Boot 기반 상품 카탈로그 마이크로서비스 구현 가이드
> **연관 스펙**: `openapi.yaml`, `01_TABLE_DESIGN_KR.md`

---

## 1. 프로젝트 설정

### 1.1 기본 정보

| 항목 | 값 |
|---|---|
| 모듈명 | `jym-catalog-service` |
| 패키지 루트 | `com.jymusic.catalog` |
| Java | 21 |
| Spring Boot | 3.x |
| 포트 | `8082` (게이트웨이 내부 라우팅용) |
| DB | MySQL (`jym_catalog_db`) |

### 1.2 Gradle 의존성 (`build.gradle`)

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // AWS S3
    implementation 'software.amazon.awssdk:s3:2.x.x'

    // DB
    runtimeOnly 'com.mysql:mysql-connector-j'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // JWT (게이트웨이가 전달한 헤더 파싱 — 직접 검증 시)
    implementation 'io.jsonwebtoken:jjwt-api:0.12.x'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.x'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.x'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

---

## 2. 프로젝트 패키지 구조

```
com.jymusic.catalog
├── config/
│   ├── SecurityConfig.java          # Spring Security 설정 (역할 기반 접근 제어)
│   └── S3Config.java                # AWS S3Client Bean 설정
│
├── controller/
│   ├── ProductController.java       # /api/v1/products
│   ├── CategoryController.java      # /api/v1/categories
│   └── MediaController.java         # /api/v1/media/presigned-url
│
├── domain/
│   ├── entity/
│   │   ├── Product.java
│   │   └── Category.java
│   └── repository/
│       ├── ProductRepository.java
│       └── CategoryRepository.java
│
├── dto/
│   ├── request/
│   │   ├── ProductCreateRequest.java
│   │   ├── ProductUpdateRequest.java
│   │   └── PresignedUrlRequest.java
│   └── response/
│       ├── ProductSummaryResponse.java
│       ├── ProductDetailResponse.java
│       ├── ProductListResponse.java
│       ├── CategoryResponse.java
│       └── PresignedUrlResponse.java
│
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── GlobalException.java
│   └── ErrorCode.java
│
└── service/
    ├── ProductService.java
    ├── CategoryService.java
    └── MediaService.java
```

---

## 3. 도메인 엔티티

### 3.1 `Category.java`

```java
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

### 3.2 `Product.java`

```java
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String artist;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    // S3 objectKey (예: "products/uuid-abbey-road.jpg")
    // imageUrl, thumbnailUrl은 이 값을 기반으로 서비스에서 조합
    @Column(length = 500)
    private String imageKey;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 수정 메서드 (엔티티 직접 수정 — setter 금지)
    public void update(String title, String artist, String description,
                       BigDecimal price, Integer stockQuantity,
                       Category category, String imageKey) {
        this.title = title;
        this.artist = artist;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.category = category;
        this.imageKey = imageKey;
    }
}
```

---

## 4. DTO 설계

### 4.1 요청 DTO

#### `ProductCreateRequest.java`
```java
@Getter
@NoArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "앨범명은 필수입니다.")
    @Size(max = 100)
    private String title;

    @NotBlank(message = "아티스트명은 필수입니다.")
    @Size(max = 100)
    private String artist;

    @Size(max = 2000)
    private String description;

    @NotNull(message = "가격은 필수입니다.")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;

    @NotNull(message = "재고 수량은 필수입니다.")
    @Min(0)
    private Integer stockQuantity;

    @NotNull(message = "카테고리는 필수입니다.")
    private Long categoryId;

    private String imageKey;  // S3 objectKey (nullable)
}
```

#### `ProductUpdateRequest.java`
```java
@Getter
@NoArgsConstructor
public class ProductUpdateRequest {

    @NotBlank @Size(max = 100)
    private String title;

    @NotBlank @Size(max = 100)
    private String artist;

    @Size(max = 2000)
    private String description;

    @NotNull @DecimalMin("0.0")
    private BigDecimal price;

    @NotNull @Min(0)
    private Integer stockQuantity;

    @NotNull
    private Long categoryId;

    private String imageKey;  // null 허용 — 이미지 제거 의도
}
```

#### `PresignedUrlRequest.java`
```java
@Getter
@NoArgsConstructor
public class PresignedUrlRequest {

    @NotBlank(message = "파일명은 필수입니다.")
    private String filename;

    @NotBlank(message = "contentType은 필수입니다.")
    private String contentType;
}
```

### 4.2 응답 DTO

#### `ProductSummaryResponse.java`
```java
@Getter
@Builder
public class ProductSummaryResponse {
    private Long id;
    private String title;
    private String artist;
    private BigDecimal price;
    private String thumbnailUrl;  // imageKey → URL 조합

    public static ProductSummaryResponse from(Product product, String s3BaseUrl) {
        return ProductSummaryResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .artist(product.getArtist())
                .price(product.getPrice())
                .thumbnailUrl(product.getImageKey() != null
                        ? s3BaseUrl + "/" + product.getImageKey() : null)
                .build();
    }
}
```

#### `ProductDetailResponse.java`
```java
@Getter
@Builder
public class ProductDetailResponse {
    private Long id;
    private String title;
    private String artist;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;

    public static ProductDetailResponse from(Product product, String s3BaseUrl) {
        return ProductDetailResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .artist(product.getArtist())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageKey() != null
                        ? s3BaseUrl + "/" + product.getImageKey() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .build();
    }
}
```

#### `ProductListResponse.java`
```java
@Getter
@Builder
public class ProductListResponse {
    private List<ProductSummaryResponse> content;
    private long totalElements;
    private int totalPages;

    public static ProductListResponse from(Page<ProductSummaryResponse> page) {
        return ProductListResponse.builder()
                .content(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
```

#### `CategoryResponse.java`
```java
@Getter
@Builder
public class CategoryResponse {
    private Long id;
    private String name;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .build();
    }
}
```

#### `PresignedUrlResponse.java`
```java
@Getter
@Builder
public class PresignedUrlResponse {
    private String presignedUrl;
    private String objectKey;
}
```

---

## 5. Repository

### 5.1 `ProductRepository.java`
```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    // isAvailable = true인 상품만 조회 (판매 중지 상품 필터)
    Page<Product> findByIsAvailableTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndIsAvailableTrue(Long categoryId, Pageable pageable);
}
```

### 5.2 `CategoryRepository.java`
```java
public interface CategoryRepository extends JpaRepository<Category, Long> {
    // 기본 CRUD만 사용
}
```

---

## 6. Service 레이어

### 6.1 `ProductService.java`

| 메서드 | 설명 | 권한 |
|---|---|---|
| `getProducts(page, size, categoryId)` | 상품 목록 조회 (페이징, 카테고리 필터) | 공개 |
| `getProduct(id)` | 상품 상세 조회 | 공개 |
| `createProduct(request)` | 상품 등록 | ROLE_ADMIN |
| `updateProduct(id, request)` | 상품 수정 | ROLE_ADMIN |
| `deleteProduct(id)` | 상품 삭제 (논리 삭제: isAvailable = false) | ROLE_ADMIN |

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Value("${cloud.aws.s3.base-url}")
    private String s3BaseUrl;

    public ProductListResponse getProducts(int page, int size, Long categoryId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage;
        if (categoryId != null) {
            productPage = productRepository.findByCategoryIdAndIsAvailableTrue(categoryId, pageable);
        } else {
            productPage = productRepository.findByIsAvailableTrue(pageable);
        }
        Page<ProductSummaryResponse> mapped = productPage
                .map(p -> ProductSummaryResponse.from(p, s3BaseUrl));
        return ProductListResponse.from(mapped);
    }

    public ProductDetailResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));
        return ProductDetailResponse.from(product, s3BaseUrl);
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductCreateRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new GlobalException(
                        "카테고리를 찾을 수 없습니다.", "ERR_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND));

        Product product = Product.builder()
                .title(request.getTitle())
                .artist(request.getArtist())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .category(category)
                .imageKey(request.getImageKey())
                .isAvailable(true)
                .build();

        return ProductDetailResponse.from(productRepository.save(product), s3BaseUrl);
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new GlobalException(
                        "카테고리를 찾을 수 없습니다.", "ERR_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND));

        product.update(request.getTitle(), request.getArtist(), request.getDescription(),
                request.getPrice(), request.getStockQuantity(), category, request.getImageKey());

        return ProductDetailResponse.from(product, s3BaseUrl);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new GlobalException(
                        "상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));
        product.update(product.getTitle(), product.getArtist(), product.getDescription(),
                product.getPrice(), product.getStockQuantity(), product.getCategory(), product.getImageKey());
        // 논리 삭제: isAvailable = false 처리
        // Product에 softDelete() 메서드 추가 권장
    }
}
```

> `deleteProduct`에서 논리 삭제를 위해 `Product` 엔티티에 `softDelete()` 메서드를 추가합니다.

### 6.2 `CategoryService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }
}
```

### 6.3 `MediaService.java`

```java
@Service
@RequiredArgsConstructor
public class MediaService {

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${cloud.aws.s3.presigned-url-expiry-minutes:10}")
    private long expiryMinutes;

    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request) {
        String objectKey = "products/" + UUID.randomUUID() + "-" + request.getFilename();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(request.getContentType())
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expiryMinutes))
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        return PresignedUrlResponse.builder()
                .presignedUrl(presignedRequest.url().toString())
                .objectKey(objectKey)
                .build();
    }
}
```

---

## 7. Controller 레이어

### 7.1 `ProductController.java`

```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ProductListResponse> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(productService.getProducts(page, size, categoryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
```

### 7.2 `CategoryController.java`

```java
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }
}
```

### 7.3 `MediaController.java`

```java
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request) {
        return ResponseEntity.ok(mediaService.generatePresignedUrl(request));
    }
}
```

---

## 8. 보안 설정 (Security)

### 8.1 인증 방식 — 게이트웨이 헤더 기반

API Gateway(`jym-api-gateway`)가 JWT를 검증한 후 다음 헤더를 전달합니다.

| 헤더 | 예시 | 설명 |
|---|---|---|
| `X-User-Id` | `"42"` | 인증된 사용자 ID |
| `X-User-Role` | `"ROLE_ADMIN"` | 사용자 역할 |

카탈로그 서비스는 이 헤더를 파싱하여 `SecurityContext`에 인증 정보를 주입합니다.

### 8.2 `GatewayAuthenticationFilter.java`

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
            List<GrantedAuthority> authorities =
                    AuthorityUtils.createAuthorityList(userRole);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
```

### 8.3 `SecurityConfig.java`

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
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

> **왜 다운스트림 서비스에서도 `@PreAuthorize`를 쓰는가? (Defense in Depth)**
>
> API Gateway는 JWT를 검증하고 `X-User-Role` 헤더를 주입하는 **인증(Authentication)** 역할을 맡습니다.
> 다운스트림 서비스의 `@PreAuthorize`는 그 헤더를 기반으로 실제 **인가(Authorization)** 를 수행합니다.
>
> | 계층 | 역할 | 처리 내용 |
> |---|---|---|
> | API Gateway | 인증 | JWT 서명 검증 → `X-User-Id`, `X-User-Role` 헤더 주입 |
> | Catalog Service | 인가 | `@PreAuthorize("hasRole('ADMIN')")` → 역할 기반 접근 제어 |
>
> Gateway에서만 역할 체크를 하면 내부 네트워크에서 게이트웨이를 우회해 서비스 포트로 직접 호출할 경우 무방비 상태가 됩니다.
> 다운스트림 서비스의 `@PreAuthorize`는 이런 우회 경로를 막는 **2차 방어선**입니다.
> Gateway는 "빠른 거절(Fail Fast)"을 위해 route 레벨에서도 역할 체크를 추가할 수 있지만, 다운스트림 서비스의 검증은 항상 유지해야 합니다.
>
> **결론**: `X-User-Role` 헤더 전달 + 다운스트림 `@PreAuthorize` 조합이 올바른 MSA 인가 패턴입니다.

---

## 9. 예외 처리

> `jym-member-auth-service`의 `GlobalErrorHandler` 패키지를 **그대로 복사**해서 사용합니다.
> 패키지 경로만 `com.jymusic.catalog.common.GlobalErrorHandler`로 변경하고 코드는 동일합니다.
> `ErrorCode` enum은 별도로 만들지 않으며, 예외 발생 시 에러 코드와 메시지를 직접 문자열로 전달합니다.

### 9.1 `GlobalException.java` (복붙 후 패키지만 변경)

```java
package com.jymusic.catalog.common.GlobalErrorHandler;

import org.springframework.http.HttpStatus;

public class GlobalException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    public GlobalException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public GlobalException(String message, String errorCode) {
        this(message, errorCode, HttpStatus.BAD_REQUEST);
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
```

### 9.2 `GlobalExceptionHandler.java` (복붙 후 패키지만 변경)

```java
package com.jymusic.catalog.common.GlobalErrorHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(GlobalException ex) {
        log.error("Business Exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        Map<String, Object> errorResponse = createErrorResponse(
                ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus().value());
        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String firstMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("유효하지 않은 요청입니다.");
        Map<String, Object> errorResponse = createErrorResponse(
                "ERR_VALIDATION_FAILED", firstMessage, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected Exception: ", ex);
        Map<String, Object> errorResponse = createErrorResponse(
                "INTERNAL_SERVER_ERROR", "시스템 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(Exception e) {
        log.error("권한 없는 접근 시도: {}", e.getMessage());
        Map<String, Object> errorResponse = createErrorResponse(
                "HTTP_UNAUTHORIZED_ERROR", "접근 권한이 없습니다.", HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    private Map<String, Object> createErrorResponse(String errorCode, String message, int status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", errorCode);
        errorResponse.put("message", message);
        errorResponse.put("status", status);
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        return errorResponse;
    }
}
```

### 9.3 서비스에서 예외 던지는 방식

`ErrorCode` enum 없이 생성자에 직접 문자열로 전달합니다.

```java
// 상품 없음
productRepository.findById(id)
    .orElseThrow(() -> new GlobalException(
        "상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));

// 카테고리 없음
categoryRepository.findById(request.getCategoryId())
    .orElseThrow(() -> new GlobalException(
        "카테고리를 찾을 수 없습니다.", "ERR_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND));

// Presigned URL 생성 실패
throw new GlobalException(
    "Presigned URL 생성에 실패했습니다.", "ERR_PRESIGNED_URL_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
```

**응답 구조** (프론트엔드 규격 준수):
```json
{
  "status": 404,
  "code": "ERR_PRODUCT_NOT_FOUND",
  "message": "상품을 찾을 수 없습니다.",
  "timestamp": "2026-03-10T12:00:00"
}
```

---

## 10. S3 설정

### 10.1 `S3Config.java`

```java
@Configuration
public class S3Config {

    @Value("${cloud.aws.region.static}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
```

> AWS SDK v2 사용. 자격 증명은 EC2 IAM Role 또는 환경 변수(`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)로 주입.

---

## 11. 설정 파일 (`application.properties`)

`jym-member-auth-service`와 동일하게 `application.properties` + profile 분리 방식을 사용합니다.

**`application.properties`** (공통 — 프로파일 설정만):
```properties
spring.application.name=jym-catalog-service
spring.profiles.active=dev

# S3 공통 설정
cloud.aws.region.static=ap-northeast-2
cloud.aws.s3.presigned-url-expiry-minutes=10
```

**`application-dev.properties`** (개발 환경):
```properties
server.port=8082

# Database
spring.datasource.url=jdbc:mysql://218.38.160.152:3306/jym_catalog_db?serverTimezone=UTC&characterEncoding=UTF-8
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

# Logging
logging.level.com.jymusic.catalog=DEBUG

# S3
cloud.aws.s3.bucket=jymusic-dev-bucket
cloud.aws.s3.base-url=https://jymusic-dev-bucket.s3.ap-northeast-2.amazonaws.com

# Error Response
server.error.include-message=always
```

**`application-prod.properties`** (운영 환경):
```properties
server.port=8082

# Database
spring.datasource.url=jdbc:mysql://${DB_HOST}:3306/jym_catalog_db?serverTimezone=UTC&characterEncoding=UTF-8
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Swagger UI (운영 비활성화)
springdoc.swagger-ui.enabled=false

# Logging
logging.level.com.jymusic.catalog=INFO

# S3
cloud.aws.s3.bucket=${S3_BUCKET_NAME}
cloud.aws.s3.base-url=https://${S3_BUCKET_NAME}.s3.ap-northeast-2.amazonaws.com
```

---

## 12. 단위 테스트 가이드

### 12.1 테스트 대상 우선순위

| 클래스 | 테스트 중점 |
|---|---|
| `ProductService` | 목록 조회 필터링, 상품 없을 때 예외, 카테고리 없을 때 예외 |
| `MediaService` | Presigned URL 생성 성공, S3 예외 처리 |
| `ProductController` | HTTP 상태 코드, 요청 유효성 검사 거절 |
| `GatewayAuthFilter` | 헤더 없을 때 익명, 헤더 있을 때 인증 주입 |

### 12.2 테스트 예시 — `ProductServiceTest`

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks ProductService productService;
    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 ERR_PRODUCT_NOT_FOUND 예외")
    void getProduct_notFound_throwsException() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(GlobalException.class)
                .satisfies(e -> {
                    GlobalException ge = (GlobalException) e;
                    assertThat(ge.getErrorCode()).isEqualTo("ERR_PRODUCT_NOT_FOUND");
                    assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("카테고리 ID 없이 상품 목록 조회 시 전체 상품 반환")
    void getProducts_noCategoryFilter_returnsAll() {
        Page<Product> mockPage = new PageImpl<>(List.of());
        given(productRepository.findByIsAvailableTrue(any())).willReturn(mockPage);

        ProductListResponse result = productService.getProducts(0, 12, null);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
```

---

## 13. API Gateway 라우팅 설정 참고

`jym-api-gateway`의 라우팅 설정에서 `/api/v1/products/**`, `/api/v1/categories`, `/api/v1/media/**` 를 `jym-catalog-service`로 라우팅해야 합니다.

```yaml
# gateway application.yml 예시
spring:
  cloud:
    gateway:
      routes:
        - id: catalog-service
          uri: http://jym-catalog-service:8082
          predicates:
            - Path=/api/v1/products/**, /api/v1/categories, /api/v1/media/**
```
