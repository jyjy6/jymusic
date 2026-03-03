# jym-front: Detailed Page Design

---

## 1. Main Page `/`

**File**: `pages/index.vue`

### UI Layout
- Hero section: service name (Jymusic), short tagline
- CTA button: "Get Started" → navigate to `/auth/register`
- If logged in: "View My Profile" → navigate to `/me`

### Notes
- No authentication required; accessible to everyone

---

## 2. Register Page `/auth/register`

**File**: `pages/auth/register.vue`

### Connected API
`POST /api/v1/auth/register`

### Request Body
```json
{
  "username": "string",
  "password": "string (min 4 chars)",
  "nickname": "string",
  "email": "string (optional)"
}
```

### Success Response `201`
→ Redirect to `/auth/login` + success toast message

### UI Layout
| Field | Type | Validation |
|---|---|---|
| Username | text | Required |
| Password | password | Required, min 4 chars |
| Nickname | text | Required |
| Email | email | Optional |
| Register button | submit | - |

### Error Handling
- `400` → display `message` field inline below the form

---

## 3. Login Page `/auth/login`

**File**: `pages/auth/login.vue`

### Connected API
`POST /api/v1/auth/login`

### Request Body
```json
{ "username": "string", "password": "string" }
```

### Success Response `200`
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer" }
```
- `accessToken` → stored in Pinia `useAuthStore`
- Refresh Token → automatically set by server as HttpOnly Cookie
- → redirect to `/me`

### UI Layout
| Field | Type | Validation |
|---|---|---|
| Username | text | Required |
| Password | password | Required |
| Login button | submit | - |
| Register link | link | `/auth/register` |

### Error Handling
- `401` → display "Invalid username or password." inline

---

## 4. My Profile Page `/me`

**File**: `pages/me.vue`

### Connected API
`GET /api/v1/members/me`
- Header: `Authorization: Bearer <accessToken>` (auto-injected by Axios interceptor)

### Success Response `200`
```json
{
  "id": 1,
  "username": "hong",
  "nickname": "Hong",
  "email": "hong@example.com",
  "role": "ROLE_USER"
}
```

### UI Layout
- Profile card: display nickname, username, email, role
- Logout button

### Logout Flow
```
POST /api/v1/auth/logout  (Bearer header + cookie auto-sent)
  → Regardless of success/failure: clear Pinia store (clearAuth())
  → navigate to `/`
```

### Error Handling
- `401` → redirect to `/auth/login` (handled in advance by middleware)

---

## 5. Common Components / Utilities

### `plugins/axios.ts`

**Request Interceptor**
- In SSR environment (`import.meta.server`): pass through without token injection
- In client environment: inject `accessToken` from Pinia store as `Authorization: Bearer <token>` header

**Response Interceptor - 401 Handling (Token Refresh Queue Pattern)**

```
Receive 401 response
  ├─ If the refresh-token request itself returns 401 → force logout
  ├─ If already refreshing (isRefreshing = true) → add to failedQueue and wait
  └─ If not refreshing:
        isRefreshing = true
        POST /api/v1/auth/refresh-token (cookie auto-sent)
          ├─ Success: new accessToken → save to Pinia store
          │           retry all requests in failedQueue with new token
          │           retry original request
          └─ Failure: reject all requests in failedQueue
                      force logout (clear store + navigate to /auth/login)
```

Module-level variables for preventing duplicate refreshes:
- `isRefreshing: boolean`
- `failedQueue: Array<{ resolve, reject }>`
- `isLoggingOut: boolean` (prevents duplicate logout)

**Axios Global Settings**
- `baseURL`: `http://localhost:8080`
- `withCredentials: true` (auto-send Refresh Token Cookie)

---

### `stores/auth.ts` (Pinia) — Current Definition
```typescript
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const user = ref<AuthUser | null>(null)

  const isLoggedIn = computed(() => accessToken.value !== null)

  const setAuth = (token: string, authUser: AuthUser) => { ... }
  const setToken = (token: string) => { ... }
  const clearAuth = () => { ... }

  return { accessToken, user, isLoggedIn, setAuth, setToken, clearAuth }
})
```
> **Architecture compliance**: Composition API style required. Options API (`state`, `getters`, `actions` objects) is prohibited.

---

### `middleware/auth.ts`
- On entry to auth-required pages like `/me`, check `isLoggedIn`
- If `false`, redirect to `/auth/login`
