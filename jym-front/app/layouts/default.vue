<template>
  <div class="min-h-screen bg-gray-50">
    <AppToast />
    <nav class="bg-white border-b border-gray-200 shadow-sm sticky top-0 z-50">
      <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <!-- 로고 -->
          <NuxtLink to="/" class="flex items-center gap-2 group">
            <span class="text-2xl">🎵</span>
            <span class="text-xl font-bold text-indigo-600 group-hover:text-indigo-700 transition-colors">
              Jymusic
            </span>
          </NuxtLink>

          <!-- 네비게이션 -->
          <div class="flex items-center gap-3">
            <NuxtLink
              to="/products"
              class="text-sm text-gray-600 hover:text-indigo-600 font-medium transition-colors px-2 py-1 rounded-md hover:bg-indigo-50"
            >
              상품
            </NuxtLink>
            <CartIcon />
            <template v-if="authStore.isLoggedIn">
              <NuxtLink
                to="/me/orders"
                class="text-sm text-gray-600 hover:text-indigo-600 font-medium transition-colors px-2 py-1 rounded-md hover:bg-indigo-50"
              >
                내 주문
              </NuxtLink>
              <NuxtLink
                v-if="authStore.user?.role === 'ROLE_ADMIN'"
                to="/admin/products"
                class="text-sm text-gray-600 hover:text-indigo-600 font-medium transition-colors px-2 py-1 rounded-md hover:bg-indigo-50"
              >
                관리
              </NuxtLink>
              <NotificationBell />
              <span class="text-sm text-gray-500 hidden sm:block">
                <span class="font-semibold text-gray-800">{{ authStore.user?.nickname }}</span> 님
              </span>
              <NuxtLink
                to="/me"
                class="text-sm text-gray-600 hover:text-indigo-600 font-medium transition-colors px-2 py-1 rounded-md hover:bg-indigo-50"
              >
                프로필
              </NuxtLink>
              <button
                @click="handleLogout"
                :disabled="loggingOut"
                class="text-sm px-3 py-1.5 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200 disabled:opacity-50 transition-colors font-medium"
              >
                {{ loggingOut ? '...' : '로그아웃' }}
              </button>
            </template>
            <template v-else>
              <NuxtLink
                to="/auth/login"
                class="text-sm text-gray-600 hover:text-indigo-600 font-medium transition-colors px-2 py-1 rounded-md hover:bg-indigo-50"
              >
                로그인
              </NuxtLink>
              <NuxtLink
                to="/auth/register"
                class="text-sm px-4 py-2 rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 font-medium transition-colors"
              >
                회원가입
              </NuxtLink>
            </template>
          </div>
        </div>
      </div>
    </nav>

    <main>
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import AppToast from '~/components/AppToast.vue'
import CartIcon from '~/components/cart/CartIcon.vue'
import NotificationBell from '~/components/notifications/NotificationBell.vue'
import { useAuthStore } from '~/stores/auth'

const authStore = useAuthStore()
const { $axios } = useNuxtApp()
const loggingOut = ref(false)

const handleLogout = async () => {
  loggingOut.value = true
  try {
    await ($axios as AxiosInstance).post('/api/v1/auth/logout')
  } catch {
    // 로그아웃 API 실패해도 클라이언트 상태는 초기화
  } finally {
    authStore.clearAuth()
    loggingOut.value = false
    await navigateTo('/')
  }
}
</script>
