<template>
  <div class="min-h-screen bg-gray-50">
    <AppToast />
    <nav class="bg-white border-b border-gray-200 shadow-sm sticky top-0 z-50">
      <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <NuxtLink to="/" class="flex items-center gap-2 group">
            <span class="text-2xl">🎵</span>
            <span class="text-xl font-bold text-indigo-600 group-hover:text-indigo-700 transition-colors">
              Jymusic
            </span>
          </NuxtLink>

          <div class="flex items-center gap-3">
            <NuxtLink
              to="/products"
              class="text-sm text-gray-600 hover:text-indigo-600 font-medium transition-colors px-2 py-1 rounded-md hover:bg-indigo-50"
            >
              상품
            </NuxtLink>
            <NuxtLink
              to="/admin/products"
              class="text-sm text-indigo-600 font-semibold transition-colors px-2 py-1 rounded-md bg-indigo-50"
            >
              관리
            </NuxtLink>
            <template v-if="authStore.isLoggedIn">
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
          </div>
        </div>
      </div>

      <!-- Admin sub-navigation -->
      <div class="border-t border-gray-100 bg-white">
        <div class="mx-auto flex max-w-6xl items-center gap-6 px-4 sm:px-6 lg:px-8">
          <NuxtLink
            to="/admin/products"
            :class="getTabClass('products')"
          >
            Products
          </NuxtLink>
          <NuxtLink
            to="/admin/products/new"
            :class="getTabClass('new')"
          >
            Add Product
          </NuxtLink>
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
import { useAuthStore } from '~/stores/auth'

const authStore = useAuthStore()
const { $axios } = useNuxtApp()
const route = useRoute()
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

const getTabClass = (type: 'products' | 'new') => {
  const isNewPage = route.path === '/admin/products/new'
  const isProductsPage = route.path === '/admin/products'
    || (route.path.startsWith('/admin/products/') && route.path.endsWith('/edit'))

  const isActive = type === 'new' ? isNewPage : isProductsPage

  return [
    'inline-flex h-11 items-center border-b-2 text-sm font-semibold transition-colors',
    isActive
      ? 'border-indigo-600 text-indigo-600'
      : 'border-transparent text-gray-500 hover:text-indigo-600',
  ]
}
</script>
