#Complete

# 00_OAS_PLAN (API Gateway)

## 1. Objective

Define the routing strategy and global security filters for the `jym-api-gateway`. It acts as the single entry point for all client requests.

## 2. Routing Rules (Path Forwarding)

- `/api/v1/auth/**` -> `jym-member-auth-service`
- `/api/v1/members/**` -> `jym-member-auth-service`
- `/api/v1/products/**` -> `jym-catalog-service`
- `/api/v1/categories/**` -> `jym-catalog-service`
- `/api/v1/orders/**` -> `jym-order-service`
- `/api/v1/payments/**` -> `jym-payment-service`

## 3. Global Responsibilities

- **Authentication**: Verify JWT tokens in the header for protected routes.
- **CORS Configuration**: Manage Cross-Origin Resource Sharing for the Nuxt 4 frontend.
- **Error Handling**: Use `GlobalExceptionHandler` to return unified error responses when a downstream service is unreachable.
