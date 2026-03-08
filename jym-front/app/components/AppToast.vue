<template>
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="opacity-0 translate-y-2"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-2"
  >
    <div
      v-if="isVisible"
      :class="toastClass"
      class="fixed top-20 right-4 z-[60] max-w-sm rounded-xl border px-4 py-3 shadow-lg"
      role="status"
      aria-live="polite"
    >
      <div class="flex items-start gap-3">
        <span class="text-base leading-none">{{ icon }}</span>
        <p class="text-sm font-medium leading-6">{{ message }}</p>
        <button
          type="button"
          class="ml-auto text-xs font-semibold opacity-70 transition hover:opacity-100"
          @click="hideToast"
        >
          닫기
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { useUiToast } from '~/composables/useUiToast'

const { isVisible, message, type, hideToast } = useUiToast()

const toastClass = computed(() => {
  switch (type.value) {
    case 'success':
      return 'border-green-200 bg-green-50 text-green-800'
    case 'warning':
      return 'border-amber-200 bg-amber-50 text-amber-800'
    case 'error':
      return 'border-red-200 bg-red-50 text-red-800'
    default:
      return 'border-indigo-200 bg-white text-gray-800'
  }
})

const icon = computed(() => {
  switch (type.value) {
    case 'success':
      return '✅'
    case 'warning':
      return '⚠️'
    case 'error':
      return '⛔'
    default:
      return 'ℹ️'
  }
})
</script>
