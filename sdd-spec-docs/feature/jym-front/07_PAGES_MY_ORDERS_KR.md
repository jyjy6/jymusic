# 07_PAGES_MY_ORDERS — 내 주문 페이지 (유저)

> **대상**: `jym-front`
> **페이지**: `/me/orders`, `/me/orders/[id]`
> **API**: `GET /api/v1/orders`, `GET /api/v1/orders/{id}` (기존 `jym-order-service`)
> **실시간 갱신**: SSE 구독 (`09_SSE_NOTIFICATION_CLIENT_KR.md` 참조)

---

## 1. 페이지 목록

| 경로 | 이름 | 인증 | 설명 |
|---|---|---|---|
| `/me/orders` | 내 주문 목록 | 필요 | 로그인한 사용자의 모든 주문을 최신순으로 조회. 상태 탭 필터 제공 |
| `/me/orders/[id]` | 주문 상세 | 필요 | 특정 주문의 아이템·상태·결제/배송 타임라인 표시 |

**레이아웃**: `layouts/default.vue`. 왼쪽 사이드바(데스크톱) 또는 상단 탭(모바일)에 `/me`, `/me/orders` 내비 제공.

---

## 2. 공통 타입 (`types/order.ts` 신규)

```ts
export type OrderStatus =
  | 'PENDING'
  | 'STOCK_RESERVED'
  | 'PAID'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'

export interface OrderItemDetail {
  productId: number
  productTitle: string
  unitPrice: number
  quantity: number
}

export interface OrderSummary {
  id: number
  totalAmount: number
  status: OrderStatus
  createdAt: string
}

export interface OrderDetail extends OrderSummary {
  items: OrderItemDetail[]
  updatedAt: string
}

/** 상태별 UI 메타 — 뱃지 색상·한글 라벨·아이콘 */
export const ORDER_STATUS_META: Record<OrderStatus, { label: string; color: string; step: number }> = {
  PENDING:        { label: '결제 대기',   color: 'bg-gray-100 text-gray-700',     step: 1 },
  STOCK_RESERVED: { label: '재고 확보',   color: 'bg-blue-100 text-blue-700',     step: 2 },
  PAID:           { label: '결제 완료',   color: 'bg-indigo-100 text-indigo-700', step: 3 },
  SHIPPED:        { label: '배송 중',     color: 'bg-purple-100 text-purple-700', step: 4 },
  COMPLETED:      { label: '구매 확정',   color: 'bg-green-100 text-green-700',   step: 5 },
  CANCELLED:      { label: '취소됨',      color: 'bg-red-100 text-red-700',       step: 0 },
}

export const ORDER_STATUS_FLOW: OrderStatus[] =
  ['PENDING', 'STOCK_RESERVED', 'PAID', 'SHIPPED', 'COMPLETED']
```

---

## 3. 컴포저블 — `composables/useMyOrders.ts`

```ts
import type { AxiosInstance } from 'axios'
import type { OrderDetail, OrderSummary } from '~/types/order'

export const useMyOrders = () => {
  const { $axios } = useNuxtApp()
  const axios = $axios as AxiosInstance

  const fetchList = async (): Promise<OrderSummary[]> => {
    const { data } = await axios.get<OrderSummary[]>('/api/v1/orders')
    return data
  }

  const fetchDetail = async (orderId: number): Promise<OrderDetail> => {
    const { data } = await axios.get<OrderDetail>(`/api/v1/orders/${orderId}`)
    return data
  }

  return { fetchList, fetchDetail }
}
```

> **주의**: 기존 `/api/v1/orders` 는 List 반환. 유저 MyPage용 페이징은 아직 스펙에 없으므로 이 스펙에서는 목록 길이가 많아지면 클라이언트 정렬 + "더보기" 페이징을 적용한다. 백엔드 페이징 필요 시 향후 스펙 확장.

---

## 4. `/me/orders` — 주문 목록 페이지

### 파일: `app/pages/me/orders/index.vue`

**UI 요구사항**

