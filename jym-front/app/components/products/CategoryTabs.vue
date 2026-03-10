<template>
  <div class="overflow-x-auto">
    <div class="flex min-w-max items-center gap-2 border-b border-gray-200 pb-1">
      <button
        type="button"
        :class="tabClass(modelValue === null)"
        class="rounded-t-lg border-b-2 px-4 py-2 text-sm font-medium transition"
        @click="emit('update:modelValue', null)"
      >
        All
      </button>

      <button
        v-for="category in categories"
        :key="category.id"
        type="button"
        :class="tabClass(modelValue === category.id)"
        class="rounded-t-lg border-b-2 px-4 py-2 text-sm font-medium transition"
        @click="emit('update:modelValue', category.id)"
      >
        {{ category.name }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Category } from '~/types/catalog'

defineProps<{
  categories: Category[]
  modelValue: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
}>()

const tabClass = (isSelected: boolean) => {
  return isSelected
    ? 'border-indigo-600 text-indigo-600'
    : 'border-transparent text-gray-500 hover:text-gray-800'
}
</script>
