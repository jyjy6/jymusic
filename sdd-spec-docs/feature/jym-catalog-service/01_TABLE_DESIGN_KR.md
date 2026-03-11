# 01_TABLE_DESIGN (상품 카탈로그 서비스)

## 1. 개요
본 문서는 `openapi.yaml` 명세를 바탕으로 `jym-catalog-service`의 데이터베이스 스키마를 정의합니다. 상품(앨범) 정보와 카테고리를 관리합니다.

## 2. 테이블: `categories`
음악 장르와 같은 상품 카테고리를 저장합니다.

| 컬럼명 | 데이터 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, 자동 증가 | 카테고리 고유 식별자 |
| `name` | VARCHAR(50) | Unique, Not Null | 카테고리 명 (예: Rock, Pop, Jazz) |
| `created_at` | DATETIME | 기본값: 현재 시간 | 레코드 생성 일시 |

## 3. 테이블: `products`
음악 앨범 상품 정보를 저장합니다.

| 컬럼명 | 데이터 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, 자동 증가 | 상품 고유 식별자 |
| `category_id` | BIGINT | FK (categories.id) | 카테고리 참조 외래 키 |
| `title` | VARCHAR(100) | Not Null | 앨범 제목 |
| `artist` | VARCHAR(100) | Not Null | 아티스트 명 |
| `description` | TEXT | | 상품 상세 설명 |
| `price` | DECIMAL(10,2) | Not Null | 상품 가격 |
| `stock_quantity` | INT | Not Null, 기본값: 0 | 현재 재고 수량 |
| `image_key` | VARCHAR(500) | Nullable | S3 objectKey (예: `products/uuid-abbey-road.jpg`) |
| `is_available` | BOOLEAN | Not Null, 기본값: TRUE | 판매 가능 여부 (논리 삭제 플래그) |
| `created_at` | DATETIME | Not Null, 기본값: 현재 시간 | 레코드 생성 일시 |
| `updated_at` | DATETIME | Not Null, 기본값: 현재 시간 | 최종 수정 일시 |

> **이미지 URL 조합 전략**: `image_key`에는 S3 objectKey만 저장합니다.
> `imageUrl` / `thumbnailUrl`은 서비스 레이어에서 `${cloud.aws.s3.base-url}/{image_key}` 형태로 조합하여 응답에 포함합니다.
> URL 직접 저장 방식 대비 S3 버킷 마이그레이션, CDN 교체 시 DB 변경 없이 설정만 변경 가능합니다.

## 4. DDL 예시

```sql
CREATE TABLE categories (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL UNIQUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE products (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    category_id    BIGINT,
    title          VARCHAR(100)   NOT NULL,
    artist         VARCHAR(100)   NOT NULL,
    description    TEXT,
    price          DECIMAL(10, 2) NOT NULL,
    stock_quantity INT            NOT NULL DEFAULT 0,
    image_key      VARCHAR(500),
    is_available   BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
```

## 5. 구현 참고 사항
- **Lombok**: 엔티티 클래스에서 `@Builder`, `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 활용합니다.
- **금액 처리**: 금액의 정확성을 위해 Java 코드에서는 `BigDecimal` 타입을 사용합니다.
- **관계**: `Product` 엔티티는 `Category` 엔티티와 다대일(Many-to-One, `FetchType.LAZY`) 관계를 가집니다.
- **논리 삭제**: `DELETE /api/v1/products/{id}` 호출 시 `is_available = false`로 변경합니다. 물리 삭제는 하지 않습니다.
- **조회 필터**: 공개 API는 `is_available = true`인 상품만 반환합니다.