1. 상단에 상태 필터 탭 — `전체 | 진행중 | 완료 | 취소`
   - "진행중" = `PENDING` + `STOCK_RESERVED` + `PAID` + `SHIPPED`
   - "완료" = `COMPLETED`
   - "취소" = `CANCELLED`
2. 주문 카드 (리스트 아이템):
   - 좌측: 상품 썸네일 스택 (최대 3장, 나머지는 `+N`)
   - 중앙: 대표 상품명 "OOO 외 N건", 생성일, 총 금액
   - 우측: 상태 뱃지 + `상세 보기` 버튼 (→ `/me/orders/{id}`)
3. 로딩/빈 상태/에러 처리
4. **실시간 갱신**: SSE로 `ORDER_STATUS_CHANGED` 수신 시 해당 `orderId` 카드의 뱃지 즉시 업데이트 + 살짝 펄스 애니메이션 (`animate-pulse` 1회)

**구현 (Composition API + `<script setup lang="ts">` + Tailwind only)**:

```vue
<template>
  <div class="max-w-4xl mx-auto px-4 py-8">
    <!-- 헤더 -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">내 주문</h1>
        <p class="text-sm text-gray-500 mt-1">총 {{ orders.length }}건의 주문</p>
      </div>
      <NuxtLink to="/products"
        class="text-sm text-indigo-600 hover:underline">쇼핑 계속하기 →</NuxtLink>
    </div>

    <!-- 상태 필터 탭 -->
    <div class="flex gap-2 mb-6 border-b border-gray-200 overflow-x-auto">
      <button v-for="tab in tabs" :key="tab.id"
        @click="activeTab = tab.id"
        :class="[
          'px-4 py-2 text-sm font-medium whitespace-nowrap -mb-px border-b-2',
          activeTab === tab.id
            ? 'border-indigo-600 text-indigo-600'
            : 'border-transparent text-gray-500 hover:text-gray-700'
        ]">
        {{ tab.label }}
        <span v-if="tab.count > 0"
          class="ml-1.5 text-xs px-1.5 py-0.5 rounded-full bg-gray-100">
          {{ tab.count }}
        </span>
      </button>
    </div>

    <!-- 로딩 -->
    <div v-if="loading" class="py-20 flex justify-center">
      <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin" />
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center py-20">
      <p class="text-gray-600 mb-4">{{ error }}</p>
      <button @click="reload"
        class="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700">
        다시 시도
      </button>
    </div>

    <!-- 빈 상태 -->
    <div v-else-if="filteredOrders.length === 0" class="text-center py-20">
      <div class="text-5xl mb-4">📦</div>
      <p class="text-gray-500 mb-4">주문 내역이 없습니다</p>
      <NuxtLink to="/products"
        class="inline-block px-5 py-2.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-medium">
        상품 둘러보기
      </NuxtLink>
    </div>

    <!-- 주문 카드 리스트 -->
    <ul v-else class="space-y-3">
      <li v-for="o in filteredOrders" :key="o.id"
        :class="[
          'bg-white border border-gray-200 rounded-xl p-4 hover:border-indigo-300 hover:shadow-sm transition',
          pulsed.has(o.id) ? 'animate-pulse ring-2 ring-indigo-400' : ''
        ]">
        <div class="flex items-center gap-4">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <span :class="['text-xs px-2 py-0.5 rounded-full font-medium',
                             ORDER_STATUS_META[o.status].color]">
                {{ ORDER_STATUS_META[o.status].label }}
              </span>
              <span class="text-xs text-gray-400">
                {{ formatDate(o.createdAt) }}
              </span>
            </div>
            <p class="text-sm font-semibold text-gray-900 truncate">
              주문번호 #{{ o.id }}
            </p>
            <p class="text-lg font-bold text-gray-900 mt-1">
              {{ formatPrice(o.totalAmount) }}원
            </p>
          </div>
          <NuxtLink :to="`/me/orders/${o.id}`"
            class="px-4 py-2 text-sm text-indigo-600 hover:bg-indigo-50 rounded-lg font-medium whitespace-nowrap">
            상세 보기 →
          </NuxtLink>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { useMyOrders } from '~/composables/useMyOrders'
import { ORDER_STATUS_META, type OrderStatus, type OrderSummary } from '~/types/order'
import { useNotificationStream } from '~/composables/useNotificationStream'

definePageMeta({
  layout: 'default',
  middleware: [() => {
    if (import.meta.server) return
    const auth = useAuthStore()
    if (!auth.isLoggedIn) return navigateTo('/auth/login')
  }],
})

const { fetchList } = useMyOrders()
const orders = ref<OrderSummary[]>([])
const loading = ref(true)
const error = ref('')
const pulsed = reactive(new Set<number>())

type TabId = 'ALL' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
const activeTab = ref<TabId>('ALL')

const inProgressStatuses: OrderStatus[] = ['PENDING', 'STOCK_RESERVED', 'PAID', 'SHIPPED']

const filteredOrders = computed(() => {
  switch (activeTab.value) {
    case 'IN_PROGRESS': return orders.value.filter(o => inProgressStatuses.includes(o.status))
    case 'COMPLETED':   return orders.value.filter(o => o.status === 'COMPLETED')
    case 'CANCELLED':   return orders.value.filter(o => o.status === 'CANCELLED')
    default:            return orders.value
  }
})

const tabs = computed(() => [
  { id: 'ALL' as TabId,         label: '전체',    count: orders.value.length },
  { id: 'IN_PROGRESS' as TabId, label: '진행중',  count: orders.value.filter(o => inProgressStatuses.includes(o.status)).length },
  { id: 'COMPLETED' as TabId,   label: '완료',    count: orders.value.filter(o => o.status === 'COMPLETED').length },
  { id: 'CANCELLED' as TabId,   label: '취소',    count: orders.value.filter(o => o.status === 'CANCELLED').length },
])

const reload = async () => {
  loading.value = true; error.value = ''
  try { orders.value = await fetchList() }
  catch (e) { error.value = (e as { response?: { data?: { message?: string } } })
                              .response?.data?.message ?? '주문 목록을 불러오지 못했습니다.' }
  finally { loading.value = false }
}
onMounted(reload)

// SSE 실시간 갱신
const { onOrderStatusChanged } = useNotificationStream()
onOrderStatusChanged((payload) => {
  const target = orders.value.find(o => o.id === payload.orderId)
  if (target) {
    target.status = payload.status
    pulsed.add(target.id)
    setTimeout(() => pulsed.delete(target.id), 1500)
  } else {
    // 새로 생긴 주문(여러 탭 동기화 시나리오) — 리스트 전체 재조회
    reload()
  }
})

const formatPrice = (n: number) => n.toLocaleString('ko-KR')
const formatDate = (iso: string) => new Date(iso)
  .toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit',
                             hour: '2-digit', minute: '2-digit' })
</script>
```

