# MyBatis 마이그레이션 & 검색 기능 스펙

> **목적**: `jym-catalog-service`의 GET(읽기 전용) 엔드포인트를 MyBatis로 마이그레이션하고, MyBatis 동적 SQL 기반 상품 검색 API를 신규 추가한다.
> **연관 스펙**: `openapi.yaml`, `01_TABLE_DESIGN_KR.md`, `02_IMPLEMENTATION_KR.md`, `03_CATEGORY_MANAGEMENT_KR.md`
> **프론트엔드 연관**: `jym-front/05_PAGES_SEARCH_FOR_MYBATIS_KR.md`

---

## 1. 개요

### 1.1 배경

현재 `jym-catalog-service`는 모든 데이터 접근을 Spring Data JPA로 처리하고 있다.
읽기(GET) 작업은 복잡한 조회 쿼리 최적화가 필요하며, 엔티티 그래프 로딩에 따른 N+1 문제를 근본적으로 해결하기 위해 **MyBatis**로 마이그레이션한다.
추가로 사용자가 키워드·가격 범위·카테고리·정렬 등 복합 조건으로 상품을 검색할 수 있는 기능도 **MyBatis 동적 SQL**(`<if>`, `<where>`, `<choose>`)로 구현한다.

### 1.2 설계 원칙

| 원칙 | 설명 |
|---|---|
| **읽기/쓰기 분리** | GET(조회·검색) → MyBatis Mapper, CUD(명령) → 기존 JPA 유지 |
| **MyBatis 동적 SQL** | 검색의 복합 조건 조합은 MyBatis XML의 `<if>`, `<where>`, `<choose>` 태그로 처리 |
| **DTO 직접 매핑** | MyBatis 쿼리 결과를 Entity가 아닌 Response DTO에 직접 매핑하여 불필요한 변환 제거 |
| **기존 API 계약 유지** | 기존 엔드포인트의 응답 스키마·HTTP 상태 코드·에러 형식 변경 없음 (프론트엔드 무영향) |

### 1.3 변경 범위

| 레이어 | 변경 내용 |
|---|---|
| `build.gradle` | MyBatis Starter 의존성 추가 |
| Config | `MyBatisConfig.java` 신규 |
| MyBatis Mapper | `ProductReadMapper.java` (인터페이스) + `ProductReadMapper.xml` 신규 |
| MyBatis Mapper | `CategoryReadMapper.java` (인터페이스) + `CategoryReadMapper.xml` 신규 |
| DTO | `ProductSearchRequest.java` 신규 |
| DTO | `ProductSummaryResponse`, `ProductDetailResponse` — `@NoArgsConstructor` 추가, `applyS3BaseUrl()` 메서드 추가 |
| Service | `ProductService` — 기존 GET 메서드를 MyBatis Mapper 호출로 전환, `searchProducts()` 추가 |
| Service | `CategoryService` — `getAllCategories()`를 MyBatis Mapper 호출로 전환 |
| Controller | `ProductController` — `GET /api/v1/products/search` 엔드포인트 추가 |
| OAS | `openapi.yaml` 버전 업데이트 (v1.3.0) |

---

## 2. 의존성 추가

### 2.1 `build.gradle` 변경

```groovy
dependencies {
    // === 기존 의존성 유지 ===
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // === [NEW] MyBatis ===
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4'

    // ... 기존 나머지 의존성 유지 (AWS S3, Lombok, MySQL 등)
}
```

> QueryDSL은 사용하지 않는다. MyBatis의 동적 SQL(`<if>`, `<where>`, `<choose>`)로 검색 조건 조합이 충분하다.

---

## 3. MyBatis 설정

### 3.1 `MyBatisConfig.java`

```java
package jymusic.jym_catalog_service.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "jymusic.jym_catalog_service.mapper", annotationClass = Mapper.class)
public class MyBatisConfig {
}
```

### 3.2 `application.properties` 추가 항목

