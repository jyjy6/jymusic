<template>
  <div class="flex items-center justify-center min-h-[calc(100vh-4rem)] px-4">
    <div class="text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto" />
      <p class="mt-4 text-gray-600">로그인 처리 중...</p>
      <p v-if="errorMessage" class="mt-3 text-red-600 text-sm">{{ errorMessage }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import { useAuthStore, type AuthUser } from '~/stores/auth'

definePageMeta({
  layout: 'default',
})

const route = useRoute()
const authStore = useAuthStore()
const { $axios } = useNuxtApp()

const errorMessage = ref('')

onMounted(async () => {
  const accessToken = route.query.accessToken as string | undefined

  if (!accessToken) {
    errorMessage.value = '로그인에 실패했습니다.'
    setTimeout(() => navigateTo('/auth/login'), 1200)
    return
  }

  authStore.setToken(accessToken)

  // 보안: 브라우저 히스토리에 토큰 노출 방지
  if (import.meta.client) {
    window.history.replaceState({}, '', '/auth/oauth2/success')
  }

  try {
    const { data } = await ($axios as AxiosInstance).get<AuthUser>('/api/v1/members/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    authStore.setAuth(accessToken, data)
    await navigateTo('/me')
  } catch {
    authStore.clearAuth()
    errorMessage.value = '사용자 정보를 불러오지 못했습니다.'
    setTimeout(() => navigateTo('/auth/login'), 1200)
  }
})
</script>