---

## 5. `/me/orders/[id]` — 주문 상세 페이지

### 파일: `app/pages/me/orders/[id].vue`

**UI 요구사항**

1. **주문 상태 타임라인** — 가로 스텝퍼 (`PENDING → STOCK_RESERVED → PAID → SHIPPED → COMPLETED`)
   - 완료된 단계: indigo 체크
   - 현재 단계: indigo 링 + 펄스
   - 미도달 단계: gray 원
   - `CANCELLED` 상태면 전체 빨간색 경고 카드로 대체 표시
2. **주문 아이템 리스트** — 썸네일(카탈로그에서 가져오거나 저장된 `productTitle` 스냅샷만 표시), 수량, 단가, 소계
3. **결제 요약** — 총 금액 큰 글씨
4. **메타 정보** — 주문일시, 최근 업데이트일시, 주문번호
5. **취소 버튼** — `PENDING` 또는 `STOCK_RESERVED` 상태에서만 노출. 클릭 시 확인 모달 → `POST /api/v1/orders/{id}/cancel` (→ 본 스펙에서 엔드포인트 제안)
6. **실시간 갱신**: 현재 보고 있는 `orderId`에 대한 `ORDER_STATUS_CHANGED` 수신 시 상태 즉시 갱신 + 토스트 표시

### 엔드포인트 확장 제안 — 유저 주문 취소

