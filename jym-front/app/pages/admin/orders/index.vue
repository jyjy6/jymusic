<template>
  <div class="mx-auto max-w-6xl px-4 py-8">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900">주문 관리</h1>
      <div class="flex items-center gap-2">
        <span class="rounded-lg bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-700">
          전체 {{ pageData.totalElements }}건
        </span>
      </div>
    </div>

    <section class="mb-4 rounded-xl border border-gray-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-4">
        <input v-model="filters.keyword" placeholder="회원 키워드" class="rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-400" />
        <input v-model="filters.productTitle" placeholder="상품명" class="rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-400" />
        <select v-model="filters.status" class="rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-400">
          <option value="">전체 상태</option>
          <option v-for="status in statuses" :key="status" :value="status">{{ status }}</option>
        </select>
        <button class="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-700" @click="search(0)">
          검색
        </button>
      </div>
    </section>

    <section class="rounded-xl border border-gray-200 bg-white">
      <div v-if="loading" class="p-8 text-center text-sm text-gray-500">목록을 불러오는 중...</div>
      <div v-else-if="errorMessage" class="p-4 text-sm text-rose-700">{{ errorMessage }}</div>
      <table v-else class="w-full text-sm">
        <thead class="border-b border-gray-100 bg-gray-50 text-left text-gray-500">
          <tr>
            <th class="px-3 py-3">주문번호</th>
            <th class="px-3 py-3">회원</th>
            <th class="px-3 py-3">상품</th>
            <th class="px-3 py-3">금액</th>
            <th class="px-3 py-3">상태</th>
            <th class="px-3 py-3">주문시각</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="order in pageData.content"
            :key="order.orderId"
            class="cursor-pointer border-b border-gray-100 hover:bg-indigo-50/40"
            @click="navigateTo(`/admin/orders/${order.orderId}`)"
          >
            <td class="px-3 py-3 font-semibold text-gray-900">#{{ order.orderId }}</td>
            <td class="px-3 py-3 text-gray-700">{{ order.nickname }} ({{ order.username }})</td>
            <td class="px-3 py-3 text-gray-700">{{ order.firstItemTitle }}</td>
            <td class="px-3 py-3 text-gray-700">{{ Number(order.totalAmount).toLocaleString() }}원</td>
            <td class="px-3 py-3 text-gray-700">{{ order.status }}</td>
            <td class="px-3 py-3 text-gray-500">{{ new Date(order.createdAt).toLocaleString() }}</td>
          </tr>
          <tr v-if="pageData.content.length === 0">
            <td colspan="6" class="px-3 py-8 text-center text-gray-500">조회 결과가 없습니다.</td>
          </tr>
        </tbody>
      </table>
      <div class="border-t border-gray-100 p-4">
        <ProductsPagination
          :current-page="pageData.number"
          :total-pages="Math.max(pageData.totalPages, 1)"
          @change="search"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useAdminOrders } from '~/composables/useAdminOrders'
import { useNotificationStream } from '~/composables/useNotificationStream'
import type { AdminOrderSearchRequest, PageResponse, AdminOrderSummary } from '~/types/admin-order'
import type { OrderStatus } from '~/types/order'
import ProductsPagination from '~/components/products/Pagination.vue'

definePageMeta({
  layout: 'admin',
  middleware: 'admin',
})

const { search: searchOrders } = useAdminOrders()
const { subscribe } = useNotificationStream()

const statuses: OrderStatus[] = ['PENDING', 'STOCK_RESERVED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED']
const filters = reactive<AdminOrderSearchRequest>({
  keyword: '',
  productTitle: '',
  status: undefined,
})
const loading = ref(true)
const errorMessage = ref('')
const pageData = ref<PageResponse<AdminOrderSummary>>({
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
})

const search = async (page = 0) => {
  loading.value = true
  try {
    pageData.value = await searchOrders(filters, page, pageData.value.size)
    errorMessage.value = ''
  } catch (error: unknown) {
    const e = error as { response?: { data?: { message?: string } } }
    errorMessage.value = e.response?.data?.message ?? '주문 목록 조회에 실패했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(() => search(0))
subscribe(() => {
  search(pageData.value.number)
})
</script>
