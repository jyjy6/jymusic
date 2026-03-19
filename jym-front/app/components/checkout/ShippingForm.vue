<template>
  <div class="space-y-4">
    <h2 class="text-base font-bold text-gray-900">배송지 정보</h2>

    <div>
      <label class="mb-1 block text-sm font-medium text-gray-700">
        받는 분 이름 <span class="text-red-500">*</span>
      </label>
      <input
        v-model="local.recipientName"
        type="text"
        maxlength="50"
        placeholder="홍길동"
        class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        :class="{ 'border-red-400': errors.recipientName }"
        @blur="validate('recipientName')"
      />
      <p v-if="errors.recipientName" class="mt-1 text-xs text-red-500">
        {{ errors.recipientName }}
      </p>
    </div>

    <div>
      <label class="mb-1 block text-sm font-medium text-gray-700">
        연락처 <span class="text-red-500">*</span>
      </label>
      <input
        v-model="local.phone"
        type="tel"
        placeholder="010-1234-5678"
        class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        :class="{ 'border-red-400': errors.phone }"
        @blur="validate('phone')"
      />
      <p v-if="errors.phone" class="mt-1 text-xs text-red-500">
        {{ errors.phone }}
      </p>
    </div>

    <div>
      <label class="mb-1 block text-sm font-medium text-gray-700">
        주소 <span class="text-red-500">*</span>
      </label>
      <input
        v-model="local.address"
        type="text"
        maxlength="200"
        placeholder="서울시 강남구 테헤란로 123"
        class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        :class="{ 'border-red-400': errors.address }"
        @blur="validate('address')"
      />
      <p v-if="errors.address" class="mt-1 text-xs text-red-500">
        {{ errors.address }}
      </p>
    </div>

    <div>
      <label class="mb-1 block text-sm font-medium text-gray-700">
        상세 주소
      </label>
      <input
        v-model="local.addressDetail"
        type="text"
        maxlength="100"
        placeholder="101동 202호 (선택)"
        class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ShippingInfo } from '~/types/checkout'

const props = defineProps<{
  shippingInfo: ShippingInfo
}>()

const emit = defineEmits<{
  'update:shippingInfo': [value: ShippingInfo]
}>()

const local = reactive<ShippingInfo>({ ...props.shippingInfo })

const errors = reactive({
  recipientName: '',
  phone: '',
  address: '',
})

watch(local, (v) => {
  emit('update:shippingInfo', { ...v })
})

function validate(field: keyof typeof errors) {
  if (field === 'recipientName') {
    errors.recipientName = local.recipientName.trim()
      ? ''
      : '이름을 입력해주세요.'
  }
  if (field === 'phone') {
    const phoneReg = /^[\d]{2,4}-[\d]{3,4}-[\d]{4}$/
    errors.phone = phoneReg.test(local.phone)
      ? ''
      : '올바른 연락처 형식으로 입력해주세요. (예: 010-1234-5678)'
  }
  if (field === 'address') {
    errors.address = local.address.trim() ? '' : '주소를 입력해주세요.'
  }
}

function validateAll(): boolean {
  validate('recipientName')
  validate('phone')
  validate('address')
  return !errors.recipientName && !errors.phone && !errors.address
}

defineExpose({ validateAll })
</script>
