<template>
  <div class="space-y-4">
    <div class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
      <div class="flex aspect-[4/3] items-center justify-center bg-gray-50">
        <img
          v-if="previewUrl"
          :src="previewUrl"
          alt="Selected product image"
          class="h-full w-full object-cover"
        />
        <div
          v-else
          class="flex h-full w-full flex-col items-center justify-center border-2 border-dashed border-gray-300 text-gray-400"
        >
          <span class="text-3xl">📷</span>
          <p class="mt-3 text-sm font-medium">Upload a product image</p>
        </div>
      </div>

      <div class="border-t border-gray-200 p-4">
        <div class="flex flex-wrap items-center gap-3">
          <button
            type="button"
            :disabled="disabled || status === 'uploading'"
            class="inline-flex items-center rounded-xl bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-300"
            @click="openFilePicker"
          >
            {{ status === 'uploading' ? 'Uploading...' : 'Choose File' }}
          </button>

          <button
            v-if="previewUrl"
            type="button"
            :disabled="disabled || status === 'uploading'"
            class="inline-flex items-center rounded-xl border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
            @click="clearSelection"
          >
            Remove
          </button>
        </div>

        <p class="mt-3 text-sm text-gray-500">
          Supported: JPG, PNG, WEBP · Max {{ maxSizeMb }}MB
        </p>

        <div
          v-if="status === 'uploading'"
          class="mt-4"
        >
          <div class="h-2 overflow-hidden rounded-full bg-gray-200">
            <div
              class="h-full rounded-full bg-indigo-600 transition-all"
              :style="{ width: `${uploadProgress}%` }"
            />
          </div>
          <p class="mt-2 text-sm font-medium text-gray-600">
            {{ uploadProgress }}%
          </p>
        </div>

        <p
          v-if="errorMessage"
          class="mt-3 text-sm font-medium text-red-600"
        >
          {{ errorMessage }}
        </p>
      </div>
    </div>

    <input
      ref="fileInput"
      type="file"
      class="hidden"
      :accept="accept"
      :disabled="disabled || status === 'uploading'"
      @change="handleFileChange"
    />
  </div>
</template>

<script setup lang="ts">
import axios, { type AxiosInstance } from 'axios'
import type { PresignedUrlResponse } from '~/types/catalog'

interface Props {
  accept?: string
  maxSizeMb?: number
  currentImageUrl?: string | null
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  accept: 'image/*',
  maxSizeMb: 5,
  currentImageUrl: null,
  disabled: false,
})

const emit = defineEmits<{
  uploaded: [objectKey: string]
  cleared: []
  error: [message: string]
}>()

const { $axios } = useNuxtApp()

const fileInput = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const previewUrl = ref<string | null>(props.currentImageUrl ?? null)
const uploadProgress = ref(0)
const status = ref<'idle' | 'uploading' | 'done' | 'error'>('idle')
const errorMessage = ref<string | null>(null)
const localPreviewUrl = ref<string | null>(null)

const revokeLocalPreview = () => {
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
    localPreviewUrl.value = null
  }
}

const setError = (message: string) => {
  status.value = 'error'
  errorMessage.value = message
  uploadProgress.value = 0
  emit('error', message)
}

const resetInputValue = () => {
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

const matchesAccept = (selectedFile: File) => {
  const accepts = props.accept
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

  if (accepts.length === 0) return true

  return accepts.some((acceptItem) => {
    if (acceptItem === '*/*') return true
    if (acceptItem.endsWith('/*')) {
      return selectedFile.type.startsWith(`${acceptItem.slice(0, -1)}`)
    }
    return selectedFile.type === acceptItem
  })
}

const openFilePicker = () => {
  fileInput.value?.click()
}

const clearSelection = () => {
  revokeLocalPreview()
  file.value = null
  previewUrl.value = null
  uploadProgress.value = 0
  status.value = 'idle'
  errorMessage.value = null
  resetInputValue()
  emit('cleared')
}

const uploadFile = async (selectedFile: File) => {
  status.value = 'uploading'
  errorMessage.value = null
  uploadProgress.value = 0

  try {
    const presignedResponse = await ($axios as AxiosInstance).post<PresignedUrlResponse>(
      '/api/v1/media/presigned-url',
      {
        filename: selectedFile.name,
        contentType: selectedFile.type,
      },
    )

    try {
      const s3Client = axios.create()
      await s3Client.put(presignedResponse.data.presignedUrl, selectedFile, {
        headers: {
          'Content-Type': selectedFile.type,
        },
        onUploadProgress: (progressEvent) => {
          if (!progressEvent.total) return
          uploadProgress.value = Math.min(
            100,
            Math.round((progressEvent.loaded / progressEvent.total) * 100),
          )
        },
      })
    } catch {
      setError('File upload failed.')
      return
    }

    revokeLocalPreview()
    localPreviewUrl.value = URL.createObjectURL(selectedFile)
    previewUrl.value = localPreviewUrl.value
    status.value = 'done'
    uploadProgress.value = 100
    emit('uploaded', presignedResponse.data.objectKey)
  } catch {
    setError('Failed to prepare upload.')
  } finally {
    resetInputValue()
  }
}

const handleFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const selectedFile = target.files?.[0]

  if (!selectedFile) return

  file.value = selectedFile
  errorMessage.value = null

  if (!matchesAccept(selectedFile)) {
    setError('Only image files are allowed.')
    return
  }

  if (selectedFile.size > props.maxSizeMb * 1024 * 1024) {
    setError(`File size exceeds ${props.maxSizeMb}MB.`)
    return
  }

  await uploadFile(selectedFile)
}

watch(
  () => props.currentImageUrl,
  (nextImageUrl) => {
    if (localPreviewUrl.value) return
    previewUrl.value = nextImageUrl ?? null
  },
)

onBeforeUnmount(() => {
  revokeLocalPreview()
})
</script>