```properties
# MyBatis
mybatis.mapper-locations=classpath:mapper/**/*.xml
mybatis.configuration.map-underscore-to-camel-case=true
mybatis.type-aliases-package=jymusic.jym_catalog_service.dto.response
```

- `map-underscore-to-camel-case`: DB 컬럼명 `stock_quantity` → DTO 필드 `stockQuantity` 자동 매핑
- `type-aliases-package`: XML에서 `resultType="ProductSummaryResponse"` 같이 단순 클래스명 사용 가능

---

## 4. 마이그레이션 대상 (기존 GET 엔드포인트)

| 컨트롤러 | 엔드포인트 | Before (JPA) | After (MyBatis) |
|---|---|---|---|
| `ProductController` | `GET /api/v1/products` | `ProductRepository.findByIsAvailableTrue()` | `ProductReadMapper.findProducts()` |
| `ProductController` | `GET /api/v1/products/{id}` | `ProductRepository.findById()` | `ProductReadMapper.findProductById()` |
| `CategoryController` | `GET /api/v1/categories` | `CategoryRepository.findAll()` | `CategoryReadMapper.findAllCategories()` |

> CUD (POST, PUT, DELETE)는 **기존 JPA 유지**. 트랜잭션 내 dirty checking, cascade 등 JPA의 장점을 그대로 활용한다.

---

## 5. 신규 검색 API 명세

### 5.1 `GET /api/v1/products/search`

| 항목 | 내용 |
|---|---|
| 인증 | 불필요 (공개 API) |
| 설명 | 키워드·카테고리·가격 범위·정렬 복합 조건 상품 검색 |
| 응답 | `200 OK` — `ProductListResponse` (기존과 동일 형식) |

**Query Parameters:**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `keyword` | String | N | - | 제목(title), 아티스트(artist) 대상 `LIKE` 검색 |
| `categoryId` | Long | N | - | 카테고리 필터 |
| `minPrice` | BigDecimal | N | - | 최소 가격 (이상) |
| `maxPrice` | BigDecimal | N | - | 최대 가격 (이하) |
| `page` | int | N | 0 | 페이지 번호 (0-based) |
| `size` | int | N | 12 | 페이지 크기 |
| `sort` | String | N | `createdAt,desc` | 정렬 기준 |

**지원 정렬 옵션:**

| 값 | 설명 |
|---|---|
| `createdAt,desc` | 최신순 (기본값) |
| `price,asc` | 가격 낮은순 |
| `price,desc` | 가격 높은순 |
| `title,asc` | 이름순 (가나다/ABC) |

**요청 예시:**

```
GET /api/v1/products/search?keyword=Beatles&categoryId=1&minPrice=10000&maxPrice=50000&page=0&size=12&sort=price,asc
```

**응답 (기존 `ProductListResponse` 동일 형식):**

```json
{
  "content": [
    {
      "id": 1,
      "title": "Abbey Road",
      "artist": "The Beatles",
      "price": 25000.00,
      "thumbnailUrl": "https://bucket.s3.../products/uuid-abbey-road.jpg"
    }
  ],
  "totalElements": 3,
  "totalPages": 1
}
```

### 5.2 SecurityConfig — 변경 불필요

기존 `requestMatchers(HttpMethod.GET, "/api/v1/products/**")`가 `/api/v1/products/search`를 이미 포함하므로 Security 설정 변경은 불필요하다.

---

## 6. 패키지 구조 (추가분)

