<template>
  <NuxtLayout :name="adminLayoutName">
    <ProductAdminForm
      mode="edit"
      :product-id="productId"
    />
  </NuxtLayout>
</template>

<script setup lang="ts">
import ProductAdminForm from '~/components/products/ProductAdminForm.vue'
import adminMiddleware from '~/middleware/admin'

// Generated Nuxt layout types are stale until prepare/build refreshes them.
const adminLayoutName = 'admin' as unknown as 'default'

definePageMeta({
  middleware: [adminMiddleware],
})

const route = useRoute()

const productId = computed(() => {
  const rawId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  const parsed = Number(rawId)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})
</script>
