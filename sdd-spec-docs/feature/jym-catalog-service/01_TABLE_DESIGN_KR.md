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
| `thumbnail_url` | VARCHAR(255) | | 목록용 썸네일 이미지 주소 |
| `image_url` | VARCHAR(255) | | 상세용 원본 이미지 주소 |
| `is_available` | BOOLEAN | 기본값: TRUE | 판매 가능 여부 |
| `created_at` | DATETIME | 기본값: 현재 시간 | 레코드 생성 일시 |
| `updated_at` | DATETIME | 기본값: 현재 시간 | 최종 수정 일시 |

## 4. 구현 참고 사항
- **Lombok**: 엔티티 클래스에서 `@Builder`, `@Getter`, `@NoArgsConstructor`를 적극 활용합니다.
- **금액 처리**: 금액의 정확성을 위해 Java 코드에서는 `BigDecimal` 타입을 사용합니다.
- **관계**: `Product` 엔티티는 `Category` 엔티티와 다대일(Many-to-One) 관계를 가집니다.
