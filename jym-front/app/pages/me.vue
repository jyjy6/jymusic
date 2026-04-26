<template>
  <div class="max-w-2xl mx-auto px-4 py-12">
    <!-- 로딩 -->
    <div v-if="loading" class="flex flex-col items-center justify-center py-24 gap-4">
      <div class="w-10 h-10 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin" />
      <p class="text-gray-500 text-sm">프로필 불러오는 중...</p>
    </div>

    <!-- 에러 -->
    <div
      v-else-if="fetchError"
      class="text-center py-24"
    >
      <div class="text-5xl mb-4">😢</div>
      <p class="text-gray-600 mb-6">{{ fetchError }}</p>
      <NuxtLink
        to="/auth/login"
        class="px-6 py-2.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors font-medium"
      >
        로그인 페이지로
      </NuxtLink>
    </div>

    <!-- 프로필 카드 -->
    <div v-else-if="profile">
      <!-- 페이지 타이틀 -->
      <div class="mb-8">
        <h1 class="text-2xl font-bold text-gray-900">내 프로필</h1>
        <p class="text-gray-500 text-sm mt-1">계정 정보를 확인할 수 있습니다.</p>
      </div>

      <!-- 프로필 카드 -->
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
        <!-- 카드 헤더 -->
        <div class="bg-gradient-to-r from-indigo-600 to-purple-600 px-6 py-8 text-white">
          <div class="flex items-center gap-4">
            <div class="w-16 h-16 rounded-full bg-white/20 flex items-center justify-center text-3xl font-bold">
              {{ profile.nickname.charAt(0).toUpperCase() }}
            </div>
            <div>
              <p class="text-xl font-bold">{{ profile.nickname }}</p>
              <p class="text-indigo-200 text-sm">@{{ profile.username }}</p>
            </div>
            <!-- 권한 배지 -->
            <span
              :class="profile.role === 'ROLE_ADMIN'
                ? 'bg-yellow-400 text-yellow-900'
                : 'bg-indigo-500/40 text-white border border-white/30'"
              class="ml-auto text-xs px-2.5 py-1 rounded-full font-semibold"
            >
              {{ profile.role === 'ROLE_ADMIN' ? '관리자' : '일반 회원' }}
            </span>
          </div>
        </div>

        <!-- 카드 본문 -->
        <div class="divide-y divide-gray-100">
          <div
            v-for="item in profileItems"
            :key="item.label"
            class="flex items-center px-6 py-4"
          >
            <span class="text-xl mr-3">{{ item.icon }}</span>
            <div class="flex-1">
              <p class="text-xs text-gray-400 font-medium uppercase tracking-wide">{{ item.label }}</p>
              <p class="text-gray-900 font-medium mt-0.5">{{ item.value || '미등록' }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 액션 버튼 -->
      <div class="mt-6 flex justify-end gap-2">
        <NuxtLink
          to="/me/orders"
          class="flex items-center gap-2 px-5 py-2.5 rounded-lg border border-indigo-200 text-indigo-700 hover:bg-indigo-50 transition-colors font-medium text-sm"
        >
          내 주문 보러가기
        </NuxtLink>
        <button
          @click="handleLogout"
          :disabled="loggingOut"
          class="flex items-center gap-2 px-5 py-2.5 rounded-lg border border-gray-300 text-gray-600 hover:bg-gray-50 hover:border-gray-400 disabled:opacity-50 transition-colors font-medium text-sm"
        >
          <span v-if="loggingOut" class="inline-block w-3.5 h-3.5 border-2 border-gray-400 border-t-gray-600 rounded-full animate-spin" />
          <span>{{ loggingOut ? '로그아웃 중...' : '로그아웃' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import { useAuthStore, type AuthUser } from '~/stores/auth'

definePageMeta({
  layout: 'default',
  middleware: [
    () => {
      if (import.meta.server) return
      const authStore = useAuthStore()
      if (!authStore.isLoggedIn) return navigateTo('/auth/login')
    },
  ],
})

const authStore = useAuthStore()
const { $axios } = useNuxtApp()

const profile = ref<AuthUser | null>(null)
const loading = ref(true)
const fetchError = ref('')
const loggingOut = ref(false)

const profileItems = computed(() => [
  { icon: '👤', label: '아이디', value: profile.value?.username },
  { icon: '✉️', label: '이메일', value: profile.value?.email },
  { icon: '🔑', label: '권한', value: profile.value?.role },
])

onMounted(async () => {
  try {
    const res = await ($axios as AxiosInstance).get<AuthUser>('/api/v1/members/me')
    profile.value = res.data
    // 스토어 유저 정보도 최신화
    if (authStore.accessToken) {
      authStore.setAuth(authStore.accessToken, res.data)
    }
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } } }
    fetchError.value = e.response?.data?.message ?? '프로필 정보를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
})

const handleLogout = async () => {
  loggingOut.value = true
  try {
    await ($axios as AxiosInstance).post('/api/v1/auth/logout')
  } catch {
    // API 실패해도 클라이언트 상태 초기화
  } finally {
    authStore.clearAuth()
    loggingOut.value = false
    await navigateTo('/')
  }
}
</script>
