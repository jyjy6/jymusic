<template>
  <ProductAdminForm mode="create" />
</template>

<script setup lang="ts">
import ProductAdminForm from '~/components/products/ProductAdminForm.vue'
import adminMiddleware from '~/middleware/admin'

definePageMeta({
  layout: 'admin',
  middleware: [adminMiddleware],
})
</script>
