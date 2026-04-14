<template>
  <div class="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
    <NuxtLink
      to="/cart"
      class="mb-6 inline-flex items-center gap-1 text-sm font-medium text-gray-500 transition hover:text-indigo-600"
    >
      ← 장바구니로 돌아가기
    </NuxtLink>

    <div v-if="isLoading" class="grid gap-8 lg:grid-cols-[1fr_360px]">
      <div class="space-y-4">
        <div class="h-48 animate-pulse rounded-2xl bg-gray-200" />
        <div class="h-64 animate-pulse rounded-2xl bg-gray-200" />
      </div>
      <div class="h-80 animate-pulse rounded-2xl bg-gray-200" />
    </div>

    <div
      v-else-if="fetchError"
      class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ fetchError }}
    </div>

    <div v-else-if="order" class="grid gap-8 lg:grid-cols-[1fr_360px]">
      <div class="space-y-6">
        <div class="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
          <h2 class="mb-4 text-base font-bold text-gray-900">주문 상품 확인</h2>
          <div class="space-y-3">
            <div
              v-for="item in order.items"
              :key="item.productId"
              class="flex items-center gap-4"
            >
              <div class="h-14 w-14 shrink-0 rounded-lg bg-gray-100">
                <img
                  :src="`/images/default-album.svg`"
                  :alt="item.productTitle"
                  class="h-full w-full rounded-lg object-cover"
                />
              </div>
              <div class="flex-1">
                <p class="text-sm font-medium text-gray-900">
                  {{ item.productTitle }}
                </p>
                <p class="text-xs text-gray-500">
                  수량: {{ item.quantity }} &nbsp;|&nbsp; 소계: ₩
                  {{ formatPrice(item.price * item.quantity) }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
          <ShippingForm
            ref="shippingFormRef"
            v-model:shipping-info="shippingInfo"
          />
        </div>
      </div>

      <div class="lg:sticky lg:top-24 lg:self-start space-y-4">
        <OrderSummaryPanel
          :items="order.items"
          :total-amount="order.totalAmount"
        />

        <div class="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
          <h2 class="mb-3 text-base font-bold text-gray-900">결제 수단 선택</h2>
          <div class="space-y-2">
            <label
              v-for="method in paymentMethods"
              :key="method.value"
              class="flex cursor-pointer items-center gap-3 rounded-lg border border-gray-200 p-3 transition hover:border-indigo-400"
              :class="{ 'border-indigo-500 bg-indigo-50': selectedMethod === method.value }"
            >
              <input
                v-model="selectedMethod"
                type="radio"
                :value="method.value"
                class="accent-indigo-600"
              />
              <span class="text-sm font-medium text-gray-800">
                {{ method.label }}
              </span>
            </label>
          </div>
        </div>

        <button
          type="button"
          :disabled="isPaying"
          class="w-full rounded-xl bg-indigo-600 py-4 text-sm font-bold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-gray-300"
          @click="handlePay"
        >
          {{ isPaying ? '처리 중...' : `결제하기 ₩ ${formatPrice(order.totalAmount)}` }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import OrderSummaryPanel from '~/components/checkout/OrderSummaryPanel.vue'
import ShippingForm from '~/components/checkout/ShippingForm.vue'
import { useUiToast } from '~/composables/useUiToast'
import { useAuthStore } from '~/stores/auth'
import type { OrderDetail, ShippingInfo, PaymentMethod, PaymentPrepareResponse } from '~/types/checkout'

definePageMeta({ layout: 'default', middleware: 'auth' })

const route = useRoute()
const { showToast } = useUiToast()
const authStore = useAuthStore()
const { $axios } = useNuxtApp()
const axios = $axios as AxiosInstance

const orderId = computed(() => {
  const raw = Array.isArray(route.query.orderId) ? route.query.orderId[0] : route.query.orderId
  const parsed = Number(raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const isLoading = ref(true)
const fetchError = ref('')
const isPaying = ref(false)
const order = ref<OrderDetail | null>(null)

const shippingInfo = ref<ShippingInfo>({
  recipientName: '',
  phone: '',
  address: '',
  addressDetail: '',
})

const selectedMethod = ref<PaymentMethod>('CARD')

const shippingFormRef = ref<InstanceType<typeof ShippingForm> | null>(null)

const paymentMethods = [
  { value: 'CARD' as PaymentMethod, label: '신용/체크카드' },
  { value: 'VIRTUAL_ACCOUNT' as PaymentMethod, label: '계좌이체' },
  { value: 'KAKAO_PAY' as PaymentMethod, label: '카카오페이' },
  { value: 'NAVER_PAY' as PaymentMethod, label: '네이버페이' },
]

function formatPrice(n: number) {
  return new Intl.NumberFormat('ko-KR').format(n)
}

onMounted(async () => {
  if (!orderId.value) {
    await navigateTo('/cart')
    return
  }
  try {
    const res = await axios.get<OrderDetail>(`/api/v1/orders/${orderId.value}`)
    if (res.data.status === 'PAID' || res.data.status === 'SHIPPED' || res.data.status === 'COMPLETED') {
      showToast('이미 결제된 주문입니다.', 'info')
      await navigateTo(`/orders/${orderId.value}`)
      return
    }
    order.value = res.data
  } catch {
    fetchError.value = '주문 정보를 불러오지 못했습니다.'
  } finally {
    isLoading.value = false
  }
})

async function handlePay() {
  const isValid = shippingFormRef.value?.validateAll()
  if (!isValid) {
    showToast('배송지 정보를 올바르게 입력해주세요.', 'warning')
    return
  }
  if (!order.value) return

  isPaying.value = true
  try {
    const prepareRes = await axios.post<PaymentPrepareResponse>('/api/v1/payments/prepare', {
      orderId: order.value.id,
      amount: order.value.totalAmount,
    })

    const { clientKey, amount } = prepareRes.data

    if (!clientKey || clientKey.startsWith('test_ck_REPLACE') || clientKey.startsWith('test_sk_REPLACE')) {
      throw new Error('Toss Payments 클라이언트 키가 설정되지 않았습니다. 서버의 toss.payments.client-key 값을 확인해주세요.')
    }

    const tossModule = await import(/* @vite-ignore */ '@tosspayments/tosspayments-sdk').catch(() => null)
    if (!tossModule) {
      throw new Error('Toss Payments SDK를 로드하지 못했습니다. (npm install @tosspayments/tosspayments-sdk)')
    }
    const tossPayments = await tossModule.loadTossPayments(clientKey)

    const payment = tossPayments.payment({
      customerKey: `member_${authStore.user?.id ?? 'anonymous'}`,
    })

    const tossMethodMap: Record<string, string> = {
      CARD: '카드',
      VIRTUAL_ACCOUNT: '계좌이체',
      KAKAO_PAY: '카카오페이',
      NAVER_PAY: '네이버페이',
    }

    await payment.requestPayment({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      method: tossMethodMap[selectedMethod.value] as any,
      amount: { currency: 'KRW', value: amount },
      orderId: String(order.value.id),
      orderName: order.value.items[0]?.productTitle ?? '음악 앨범',
      successUrl: `${window.location.origin}/checkout/success?orderId=${order.value.id}`,
      failUrl: `${window.location.origin}/checkout/fail?orderId=${order.value.id}`,
    })
  } catch (err) {
    const message = err instanceof Error ? err.message : '결제 준비에 실패했습니다.'
    showToast(message, 'error')
  } finally {
    isPaying.value = false
  }
}
</script>