현재 백엔드 OAS에는 유저가 자기 주문을 취소하는 경로가 없다. 다음을 추가 제안:

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/v1/orders/{id}/cancel` | `PENDING` / `STOCK_RESERVED` 상태에서만 허용. 내부적으로 `order.transitionTo(CANCELLED)` + Kafka `ORDER_CANCELLED` 발행 |

(백엔드 스펙 `02_IMPLEMENTATION_KR.md` 에도 추후 반영 필요.)

**구현 스켈레톤**:

```vue
<template>
  <div class="max-w-3xl mx-auto px-4 py-8">
    <button @click="$router.back()"
      class="mb-4 text-sm text-gray-500 hover:text-gray-700">← 목록으로</button>

    <div v-if="loading" class="py-20 flex justify-center">
      <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin" />
    </div>

    <div v-else-if="order">
      <div class="mb-6 flex items-baseline justify-between">
        <h1 class="text-2xl font-bold text-gray-900">주문 #{{ order.id }}</h1>
        <span :class="['text-xs px-2.5 py-1 rounded-full font-semibold',
                       ORDER_STATUS_META[order.status].color]">
          {{ ORDER_STATUS_META[order.status].label }}
        </span>
      </div>

      <!-- 취소 경고 or 타임라인 -->
      <div v-if="order.status === 'CANCELLED'"
        class="bg-red-50 border border-red-200 rounded-xl p-4 mb-6 text-red-700 text-sm">
        이 주문은 취소되었습니다.
      </div>
      <OrderStatusStepper v-else :status="order.status" class="mb-8" />

      <!-- 아이템 -->
      <section class="bg-white border border-gray-200 rounded-xl mb-4">
        <h2 class="px-5 py-3 text-sm font-semibold text-gray-700 border-b border-gray-100">주문 상품</h2>
        <ul class="divide-y divide-gray-100">
          <li v-for="it in order.items" :key="it.productId"
            class="px-5 py-4 flex items-center gap-4">
            <div class="flex-1 min-w-0">
              <p class="font-medium text-gray-900 truncate">{{ it.productTitle }}</p>
              <p class="text-xs text-gray-500 mt-0.5">
                {{ formatPrice(it.unitPrice) }}원 × {{ it.quantity }}
              </p>
            </div>
            <p class="font-semibold text-gray-900 whitespace-nowrap">
              {{ formatPrice(it.unitPrice * it.quantity) }}원
            </p>
          </li>
        </ul>
      </section>

      <!-- 결제 요약 -->
      <section class="bg-white border border-gray-200 rounded-xl p-5 mb-4">
        <div class="flex items-center justify-between">
          <span class="text-sm text-gray-500">총 결제 금액</span>
          <span class="text-2xl font-bold text-gray-900">
            {{ formatPrice(order.totalAmount) }}원
          </span>
        </div>
      </section>

      <!-- 메타 -->
      <section class="text-xs text-gray-400 space-y-1 px-1 mb-6">
        <p>주문일시: {{ formatDate(order.createdAt) }}</p>
        <p>최근 업데이트: {{ formatDate(order.updatedAt) }}</p>
      </section>

      <!-- 취소 버튼 -->
      <div v-if="cancellable" class="flex justify-end">
        <button @click="onCancel" :disabled="cancelling"
          class="px-5 py-2.5 border border-red-300 text-red-600 rounded-lg hover:bg-red-50 disabled:opacity-50 font-medium text-sm">
          {{ cancelling ? '취소 처리 중...' : '주문 취소' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AxiosInstance } from 'axios'
import { useMyOrders } from '~/composables/useMyOrders'
import { ORDER_STATUS_META, type OrderDetail } from '~/types/order'
import { useNotificationStream } from '~/composables/useNotificationStream'
import { useUiToast } from '~/composables/useUiToast'

definePageMeta({
  layout: 'default',
  middleware: [() => {
    if (import.meta.server) return
    const auth = useAuthStore()
    if (!auth.isLoggedIn) return navigateTo('/auth/login')
  }],
})

const route = useRoute()
const orderId = computed(() => Number(route.params.id))
const { fetchDetail } = useMyOrders()
const { $axios } = useNuxtApp()
const { toast } = useUiToast()

const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const cancelling = ref(false)

const cancellable = computed(() =>
  order.value && (order.value.status === 'PENDING' || order.value.status === 'STOCK_RESERVED'))

const load = async () => {
  loading.value = true
  try { order.value = await fetchDetail(orderId.value) }
  finally { loading.value = false }
}
onMounted(load)

const onCancel = async () => {
  if (!confirm('주문을 취소하시겠습니까?')) return
  cancelling.value = true
  try {
    await ($axios as AxiosInstance).post(`/api/v1/orders/${orderId.value}/cancel`)
    await load()
    toast.success('주문이 취소되었습니다.')
  } catch (e) {
    const msg = (e as { response?: { data?: { message?: string } } }).response?.data?.message
    toast.error(msg ?? '취소에 실패했습니다.')
  } finally { cancelling.value = false }
}

// SSE 실시간 갱신
const { onOrderStatusChanged } = useNotificationStream()
onOrderStatusChanged((payload) => {
  if (payload.orderId === orderId.value) {
    load()
    toast.info(payload.title)
  }
})

const formatPrice = (n: number) => n.toLocaleString('ko-KR')
const formatDate = (iso: string) => new Date(iso).toLocaleString('ko-KR')
</script>
```

---

## 6. 공용 컴포넌트 — `OrderStatusStepper.vue`

### 파일: `app/components/orders/OrderStatusStepper.vue`

```vue
<template>
  <ol class="flex items-center">
    <li v-for="(s, idx) in steps" :key="s"
      :class="['flex items-center', idx < steps.length - 1 ? 'flex-1' : '']">
      <!-- 원 -->
      <div :class="['w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold shrink-0',
                    idx < currentIdx  ? 'bg-indigo-600 text-white'
                    : idx === currentIdx ? 'bg-indigo-100 text-indigo-700 ring-4 ring-indigo-200 animate-pulse'
                    : 'bg-gray-100 text-gray-400']">
        <span v-if="idx < currentIdx">✓</span>
        <span v-else>{{ idx + 1 }}</span>
      </div>
      <!-- 라벨 -->
      <span :class="['text-[11px] ml-2 whitespace-nowrap',
                     idx <= currentIdx ? 'text-gray-700 font-medium' : 'text-gray-400']">
        {{ ORDER_STATUS_META[s].label }}
      </span>
      <!-- 연결선 -->
      <div v-if="idx < steps.length - 1"
        :class="['flex-1 h-0.5 mx-3', idx < currentIdx ? 'bg-indigo-600' : 'bg-gray-200']" />
    </li>
  </ol>