```
jymusic.jym_catalog_service
├── config/
│   ├── JpaConfig.java                  # [유지]
│   ├── S3Config.java                   # [유지]
│   ├── SecurityConfig.java             # [유지]
│   └── MyBatisConfig.java              # [NEW]
├── mapper/                             # [NEW] MyBatis 매퍼 인터페이스
│   ├── ProductReadMapper.java
│   └── CategoryReadMapper.java
├── domain/
│   └── repository/
│       ├── ProductRepository.java      # [유지] CUD 전용
│       └── CategoryRepository.java     # [유지] CUD 전용
├── dto/
│   ├── request/
│   │   ├── ProductSearchRequest.java   # [NEW]
│   │   └── ...                         # [유지]
│   └── response/
│       ├── ProductSummaryResponse.java # [수정] NoArgsConstructor, applyS3BaseUrl() 추가
│       ├── ProductDetailResponse.java  # [수정] NoArgsConstructor, applyS3BaseUrl() 추가
│       └── ...                         # [유지]
└── src/main/resources/
    └── mapper/                         # [NEW] MyBatis XML 매퍼
        ├── ProductReadMapper.xml
        └── CategoryReadMapper.xml
```

---

## 7. MyBatis 매퍼 인터페이스

### 7.1 `ProductReadMapper.java`

```java
package jymusic.jym_catalog_service.mapper;

import jymusic.jym_catalog_service.dto.request.ProductSearchRequest;
import jymusic.jym_catalog_service.dto.response.ProductDetailResponse;
import jymusic.jym_catalog_service.dto.response.ProductSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductReadMapper {

    // 기존 상품 목록 조회 (카테고리 필터, 페이징)
    List<ProductSummaryResponse> findProducts(
            @Param("categoryId") Long categoryId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    long countProducts(@Param("categoryId") Long categoryId);

    // 상품 상세 조회
    ProductDetailResponse findProductById(@Param("id") Long id);

    // [NEW] 검색 (동적 SQL)
    List<ProductSummaryResponse> searchProducts(@Param("req") ProductSearchRequest request);

    long countSearchProducts(@Param("req") ProductSearchRequest request);
}
```

### 7.2 `CategoryReadMapper.java`

```java
package jymusic.jym_catalog_service.mapper;

import jymusic.jym_catalog_service.dto.response.CategoryResponse;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryReadMapper {

    List<CategoryResponse> findAllCategories();
}
```

---

## 8. MyBatis XML 매퍼

