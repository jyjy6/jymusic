import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface AuthUser {
  id: number
  username: string
  nickname: string
  role: string
  email?: string
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const user = ref<AuthUser | null>(null)

  const isLoggedIn = computed(() => accessToken.value !== null)

  const setAuth = (token: string, authUser: AuthUser) => {
    accessToken.value = token
    user.value = authUser
  }

  const setToken = (token: string) => {
    accessToken.value = token
  }

  const clearAuth = () => {
    accessToken.value = null
    user.value = null
  }

  return {
    accessToken,
    user,
    isLoggedIn,
    setAuth,
    setToken,
    clearAuth,
  }
})
