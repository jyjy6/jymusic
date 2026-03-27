<template>
  <div class="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
    <div class="mb-8 flex flex-col gap-2">
      <h1 class="text-3xl font-bold text-gray-900">상품 목록</h1>
      <p class="text-sm text-gray-500">
        카테고리별로 앨범을 탐색하고 원하는 상품 상세 페이지로 이동할 수 있습니다.
      </p>
    </div>

    <div
      v-if="categoryErrorMessage || productErrorMessage || (isSearchMode && searchErrorMessage)"
      class="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ categoryErrorMessage || productErrorMessage || searchErrorMessage }}
    </div>

    <section class="mb-6 rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-end">
        <div class="flex-1">
          <label class="mb-1 block text-xs font-medium text-gray-600">검색어</label>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="앨범명 또는 아티스트명으로 검색..."
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm transition focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="flex gap-2">
          <div>
            <label class="mb-1 block text-xs font-medium text-gray-600">최소 가격</label>
            <input
              v-model.number="searchMinPrice"
              type="number"
              placeholder="₩ 0"
              min="0"
              class="w-28 rounded-lg border border-gray-300 px-3 py-2 text-sm transition focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-gray-600">최대 가격</label>
            <input
              v-model.number="searchMaxPrice"
              type="number"
              placeholder="₩ ∞"
              min="0"
              class="w-28 rounded-lg border border-gray-300 px-3 py-2 text-sm transition focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>
        </div>

        <div>
          <label class="mb-1 block text-xs font-medium text-gray-600">정렬</label>
          <select
            v-model="searchSort"
            class="rounded-lg border border-gray-300 px-3 py-2 text-sm transition focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          >
            <option value="createdAt,desc">최신순</option>
            <option value="price,asc">가격 낮은순</option>
            <option value="price,desc">가격 높은순</option>
            <option value="title,asc">이름순</option>
          </select>
        </div>

        <div class="flex gap-2">
          <button
            type="button"
            class="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700 disabled:opacity-50"
            :disabled="isSearchLoading"
            @click="handleSearch"
          >
            검색
          </button>
          <button
            v-if="isSearchMode"
            type="button"
            class="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50"
            @click="handleSearchReset"
          >
            초기화
          </button>
        </div>
      </div>
    </section>

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
          총 {{ displayTotalElements.toLocaleString('ko-KR') }}개의 상품
        </p>
        <p class="text-sm text-gray-500">
          {{ displayCurrentPage + 1 }} / {{ Math.max(displayTotalPages, 1) }} 페이지
        </p>
      </div>

      <div
        v-if="displayLoading"
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
        v-else-if="displayProducts.length === 0"
        class="rounded-2xl border border-dashed border-gray-300 bg-white px-6 py-16 text-center"
      >
        <p class="text-lg font-semibold text-gray-900">
          {{ isSearchMode ? '검색 결과가 없습니다.' : 'No products registered.' }}
        </p>
        <p class="mt-2 text-sm text-gray-500">
          {{
            isSearchMode
              ? '다른 검색어나 조건으로 다시 시도해 보세요.'
              : '선택한 조건에 맞는 상품이 아직 없습니다.'
          }}
        </p>
      </div>

      <div
        v-else
        class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"
      >
        <ProductCard
          v-for="product in displayProducts"
          :key="product.id"
          :product="product"
        />
      </div>
    </section>

    <div
      v-if="displayTotalPages > 1"
      class="mt-8"
    >
      <Pagination
        :current-page="displayCurrentPage"
        :total-pages="displayTotalPages"
        @change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import CategoryTabs from '~/components/products/CategoryTabs.vue'
import Pagination from '~/components/products/Pagination.vue'
import ProductCard from '~/components/products/ProductCard.vue'
import { useCategories, useProductSearch, useProducts } from '~/composables/useCatalog'

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
  searchResults,
  totalElements: searchTotalElements,
  totalPages: searchTotalPages,
  currentPage: searchCurrentPage,
  isLoading: isSearchLoading,
  errorMessage: searchErrorMessage,
  searchProducts,
  resetSearch,
} = useProductSearch()

const {
  categories,
  isLoading: isCategoryLoading,
  errorMessage: categoryErrorMessage,
  fetchCategories,
} = useCategories()

const pageSize = 12

const searchKeyword = ref('')
const searchMinPrice = ref<number | undefined>(undefined)
const searchMaxPrice = ref<number | undefined>(undefined)
const searchSort = ref('createdAt,desc')
const isSearchMode = ref(false)

const displayProducts = computed(() =>
  isSearchMode.value ? searchResults.value : products.value,
)
const displayTotalElements = computed(() =>
  isSearchMode.value ? searchTotalElements.value : totalElements.value,
)
const displayTotalPages = computed(() =>
  isSearchMode.value ? searchTotalPages.value : totalPages.value,
)
const displayCurrentPage = computed(() =>
  isSearchMode.value ? searchCurrentPage.value : currentPage.value,
)
const displayLoading = computed(() =>
  isSearchMode.value ? isSearchLoading.value : isProductLoading.value,
)

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

