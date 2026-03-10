<template>
  <div class="mx-auto max-w-4xl px-4 py-10 sm:px-6 lg:px-8">
    <button
      type="button"
      class="mb-6 inline-flex items-center gap-2 text-sm font-medium text-gray-500 transition hover:text-indigo-600"
      @click="handleBack"
    >
      <span aria-hidden="true">←</span>
      <span>Back to products</span>
    </button>

    <div class="mb-8">
      <h1 class="text-3xl font-bold text-gray-900">
        {{ pageTitle }}
      </h1>
      <p class="mt-2 text-sm text-gray-500">
        {{ pageDescription }}
      </p>
    </div>

    <div
      v-if="categoryErrorMessage || errors.form"
      class="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ categoryErrorMessage || errors.form }}
    </div>

    <div
      v-if="isInitializing"
      class="space-y-6"
    >
      <div class="h-72 animate-pulse rounded-3xl bg-gray-200" />
      <div class="grid gap-6 md:grid-cols-2">
        <div class="h-24 animate-pulse rounded-2xl bg-gray-200" />
        <div class="h-24 animate-pulse rounded-2xl bg-gray-200" />
        <div class="h-24 animate-pulse rounded-2xl bg-gray-200" />
        <div class="h-24 animate-pulse rounded-2xl bg-gray-200" />
      </div>
      <div class="h-48 animate-pulse rounded-2xl bg-gray-200" />
    </div>

    <form
      v-else
      class="space-y-8"
      @submit.prevent="handleSubmit"
    >
      <section class="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm">
        <h2 class="mb-4 text-lg font-semibold text-gray-900">
          Image Upload
        </h2>
        <FileUpload
          accept="image/*"
          :max-size-mb="5"
          :current-image-url="currentImageUrl"
          :disabled="isSubmitting"
          @uploaded="handleUploaded"
          @cleared="handleCleared"
          @error="handleUploadError"
        />
        <p
          v-if="errors.imageKey"
          class="mt-3 text-sm font-medium text-red-600"
        >
          {{ errors.imageKey }}
        </p>
      </section>

      <section class="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm">
        <div class="grid gap-6 md:grid-cols-2">
          <div>
            <label
              for="title"
              class="mb-2 block text-sm font-semibold text-gray-700"
            >
              Album Title *
            </label>
            <input
              id="title"
              v-model.trim="form.title"
              type="text"
              maxlength="100"
              :disabled="isSubmitting"
              class="w-full rounded-xl border border-gray-300 px-4 py-3 text-sm text-gray-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-gray-100"
              placeholder="Abbey Road"
            />
            <p
              v-if="errors.title"
              class="mt-2 text-sm font-medium text-red-600"
            >
              {{ errors.title }}
            </p>
          </div>

          <div>
            <label
              for="artist"
              class="mb-2 block text-sm font-semibold text-gray-700"
            >
              Artist *
            </label>
            <input
              id="artist"
              v-model.trim="form.artist"
              type="text"
              maxlength="100"
              :disabled="isSubmitting"
              class="w-full rounded-xl border border-gray-300 px-4 py-3 text-sm text-gray-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-gray-100"
              placeholder="The Beatles"
            />
            <p
              v-if="errors.artist"
              class="mt-2 text-sm font-medium text-red-600"
            >
              {{ errors.artist }}
            </p>
          </div>

          <div>
            <label
              for="price"
              class="mb-2 block text-sm font-semibold text-gray-700"
            >
              Price (₩) *
            </label>
            <input
              id="price"
              v-model.number="form.price"
              type="number"
              min="0"
              :disabled="isSubmitting"
              class="w-full rounded-xl border border-gray-300 px-4 py-3 text-sm text-gray-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-gray-100"
              placeholder="29000"
            />
            <p
              v-if="errors.price"
              class="mt-2 text-sm font-medium text-red-600"
            >
              {{ errors.price }}
            </p>
          </div>

          <div>
            <label
              for="stockQuantity"
              class="mb-2 block text-sm font-semibold text-gray-700"
            >
              Stock Quantity *
            </label>
            <input
              id="stockQuantity"
              v-model.number="form.stockQuantity"
              type="number"
              min="0"
              step="1"
              :disabled="isSubmitting"
              class="w-full rounded-xl border border-gray-300 px-4 py-3 text-sm text-gray-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-gray-100"
              placeholder="100"
            />
            <p
              v-if="errors.stockQuantity"
              class="mt-2 text-sm font-medium text-red-600"
            >
              {{ errors.stockQuantity }}
            </p>
          </div>
        </div>

        <div class="mt-6">
          <label
            for="categoryId"
            class="mb-2 block text-sm font-semibold text-gray-700"
          >
            Category *
          </label>
          <select
            id="categoryId"
            v-model.number="form.categoryId"
            :disabled="isSubmitting || isCategoryLoading"
            class="w-full rounded-xl border border-gray-300 px-4 py-3 text-sm text-gray-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-gray-100"
          >
            <option :value="0">
              Select a category
            </option>
            <option
              v-for="category in categories"
              :key="category.id"
              :value="category.id"
            >
              {{ category.name }}
            </option>
          </select>
          <p
            v-if="errors.categoryId"
            class="mt-2 text-sm font-medium text-red-600"
          >
            {{ errors.categoryId }}
          </p>
        </div>

        <div class="mt-6">
          <label
            for="description"
            class="mb-2 block text-sm font-semibold text-gray-700"
          >
            Description
          </label>
          <textarea
            id="description"
            v-model.trim="form.description"
            rows="6"
            maxlength="2000"
            :disabled="isSubmitting"
            class="w-full rounded-2xl border border-gray-300 px-4 py-3 text-sm text-gray-900 outline-none transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 disabled:bg-gray-100"
            placeholder="The Beatles' landmark 1969 release..."
          />
          <div class="mt-2 flex items-center justify-between gap-3 text-sm text-gray-500">
            <span>{{ form.description.length }}/2000</span>
            <span v-if="errors.description" class="font-medium text-red-600">
              {{ errors.description }}
            </span>
          </div>
        </div>
      </section>

      <div class="flex justify-end gap-3">
        <button
          type="button"
          :disabled="isSubmitting"
          class="rounded-xl border border-gray-300 px-5 py-3 text-sm font-semibold text-gray-700 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
          @click="handleCancel"
        >
          Cancel
        </button>
        <button
          type="submit"
          :disabled="isSubmitting || isCategoryLoading"
          class="rounded-xl bg-indigo-600 px-5 py-3 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-300"
        >
          {{ isSubmitting ? submittingLabel : submitLabel }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import type { AxiosError } from 'axios'
import FileUpload from '~/components/common/FileUpload.vue'
import { useCatalogAdmin } from '~/composables/useCatalogAdmin'
import { useCategories } from '~/composables/useCatalog'
import { useUiToast } from '~/composables/useUiToast'

interface Props {
  mode: 'create' | 'edit'
  productId?: number | null
}

interface ApiErrorBody {
  message?: string
}

const props = withDefaults(defineProps<Props>(), {
  productId: null,
})

const { showToast } = useUiToast()
const {
  categories,
  isLoading: isCategoryLoading,
  errorMessage: categoryErrorMessage,
  fetchCategories,
} = useCategories()
const {
  form,
  errors,
  isSubmitting,
  fetchProduct,
  submitCreate,
  submitUpdate,
  setFieldError,
  toErrorMessage,
} = useCatalogAdmin(props.mode === 'edit')

const isInitializing = ref(props.mode === 'edit')
const currentImageUrl = ref<string | null>(null)

const pageTitle = computed(() => (
  props.mode === 'edit' ? 'Edit Product' : 'Add Product'
))

const pageDescription = computed(() => (
  props.mode === 'edit'
    ? '기존 상품 정보를 수정하고 최신 상태로 유지합니다.'
    : '새 상품을 등록하고 카탈로그에 노출합니다.'
))

const submitLabel = computed(() => (
  props.mode === 'edit' ? 'Save Changes' : 'Add Product'
))

const submittingLabel = computed(() => (
  props.mode === 'edit' ? 'Saving...' : 'Creating...'
))

const handleBack = async () => {
  await navigateTo('/admin/products')
}

const handleCancel = async () => {
  await navigateTo('/admin/products')
}

const handleUploaded = (objectKey: string) => {
  form.value.imageKey = objectKey
  delete errors.value.imageKey
}

const handleCleared = () => {
  currentImageUrl.value = null
  form.value.imageKey = null
  delete errors.value.imageKey
}

const handleUploadError = (message: string) => {
  setFieldError('imageKey', message)
  showToast(message, 'error')
}

const handleEditInitError = async (error: unknown) => {
  const axiosError = error as AxiosError<ApiErrorBody>

  if (axiosError.response?.status === 404) {
    showToast('Product not found.', 'warning')
    await navigateTo('/admin/products')
    return
  }

  if (axiosError.response?.status === 403) {
    showToast('Access denied.', 'warning')
    await navigateTo('/')
    return
  }

  showToast(toErrorMessage(error, 'Failed to load product.'), 'error')
  await navigateTo('/admin/products')
}

const initialize = async () => {
  await fetchCategories()

  if (props.mode !== 'edit') {
    isInitializing.value = false
    return
  }

  if (props.productId === null) {
    isInitializing.value = false
    showToast('Product not found.', 'warning')
    await navigateTo('/admin/products')
    return
  }

  try {
    const product = await fetchProduct(props.productId)
    currentImageUrl.value = product.imageUrl
  } catch (error: unknown) {
    await handleEditInitError(error)
  } finally {
    isInitializing.value = false
  }
}

const handleSubmitError = async (error: unknown) => {
  const axiosError = error as AxiosError<ApiErrorBody>

  if (axiosError.response?.status === 403) {
    showToast('Access denied.', 'warning')
    await navigateTo('/')
    return
  }

  if (axiosError.response?.status === 404) {
    showToast('Product not found.', 'warning')
    await navigateTo('/admin/products')
    return
  }

  if (axiosError.response?.status === 409) {
    showToast('Conflict detected. Please try again.', 'warning')
    return
  }

  showToast(toErrorMessage(error, 'Failed to save product.'), 'error')
}

const handleSubmit = async () => {
  try {
    if (props.mode === 'edit') {
      if (props.productId === null) {
        showToast('Product not found.', 'warning')
        await navigateTo('/admin/products')
        return
      }

      const updatedProduct = await submitUpdate(props.productId)
      if (!updatedProduct) return

      showToast('Product updated successfully.', 'success')
      await navigateTo(`/products/${updatedProduct.id}`)
      return
    }

    const createdProduct = await submitCreate()
    if (!createdProduct) return

    showToast('Product registered successfully.', 'success')
    await navigateTo(`/products/${createdProduct.id}`)
  } catch (error: unknown) {
    await handleSubmitError(error)
  }
}

onMounted(async () => {
  await initialize()
})
</script>
