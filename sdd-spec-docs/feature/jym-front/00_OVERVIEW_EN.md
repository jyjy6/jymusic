# jym-front: Frontend Overview & Page Design

## 1. Tech Stack

| Item | Technology |
|---|---|
| Framework | Nuxt 4 (Vue 3, TypeScript) |
| Styling | Tailwind CSS |
| API Communication | Axios |
| State Management | Pinia |
| Authentication | JWT (Access Token: memory/Pinia, Refresh Token: HttpOnly Cookie) |

## 2. API Target

All API requests are sent exclusively to the **API Gateway** single endpoint.

```
http://localhost:8080  ←  jym-api-gateway
```

The frontend must never know the port of any downstream service (member-auth, catalog, etc.).

## 3. Page List

| Path | Page | Auth Required | Purpose | Spec File |
|---|---|---|---|---|
| `/` | Main (Home) | No | Service intro, navigation | `01_PAGES_AUTH_EN.md` |
| `/auth/register` | Register | No | POST `/api/v1/auth/register` | `01_PAGES_AUTH_EN.md` |
| `/auth/login` | Login | No | POST `/api/v1/auth/login` | `01_PAGES_AUTH_EN.md` |
| `/me` | My Profile | **Yes** | GET `/api/v1/members/me` | `01_PAGES_AUTH_EN.md` |
| `/products` | Product List | No | GET `/api/v1/products`, GET `/api/v1/categories` | `02_PAGES_CATALOG_EN.md` |
| `/products/[id]` | Product Detail | No | GET `/api/v1/products/{id}` | `02_PAGES_CATALOG_EN.md` |

## 4. Spec File Structure

| File | Domain |
|---|---|
| `01_PAGES_AUTH_EN.md` | Main, Register, Login, My Profile, Axios/Pinia/middleware common setup |
| `02_PAGES_CATALOG_EN.md` | Product list, Product detail, catalog types/composables/components |

## 5. Auth Flow

```
[Login Success]
  → Access Token → Pinia store (memory)
  → Refresh Token → HttpOnly Cookie (set automatically by server)

[Access to Protected Page]
  → No Access Token in Pinia → redirect to /auth/login

[API Request]
  → Axios interceptor auto-injects Authorization: Bearer <token>
```

## 6. Layout

- **Common Layout** (`layouts/default.vue`): includes top navigation bar
  - Logged in: display username + logout button
  - Not logged in: login / register links

## 7. Error Handling

- API response `401` → treated as Access Token expiry → redirect to login page
- API response `400` / `409` etc. → inline error message below the relevant form
- Common error response structure (based on `GlobalExceptionHandler`):
  ```json
  { "status": 400, "code": "ERR_XXX", "message": "..." }
  ```
