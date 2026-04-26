<template>
  <div class="mx-auto max-w-4xl px-4 py-8">
    <NuxtLink to="/admin/orders" class="text-sm font-medium text-indigo-600 hover:text-indigo-700">← 주문 목록</NuxtLink>

    <div v-if="loading" class="mt-4 rounded-xl border border-gray-200 bg-white p-8 text-center text-gray-500">
      주문 상세를 불러오는 중...
    </div>

    <div v-else-if="errorMessage" class="mt-4 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
      {{ errorMessage }}
    </div>

    <div v-else-if="detail" class="mt-4 space-y-4">
      <section class="rounded-xl border border-gray-200 bg-white p-5">
        <div class="flex items-center justify-between">
          <h1 class="text-xl font-bold text-gray-900">주문 #{{ detail.orderId }}</h1>
          <span class="rounded-full bg-indigo-100 px-3 py-1 text-xs font-semibold text-indigo-700">{{ detail.status }}</span>
        </div>
        <p class="mt-2 text-sm text-gray-600">{{ detail.nickname }} ({{ detail.username }}) / {{ detail.email || '이메일 없음' }}</p>
        <p class="text-sm text-gray-500">{{ new Date(detail.createdAt).toLocaleString() }}</p>
      </section>

      <section class="rounded-xl border border-gray-200 bg-white p-5">
        <h2 class="text-base font-semibold text-gray-900">상태 변경</h2>
        <div class="mt-3 flex flex-wrap items-center gap-2">
          <select v-model="nextStatus" class="rounded-lg border border-gray-300 px-3 py-2 text-sm">
            <option value="">다음 상태 선택</option>
            <option v-for="status in detail.allowedNextStatuses" :key="status" :value="status">{{ status }}</option>
          </select>
          <input v-model="reason" placeholder="사유(선택)" class="min-w-48 rounded-lg border border-gray-300 px-3 py-2 text-sm" />
          <button
            class="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
            :disabled="!nextStatus || updating"
            @click="submitStatus"
          >
            {{ updating ? '변경 중...' : '상태 변경' }}
          </button>
        </div>
      </section>

      <section class="rounded-xl border border-gray-200 bg-white p-5">
        <h2 class="text-base font-semibold text-gray-900">주문 상품</h2>
        <ul class="mt-3 divide-y divide-gray-100">
          <li v-for="item in detail.items" :key="`${item.productId}-${item.productTitle}`" class="py-3">
            <div class="flex items-center justify-between">
              <p class="text-sm font-medium text-gray-900">{{ item.productTitle }}</p>
              <p class="text-sm text-gray-500">{{ item.quantity }}개</p>
            </div>
            <p class="text-sm text-gray-600">{{ Number(item.price).toLocaleString() }}원</p>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAdminOrders } from '~/composables/useAdminOrders'
import { useNotificationStream } from '~/composables/useNotificationStream'
import type { AdminOrderDetail } from '~/types/admin-order'
import type { OrderStatus } from '~/types/order'

definePageMeta({
  layout: 'admin',
  middleware: 'admin',
})

const route = useRoute()
const orderId = computed(() => Number(route.params.id))

const { getDetail, updateStatus } = useAdminOrders()
const { subscribe } = useNotificationStream()

const loading = ref(true)
const updating = ref(false)
const errorMessage = ref('')
const detail = ref<AdminOrderDetail | null>(null)
const nextStatus = ref<OrderStatus | ''>('')
const reason = ref('')

const refresh = async () => {
  loading.value = true
  try {
    detail.value = await getDetail(orderId.value)
    errorMessage.value = ''
  } catch (error: unknown) {
    const e = error as { response?: { data?: { message?: string } } }
    errorMessage.value = e.response?.data?.message ?? '주문 상세 조회에 실패했습니다.'
  } finally {
    loading.value = false
  }
}

const submitStatus = async () => {
  if (!nextStatus.value) return
  updating.value = true
  try {
    detail.value = await updateStatus(orderId.value, {
      status: nextStatus.value,
      reason: reason.value || undefined,
    })
    nextStatus.value = ''
    reason.value = ''
  } catch (error: unknown) {
    const e = error as { response?: { data?: { message?: string } } }
    errorMessage.value = e.response?.data?.message ?? '상태 변경에 실패했습니다.'
  } finally {
    updating.value = false
  }
}

onMounted(refresh)
subscribe((payload) => {
  if (payload.orderId === orderId.value) {
    refresh()
  }
})
</script>
