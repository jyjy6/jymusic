# 00_OAS_PLAN (Order Service)

## 1. Objective
Define the API contract for order creation, history tracking, and status updates in `jym-order-service`.

## 2. Core Endpoints
- `POST /api/v1/orders`: Place a new order.
- `GET /api/v1/orders`: Retrieve user's order history.
- `GET /api/v1/orders/{id}`: Detailed view of a specific order.

## 3. Tech Stack Requirements
- Backend: Spring Boot
- Database: MySQL (Order/OrderItem table)
- Error Pattern: `GlobalErrorHandler.GlobalException`
