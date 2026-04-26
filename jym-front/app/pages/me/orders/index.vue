<template>
  <div class="mx-auto max-w-5xl px-4 py-8">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900">내 주문</h1>
      <NuxtLink to="/me" class="text-sm font-medium text-indigo-600 hover:text-indigo-700">프로필로</NuxtLink>
    </div>

    <div v-if="loading" class="rounded-xl border border-gray-200 bg-white p-8 text-center text-gray-500">
      주문 목록을 불러오는 중...
    </div>

    <div v-else-if="errorMessage" class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
      {{ errorMessage }}
    </div>

    <div v-else-if="orders.length === 0" class="rounded-xl border border-gray-200 bg-white p-8 text-center text-gray-500">
      주문 내역이 없습니다.
    </div>

    <ul v-else class="space-y-3">
      <li v-for="order in orders" :key="order.id">
        <NuxtLink
          :to="`/me/orders/${order.id}`"
          class="block rounded-xl border border-gray-200 bg-white p-4 transition hover:border-indigo-200 hover:shadow-sm"
        >
          <div class="flex items-center justify-between gap-3">
            <p class="text-sm font-semibold text-gray-900">주문 #{{ order.id }}</p>
            <span class="rounded-full px-2.5 py-1 text-xs font-semibold" :class="statusClass(order.status)">
              {{ order.status }}
            </span>
          </div>
          <div class="mt-2 flex items-center justify-between text-sm text-gray-600">
            <p>{{ formatDate(order.createdAt) }}</p>
            <p class="font-semibold text-gray-900">{{ formatAmount(order.totalAmount) }}원</p>
          </div>
        </NuxtLink>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import type { OrderStatus, OrderSummary } from '~/types/order'
import { useMyOrders } from '~/composables/useMyOrders'
import { useNotificationStream } from '~/composables/useNotificationStream'

definePageMeta({
  middleware: 'auth',
})

const { fetchMyOrders } = useMyOrders()
const { subscribe } = useNotificationStream()

const loading = ref(true)
const errorMessage = ref('')
const orders = ref<OrderSummary[]>([])

const formatDate = (iso: string) => new Date(iso).toLocaleString()
const formatAmount = (amount: number) => amount.toLocaleString()

const statusClass = (status: OrderStatus) => {
  if (status === 'COMPLETED') return 'bg-emerald-100 text-emerald-700'
  if (status === 'CANCELLED') return 'bg-rose-100 text-rose-700'
  if (status === 'SHIPPED') return 'bg-blue-100 text-blue-700'
  return 'bg-indigo-100 text-indigo-700'
}

const refresh = async () => {
  try {
    orders.value = await fetchMyOrders()
    errorMessage.value = ''
  } catch (error: unknown) {
    const e = error as { response?: { data?: { message?: string } } }
    errorMessage.value = e.response?.data?.message ?? '주문 목록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
subscribe(() => {
  refresh()
})
</script>
