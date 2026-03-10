<template>
  <NuxtLayout :name="adminLayoutName">
    <ProductAdminForm mode="create" />
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
</script>