### 8.1 `ProductReadMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="jymusic.jym_catalog_service.mapper.ProductReadMapper">

    <!-- ============================== -->
    <!--  공통 SQL Fragment              -->
    <!-- ============================== -->

    <sql id="productSummaryColumns">
        p.id,
        p.title,
        p.artist,
        p.price,
        p.image_key AS imageKey
    </sql>

    <sql id="searchWhereClause">
        <where>
            p.is_available = TRUE
            <if test="req.keyword != null and req.keyword != ''">
                AND (
                    p.title LIKE CONCAT('%', #{req.keyword}, '%')
                    OR p.artist LIKE CONCAT('%', #{req.keyword}, '%')
                )
            </if>
            <if test="req.categoryId != null">
                AND p.category_id = #{req.categoryId}
            </if>
            <if test="req.minPrice != null">
                AND p.price &gt;= #{req.minPrice}
            </if>
            <if test="req.maxPrice != null">
                AND p.price &lt;= #{req.maxPrice}
            </if>
        </where>
    </sql>

    <sql id="searchOrderBy">
        <choose>
            <when test="req.sort == 'price,asc'">ORDER BY p.price ASC</when>
            <when test="req.sort == 'price,desc'">ORDER BY p.price DESC</when>
            <when test="req.sort == 'title,asc'">ORDER BY p.title ASC</when>
            <otherwise>ORDER BY p.created_at DESC</otherwise>
        </choose>
    </sql>

    <!-- ============================== -->
    <!--  기존 상품 목록 조회 (MyBatis 전환) -->
    <!-- ============================== -->

    <select id="findProducts" resultType="ProductSummaryResponse">
        SELECT
            <include refid="productSummaryColumns"/>
        FROM products p
        WHERE p.is_available = TRUE
        <if test="categoryId != null">
            AND p.category_id = #{categoryId}
        </if>
        ORDER BY p.created_at DESC
        LIMIT #{size} OFFSET #{offset}
    </select>

    <select id="countProducts" resultType="long">
        SELECT COUNT(*)
        FROM products p
        WHERE p.is_available = TRUE
        <if test="categoryId != null">
            AND p.category_id = #{categoryId}
        </if>
    </select>

    <!-- ============================== -->
    <!--  상품 상세 조회 (MyBatis 전환)    -->
    <!-- ============================== -->

    <select id="findProductById" resultType="ProductDetailResponse">
        SELECT
            p.id,
            p.title,
            p.artist,
            p.description,
            p.price,
            p.stock_quantity  AS stockQuantity,
            p.image_key       AS imageKey,
            p.category_id     AS categoryId,
            c.name            AS categoryName
        FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
        WHERE p.id = #{id}
    </select>

    <!-- ============================== -->
    <!--  [NEW] 검색 (동적 SQL)           -->
    <!-- ============================== -->

    <select id="searchProducts" resultType="ProductSummaryResponse">
        SELECT
            <include refid="productSummaryColumns"/>
        FROM products p
        <include refid="searchWhereClause"/>
        <include refid="searchOrderBy"/>
        LIMIT #{req.size} OFFSET #{req.offset}
    </select>

    <select id="countSearchProducts" resultType="long">
        SELECT COUNT(*)
        FROM products p
        <include refid="searchWhereClause"/>
    </select>

</mapper>
```

> **핵심**: `<sql id="searchWhereClause">`에서 `<where>` + `<if>` 태그로 모든 동적 조건을 조합한다.
> 조건이 하나도 없으면 `is_available = TRUE`만 적용되어 전체 판매 중 상품을 반환한다.
> 정렬은 `<choose>` 태그로 허용된 옵션만 매핑하여 SQL Injection을 방지한다.

### 8.2 `CategoryReadMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="jymusic.jym_catalog_service.mapper.CategoryReadMapper">

    <select id="findAllCategories" resultType="CategoryResponse">
        SELECT id, name
        FROM categories
        ORDER BY id ASC
    </select>

</mapper>
```

---

## 9. DTO 변경

MyBatis가 결과를 DTO에 직접 매핑하려면 **기본 생성자**가 필요하다.
또한 S3 URL 조합은 SQL이 아닌 서비스 레이어에서 처리하므로 `applyS3BaseUrl()` 유틸 메서드를 추가한다.

### 9.1 `ProductSummaryResponse` 변경

```java
@Getter
@Builder
@NoArgsConstructor   // [NEW] MyBatis 매핑용
@AllArgsConstructor
public class ProductSummaryResponse {
    private Long id;
    private String title;
    private String artist;
    private BigDecimal price;
    private String imageKey;       // [NEW] MyBatis가 image_key를 직접 매핑
    private String thumbnailUrl;

    // 기존 JPA 경유 시 사용 (CUD 응답 등) — 기존 코드 유지
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

    // [NEW] MyBatis 결과에 S3 URL 조합
    public void applyS3BaseUrl(String s3BaseUrl) {
        this.thumbnailUrl = (this.imageKey != null)
                ? s3BaseUrl + "/" + this.imageKey : null;
    }
}
```

### 9.2 `ProductDetailResponse` 변경

```java
@Getter
@Builder
@NoArgsConstructor   // [NEW] MyBatis 매핑용
@AllArgsConstructor
public class ProductDetailResponse {
    private Long id;
    private String title;
    private String artist;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String imageKey;       // [NEW] MyBatis가 image_key를 직접 매핑
    private String imageUrl;
    private Long categoryId;
    private String categoryName;

    // 기존 JPA 경유 시 사용 (CUD 응답 등) — 기존 코드 유지
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

    // [NEW] MyBatis 결과에 S3 URL 조합
    public void applyS3BaseUrl(String s3BaseUrl) {
        this.imageUrl = (this.imageKey != null)
                ? s3BaseUrl + "/" + this.imageKey : null;
    }
}
```

### 9.3 `ProductSearchRequest.java` (신규)

```java
package jymusic.jym_catalog_service.dto.request;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSearchRequest {

    private String keyword;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 12;

    @Builder.Default
    private String sort = "createdAt,desc";

    /** MyBatis XML에서 OFFSET 계산 시 사용 */
    public int getOffset() {
        return page * size;
    }
}
```

> `getOffset()` 메서드를 통해 XML에서 `#{req.offset}`으로 바로 접근 가능. 별도 계산 없이 깔끔하게 페이징 처리.

---

## 10. 서비스 레이어 변경

### 10.1 `ProductService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;       // CUD 전용 (기존 유지)
    private final CategoryRepository categoryRepository;     // CUD 전용 (기존 유지)
    private final ProductReadMapper productReadMapper;       // [NEW] 읽기 전용 (MyBatis)

    @Value("${spring.cloud.aws.s3.base-url}")
    private String s3BaseUrl;

    // ── GET /api/v1/products — MyBatis로 전환 ──
    public ProductListResponse getProducts(int page, int size, Long categoryId) {
        int offset = page * size;

        List<ProductSummaryResponse> content =
                productReadMapper.findProducts(categoryId, offset, size);
        content.forEach(dto -> dto.applyS3BaseUrl(s3BaseUrl));

        long totalElements = productReadMapper.countProducts(categoryId);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ProductListResponse.builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    // ── GET /api/v1/products/{id} — MyBatis로 전환 ──
    public ProductDetailResponse getProduct(Long id) {
        ProductDetailResponse response = productReadMapper.findProductById(id);

        if (response == null) {
            throw new GlobalException(
                    "상품을 찾을 수 없습니다.", "ERR_PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND);
        }

        response.applyS3BaseUrl(s3BaseUrl);
        return response;
    }

    // ── [NEW] GET /api/v1/products/search — MyBatis 동적 SQL ──
    public ProductListResponse searchProducts(ProductSearchRequest request) {
        List<ProductSummaryResponse> content =
                productReadMapper.searchProducts(request);
        content.forEach(dto -> dto.applyS3BaseUrl(s3BaseUrl));

        long totalElements = productReadMapper.countSearchProducts(request);
        int totalPages = (int) Math.ceil((double) totalElements / request.getSize());

        return ProductListResponse.builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    // ── CUD 메서드는 기존 JPA 유지 (변경 없음) ──
    @Transactional
    public ProductDetailResponse createProduct(ProductCreateRequest request) { /* 기존 코드 */ }

    @Transactional
    public ProductDetailResponse updateProduct(Long id, ProductUpdateRequest request) { /* 기존 코드 */ }

    @Transactional
    public void deleteProduct(Long id) { /* 기존 코드 */ }
}
```

### 10.2 `CategoryService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;    // CUD 전용 (기존 유지)
    private final CategoryReadMapper categoryReadMapper;    // [NEW] 읽기 전용 (MyBatis)

    // ── GET /api/v1/categories — MyBatis로 전환 ──
    public List<CategoryResponse> getAllCategories() {
        return categoryReadMapper.findAllCategories();
    }

    // ── CUD 메서드는 기존 JPA 유지 (변경 없음) ──
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) { /* 기존 코드 */ }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) { /* 기존 코드 */ }

    @Transactional
    public void deleteCategory(Long id) { /* 기존 코드 */ }
}
```

---

## 11. 컨트롤러 변경

### 11.1 `ProductController.java`

```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // 기존 엔드포인트 (내부 구현만 MyBatis로 전환, 시그니처 동일)
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

    // [NEW] 검색 엔드포인트
    @GetMapping("/search")
    public ResponseEntity<ProductListResponse> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        ProductSearchRequest request = ProductSearchRequest.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .page(page)
                .size(size)
                .sort(sort)
                .build();

        return ResponseEntity.ok(productService.searchProducts(request));
    }

    // CUD 엔드포인트 기존 유지 (변경 없음)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> createProduct(
            @Valid @RequestBody ProductCreateRequest request) { /* 기존 코드 */ }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) { /* 기존 코드 */ }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) { /* 기존 코드 */ }
}
```

---

## 12. 아키텍처 다이어그램 — 읽기/쓰기 분리

```
┌─────────────────────────────────────────────────────────────┐
│                     ProductService                          │
│                                                             │
│  ┌─── 읽기 (GET) ── MyBatis ──────────────────────────┐     │
│  │                                                     │     │
│  │  getProducts()    ──→  ProductReadMapper.xml         │     │
│  │  getProduct()     ──→  ProductReadMapper.xml         │     │
│  │  searchProducts() ──→  ProductReadMapper.xml (동적SQL)│     │
│  └─────────────────────────────────────────────────────┘     │
│                                                             │
│  ┌─── 쓰기 (CUD) ── JPA ──────────────────────────────┐     │
│  │                                                     │     │
│  │  createProduct()  ──→  ProductRepository (JPA)      │     │
│  │  updateProduct()  ──→  ProductRepository (JPA)      │     │
│  │  deleteProduct()  ──→  ProductRepository (JPA)      │     │
│  └─────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    CategoryService                          │
│                                                             │
│  읽기: getAllCategories() ──→ CategoryReadMapper.xml (MyBatis)│
│  쓰기: create/update/delete ──→ CategoryRepository (JPA)   │
└─────────────────────────────────────────────────────────────┘
```

---

## 13. 단위 테스트 가이드

### 13.1 MyBatis 매퍼 테스트

```java
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductReadMapperTest {

    @Autowired
    private ProductReadMapper productReadMapper;

    @Test
    @DisplayName("카테고리 필터 없이 상품 목록 조회")
    void findProducts_noCategoryFilter() {
        List<ProductSummaryResponse> result =
                productReadMapper.findProducts(null, 0, 12);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("카테고리 필터 적용 상품 목록 조회")
    void findProducts_withCategoryFilter() {
        List<ProductSummaryResponse> result =
                productReadMapper.findProducts(1L, 0, 12);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 상품 ID 조회 시 null 반환")
    void findProductById_notFound_returnsNull() {
        ProductDetailResponse result = productReadMapper.findProductById(999L);
        assertThat(result).isNull();
    }
}
```

### 13.2 검색 매퍼 테스트

```java
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductSearchMapperTest {

    @Autowired
    private ProductReadMapper productReadMapper;

    @Test
    @DisplayName("키워드로 상품 검색 — 제목/아티스트 매칭")
    void searchProducts_byKeyword() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .keyword("Abbey")
                .page(0)
                .size(12)
                .build();

        List<ProductSummaryResponse> result = productReadMapper.searchProducts(request);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("가격 범위 필터 적용 검색")
    void searchProducts_byPriceRange() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .minPrice(new BigDecimal("10000"))
                .maxPrice(new BigDecimal("30000"))
                .page(0)
                .size(12)
                .build();

        List<ProductSummaryResponse> result = productReadMapper.searchProducts(request);
        long count = productReadMapper.countSearchProducts(request);
        assertThat(result).isNotNull();
        assertThat(count).isGreaterThanOrEqualTo(result.size());
    }

    @Test
    @DisplayName("조건 없는 검색 시 전체 판매 중 상품 반환")
    void searchProducts_emptyCondition() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .page(0)
                .size(12)
                .build();

        List<ProductSummaryResponse> result = productReadMapper.searchProducts(request);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("정렬 옵션 — 가격 낮은순")
    void searchProducts_sortByPriceAsc() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .sort("price,asc")
                .page(0)
                .size(12)
                .build();

        List<ProductSummaryResponse> result = productReadMapper.searchProducts(request);
        assertThat(result).isNotNull();
    }
}
```

### 13.3 서비스 레이어 테스트

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceMyBatisTest {

    @InjectMocks ProductService productService;
    @Mock ProductReadMapper productReadMapper;

    @Test
    @DisplayName("MyBatis 상품 목록 조회 시 S3 URL 정상 조합")
    void getProducts_mybatis_appliesS3Url() {
        ProductSummaryResponse dto = new ProductSummaryResponse();
        given(productReadMapper.findProducts(null, 0, 12)).willReturn(List.of(dto));
        given(productReadMapper.countProducts(null)).willReturn(1L);

        ProductListResponse result = productService.getProducts(0, 12, null);

        assertThat(result.getContent()).hasSize(1);
        verify(productReadMapper).findProducts(null, 0, 12);
    }

    @Test
    @DisplayName("MyBatis 상품 상세 조회 — 존재하지 않으면 GlobalException")
    void getProduct_notFound_throwsException() {
        given(productReadMapper.findProductById(999L)).willReturn(null);

        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("MyBatis 검색 — 정상 응답")
    void searchProducts_returnsResult() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .keyword("Beatles")
                .page(0)
                .size(12)
                .build();

        given(productReadMapper.searchProducts(request)).willReturn(List.of());
        given(productReadMapper.countSearchProducts(request)).willReturn(0L);

        ProductListResponse result = productService.searchProducts(request);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
```

