import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '~/stores/auth'

export default defineNuxtPlugin((nuxtApp) => {
  const instance: AxiosInstance = axios.create({
    baseURL: 'http://localhost:8080',
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
