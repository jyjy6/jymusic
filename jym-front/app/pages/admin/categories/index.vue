<template>
  <div class="mx-auto max-w-3xl px-4 py-10 sm:px-6 lg:px-8">
    <div class="mb-8 flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold text-gray-900">
          Categories
        </h1>
        <p class="mt-1 text-sm text-gray-500">
          음악 장르 카테고리를 관리합니다.
        </p>
      </div>
    </div>

    <!-- 새 카테고리 추가 폼 -->
    <div class="mb-8 rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
      <h2 class="mb-4 text-base font-semibold text-gray-800">
        새 카테고리 추가
      </h2>
      <form
        class="flex gap-3"
        @submit.prevent="handleCreate"
      >
        <input
          v-model.trim="newName"
          type="text"
          maxlength="50"
          placeholder="카테고리 이름 (예: Jazz)"
          :disabled="isSubmitting"
          class="flex-1 rounded-xl border border-gray-300 px-4 py-2.5 text-sm text-gray-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-gray-100"
        />
        <button
          type="submit"
          :disabled="isSubmitting || !newName"
          class="rounded-xl bg-indigo-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-300"
        >
          {{ isSubmitting ? '추가 중...' : '추가' }}
        </button>
      </form>
      <p
        v-if="createError"
        class="mt-2 text-sm font-medium text-red-600"
      >
        {{ createError }}
      </p>
    </div>

    <!-- 카테고리 목록 -->
    <div class="rounded-2xl border border-gray-200 bg-white shadow-sm">
      <div
        v-if="isLoading"
        class="space-y-3 p-6"
      >
        <div
          v-for="i in 4"
          :key="i"
          class="h-12 animate-pulse rounded-xl bg-gray-100"
        />
      </div>

      <div
        v-else-if="errorMessage"
        class="p-6 text-center text-sm text-red-600"
      >
        {{ errorMessage }}
      </div>

      <div
        v-else-if="categories.length === 0"
        class="p-10 text-center text-sm text-gray-400"
      >
        등록된 카테고리가 없습니다.
      </div>

      <ul
        v-else
        class="divide-y divide-gray-100"
      >
        <li
          v-for="category in categories"
          :key="category.id"
          class="flex items-center gap-4 px-6 py-4"
        >
          <template v-if="editingId === category.id">
            <input
              v-model.trim="editName"
              type="text"
              maxlength="50"
              :disabled="isSubmitting"
              class="flex-1 rounded-xl border border-indigo-400 px-4 py-2 text-sm text-gray-900 outline-none focus:ring-2 focus:ring-indigo-100 disabled:bg-gray-100"
              @keydown.enter.prevent="handleUpdate(category.id)"
              @keydown.escape="cancelEdit"
            />
            <button
              :disabled="isSubmitting || !editName"
              class="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-300"
              @click="handleUpdate(category.id)"
            >
              저장
            </button>
            <button
              :disabled="isSubmitting"
              class="rounded-xl border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-600 transition hover:bg-gray-50 disabled:opacity-50"
              @click="cancelEdit"
            >
              취소
            </button>
          </template>

          <template v-else>
            <span class="flex-1 text-sm font-medium text-gray-900">
              {{ category.name }}
            </span>
            <span class="text-xs text-gray-400">
              #{{ category.id }}
            </span>
            <button
              class="rounded-lg px-3 py-1.5 text-xs font-semibold text-indigo-600 transition hover:bg-indigo-50"
              @click="startEdit(category)"
            >
              수정
            </button>
            <button
              class="rounded-lg px-3 py-1.5 text-xs font-semibold text-red-500 transition hover:bg-red-50"
              @click="handleDelete(category)"
            >
              삭제
            </button>
          </template>
        </li>
      </ul>
    </div>

    <!-- 삭제 확인 모달 -->
    <div
      v-if="deletingCategory"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="deletingCategory = null"
    >
      <div class="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
        <h3 class="mb-2 text-lg font-bold text-gray-900">
          카테고리 삭제
        </h3>
        <p class="mb-6 text-sm text-gray-600">
          <span class="font-semibold text-gray-900">{{ deletingCategory.name }}</span> 카테고리를 삭제하시겠습니까?
          해당 카테고리에 연결된 상품의 카테고리 정보가 초기화될 수 있습니다.
        </p>
        <div class="flex justify-end gap-3">
          <button
            :disabled="isSubmitting"
            class="rounded-xl border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 transition hover:bg-gray-50 disabled:opacity-50"
            @click="deletingCategory = null"
          >
            취소
          </button>
          <button
            :disabled="isSubmitting"
            class="rounded-xl bg-red-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-gray-300"
            @click="confirmDelete"
          >
            {{ isSubmitting ? '삭제 중...' : '삭제' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosError } from 'axios'
import type { Category } from '~/types/catalog'
import { useCategories, useCategoryAdmin } from '~/composables/useCatalog'
import { useUiToast } from '~/composables/useUiToast'
import adminMiddleware from '~/middleware/admin'

definePageMeta({
  layout: 'admin',
  middleware: [adminMiddleware],
})

interface ApiErrorBody {
  message?: string
}

const { showToast } = useUiToast()
const {
  categories,
  isLoading,
  errorMessage,
  fetchCategories,
} = useCategories()
const { isSubmitting, createCategory, updateCategory, deleteCategory } = useCategoryAdmin()

const newName = ref('')
const createError = ref('')
const editingId = ref<number | null>(null)
const editName = ref('')
const deletingCategory = ref<Category | null>(null)

const toErrorMessage = (error: unknown, fallback: string): string => {
  const axiosError = error as AxiosError<ApiErrorBody>
  return axiosError.response?.data?.message ?? fallback
}

const handleCreate = async () => {
  createError.value = ''
  try {
    const created = await createCategory(newName.value)
    categories.value.push(created)
    newName.value = ''
    showToast(`"${created.name}" 카테고리가 추가되었습니다.`, 'success')
  } catch (error: unknown) {
    const axiosError = error as AxiosError<ApiErrorBody>
    if (axiosError.response?.status === 409) {
      createError.value = '이미 존재하는 카테고리 이름입니다.'
      return
    }
    createError.value = toErrorMessage(error, '카테고리 추가에 실패했습니다.')
  }
}

const startEdit = (category: Category) => {
  editingId.value = category.id
  editName.value = category.name
}

const cancelEdit = () => {
  editingId.value = null
  editName.value = ''
}

const handleUpdate = async (id: number) => {
  if (!editName.value) return
  try {
    const updated = await updateCategory(id, editName.value)
    const idx = categories.value.findIndex(c => c.id === id)
    if (idx !== -1) categories.value[idx] = updated
    cancelEdit()
    showToast(`"${updated.name}"으로 수정되었습니다.`, 'success')
  } catch (error: unknown) {
    const axiosError = error as AxiosError<ApiErrorBody>
    if (axiosError.response?.status === 409) {
      showToast('이미 존재하는 카테고리 이름입니다.', 'warning')
      return
    }
    showToast(toErrorMessage(error, '카테고리 수정에 실패했습니다.'), 'error')
  }
}

const handleDelete = (category: Category) => {
  deletingCategory.value = category
}

const confirmDelete = async () => {
  if (!deletingCategory.value) return
  const target = deletingCategory.value
  try {
    await deleteCategory(target.id)
    categories.value = categories.value.filter(c => c.id !== target.id)
    deletingCategory.value = null
    showToast(`"${target.name}" 카테고리가 삭제되었습니다.`, 'success')
  } catch (error: unknown) {
    const axiosError = error as AxiosError<ApiErrorBody>
    if (axiosError.response?.status === 404) {
      showToast('카테고리를 찾을 수 없습니다.', 'warning')
      deletingCategory.value = null
      return
    }
    showToast(toErrorMessage(error, '카테고리 삭제에 실패했습니다.'), 'error')
  }
}

onMounted(async () => {
  await fetchCategories()
})
</script>
