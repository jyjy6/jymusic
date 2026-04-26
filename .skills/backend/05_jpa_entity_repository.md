# JPA 엔티티 / 리포지토리 설계

> **참조**: `.skills/_common/00_project_context.md`

## 개요
JPA 엔티티, Spring Data JPA 리포지토리, QueryDSL Custom 리포지토리 설계 표준.

## 입력
- 엔티티명, 필드 목록, 연관관계, 필요한 조회 쿼리

## 1. BaseTimeEntity (공통 상속)

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
> `@EnableJpaAuditing`이 JpaConfig에 선언되어야 합니다.

## 2. 엔티티 패턴

```java
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 기본 생성자
@Builder
@AllArgsConstructor
public class Review extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    // ── 연관관계 예시 ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReviewImage> images = new ArrayList<>();

    // ── 도메인 메서드 (상태 변경 로직은 엔티티 안에) ──
    public void updateContent(String content, int rating) {
        this.content = content;
        this.rating = rating;
    }
}
```

### 핵심 규칙
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` — JPA용, 외부 직접 생성 방지
- `@Builder` + `@AllArgsConstructor` — Builder 패턴으로만 생성
- `@Builder.Default` — 컬렉션 필드에 기본값 설정
- `FetchType.LAZY` — `@ManyToOne` 연관관계 기본 (N+1 방지)
- 상태 전이 로직은 엔티티 도메인 메서드로 캡슐화 (Order의 `transitionTo()` 참고)

### Enum 타입
```java
@Enumerated(EnumType.STRING)  // ← 반드시 STRING (ORDINAL 금지)
@Column(nullable = false, length = 20)
private ReviewStatus status;
```

## 3. JpaRepository 기본

```java
public interface ReviewRepository extends JpaRepository<Review, Long>, ReviewRepositoryCustom {
    List<Review> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<Review> findAllByProductId(Long productId);
    Optional<Review> findByMemberIdAndProductId(Long memberId, Long productId);
}
```

## 4. QueryDSL Custom Repository

### 인터페이스
```java
public interface ReviewRepositoryCustom {
    Page<Review> searchReviews(ReviewSearchCriteria criteria, Pageable pageable);
}
```

### 구현체
```java
@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Review> searchReviews(ReviewSearchCriteria c, Pageable pageable) {
        QReview review = QReview.review;

        BooleanBuilder where = new BooleanBuilder()
                .and(productIdEq(c.productId()))
                .and(ratingGoe(c.minRating()));

        List<Review> content = queryFactory
                .selectFrom(review)
                .where(where)
                .orderBy(review.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(review.count())
                .from(review)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // ── BooleanExpression 헬퍼 (null 반환 → 조건 무시) ──
    private BooleanExpression productIdEq(Long productId) {
        return productId == null ? null : QReview.review.productId.eq(productId);
    }

    private BooleanExpression ratingGoe(Integer minRating) {
        return minRating == null ? null : QReview.review.rating.goe(minRating);
    }
}
```

## 5. MyBatis Mapper (Read 전용 복잡 쿼리)

CUD는 JPA, 복잡한 R(조회)은 MyBatis 조합:

```java
@Mapper
public interface ProductReadMapper {
    List<ProductSummaryResponse> searchProducts(ProductSearchRequest request);
}
```

```xml
<!-- src/main/resources/mapper/ProductReadMapper.xml -->
<mapper namespace="jymusic.jym_catalog_service.mapper.ProductReadMapper">
    <select id="searchProducts" resultType="...ProductSummaryResponse">
        SELECT p.id, p.title, p.artist, p.price, p.thumbnail_url
        FROM products p
        WHERE 1=1
        <if test="keyword != null">
            AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                 OR p.artist LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        ORDER BY p.created_at DESC
        LIMIT #{size} OFFSET #{offset}
    </select>
</mapper>
```

## 체크리스트
- [ ] `BaseTimeEntity` 상속
- [ ] `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 적용
- [ ] `@Builder` + `@AllArgsConstructor` 적용
- [ ] `@Enumerated(EnumType.STRING)` 사용 (ORDINAL 금지)
- [ ] 연관관계 `FetchType.LAZY` 기본
- [ ] 컬렉션 필드에 `@Builder.Default` 적용
- [ ] QueryDSL Custom 인터페이스명: `{Entity}RepositoryCustom`
- [ ] QueryDSL 구현체명: `{Entity}RepositoryImpl`
- [ ] BooleanExpression 헬퍼에서 null 반환 = 조건 무시

## 관련 스킬
- `backend/01_new_api_endpoint.md` — API와 연결
