<template>
  <div class="mx-auto max-w-xl px-4 py-20 text-center sm:px-6">
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

    <h1 class="mt-6 text-2xl font-bold text-gray-900">결제에 실패했습니다.</h1>
    <p v-if="errorMessage" class="mt-3 text-sm text-red-500">
      {{ errorMessage }}
    </p>
    <p v-if="errorCode" class="mt-1 text-xs text-gray-400">
      오류 코드: {{ errorCode }}
    </p>

    <div class="mt-8 flex justify-center gap-3">
      <NuxtLink
        v-if="orderId"
        :to="`/checkout?orderId=${orderId}`"
        class="rounded-xl bg-indigo-600 px-6 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700"
      >
        다시 결제하기
      </NuxtLink>
      <NuxtLink
        to="/cart"
        class="rounded-xl border border-gray-200 px-6 py-3 text-sm font-semibold text-gray-700 transition hover:bg-gray-50"
      >
        장바구니로 돌아가기
      </NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ layout: 'default', middleware: 'auth' })

const route = useRoute()

const errorCode = computed(() =>
  Array.isArray(route.query.code) ? route.query.code[0] : route.query.code,
)
const errorMessage = computed(() =>
  Array.isArray(route.query.message) ? route.query.message[0] : route.query.message,
)
const orderId = computed(() =>
  Array.isArray(route.query.orderId) ? route.query.orderId[0] : route.query.orderId,
)
</script>
