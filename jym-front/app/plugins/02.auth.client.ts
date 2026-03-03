import type { AxiosInstance } from 'axios'
import { useAuthStore, type AuthUser } from '~/stores/auth'

/**
 * 페이지 새로고침 후 사일런트 리프레시.
 * Pinia store에 토큰이 없을 때 Refresh Token Cookie로 Access Token을 자동 복구합니다.
 * 이 플러그인은 라우트 미들웨어보다 먼저 실행되므로,
 * 성공 시 /me 등 보호 페이지에서 로그인 리다이렉트가 발생하지 않습니다.
 */
export default defineNuxtPlugin(async (nuxtApp) => {
  const authStore = useAuthStore()

  if (authStore.isLoggedIn) return

  try {
    const axios = nuxtApp.$axios as AxiosInstance

    const refreshRes = await axios.post<{ accessToken: string }>(
      '/api/v1/auth/refresh-token',
    )
    const newToken = refreshRes.data.accessToken
    authStore.setToken(newToken)

    const profileRes = await axios.get<AuthUser>('/api/v1/members/me')
    authStore.setAuth(newToken, profileRes.data)
  } catch {
    // Refresh Token 없거나 만료 → 로그인 필요 (정상 케이스)
  }
})
