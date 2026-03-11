<template>
  <ProductAdminForm
    mode="edit"
    :product-id="productId"
  />
</template>

<script setup lang="ts">
import ProductAdminForm from '~/components/products/ProductAdminForm.vue'
import adminMiddleware from '~/middleware/admin'

definePageMeta({
  layout: 'admin',
  middleware: [adminMiddleware],
})

const route = useRoute()

const productId = computed(() => {
  const rawId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  const parsed = Number(rawId)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})
</script>
