import type { AxiosError, AxiosInstance } from 'axios'
import type {
  ProductCreateRequest,
  ProductDetail,
  ProductUpdateRequest,
} from '~/types/catalog'

type ProductForm = ProductCreateRequest | ProductUpdateRequest

type ProductFormField =
  | 'title'
  | 'artist'
  | 'description'
  | 'price'
  | 'stockQuantity'
  | 'categoryId'
  | 'imageKey'
  | 'form'

type ProductFormErrors = Partial<Record<ProductFormField, string>>

interface ApiErrorBody {
  message?: string
  errors?: Record<string, string>
  fieldErrors?: Array<{
    field?: string
    message?: string
  }>
}

const createInitialForm = (): ProductCreateRequest => ({
  title: '',
  artist: '',
  description: '',
  price: 0,
  stockQuantity: 0,
  categoryId: 0,
  imageKey: null,
})

const PRODUCT_FIELDS = new Set<ProductFormField>([
  'title',
  'artist',
  'description',
  'price',
  'stockQuantity',
  'categoryId',
  'imageKey',
  'form',
])

const toErrorMessage = (error: unknown, fallback: string) => {
  const axiosError = error as AxiosError<ApiErrorBody>
  return axiosError.response?.data?.message ?? fallback
}

export const useCatalogAdmin = (isEditMode = false) => {
  const { $axios } = useNuxtApp()

  const form = ref<ProductForm>(createInitialForm())
  const errors = ref<ProductFormErrors>({})
  const isSubmitting = ref(false)

  const resetForm = () => {
    form.value = createInitialForm()
    errors.value = {}
  }

  const setFieldError = (field: ProductFormField, message: string) => {
    errors.value = {
      ...errors.value,
      [field]: message,
    }
  }

  const applyServerErrors = (data?: ApiErrorBody) => {
    if (!data) return

    const nextErrors: ProductFormErrors = {}

    if (data.errors) {
      Object.entries(data.errors).forEach(([field, message]) => {
        if (PRODUCT_FIELDS.has(field as ProductFormField) && message) {
          nextErrors[field as ProductFormField] = message
        }
      })
    }

    if (Array.isArray(data.fieldErrors)) {
      data.fieldErrors.forEach((item) => {
        const field = item.field as ProductFormField | undefined
        if (field && PRODUCT_FIELDS.has(field) && item.message) {
          nextErrors[field] = item.message
        }
      })
    }

    if (Object.keys(nextErrors).length === 0 && data.message) {
      nextErrors.form = data.message
    }

    errors.value = nextErrors
  }

  const validate = () => {
    const nextErrors: ProductFormErrors = {}
    const title = form.value.title.trim()
    const artist = form.value.artist.trim()
    const description = form.value.description.trim()

    if (!title) {
      nextErrors.title = 'Album title is required.'
    } else if (title.length > 100) {
      nextErrors.title = 'Album title must be 100 characters or fewer.'
    }

    if (!artist) {
      nextErrors.artist = 'Artist is required.'
    } else if (artist.length > 100) {
      nextErrors.artist = 'Artist must be 100 characters or fewer.'
    }

    if (!Number.isFinite(form.value.price) || form.value.price < 0) {
      nextErrors.price = 'Price must be 0 or higher.'
    }

    if (
      !Number.isInteger(form.value.stockQuantity)
      || form.value.stockQuantity < 0
    ) {
      nextErrors.stockQuantity = 'Stock quantity must be a whole number of 0 or higher.'
    }

    if (!Number.isInteger(form.value.categoryId) || form.value.categoryId <= 0) {
      nextErrors.categoryId = 'Category is required.'
    }

    if (description.length > 2000) {
      nextErrors.description = 'Description must be 2000 characters or fewer.'
    }

    errors.value = nextErrors

    if (Object.keys(nextErrors).length > 0) {
      return false
    }

    form.value = {
      ...form.value,
      title,
      artist,
      description,
    }

    return true
  }

  const fetchProduct = async (id: number) => {
    const response = await ($axios as AxiosInstance).get<ProductDetail>(`/api/v1/products/${id}`)
    const product = response.data

    form.value = {
      title: product.title,
      artist: product.artist,
      description: product.description ?? '',
      price: product.price,
      stockQuantity: product.stockQuantity,
      categoryId: product.categoryId,
      imageKey: product.imageKey ?? null,
    }

    errors.value = {}

    return product
  }

  const submitCreate = async () => {
    if (!validate()) return null

    isSubmitting.value = true
    errors.value = {}

    try {
      const response = await ($axios as AxiosInstance).post<ProductDetail>(
        '/api/v1/products',
        form.value,
      )
      return response.data
    } catch (error: unknown) {
      const axiosError = error as AxiosError<ApiErrorBody>
      if (axiosError.response?.status === 400) {
        applyServerErrors(axiosError.response.data)
        return null
      }
      throw error
    } finally {
      isSubmitting.value = false
    }
  }

  const submitUpdate = async (id: number) => {
    if (!validate()) return null

    isSubmitting.value = true
    errors.value = {}

    try {
      const response = await ($axios as AxiosInstance).put<ProductDetail>(
        `/api/v1/products/${id}`,
        form.value,
      )
      return response.data
    } catch (error: unknown) {
      const axiosError = error as AxiosError<ApiErrorBody>
      if (axiosError.response?.status === 400) {
        applyServerErrors(axiosError.response.data)
        return null
      }
      throw error
    } finally {
      isSubmitting.value = false
    }
  }

  return {
    form,
    errors,
    isSubmitting,
    isEditMode,
    resetForm,
    setFieldError,
    validate,
    fetchProduct,
    submitCreate,
    submitUpdate,
    toErrorMessage,
  }
}
