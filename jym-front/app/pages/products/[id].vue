<template>
  <div class="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
    <button
      type="button"
      class="mb-6 inline-flex items-center gap-2 text-sm font-medium text-gray-500 transition hover:text-indigo-600"
      @click="handleBack"
    >
      <span aria-hidden="true">←</span>
      <span>Back to list</span>
    </button>

    <div
      v-if="fetchError && !isNotFound"
      class="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ fetchError }}
    </div>

    <div
      v-if="isLoading"
      class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]"
    >
      <div class="aspect-square animate-pulse rounded-3xl bg-gray-200" />
      <div class="space-y-4">
        <div class="h-8 w-2/3 animate-pulse rounded bg-gray-200" />
        <div class="h-6 w-1/3 animate-pulse rounded bg-gray-200" />
        <div class="h-px bg-gray-200" />
        <div class="h-8 w-1/4 animate-pulse rounded bg-gray-200" />
        <div class="h-6 w-1/3 animate-pulse rounded bg-gray-200" />
        <div class="h-12 w-40 animate-pulse rounded-xl bg-gray-200" />
      </div>
    </div>

    <div
      v-else-if="isNotFound"
      class="rounded-3xl border border-dashed border-gray-300 bg-white px-6 py-16 text-center"
    >
      <p class="text-2xl font-bold text-gray-900">Product not found.</p>
      <p class="mt-3 text-sm text-gray-500">요청하신 상품을 찾을 수 없습니다.</p>
      <NuxtLink
        to="/products"
        class="mt-6 inline-flex rounded-xl bg-indigo-600 px-5 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700"
      >
        상품 목록으로 돌아가기
      </NuxtLink>
    </div>

    <div
      v-else-if="product"
      class="space-y-10"
    >
      <section class="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
        <div class="overflow-hidden rounded-3xl border border-gray-200 bg-white shadow-sm">
          <div class="aspect-square bg-gray-100">
            <img
              :src="imageSrc"
              :alt="product.title"
              class="h-full w-full object-cover"
              @error="handleImageError"
            />
          </div>
        </div>

        <div class="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm sm:p-8">
          <p class="mb-2 text-sm font-semibold uppercase tracking-[0.2em] text-indigo-600">
            Album Detail
          </p>
          <h1 class="text-3xl font-bold text-gray-900">
            {{ product.title }}
          </h1>
          <p class="mt-2 text-lg text-gray-500">
            {{ product.artist }}
          </p>

          <div class="my-6 h-px bg-gray-200" />

          <p class="text-3xl font-semibold text-indigo-600">
            {{ formattedPrice }}
          </p>
          <p
            :class="stockClass"
            class="mt-4 text-sm font-semibold"
          >
            {{ stockLabel }}
          </p>

          <div class="mt-6">
            <QuantityInput
              v-model="quantity"
              :max="product.stockQuantity"
            />
          </div>

          <div class="mt-4 flex gap-3">
            <button
              type="button"
              :disabled="product.stockQuantity === 0 || isAddingToCart"
              class="flex-1 rounded-xl border border-indigo-600 px-4 py-3 text-sm font-semibold text-indigo-600 transition hover:bg-indigo-50 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400"
              @click="handleAddToCart"
            >
              {{ isAddingToCart ? '담는 중...' : '🛒 장바구니 담기' }}
            </button>
            <button
              type="button"
              :disabled="product.stockQuantity === 0 || isDirectBuying"
              class="flex-1 rounded-xl bg-indigo-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-300"
              @click="handleDirectBuy"
            >
              {{ isDirectBuying ? '처리 중...' : '바로 결제 →' }}
            </button>
          </div>
        </div>
      </section>

      <section class="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm sm:p-8">
        <h2 class="text-xl font-bold text-gray-900">Product description</h2>
        <p class="mt-4 whitespace-pre-wrap text-sm leading-7 text-gray-600">
          {{ product.description }}
        </p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import { useProductDetail } from '~/composables/useCatalog'
import { useUiToast } from '~/composables/useUiToast'
import { useAuthStore } from '~/stores/auth'
import { useCartStore } from '~/stores/cart'
import QuantityInput from '~/components/cart/QuantityInput.vue'
import type { ProductDetail } from '~/types/catalog'

definePageMeta({
  layout: 'default',
})

const FALLBACK_IMAGE = '/images/default-album.svg'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const cartStore = useCartStore()
const { showToast } = useUiToast()
const { $axios } = useNuxtApp()
const axios = $axios as AxiosInstance

const product = ref<ProductDetail | null>(null)
const isLoading = ref(true)
const isNotFound = ref(false)
const fetchError = ref('')
const imageSrc = ref(FALLBACK_IMAGE)
const quantity = ref(1)
const isAddingToCart = ref(false)
const isDirectBuying = ref(false)

const productId = computed(() => {
  const rawId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  const parsed = Number(rawId)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const formattedPrice = computed(() => {
  if (!product.value) return ''
  return `₩ ${new Intl.NumberFormat('ko-KR').format(product.value.price)}`
})

const stockLabel = computed(() => {
  if (!product.value) return ''
  return product.value.stockQuantity > 0
    ? `In stock: ${product.value.stockQuantity} left`
    : 'Out of stock'
})

const stockClass = computed(() => {
  return product.value?.stockQuantity && product.value.stockQuantity > 0
    ? 'text-green-600'
    : 'text-red-600'
})

const fetchProduct = async () => {
  if (productId.value === null) {
    product.value = null
    isLoading.value = false
    isNotFound.value = true
    return
  }

  isLoading.value = true
  isNotFound.value = false
  fetchError.value = ''
  product.value = null

  try {
    const detail = await useProductDetail(productId.value)
    product.value = detail
    imageSrc.value = detail.imageUrl ?? FALLBACK_IMAGE
  } catch (err: unknown) {
    const error = err as { response?: { status?: number; data?: { message?: string } } }
    if (error.response?.status === 404) {
      isNotFound.value = true
      product.value = null
      return
    }

    fetchError.value = error.response?.data?.message ?? '상품 정보를 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
}

const handleImageError = () => {
  imageSrc.value = FALLBACK_IMAGE
}

const handleBack = async () => {
  if (import.meta.client && window.history.length > 1) {
    router.back()
    return
  }

  await navigateTo('/products')
}

const handleAddToCart = async () => {
  if (!product.value || product.value.stockQuantity === 0) return
  if (!authStore.isLoggedIn) {
    showToast('로그인이 필요합니다.', 'warning')
    await navigateTo('/auth/login')
    return
  }
  isAddingToCart.value = true
  try {
    await cartStore.addItem(product.value.id, quantity.value)
    showToast('장바구니에 담았습니다.', 'success')
  } catch {
    showToast('담기에 실패했습니다. 다시 시도해 주세요.', 'error')
  } finally {
    isAddingToCart.value = false
  }
}

const handleDirectBuy = async () => {
  if (!product.value || product.value.stockQuantity === 0) return
  if (!authStore.isLoggedIn) {
    showToast('로그인이 필요합니다.', 'warning')
    await navigateTo('/auth/login')
    return
  }
  isDirectBuying.value = true
  try {
    const res = await axios.post<{ id: number }>('/api/v1/orders', {
      items: [{ productId: product.value.id, quantity: quantity.value }],
    })
    await navigateTo(`/checkout?orderId=${res.data.id}`)
  } catch {
    showToast('주문 생성에 실패했습니다.', 'error')
  } finally {
    isDirectBuying.value = false
  }
}

onMounted(async () => {
  await fetchProduct()
})

watch(productId, async () => {
  await fetchProduct()
})
</script>