</template>

<script setup lang="ts">
import { ORDER_STATUS_FLOW, ORDER_STATUS_META, type OrderStatus } from '~/types/order'

const props = defineProps<{ status: OrderStatus }>()

const steps = ORDER_STATUS_FLOW
const currentIdx = computed(() => steps.indexOf(props.status))
</script>
```

---

## 7. 내비게이션 업데이트

`layouts/default.vue` 의 로그인 사용자 드롭다운/프로필 메뉴에 **"내 주문"** 링크 추가:

```vue
<NuxtLink to="/me/orders" class="block px-4 py-2 hover:bg-gray-50">
  내 주문
</NuxtLink>
```

`/me` 페이지(`me.vue`)의 프로필 카드에도 "내 주문 보러가기" 버튼을 추가하면 UX가 부드럽다.

---

## 8. 체크리스트

- [ ] `/me/orders` 진입 시 비로그인 → `/auth/login` 리다이렉트 (미들웨어)
- [ ] 401 응답 → 기존 axios 인터셉터가 로그인 페이지로 리다이렉트
- [ ] 로딩/에러/빈 상태 모두 구현
- [ ] SSE 수신 시 목록/상세 페이지 실시간 반영
- [ ] Tailwind 유틸리티만 사용 (헌법 §2.1 — scoped `<style>` 금지)
- [ ] Composition API + `<script setup lang="ts">` 준수
- [ ] 모든 API 호출은 `$axios` 사용 (헌법 §2.1)
