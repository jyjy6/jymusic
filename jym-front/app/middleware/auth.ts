import { useAuthStore } from "~/stores/auth"

const protectedRoutes = ['/cart', '/checkout']

export default defineNuxtRouteMiddleware((to) => {
  // SSR에서는 Pinia에 토큰이 없으므로 클라이언트에서만 체크합니다.
  // 02.auth.client.ts 플러그인이 사일런트 리프레시를 먼저 수행하므로
  // 클라이언트 첫 진입 시에도 올바르게 동작합니다.
  if (import.meta.server) return

  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    const isProtected = protectedRoutes.some((r) => to.path.startsWith(r))
    const redirectPath = isProtected ? to.fullPath : undefined
    return navigateTo(
      redirectPath ? `/auth/login?redirect=${encodeURIComponent(redirectPath)}` : '/auth/login',
    )
  }
})
