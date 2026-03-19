<template>
  <div class="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
    <h2 class="text-base font-bold text-gray-900">결제 금액 요약</h2>
    <div class="mt-4 space-y-2">
      <div
        v-for="item in props.items"
        :key="item.productId"
        class="flex items-start justify-between gap-2 text-sm"
      >
        <span class="line-clamp-1 flex-1 text-gray-700">
          {{ item.productTitle }}
          <span class="text-gray-400">×{{ item.quantity }}</span>
        </span>
        <span class="shrink-0 font-medium text-gray-900">
          ₩ {{ formatPrice(item.price * item.quantity) }}
        </span>
      </div>
    </div>

    <div class="my-4 border-t border-gray-100" />

    <div class="flex items-center justify-between text-sm text-gray-600">
      <span>상품 금액</span>
      <span>₩ {{ formatPrice(props.totalAmount) }}</span>
    </div>
    <div class="mt-1 flex items-center justify-between text-sm text-gray-600">
      <span>배송비</span>
      <span class="text-green-600 font-medium">무료</span>
    </div>

    <div class="my-4 border-t border-gray-200" />

    <div class="flex items-center justify-between">
      <span class="font-bold text-gray-900">최종 결제금액</span>
      <span class="text-lg font-bold text-indigo-600">
        ₩ {{ formatPrice(props.totalAmount) }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { OrderItemDetail } from '~/types/checkout'

const props = defineProps<{
  items: OrderItemDetail[]
  totalAmount: number
}>()

function formatPrice(n: number) {
  return new Intl.NumberFormat('ko-KR').format(n)
}
</script>
