<template>
  <div class="flex items-center gap-2 overflow-x-auto">
    <template v-for="(step, index) in steps" :key="step">
      <div
        class="flex h-8 min-w-8 items-center justify-center rounded-full border text-xs font-semibold"
        :class="index <= currentStepIndex ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-gray-200 bg-white text-gray-400'"
      >
        {{ index + 1 }}
      </div>
      <div
        v-if="index < steps.length - 1"
        class="h-0.5 w-10"
        :class="index < currentStepIndex ? 'bg-indigo-600' : 'bg-gray-200'"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import type { OrderStatus } from '~/types/order'

const props = defineProps<{
  status: OrderStatus
}>()

const steps: OrderStatus[] = ['PENDING', 'STOCK_RESERVED', 'PAID', 'SHIPPED', 'COMPLETED']
const currentStepIndex = computed(() => {
  if (props.status === 'CANCELLED') {
    return 0
  }
  return Math.max(0, steps.indexOf(props.status))
})
</script>
