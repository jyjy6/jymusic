<template>
  <div class="mx-auto max-w-xl px-4 py-20 text-center sm:px-6">
    <div v-if="isLoading">
      <div class="mx-auto h-16 w-16 animate-pulse rounded-full bg-gray-200" />
      <div class="mt-6 h-6 w-1/2 mx-auto animate-pulse rounded bg-gray-200" />
    </div>

    <div v-else-if="isSuccess && confirmData">
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          class="h-8 w-8 text-green-600"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="2"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
        </svg>
      </div>
      <h1 class="mt-6 text-2xl font-bold text-gray-900">결제가 완료되었습니다!</h1>
      <p class="mt-2 text-sm text-gray-500">
        주문번호: <span class="font-semibold text-gray-800">{{ route.query.orderId }}</span>
      </p>
      <p class="mt-1 text-sm text-gray-500">
        결제금액: <span class="font-semibold text-indigo-600">₩ {{ formatPrice(confirmData.paidAmount) }}</span>
      </p>
      <p class="mt-1 text-sm text-gray-500">
        결제일시: {{ formatDate(confirmData.paidAt) }}
      </p>
      <div class="mt-8 flex justify-center gap-3">
        <NuxtLink
          :to="`/orders/${route.query.orderId}`"
          class="rounded-xl bg-indigo-600 px-6 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700"
        >
          주문 내역 확인
        </NuxtLink>
        <NuxtLink
          to="/products"
          class="rounded-xl border border-gray-200 px-6 py-3 text-sm font-semibold text-gray-700 transition hover:bg-gray-50"
        >
          쇼핑 계속하기
        </NuxtLink>
      </div>
    </div>

    <div v-else>
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          class="h-8 w-8 text-red-600"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="2"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </div>
      <h1 class="mt-6 text-2xl font-bold text-gray-900">결제 승인에 실패했습니다.</h1>
      <p class="mt-2 text-sm text-gray-500">잠시 후 다시 시도해 주세요.</p>
      <NuxtLink
        :to="`/checkout?orderId=${route.query.orderId}`"
        class="mt-6 inline-flex rounded-xl bg-indigo-600 px-6 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700"
      >
        다시 시도
      </NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import type { PaymentConfirmResponse } from '~/types/checkout'

definePageMeta({ layout: 'default', middleware: 'auth' })

const route = useRoute()
const { $axios } = useNuxtApp()
const axios = $axios as AxiosInstance

const isLoading = ref(true)
const isSuccess = ref(false)
const confirmData = ref<PaymentConfirmResponse | null>(null)

function formatPrice(n: number) {
  return new Intl.NumberFormat('ko-KR').format(n)
}
function formatDate(iso: string) {
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(async () => {
  const { paymentKey, orderId, amount } = route.query
  if (!paymentKey || !orderId || !amount) {
    isLoading.value = false
    return
  }
  try {
    const res = await axios.post<PaymentConfirmResponse>('/api/v1/payments/confirm', {
      paymentKey: String(paymentKey),
      orderId: Number(orderId),
      amount: Number(amount),
    })
    confirmData.value = res.data
    isSuccess.value = res.data.status === 'SUCCESS'
  } catch {
    isSuccess.value = false
  } finally {
    isLoading.value = false
  }
})
</script>
