# 01_TABLE_DESIGN (Catalog Service)

## 1. Overview
This document defines the database schema for `jym-catalog-service` based on the OpenAPI Specification. It manages products (albums) and categories.

## 2. Table: `categories`
Stores product categories such as music genres.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Increment | Unique identifier for category |
| `name` | VARCHAR(50) | Unique, Not Null | Category name (e.g., Rock, Pop) |
| `created_at` | DATETIME | Default: CURRENT_TIMESTAMP | Record creation timestamp |

## 3. Table: `products`
Stores music album information.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Increment | Unique identifier for product |
| `category_id` | BIGINT | FK (categories.id) | Reference to category |
| `title` | VARCHAR(100) | Not Null | Album title |
| `artist` | VARCHAR(100) | Not Null | Artist name |
| `description` | TEXT | | Detailed album description |
| `price` | DECIMAL(10,2) | Not Null | Product price |
| `stock_quantity` | INT | Not Null, Default: 0 | Current inventory count |
| `thumbnail_url` | VARCHAR(255) | | Small image for list view |
| `image_url` | VARCHAR(255) | | Large image for detail view |
| `is_available` | BOOLEAN | Default: TRUE | Sales status |
| `created_at` | DATETIME | Default: CURRENT_TIMESTAMP | Record creation timestamp |
| `updated_at` | DATETIME | Default: CURRENT_TIMESTAMP | Last record update timestamp |

## 4. Implementation Notes
- **Lombok**: Use `@Builder`, `@Getter`, and `@NoArgsConstructor` for all Entity classes.
- **Precision**: Use `BigDecimal` in Java for the `price` field to avoid rounding errors.
- **Relationship**: `Product` has a Many-to-One relationship with `Category`.
