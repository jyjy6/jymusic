<template>
  <div class="min-h-[calc(100vh-4rem)] flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md">
      <div class="bg-white rounded-2xl shadow-lg border border-gray-100 p-8">
        <!-- 헤더 -->
        <div class="text-center mb-8">
          <div class="text-5xl mb-3">🎵</div>
          <h1 class="text-2xl font-bold text-gray-900">회원가입</h1>
          <p class="text-gray-500 mt-1 text-sm">Jymusic와 함께 음악 여정을 시작하세요</p>
        </div>

        <!-- 성공 메시지 -->
        <div
          v-if="successMessage"
          class="flex items-center gap-2 text-green-700 text-sm bg-green-50 border border-green-200 px-4 py-3 rounded-lg mb-5"
        >
          <span>✅</span>
          <span>{{ successMessage }}</span>
        </div>

        <form @submit.prevent="handleRegister" class="space-y-5">
          <!-- 아이디 -->
          <div>
            <label for="username" class="block text-sm font-medium text-gray-700 mb-1.5">
              아이디 <span class="text-red-500">*</span>
            </label>
            <input
              id="username"
              v-model="form.username"
              type="text"
              required
              autocomplete="username"
              placeholder="로그인에 사용할 아이디"
              class="w-full px-4 py-2.5 rounded-lg border border-gray-300 text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-colors"
            />
          </div>

          <!-- 비밀번호 -->
          <div>
            <label for="password" class="block text-sm font-medium text-gray-700 mb-1.5">
              비밀번호 <span class="text-red-500">*</span>
            </label>
            <input
              id="password"
              v-model="form.password"
              type="password"
              required
              minlength="4"
              autocomplete="new-password"
              placeholder="최소 4자 이상"
              class="w-full px-4 py-2.5 rounded-lg border border-gray-300 text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-colors"
            />
          </div>

          <!-- 닉네임 -->
          <div>
            <label for="nickname" class="block text-sm font-medium text-gray-700 mb-1.5">
              닉네임 <span class="text-red-500">*</span>
            </label>
            <input
              id="nickname"
              v-model="form.nickname"
              type="text"
              required
              placeholder="서비스 내 표시될 이름"
              class="w-full px-4 py-2.5 rounded-lg border border-gray-300 text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-colors"
            />
          </div>

          <!-- 이메일 (선택) -->
          <div>
            <label for="email" class="block text-sm font-medium text-gray-700 mb-1.5">
              이메일
              <span class="text-gray-400 font-normal text-xs ml-1">(선택)</span>
            </label>
            <input
              id="email"
              v-model="form.email"
              type="email"
              autocomplete="email"
              placeholder="example@email.com"
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

          <!-- 가입하기 버튼 -->
          <button
            type="submit"
            :disabled="loading"
            class="w-full py-3 bg-indigo-600 text-white font-semibold rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
          >
            <span v-if="loading" class="inline-block w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            <span>{{ loading ? '처리 중...' : '가입하기' }}</span>
          </button>
        </form>

        <p class="text-center text-sm text-gray-500 mt-6">
          이미 계정이 있으신가요?
          <NuxtLink to="/auth/login" class="text-indigo-600 hover:text-indigo-700 hover:underline font-medium">
            로그인
          </NuxtLink>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import { useAuthStore } from '~/stores/auth'

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

const { $axios } = useNuxtApp()

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
})
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const handleRegister = async () => {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''

  const payload = {
    username: form.username,
    password: form.password,
    nickname: form.nickname,
    ...(form.email ? { email: form.email } : {}),
  }

  try {
    await ($axios as AxiosInstance).post('/api/v1/auth/register', payload)
    successMessage.value = '회원가입 완료! 로그인 페이지로 이동합니다...'
    setTimeout(() => navigateTo('/auth/login'), 1500)
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } } }
    errorMessage.value = e.response?.data?.message ?? '회원가입 중 오류가 발생했습니다.'
  } finally {
    loading.value = false
  }
}
</script>