const parseOptionalNumber = (q: unknown): number | null => {
  const value = Array.isArray(q) ? q[0] : q
  if (value === undefined || value === null || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

const syncBrowseStateFromRoute = () => {
  currentPage.value = parsePage(route.query.page)
  selectedCategoryId.value = parseCategoryId(route.query.categoryId)
}

const syncProductsFromRoute = async () => {
  syncBrowseStateFromRoute()
  await fetchProducts({
    page: currentPage.value,
    size: pageSize,
    categoryId: selectedCategoryId.value,
  })
}

const pushBrowseQuery = async (page: number, categoryId: number | null) => {
  await router.push({
    query: {
      page: String(page),
      ...(categoryId !== null ? { categoryId: String(categoryId) } : {}),
    },
  })
}

const pushSearchQuery = async (page: number) => {
  const query: Record<string, string> = {
    mode: 'search',
    page: String(page),
    sort: searchSort.value,
  }
  if (searchKeyword.value) query.keyword = searchKeyword.value
  if (selectedCategoryId.value != null) {
    query.categoryId = String(selectedCategoryId.value)
  }
  if (searchMinPrice.value != null) {
    query.minPrice = String(searchMinPrice.value)
  }
  if (searchMaxPrice.value != null) {
    query.maxPrice = String(searchMaxPrice.value)
  }
  await router.push({ query })
}

const hydrateSearchFromRoute = async () => {
  if (route.query.mode !== 'search') return

  const keyword =
    typeof route.query.keyword === 'string' ? route.query.keyword : ''
  const minP = parseOptionalNumber(route.query.minPrice)
  const maxP = parseOptionalNumber(route.query.maxPrice)
  const sort =
    typeof route.query.sort === 'string' ? route.query.sort : 'createdAt,desc'
  const page = parsePage(route.query.page)

  if (!keyword.trim() && minP == null && maxP == null) {
    return
  }

  isSearchMode.value = true
  searchKeyword.value = keyword
  searchMinPrice.value = minP ?? undefined
  searchMaxPrice.value = maxP ?? undefined
  searchSort.value = sort
  selectedCategoryId.value = parseCategoryId(route.query.categoryId)

  await searchProducts({
    keyword: keyword.trim() || undefined,
    categoryId: selectedCategoryId.value,
    minPrice: searchMinPrice.value,
    maxPrice: searchMaxPrice.value,
    page,
    size: pageSize,
    sort: searchSort.value,
  })
}

const handleSearch = async () => {
  if (
    !searchKeyword.value.trim()
    && searchMinPrice.value == null
    && searchMaxPrice.value == null
  ) {
    return
  }
  isSearchMode.value = true
  await pushSearchQuery(0)
}

const handleSearchReset = async () => {
  isSearchMode.value = false
  searchKeyword.value = ''
  searchMinPrice.value = undefined
  searchMaxPrice.value = undefined
  searchSort.value = 'createdAt,desc'
  resetSearch()
  await router.push({
    query: {
      page: '0',
      ...(selectedCategoryId.value != null
        ? { categoryId: String(selectedCategoryId.value) }
        : {}),
    },
  })
}

const handleCategoryChange = async (nextCategoryId: number | null) => {
  if (nextCategoryId === selectedCategoryId.value) return

  if (isSearchMode.value) {
    selectedCategoryId.value = nextCategoryId
    const canSearch =
      searchKeyword.value.trim()
      || searchMinPrice.value != null
      || searchMaxPrice.value != null
    if (canSearch) {
      await pushSearchQuery(0)
    }
    return
  }

  await pushBrowseQuery(0, nextCategoryId)
}

const handlePageChange = async (nextPage: number) => {
  if (
    nextPage === displayCurrentPage.value
    || nextPage < 0
    || nextPage >= displayTotalPages.value
  ) {
    return
  }

  if (isSearchMode.value) {
    await pushSearchQuery(nextPage)
    return
  }

  await pushBrowseQuery(nextPage, selectedCategoryId.value)
}

onMounted(async () => {
  if (route.query.mode === 'search') {
    await fetchCategories()
    await hydrateSearchFromRoute()
    return
  }

  syncBrowseStateFromRoute()
  await fetchCategories()
  await syncProductsFromRoute()
})

watch(
  () => route.fullPath,
  async (path, prev) => {
    if (path === prev) return
    if (route.query.mode === 'search') {
      await hydrateSearchFromRoute()
    } else {
      isSearchMode.value = false
      await syncProductsFromRoute()
    }
  },
)
</script>
