import { useUiToast } from '~/composables/useUiToast'
import { useAuthStore } from '~/stores/auth'

export default defineNuxtRouteMiddleware(() => {
  if (import.meta.server) return

  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    return navigateTo('/auth/login')
  }

  if (authStore.user?.role !== 'ROLE_ADMIN') {
    const { showToast } = useUiToast()
    showToast('Access denied.', 'warning')
    return navigateTo('/')
  }
})