---

## 14. OAS 변경사항

### 14.1 `openapi.yaml` 추가 엔드포인트

```yaml
  /api/v1/products/search:
    get:
      tags:
        - Products (Public)
      summary: 상품 검색
      description: 키워드, 카테고리, 가격 범위, 정렬 복합 조건 검색 (MyBatis 동적 SQL)
      operationId: searchProducts
      parameters:
        - name: keyword
          in: query
          schema:
            type: string
          description: 제목/아티스트 검색 키워드 (LIKE 검색)
        - name: categoryId
          in: query
          schema:
            type: integer
            format: int64
        - name: minPrice
          in: query
          schema:
            type: number
            format: decimal
        - name: maxPrice
          in: query
          schema:
            type: number
            format: decimal
        - name: page
          in: query
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 12
        - name: sort
          in: query
          schema:
            type: string
            default: "createdAt,desc"
            enum:
              - "createdAt,desc"
              - "price,asc"
              - "price,desc"
              - "title,asc"
      responses:
        '200':
          description: 검색 결과
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProductListResponse'
```

### 14.2 버전 이력

```yaml
info:
  version: 1.3.0
```

| 버전 | 날짜 | 내용 |
|---|---|---|
| 1.1.0 | 2026-03-10 | 초기 상품/카테고리/미디어 API 정의 |
| 1.2.0 | 2026-03-12 | 카테고리 관리자 CRUD 엔드포인트 추가 |
| **1.3.0** | **2026-03-27** | GET 엔드포인트 MyBatis 마이그레이션, 상품 검색 API 추가 |

