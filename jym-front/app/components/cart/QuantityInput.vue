<template>
  <div class="flex items-center gap-1">
    <button
      type="button"
      class="flex h-8 w-8 items-center justify-center rounded-lg border border-gray-300 bg-white text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
      :disabled="localValue <= props.min"
      @click="decrement"
    >
      −
    </button>
    <input
      type="number"
      :min="props.min"
      :max="props.max"
      :value="localValue"
      class="h-8 w-14 rounded-lg border border-gray-300 text-center text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500"
      @change="onInputChange"
      @blur="onInputChange"
      @keydown.enter.prevent="($event.target as HTMLInputElement).blur()"
    />
    <button
      type="button"
      class="flex h-8 w-8 items-center justify-center rounded-lg border border-gray-300 bg-white text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
      :disabled="localValue >= props.max"
      @click="increment"
    >
      +
    </button>
  </div>
</template>

<script setup lang="ts">
import { useUiToast } from '~/composables/useUiToast'

const props = withDefaults(
  defineProps<{
    modelValue: number
    max: number
    min?: number
  }>(),
  { min: 1 },
)

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

const { showToast } = useUiToast()
const localValue = ref(props.modelValue)

watch(
  () => props.modelValue,
  (v) => {
    localValue.value = v
  },
)

function clamp(v: number): number {
  return Math.min(props.max, Math.max(props.min, v))
}

function decrement() {
  const next = localValue.value - 1
  if (next < props.min) return
  localValue.value = next
  emit('update:modelValue', next)
}

function increment() {
  if (localValue.value >= props.max) {
    showToast(`최대 ${props.max}개까지 구매 가능합니다.`, 'warning')
    return
  }
  const next = localValue.value + 1
  localValue.value = next
  emit('update:modelValue', next)
}

function onInputChange(e: Event) {
  const raw = (e.target as HTMLInputElement).value
  const parsed = parseInt(raw, 10)

  if (isNaN(parsed)) {
    localValue.value = props.modelValue
    return
  }

  if (parsed > props.max) {
    showToast(
      `재고가 부족합니다. 최대 ${props.max}개까지 구매 가능합니다.`,
      'warning',
    )
    localValue.value = props.max
    emit('update:modelValue', props.max)
    return
  }

  const clamped = clamp(parsed)
  localValue.value = clamped
  emit('update:modelValue', clamped)
}
</script>
