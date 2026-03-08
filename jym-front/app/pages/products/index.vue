<template>
  <div class="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
    <div class="mb-8 flex flex-col gap-2">
      <h1 class="text-3xl font-bold text-gray-900">상품 목록</h1>
      <p class="text-sm text-gray-500">
        카테고리별로 앨범을 탐색하고 원하는 상품 상세 페이지로 이동할 수 있습니다.
      </p>
    </div>

    <div
      v-if="categoryErrorMessage || productErrorMessage"
      class="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ categoryErrorMessage || productErrorMessage }}
    </div>

    <section class="mb-8 rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
      <div class="mb-3 flex items-center justify-between gap-3">
        <h2 class="text-sm font-semibold text-gray-900">카테고리</h2>
        <span
          v-if="isCategoryLoading"
          class="text-xs font-medium text-gray-400"
        >
          불러오는 중...
        </span>
      </div>

      <CategoryTabs
        :categories="categories"
        :model-value="selectedCategoryId"
        @update:model-value="handleCategoryChange"
      />
    </section>

    <section>
      <div class="mb-4 flex items-center justify-between">
        <p class="text-sm text-gray-500">
          총 {{ totalElements.toLocaleString('ko-KR') }}개의 상품
        </p>
        <p class="text-sm text-gray-500">
          {{ currentPage + 1 }} / {{ Math.max(totalPages, 1) }} 페이지
        </p>
      </div>

      <div
        v-if="isProductLoading"
        class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"
      >
        <div
          v-for="index in 12"
          :key="index"
          class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm"
        >
          <div class="aspect-square animate-pulse bg-gray-200" />
          <div class="space-y-3 p-4">
            <div class="h-4 animate-pulse rounded bg-gray-200" />
            <div class="h-4 w-2/3 animate-pulse rounded bg-gray-200" />
            <div class="h-5 w-1/3 animate-pulse rounded bg-gray-200" />
          </div>
        </div>
      </div>

      <div
        v-else-if="products.length === 0"
        class="rounded-2xl border border-dashed border-gray-300 bg-white px-6 py-16 text-center"
      >
        <p class="text-lg font-semibold text-gray-900">No products registered.</p>
        <p class="mt-2 text-sm text-gray-500">선택한 조건에 맞는 상품이 아직 없습니다.</p>
      </div>

      <div
        v-else
        class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"
      >
        <ProductCard
          v-for="product in products"
          :key="product.id"
          :product="product"
        />
      </div>
    </section>

    <div
      v-if="totalPages > 1"
      class="mt-8"
    >
      <Pagination
        :current-page="currentPage"
        :total-pages="totalPages"
        @change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import CategoryTabs from '~/components/products/CategoryTabs.vue'
import Pagination from '~/components/products/Pagination.vue'
import ProductCard from '~/components/products/ProductCard.vue'
import { useCategories, useProducts } from '~/composables/useCatalog'

definePageMeta({
  layout: 'default',
})

const route = useRoute()
const router = useRouter()

const {
  products,
  totalElements,
  totalPages,
  currentPage,
  selectedCategoryId,
  isLoading: isProductLoading,
  errorMessage: productErrorMessage,
  fetchProducts,
} = useProducts()

const {
  categories,
  isLoading: isCategoryLoading,
  errorMessage: categoryErrorMessage,
  fetchCategories,
} = useCategories()

const pageSize = 12

const parsePage = (pageQuery: unknown) => {
  const value = Array.isArray(pageQuery) ? pageQuery[0] : pageQuery
  const parsed = Number(value ?? 0)

  if (!Number.isInteger(parsed) || parsed < 0) {
    return 0
  }

  return parsed
}

const parseCategoryId = (categoryQuery: unknown) => {
  const value = Array.isArray(categoryQuery) ? categoryQuery[0] : categoryQuery

  if (value === undefined || value === null || value === '') {
    return null
  }

  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const syncStateFromRoute = () => {
  currentPage.value = parsePage(route.query.page)
  selectedCategoryId.value = parseCategoryId(route.query.categoryId)
}

const syncProductsFromRoute = async () => {
  syncStateFromRoute()
  await fetchProducts({
    page: currentPage.value,
    size: pageSize,
    categoryId: selectedCategoryId.value,
  })
}

const pushQuery = async (page: number, categoryId: number | null) => {
  await router.push({
    query: {
      page: String(page),
      ...(categoryId !== null ? { categoryId: String(categoryId) } : {}),
    },
  })
}

const handleCategoryChange = async (nextCategoryId: number | null) => {
  if (nextCategoryId === selectedCategoryId.value) return
  await pushQuery(0, nextCategoryId)
}

const handlePageChange = async (nextPage: number) => {
  if (nextPage === currentPage.value || nextPage < 0 || nextPage >= totalPages.value) return
  await pushQuery(nextPage, selectedCategoryId.value)
}

onMounted(async () => {
  syncStateFromRoute()
  await fetchCategories()
  await syncProductsFromRoute()
})

watch(
  () => [route.query.page, route.query.categoryId],
  async () => {
    await syncProductsFromRoute()
  },
)
</script>
