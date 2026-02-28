# 01_TABLE_DESIGN (Member & Auth Service)

## 1. Overview
This document defines the database schema for `jym-member-auth-service` based on the OpenAPI Specification.

## 2. Table: `members`
Stores user account information and authentication details.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Increment | Unique identifier for each user |
| `username` | VARCHAR(50) | Unique, Not Null | Unique login identifier (ID) |
| `password` | VARCHAR(255) | | BCrypt hashed password (can be NULL for social login) |
| `email` | VARCHAR(100) | | User contact email (Optional) |
| `nickname` | VARCHAR(50) | Not Null | Display name used in the service |
| `role` | VARCHAR(20) | Not Null | User authority (ROLE_USER, ROLE_ADMIN) |
| `auth_provider` | VARCHAR(20) | Not Null | Auth source (LOCAL, GOOGLE, NAVER, KAKAO) |
| `provider_id` | VARCHAR(255) | | Unique identifier from the social provider |
| `is_active` | BOOLEAN | Default: TRUE | Account status (Active/Suspended) |
| `created_at` | DATETIME | Default: CURRENT_TIMESTAMP | Record creation timestamp |
| `updated_at` | DATETIME | Default: CURRENT_TIMESTAMP | Last record update timestamp |

## 3. Implementation Notes
- **Lombok**: Use `@Builder`, `@Getter`, and `@NoArgsConstructor` for the Entity class.
- **Security**: Never store raw passwords; use Spring Security's `PasswordEncoder`.
- **Indexing**: Unique index on `email` and `username` for fast lookups.
