<template>
  <div class="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
    <div class="mb-6 flex items-center justify-between">
      <NuxtLink
        to="/products"
        class="inline-flex items-center gap-1 text-sm font-medium text-gray-500 transition hover:text-indigo-600"
      >
        ← 쇼핑 계속하기
      </NuxtLink>
      <button
        v-if="cartStore.items.length > 0"
        type="button"
        class="text-sm text-gray-400 transition hover:text-red-500"
        @click="handleClearCart"
      >
        장바구니 비우기
      </button>
    </div>

    <div v-if="isLoading" class="space-y-3">
      <div
        v-for="i in 3"
        :key="i"
        class="h-28 w-full animate-pulse rounded-xl bg-gray-200"
      />
    </div>

    <div
      v-else-if="fetchError"
      class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ fetchError }}
    </div>

    <div
      v-else-if="cartStore.items.length === 0"
      class="rounded-3xl border border-dashed border-gray-300 bg-white px-6 py-20 text-center"
    >
      <p class="text-xl font-bold text-gray-900">장바구니가 비어있습니다.</p>
      <p class="mt-2 text-sm text-gray-500">마음에 드는 앨범을 담아보세요!</p>
      <NuxtLink
        to="/products"
        class="mt-6 inline-flex rounded-xl bg-indigo-600 px-6 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700"
      >
        쇼핑하러 가기
      </NuxtLink>
    </div>

    <div v-else class="grid gap-8 lg:grid-cols-[1fr_320px]">
      <div class="space-y-3">
        <div class="mb-2 flex items-center gap-2">
          <input
            type="checkbox"
            :checked="isAllSelected"
            class="h-4 w-4 cursor-pointer accent-indigo-600"
            @change="toggleSelectAll"
          />
          <span class="text-sm text-gray-600">전체 선택</span>
        </div>

        <CartItemRow
          v-for="item in cartStore.items"
          :key="item.cartItemId"
          :item="item"
          :is-selected="selectedIds.has(item.cartItemId)"
          @update:is-selected="(v) => toggleSelect(item.cartItemId, v)"
          @update:quantity="(q) => handleQuantityChange(item.cartItemId, q)"
          @remove="handleRemoveItem(item.cartItemId)"
        />
      </div>

      <div class="lg:sticky lg:top-24 lg:self-start">
        <div class="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
          <div class="flex items-center justify-between text-sm text-gray-600">
            <span>선택 상품</span>
            <span class="font-semibold text-gray-900">
              {{ selectedItems.length }}개
            </span>
          </div>
          <div class="mt-3 flex items-center justify-between">
            <span class="font-bold text-gray-900">총 금액</span>
            <span class="text-xl font-bold text-indigo-600">
              ₩ {{ formatPrice(selectedTotal) }}
            </span>
          </div>
          <button
            type="button"
            :disabled="selectedItems.length === 0 || isOrdering"
            class="mt-4 w-full rounded-xl bg-indigo-600 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-300"
            @click="handleOrder"
          >
            {{ isOrdering ? '처리 중...' : '선택 상품 주문하기' }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="showDeleteModal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="showDeleteModal = false"
    >
      <div class="w-80 rounded-2xl bg-white p-6 shadow-xl">
        <p class="text-sm font-medium text-gray-900">
          수량이 0이 됩니다. 해당 상품을 장바구니에서 삭제할까요?
        </p>
        <div class="mt-4 flex gap-2">
          <button
            type="button"
            class="flex-1 rounded-lg border border-gray-200 py-2 text-sm font-medium text-gray-600 hover:bg-gray-50"
            @click="showDeleteModal = false"
          >
            취소
          </button>
          <button
            type="button"
            class="flex-1 rounded-lg bg-red-500 py-2 text-sm font-medium text-white hover:bg-red-600"
            @click="confirmDelete"
          >
            삭제
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import CartItemRow from '~/components/cart/CartItemRow.vue'
import { useCartStore } from '~/stores/cart'
import { useUiToast } from '~/composables/useUiToast'

definePageMeta({ layout: 'default', middleware: 'auth' })

const cartStore = useCartStore()
const { showToast } = useUiToast()
const { $axios } = useNuxtApp()
const axios = $axios as AxiosInstance

const isLoading = ref(true)
const fetchError = ref('')
const isOrdering = ref(false)

const selectedIds = ref<Set<number>>(new Set())
const showDeleteModal = ref(false)
const pendingDeleteId = ref<number | null>(null)

const quantityDebounceTimers = new Map<number, ReturnType<typeof setTimeout>>()

onMounted(async () => {
  try {
    await cartStore.fetchCart()
    selectedIds.value = new Set(cartStore.items.map((i) => i.cartItemId))
  } catch {
    fetchError.value = '장바구니를 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
})

const selectedItems = computed(() =>
  cartStore.items.filter((i) => selectedIds.value.has(i.cartItemId)),
)
const selectedTotal = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + i.price * i.quantity, 0),
)
const isAllSelected = computed(
  () =>
    cartStore.items.length > 0 &&
    cartStore.items.every((i) => selectedIds.value.has(i.cartItemId)),
)

function formatPrice(n: number) {
  return new Intl.NumberFormat('ko-KR').format(n)
}

function toggleSelect(id: number, selected: boolean) {
  if (selected) selectedIds.value.add(id)
  else selectedIds.value.delete(id)
}

function toggleSelectAll(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  if (checked) {
    selectedIds.value = new Set(cartStore.items.map((i) => i.cartItemId))
  } else {
    selectedIds.value.clear()
  }
}

function handleQuantityChange(cartItemId: number, quantity: number) {
  if (quantity < 1) {
    pendingDeleteId.value = cartItemId
    showDeleteModal.value = true
    return
  }
  const existing = quantityDebounceTimers.get(cartItemId)
  if (existing) clearTimeout(existing)
  const timer = setTimeout(async () => {
    try {
      await cartStore.updateItemQuantity(cartItemId, quantity)
    } catch {
      showToast('수량 변경에 실패했습니다.', 'error')
    }
    quantityDebounceTimers.delete(cartItemId)
  }, 500)
  quantityDebounceTimers.set(cartItemId, timer)
}

async function handleRemoveItem(cartItemId: number) {
  try {
    await cartStore.removeItem(cartItemId)
    selectedIds.value.delete(cartItemId)
    showToast('상품을 삭제했습니다.', 'info')
  } catch {
    showToast('삭제에 실패했습니다.', 'error')
  }
}

async function confirmDelete() {
  if (pendingDeleteId.value === null) return
  showDeleteModal.value = false
  await handleRemoveItem(pendingDeleteId.value)
  pendingDeleteId.value = null
}

async function handleClearCart() {
  try {
    await cartStore.clearCart()
    selectedIds.value.clear()
    showToast('장바구니를 비웠습니다.', 'info')
  } catch {
    showToast('장바구니 비우기에 실패했습니다.', 'error')
  }
}

async function handleOrder() {
  if (selectedItems.value.length === 0) return
  isOrdering.value = true
  try {
    const res = await axios.post<{ id: number }>('/api/v1/orders', {
      items: selectedItems.value.map((i) => ({
        productId: i.productId,
        quantity: i.quantity,
      })),
    })
    await navigateTo(`/checkout?orderId=${res.data.id}`)
  } catch {
    showToast('주문 생성에 실패했습니다.', 'error')
  } finally {
    isOrdering.value = false
  }
}
</script>
