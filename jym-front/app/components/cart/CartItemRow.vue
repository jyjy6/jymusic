<template>
  <div class="flex items-center gap-4 rounded-xl border border-gray-100 bg-white p-4 shadow-sm">
    <input
      type="checkbox"
      :checked="props.isSelected"
      class="h-4 w-4 cursor-pointer accent-indigo-600"
      @change="emit('update:isSelected', ($event.target as HTMLInputElement).checked)"
    />

    <NuxtLink :to="`/products/${props.item.productId}`" class="shrink-0">
      <img
        :src="thumbnailSrc"
        :alt="props.item.title"
        class="h-16 w-16 rounded-lg object-cover"
        @error="handleImageError"
      />
    </NuxtLink>

    <div class="min-w-0 flex-1">
      <NuxtLink
        :to="`/products/${props.item.productId}`"
        class="block truncate text-sm font-medium text-gray-900 hover:text-indigo-600"
      >
        {{ props.item.title }}
      </NuxtLink>
      <p class="mt-0.5 truncate text-xs text-gray-500">
        {{ props.item.artist }}
      </p>
      <div class="mt-2">
        <QuantityInput
          :model-value="localQuantity"
          :max="props.item.stockQuantity"
          @update:model-value="onQuantityChange"
        />
      </div>
    </div>

    <div class="flex flex-col items-end gap-2">
      <p class="text-sm font-semibold text-gray-900">
        ₩ {{ formatPrice(props.item.price * localQuantity) }}
      </p>
      <button
        type="button"
        class="text-gray-400 transition hover:text-red-500"
        title="삭제"
        @click="emit('remove')"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          class="h-5 w-5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="1.8"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6M9 7V4h6v3M3 7h18"
          />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CartItem } from '~/types/cart'
import QuantityInput from '~/components/cart/QuantityInput.vue'

const FALLBACK_IMAGE = '/images/default-album.svg'

const props = defineProps<{
  item: CartItem
  isSelected: boolean
}>()

const emit = defineEmits<{
  'update:isSelected': [value: boolean]
  'update:quantity': [newQty: number]
  remove: []
}>()

const localQuantity = ref(props.item.quantity)
const thumbnailSrc = ref(props.item.thumbnailUrl ?? FALLBACK_IMAGE)

watch(
  () => props.item.quantity,
  (v) => {
    localQuantity.value = v
  },
)

function handleImageError() {
  thumbnailSrc.value = FALLBACK_IMAGE
}

function formatPrice(n: number) {
  return new Intl.NumberFormat('ko-KR').format(n)
}

function onQuantityChange(newQty: number) {
  localQuantity.value = newQty
  emit('update:quantity', newQty)
}
</script>
