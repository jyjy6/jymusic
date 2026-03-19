<template>
  <NuxtLink
    to="/cart"
    class="relative inline-flex items-center rounded-md px-2 py-1 text-gray-600 transition hover:text-indigo-600"
    aria-label="장바구니"
  >
    <svg
      xmlns="http://www.w3.org/2000/svg"
      class="h-6 w-6"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      stroke-width="1.8"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13l-1.6 8H19M7 13L5.4 5M10 21a1 1 0 100-2 1 1 0 000 2zm8 0a1 1 0 100-2 1 1 0 000 2z"
      />
    </svg>
    <span
      v-if="cartStore.totalCount > 0"
      class="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-indigo-600 text-[10px] font-bold text-white"
    >
      {{ cartStore.totalCount > 99 ? '99+' : cartStore.totalCount }}
    </span>
  </NuxtLink>
</template>

<script setup lang="ts">
import { useCartStore } from '~/stores/cart'

const cartStore = useCartStore()
</script>
