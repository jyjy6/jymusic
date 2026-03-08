<template>
  <NuxtLink
    :to="`/products/${product.id}`"
    class="group block overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg"
  >
    <div class="aspect-square overflow-hidden bg-gray-100">
      <img
        :src="imageSrc"
        :alt="product.title"
        class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
        @error="handleImageError"
      />
    </div>

    <div class="space-y-2 p-4">
      <h3 class="truncate text-base font-semibold text-gray-900">
        {{ product.title }}
      </h3>
      <p class="truncate text-sm text-gray-500">
        {{ product.artist }}
      </p>
      <p class="text-lg font-semibold text-indigo-600">
        {{ formattedPrice }}
      </p>
    </div>
  </NuxtLink>
</template>

<script setup lang="ts">
import type { ProductSummary } from '~/types/catalog'

const FALLBACK_IMAGE = '/images/default-album.svg'

const props = defineProps<{
  product: ProductSummary
}>()

const imageSrc = ref(props.product.thumbnailUrl ?? FALLBACK_IMAGE)

const formattedPrice = computed(() => `₩ ${new Intl.NumberFormat('ko-KR').format(props.product.price)}`)

watch(
  () => props.product.thumbnailUrl,
  (nextThumbnailUrl) => {
    imageSrc.value = nextThumbnailUrl ?? FALLBACK_IMAGE
  },
)

const handleImageError = () => {
  imageSrc.value = FALLBACK_IMAGE
}
</script>