---

## 15. 체크리스트

- [ ] `build.gradle` — `mybatis-spring-boot-starter` 의존성 추가
- [ ] `MyBatisConfig.java` — `@MapperScan` 설정
- [ ] `application.properties` — MyBatis mapper-locations, camelCase 설정 추가
- [ ] `ProductReadMapper.java` + `ProductReadMapper.xml` 작성
- [ ] `CategoryReadMapper.java` + `CategoryReadMapper.xml` 작성
- [ ] `ProductSearchRequest.java` DTO 신규 작성
- [ ] `ProductSummaryResponse` — `@NoArgsConstructor`, `imageKey` 필드, `applyS3BaseUrl()` 추가
- [ ] `ProductDetailResponse` — `@NoArgsConstructor`, `imageKey` 필드, `applyS3BaseUrl()` 추가
- [ ] `ProductService` — GET 메서드 MyBatis 전환, `searchProducts()` 추가
- [ ] `CategoryService` — `getAllCategories()` MyBatis 전환
- [ ] `ProductController` — `GET /api/v1/products/search` 엔드포인트 추가
- [ ] SecurityConfig 확인 (기존 패턴으로 `/search` 커버됨)
- [ ] 단위 테스트 작성 (매퍼 + 서비스)
- [ ] `openapi.yaml` v1.3.0 업데이트
