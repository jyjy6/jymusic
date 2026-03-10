<template>
  <NuxtLayout name="default">
    <div class="border-b border-gray-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center gap-6 px-4 sm:px-6 lg:px-8">
        <NuxtLink
          to="/admin/products"
          :class="getLinkClass('products')"
        >
          Products
        </NuxtLink>
        <NuxtLink
          to="/admin/products/new"
          :class="getLinkClass('new')"
        >
          Add Product
        </NuxtLink>
      </div>
    </div>

    <slot />
  </NuxtLayout>
</template>

<script setup lang="ts">
const route = useRoute()

const getLinkClass = (type: 'products' | 'new') => {
  const isNewPage = route.path === '/admin/products/new'
  const isProductsPage = route.path === '/admin/products'
    || (route.path.startsWith('/admin/products/') && route.path.endsWith('/edit'))

  const isActive = type === 'new' ? isNewPage : isProductsPage

  return [
    'inline-flex h-12 items-center border-b-2 text-sm font-semibold transition-colors',
    isActive
      ? 'border-indigo-600 text-indigo-600'
      : 'border-transparent text-gray-500 hover:text-indigo-600',
  ]
}
</script>
