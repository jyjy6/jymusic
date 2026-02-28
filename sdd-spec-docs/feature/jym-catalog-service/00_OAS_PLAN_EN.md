# 00_OAS_PLAN (Catalog Service)

## 1. Objective
Define the API contract for product browsing, detailed views, and category management in `jym-catalog-service`.

## 2. Core Endpoints
- `GET /api/v1/products`: List products with pagination.
- `GET /api/v1/products/{id}`: View product details.
- `GET /api/v1/categories`: List product categories.

## 3. Tech Stack Requirements
- Backend: Spring Boot
- Database: MySQL (Product/Category/Stock table)
- Error Pattern: `GlobalErrorHandler.GlobalException`
