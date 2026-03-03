# jym-front: 페이지별 상세 설계

---

## 1. 메인 페이지 `/`

**파일**: `pages/index.vue`

### UI 구성
- 히어로 섹션: 서비스명(Jymusic), 짧은 소개 문구
- CTA 버튼: "지금 시작하기" → `/auth/register` 이동
- 로그인 상태면 "내 프로필 보기" → `/me` 이동

### 특이사항
- 인증 불필요, 누구나 접근 가능

---

## 2. 회원가입 페이지 `/auth/register`

**파일**: `pages/auth/register.vue`

### 연결 API
`POST /api/v1/auth/register`

### 요청 Body
```json
{
  "username": "string",
  "password": "string (min 4자)",
  "nickname": "string",
  "email": "string (optional)"
}
```

### 성공 응답 `201`
→ `/auth/login` 페이지로 리다이렉트 + 성공 토스트 메시지

### UI 구성
| 필드 | 타입 | 유효성 검사 |
|---|---|---|
| 아이디 (username) | text | 필수 |
| 비밀번호 (password) | password | 필수, 최소 4자 |
| 닉네임 (nickname) | text | 필수 |
| 이메일 (email) | email | 선택 |
| 가입하기 버튼 | submit | - |

### 에러 처리
- `400` → 폼 하단에 `message` 필드 인라인 표시

---

## 3. 로그인 페이지 `/auth/login`

**파일**: `pages/auth/login.vue`

### 연결 API
`POST /api/v1/auth/login`

### 요청 Body
```json
{ "username": "string", "password": "string" }
```

### 성공 응답 `200`
```json
{ "accessToken": "eyJ...", "tokenType": "Bearer" }
```
- `accessToken` → Pinia `useAuthStore`에 저장
- Refresh Token → 서버가 HttpOnly Cookie로 자동 설정
- → `/me` 페이지로 리다이렉트

### UI 구성
| 필드 | 타입 | 유효성 검사 |
|---|---|---|
| 아이디 (username) | text | 필수 |
| 비밀번호 (password) | password | 필수 |
| 로그인 버튼 | submit | - |
| 회원가입 링크 | link | `/auth/register` |

### 에러 처리
- `401` → "아이디 또는 비밀번호가 올바르지 않습니다." 인라인 표시

---

## 4. 내 프로필 페이지 `/me`

**파일**: `pages/me.vue`

### 연결 API
`GET /api/v1/members/me`
- Header: `Authorization: Bearer <accessToken>` (Axios 인터셉터 자동 주입)

### 성공 응답 `200`
```json
{
  "id": 1,
  "username": "hong",
  "nickname": "홍길동",
  "email": "hong@example.com",
  "role": "ROLE_USER"
}
```

### UI 구성
- 프로필 카드: 닉네임, 아이디, 이메일, 권한(role) 표시
- 로그아웃 버튼

### 로그아웃 흐름
```
POST /api/v1/auth/logout  (Bearer 헤더 + 쿠키 자동 전송)
  → 성공/실패 무관하게 Pinia store 초기화 (clearAuth())
  → `/` 이동
```

### 에러 처리
- `401` → `/auth/login` 리다이렉트 (미들웨어에서 사전 처리)

---

## 5. 공통 컴포넌트 / 유틸

### `stores/auth.ts` (Pinia)
```
state:
  accessToken: string | null
  user: { username, nickname, role } | null

actions:
  setToken(token)
  clearToken()
  isLoggedIn (getter)
```

### `plugins/axios.ts`

**요청 인터셉터**
- SSR 환경(`import.meta.server`)에서는 토큰 주입 없이 통과
- 클라이언트 환경: Pinia store의 `accessToken`을 `Authorization: Bearer <token>` 헤더에 주입

**응답 인터셉터 - 401 처리 (토큰 갱신 큐 패턴)**

```
401 응답 수신
  ├─ refresh-token 요청 자체가 401이면 → 강제 로그아웃
  ├─ 이미 갱신 중(isRefreshing = true)이면 → failedQueue에 추가하고 대기
  └─ 갱신 중이 아니면:
        isRefreshing = true
        POST /api/v1/auth/refresh-token (쿠키 자동 전송)
          ├─ 성공: 새 accessToken → Pinia store 저장
          │         failedQueue의 모든 요청 새 토큰으로 재시도
          │         원래 요청도 재시도
          └─ 실패: failedQueue 전부 reject
                    강제 로그아웃 (store 초기화 + /auth/login 이동)
```

중복 갱신 방지를 위한 모듈 레벨 변수:
- `isRefreshing: boolean`
- `failedQueue: Array<{ resolve, reject }>`
- `isLoggingOut: boolean` (로그아웃 중복 방지)

**axios 전역 설정**
- `baseURL`: `http://localhost:8080`
- `withCredentials: true` (Refresh Token Cookie 자동 전송)

---

### `stores/auth.ts` (Pinia)
```
state:
  accessToken: string | null
  user: { id, username, nickname, role } | null

actions:
  setAuth(token, user)   → 로그인 성공 시 호출
  clearAuth()            → 로그아웃 / 갱신 실패 시 호출
  setToken(token)        → 토큰 갱신 성공 시 호출

getters:
  isLoggedIn: accessToken !== null
```

---

### `middleware/auth.ts`
- `/me` 등 인증 필요 페이지 진입 시 `isLoggedIn` 확인
- `false`이면 `/auth/login` 리다이렉트
