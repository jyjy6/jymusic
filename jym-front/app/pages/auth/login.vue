<template>
  <div class="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md">
      <div class="bg-white rounded-2xl shadow-lg border border-gray-100 p-8">
        <!-- 헤더 -->
        <div class="text-center mb-8">
          <div class="text-5xl mb-3">🎵</div>
          <h1 class="text-2xl font-bold text-gray-900">로그인</h1>
          <p class="text-gray-500 mt-1 text-sm">Jymusic에 오신 것을 환영합니다</p>
        </div>

        <form @submit.prevent="handleLogin" class="space-y-5">
          <!-- 아이디 -->
          <div>
            <label for="username" class="block text-sm font-medium text-gray-700 mb-1.5">
              아이디
            </label>
            <input
              id="username"
              v-model="form.username"
              type="text"
              required
              autocomplete="username"
              placeholder="아이디를 입력하세요"
              class="w-full px-4 py-2.5 rounded-lg border border-gray-300 text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-colors"
            />
          </div>

          <!-- 비밀번호 -->
          <div>
            <label for="password" class="block text-sm font-medium text-gray-700 mb-1.5">
              비밀번호
            </label>
            <input
              id="password"
              v-model="form.password"
              type="password"
              required
              autocomplete="current-password"
              placeholder="비밀번호를 입력하세요"
              class="w-full px-4 py-2.5 rounded-lg border border-gray-300 text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-colors"
            />
          </div>

          <!-- 에러 메시지 -->
          <div
            v-if="errorMessage"
            class="flex items-center gap-2 text-red-600 text-sm bg-red-50 border border-red-200 px-4 py-3 rounded-lg"
          >
            <span>⚠️</span>
            <span>{{ errorMessage }}</span>
          </div>

          <!-- 로그인 버튼 -->
          <button
            type="submit"
            :disabled="loading"
            class="w-full py-3 bg-indigo-600 text-white font-semibold rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
          >
            <span v-if="loading" class="inline-block w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            <span>{{ loading ? '로그인 중...' : '로그인' }}</span>
          </button>
        </form>

        <!-- 소셜 로그인 -->
        <SocialLoginButtons />

        <p class="text-center text-sm text-gray-500 mt-6">
          아직 계정이 없으신가요?
          <NuxtLink to="/auth/register" class="text-indigo-600 hover:text-indigo-700 hover:underline font-medium">
            회원가입
          </NuxtLink>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import { useAuthStore, type AuthUser } from '~/stores/auth'
import SocialLoginButtons from '~/components/auth/SocialLoginButtons.vue'

definePageMeta({
  layout: 'default',
  middleware: [
    () => {
      if (import.meta.client) {
        const authStore = useAuthStore()
        if (authStore.isLoggedIn) return navigateTo('/me')
      }
    },
  ],
})

const authStore = useAuthStore()
const { $axios } = useNuxtApp()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMessage = ref('')

const handleLogin = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const loginRes = await ($axios as AxiosInstance).post<{ accessToken: string }>(
      '/api/v1/auth/login',
      form,
    )
    const token = loginRes.data.accessToken

    const profileRes = await ($axios as AxiosInstance).get<AuthUser>('/api/v1/members/me', {
      headers: { Authorization: `Bearer ${token}` },
    })

    authStore.setAuth(token, profileRes.data)
    await navigateTo('/me')
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } } }
    errorMessage.value = e.response?.data?.message ?? '아이디 또는 비밀번호가 올바르지 않습니다.'
  } finally {
    loading.value = false
  }
}
</script>
