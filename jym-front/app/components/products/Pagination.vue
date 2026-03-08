<template>
  <nav
    v-if="totalPages > 1"
    class="flex flex-wrap items-center justify-center gap-2"
    aria-label="Pagination"
  >
    <button
      type="button"
      class="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-600 transition hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-50"
      :disabled="currentPage === 0"
      @click="emit('change', currentPage - 1)"
    >
      Prev
    </button>

    <button
      v-for="page in visiblePages"
      :key="page"
      type="button"
      :class="pageButtonClass(page)"
      class="rounded-lg px-3 py-2 text-sm font-medium transition"
      @click="emit('change', page)"
    >
      {{ page + 1 }}
    </button>

    <button
      type="button"
      class="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-600 transition hover:border-indigo-200 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-50"
      :disabled="currentPage >= totalPages - 1"
      @click="emit('change', currentPage + 1)"
    >
      Next
    </button>
  </nav>
</template>

<script setup lang="ts">
const props = defineProps<{
  currentPage: number
  totalPages: number
}>()

const emit = defineEmits<{
  change: [page: number]
}>()

const visiblePages = computed(() => {
  const maxVisiblePages = 5

  if (props.totalPages <= maxVisiblePages) {
    return Array.from({ length: props.totalPages }, (_, index) => index)
  }

  let startPage = Math.max(0, props.currentPage - 2)
  let endPage = Math.min(props.totalPages - 1, startPage + maxVisiblePages - 1)

  if (endPage - startPage < maxVisiblePages - 1) {
    startPage = Math.max(0, endPage - maxVisiblePages + 1)
  }

  return Array.from(
    { length: endPage - startPage + 1 },
    (_, index) => startPage + index,
  )
})

const pageButtonClass = (page: number) => {
  return page === props.currentPage
    ? 'bg-indigo-600 text-white shadow-sm'
    : 'border border-gray-200 bg-white text-gray-600 hover:border-indigo-200 hover:text-indigo-600'
}
</script>
