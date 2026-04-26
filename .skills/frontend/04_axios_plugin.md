# Axios Plugin (Nuxt 3/4 — JWT 인증 + 토큰 갱신)

> **참조**: `.skills/_common/00_project_context.md`

## 개요
Nuxt 플러그인으로 Axios 인스턴스를 생성하고 전역(`$axios`)으로 주입한다.
Access Token 자동 주입, 401 응답 시 Refresh Token 갱신 & 요청 큐 패턴을 포함한다.

## 핵심 설계

| 항목 | 설명 |
|------|------|
| 파일 위치 | `app/plugins/01.axios.ts` |
| Base URL | `useRuntimeConfig().public.apiBase` (환경변수 오버라이드: `NUXT_PUBLIC_API_BASE`) |
| 인증 토큰 | Pinia `useAuthStore().accessToken` 에서 가져옴 |
| SSR 스킵 | `import.meta.server` 체크 → SSR 환경에서는 인터셉터 로직 바이패스 |
| 토큰 갱신 | 401 → `/api/v1/auth/refresh-token` POST → 큐에 대기 중인 요청 일괄 재시도 |

## 환경변수 설정

### nuxt.config.ts (기본값 설정)
```typescript
export default defineNuxtConfig({
  runtimeConfig: {
    public: {
      apiBase: 'http://localhost:8080',  // 개발 기본값
    },
  },
})
```

### 환경변수로 오버라이드
```env
# .env (개발)
NUXT_PUBLIC_API_BASE=http://localhost:8080

# 프로덕션
NUXT_PUBLIC_API_BASE=https://api.jymusic.com
```

> **중요**: Nuxt는 `NUXT_PUBLIC_` 접두사가 붙은 환경변수를 `runtimeConfig.public.*`에 자동 매핑한다.

## 전체 구현 코드

```typescript
// app/plugins/01.axios.ts
import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '~/stores/auth'

export default defineNuxtPlugin((nuxtApp) => {
  const config = useRuntimeConfig()

  const instance: AxiosInstance = axios.create({
    baseURL: config.public.apiBase as string,
    withCredentials: true,
  })

  // ── 요청 인터셉터: Access Token 자동 주입 ──────────────────────────
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    if (import.meta.server) return config
    const authStore = useAuthStore()
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }
    return config
  })

  // ── 응답 인터셉터: 401 처리 (토큰 갱신 큐 패턴) ───────────────────
  let isRefreshing = false
  let isLoggingOut = false
  let failedQueue: Array<{
    resolve: (token: string) => void
    reject: (err: unknown) => void
  }> = []

  const processQueue = (error: unknown, token: string | null = null) => {
    failedQueue.forEach(({ resolve, reject }) => {
      if (error) reject(error)
      else if (token) resolve(token)
    })
    failedQueue = []
  }

  const handleLogout = async () => {
    if (isLoggingOut) return
    isLoggingOut = true
    const authStore = useAuthStore()
    authStore.clearAuth()
    isRefreshing = false
    failedQueue = []
    isLoggingOut = false
    await nuxtApp.runWithContext(() => navigateTo('/auth/login'))
  }

  instance.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (import.meta.server) return Promise.reject(error)

      const originalRequest = error.config

      // refresh-token 요청 자체가 실패하면 → 강제 로그아웃
      if (originalRequest?.url?.includes('/auth/refresh-token')) {
        await handleLogout()
        return Promise.reject(error)
      }

      if (error.response?.status === 401 && !originalRequest?._retry) {
        // 이미 갱신 중이면 큐에 추가하고 대기
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({
              resolve: (token) => {
                originalRequest.headers.Authorization = `Bearer ${token}`
                resolve(instance(originalRequest))
              },
              reject,
            })
          })
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          const response = await instance.post<{ accessToken: string }>(
            '/api/v1/auth/refresh-token',
          )
          const newToken = response.data.accessToken

          const authStore = useAuthStore()
          authStore.setToken(newToken)

          processQueue(null, newToken)
          isRefreshing = false

          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return instance(originalRequest)
        } catch (refreshError) {
          processQueue(refreshError, null)
          isRefreshing = false
          await handleLogout()
          return Promise.reject(refreshError)
        }
      }

      return Promise.reject(error)
    },
  )

  return {
    provide: {
      axios: instance,
    },
  }
})
```

## 사용법

### Composable에서 사용
```typescript
// app/composables/useXxx.ts
export function useXxx() {
  const { $axios } = useNuxtApp()

  const fetchItems = async () => {
    const { data } = await $axios.get('/api/v1/xxx')
    return data
  }

  return { fetchItems }
}
```

### 컴포넌트에서 직접 사용 (비권장 — composable 분리 권장)
```vue
<script setup lang="ts">
const { $axios } = useNuxtApp()
const { data } = await $axios.get('/api/v1/items')
</script>
```

## 아키텍처 흐름

```
[컴포넌트/Composable]
  ↓ $axios.get/post(...)
[요청 인터셉터]
  → SSR이면 그대로 통과
  → CSR이면 authStore.accessToken을 Authorization 헤더에 주입
[API Gateway / Backend]
  ↓ 응답
[응답 인터셉터]
  → 200: 그대로 반환
  → 401 (첫 번째):
      1. isRefreshing = true
      2. POST /api/v1/auth/refresh-token (쿠키 기반)
      3. 성공 → 새 토큰으로 원본 요청 재시도 + 큐 처리
      4. 실패 → 로그아웃 + /auth/login 리다이렉트
  → 401 (갱신 중): failedQueue에 추가 → 갱신 완료 시 일괄 재시도
  → refresh-token 엔드포인트 자체 실패 → 즉시 로그아웃
```

## 커스터마이즈 포인트

| 상황 | 변경 위치 |
|------|-----------|
| API 서버 주소 변경 | `.env`의 `NUXT_PUBLIC_API_BASE` 또는 `nuxt.config.ts`의 `runtimeConfig.public.apiBase` |
| Refresh Token 엔드포인트 변경 | 응답 인터셉터의 `/api/v1/auth/refresh-token` 경로 |
| 로그아웃 후 리다이렉트 경로 변경 | `handleLogout` 함수의 `navigateTo('/auth/login')` |
| 토큰 저장소 변경 (Pinia → cookie 등) | 요청 인터셉터의 `useAuthStore()` 호출 부분 |
| 타임아웃 추가 | `axios.create({ timeout: 10000 })` |

## 체크리스트
- [ ] `nuxt.config.ts`에 `runtimeConfig.public.apiBase` 정의됨
- [ ] `.env`에 `NUXT_PUBLIC_API_BASE` 설정됨 (프로덕션)
- [ ] `useAuthStore()`에 `accessToken`, `setToken()`, `clearAuth()` 존재
- [ ] `withCredentials: true` — Refresh Token이 HttpOnly 쿠키 기반일 때 필수
- [ ] SSR 환경 바이패스 (`import.meta.server`) 처리됨
- [ ] 토큰 갱신 실패 시 로그아웃 + 리다이렉트 동작 확인

## 관련 스킬
- `frontend/01_new_page.md` — 페이지에서 composable 사용 패턴
- `frontend/02_composable.md` — `$axios` 활용 composable 작성법
- `backend/01_new_api_endpoint.md` — 백엔드 API 엔드포인트 (인증 포함)
