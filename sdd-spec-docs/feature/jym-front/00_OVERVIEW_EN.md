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

| Path | Page | Auth Required | Purpose |
|---|---|---|---|
| `/` | Main (Home) | No | Service intro, navigation |
| `/auth/register` | Register | No | POST `/api/v1/auth/register` |
| `/auth/login` | Login | No | POST `/api/v1/auth/login` |
| `/me` | My Profile | **Yes** | GET `/api/v1/members/me` |

## 4. Auth Flow

```
[Login Success]
  → Access Token → Pinia store (memory)
  → Refresh Token → HttpOnly Cookie (set automatically by server)

[Access to Protected Page]
  → No Access Token in Pinia → redirect to /auth/login

[API Request]
  → Axios interceptor auto-injects Authorization: Bearer <token>
```

## 5. Layout

- **Common Layout** (`layouts/default.vue`): includes top navigation bar
  - Logged in: display username + logout button
  - Not logged in: login / register links

## 6. Error Handling

- API response `401` → treated as Access Token expiry → redirect to login page
- API response `400` / `409` etc. → inline error message below the relevant form
- Common error response structure (based on `GlobalExceptionHandler`):
  ```json
  { "status": 400, "code": "ERR_XXX", "message": "..." }
  ```
