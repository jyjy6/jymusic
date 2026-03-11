<template>
  <div class="mx-auto max-w-4xl px-4 py-10 sm:px-6 lg:px-8">
    <div class="rounded-3xl border border-gray-200 bg-white p-8 shadow-sm">
      <p class="text-sm font-semibold uppercase tracking-[0.2em] text-indigo-600">
        Admin
      </p>
      <h1 class="mt-3 text-3xl font-bold text-gray-900">
        Product management
      </h1>
      <p class="mt-3 text-sm leading-6 text-gray-500">
        관리자용 상품 목록 페이지는 다음 단계에서 확장할 예정입니다. 현재는 상품 등록과 수정 진입점을 먼저 제공합니다.
      </p>

      <div class="mt-8 flex flex-wrap gap-3">
        <NuxtLink
          to="/admin/products/new"
          class="inline-flex rounded-xl bg-indigo-600 px-5 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700"
        >
          Add Product
        </NuxtLink>
        <NuxtLink
          to="/products"
          class="inline-flex rounded-xl border border-gray-300 px-5 py-3 text-sm font-semibold text-gray-700 transition hover:bg-gray-50"
        >
          View Public Catalog
        </NuxtLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import adminMiddleware from '~/middleware/admin'

definePageMeta({
  layout: 'admin',
  middleware: [adminMiddleware],
})
</script>
