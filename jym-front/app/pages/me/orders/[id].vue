<template>
  <div class="mx-auto max-w-4xl px-4 py-8">
    <NuxtLink to="/me/orders" class="text-sm font-medium text-indigo-600 hover:text-indigo-700">← 주문 목록</NuxtLink>

    <div v-if="loading" class="mt-4 rounded-xl border border-gray-200 bg-white p-8 text-center text-gray-500">
      주문 상세를 불러오는 중...
    </div>

    <div v-else-if="errorMessage" class="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
      {{ errorMessage }}
    </div>

    <div v-else-if="order" class="mt-4 space-y-4">
      <section class="rounded-xl border border-gray-200 bg-white p-5">
        <div class="flex items-center justify-between">
          <h1 class="text-xl font-bold text-gray-900">주문 #{{ order.id }}</h1>
          <span class="rounded-full bg-indigo-100 px-3 py-1 text-xs font-semibold text-indigo-700">
            {{ order.status }}
          </span>
        </div>
        <p class="mt-2 text-sm text-gray-500">{{ new Date(order.createdAt).toLocaleString() }}</p>
        <div class="mt-4">
          <OrderStatusStepper :status="order.status" />
        </div>
      </section>

      <section class="rounded-xl border border-gray-200 bg-white p-5">
        <h2 class="text-base font-semibold text-gray-900">주문 상품</h2>
        <ul class="mt-3 divide-y divide-gray-100">
          <li v-for="item in order.items" :key="`${item.productId}-${item.productTitle}`" class="py-3">
            <div class="flex items-center justify-between text-sm">
              <p class="font-medium text-gray-900">{{ item.productTitle }}</p>
              <p class="text-gray-500">{{ item.quantity }}개</p>
            </div>
            <p class="mt-1 text-sm text-gray-600">{{ Number(item.price).toLocaleString() }}원</p>
          </li>
        </ul>
        <div class="mt-4 border-t border-gray-100 pt-4 text-right">
          <p class="text-sm text-gray-500">총 결제 금액</p>
          <p class="text-lg font-bold text-gray-900">{{ Number(order.totalAmount).toLocaleString() }}원</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useMyOrders } from '~/composables/useMyOrders'
import { useNotificationStream } from '~/composables/useNotificationStream'
import type { OrderDetail } from '~/types/order'

definePageMeta({
  middleware: 'auth',
})

const route = useRoute()
const orderId = computed(() => Number(route.params.id))

const { fetchMyOrderDetail } = useMyOrders()
const { subscribe } = useNotificationStream()

const loading = ref(true)
const errorMessage = ref('')
const order = ref<OrderDetail | null>(null)

const refresh = async () => {
  try {
    order.value = await fetchMyOrderDetail(orderId.value)
    errorMessage.value = ''
  } catch (error: unknown) {
    const e = error as { response?: { data?: { message?: string } } }
    errorMessage.value = e.response?.data?.message ?? '주문 상세를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
subscribe((payload) => {
  if (payload.orderId === orderId.value) {
    refresh()
  }
})
</script>
