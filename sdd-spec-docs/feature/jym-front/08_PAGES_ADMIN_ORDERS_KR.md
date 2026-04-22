# 08_PAGES_ADMIN_ORDERS — 운영자 주문 관리 페이지

> **대상**: `jym-front`
> **페이지**: `/admin/orders`, `/admin/orders/[id]`
> **API**: `jym-order-service` 관리자 엔드포인트 (`04_ADMIN_ORDERS_KR.md`)
> **접근**: `ROLE_ADMIN` — 미들웨어에서 검증
> **실시간 알림**: 관리자 SSE 구독 (`09_SSE_NOTIFICATION_CLIENT_KR.md`)

---

## 1. 페이지 목록

| 경로 | 이름 | 접근 | 주요 API |
|---|---|---|---|
| `/admin/orders` | 전체 주문 검색·목록 | ROLE_ADMIN | `GET /api/v1/admin/orders`, `GET /api/v1/admin/orders/stats` |
| `/admin/orders/[id]` | 주문 상세 + 상태 변경 | ROLE_ADMIN | `GET /api/v1/admin/orders/{id}`, `PATCH /api/v1/admin/orders/{id}/status` |

---

## 2. 미들웨어 — `middleware/admin.ts`

이미 `03_PAGES_CATALOG_ADMIN_KR.md` 기반 admin 미들웨어가 있다면 재사용한다. 없을 경우:

```ts
// app/middleware/admin.ts
export default defineNuxtRouteMiddleware(() => {
  if (import.meta.server) return
  const auth = useAuthStore()
  if (!auth.isLoggedIn) return navigateTo('/auth/login')
  if (auth.user?.role !== 'ROLE_ADMIN') return navigateTo('/')
})
```

각 페이지에서:

```ts
definePageMeta({ layout: 'default', middleware: ['admin'] })
```

---

## 3. 타입 (`types/admin-order.ts`)

```ts
import type { OrderStatus, OrderItemDetail } from '~/types/order'

export interface AdminOrderSummary {
  orderId: number
  memberId: number
  username: string
  nickname: string
  totalAmount: number
  status: OrderStatus
  itemCount: number
  firstItemTitle: string
  createdAt: string
  updatedAt: string
}

export interface AdminOrderDetail extends AdminOrderSummary {
  email: string
  items: OrderItemDetail[]
  allowedNextStatuses: OrderStatus[]
}

export interface AdminOrderSearchParams {
  keyword?: string
  productTitle?: string
  status?: OrderStatus
  statuses?: OrderStatus[]
  startDate?: string
  endDate?: string
  minAmount?: number
  maxAmount?: number
  page?: number
  size?: number
  sort?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
```

---

## 4. 컴포저블 — `composables/useAdminOrders.ts`

```ts
import type { AxiosInstance } from 'axios'
import type {
  AdminOrderSummary, AdminOrderDetail, AdminOrderSearchParams, Page
} from '~/types/admin-order'
import type { OrderStatus } from '~/types/order'

export const useAdminOrders = () => {
  const { $axios } = useNuxtApp()
  const axios = $axios as AxiosInstance

  const search = async (params: AdminOrderSearchParams): Promise<Page<AdminOrderSummary>> => {
    const { data } = await axios.get<Page<AdminOrderSummary>>('/api/v1/admin/orders', {
      params: {
        ...params,
        statuses: params.statuses?.join(','),  // 콤마 직렬화 (백엔드 explode=false)
      },
    })
    return data
  }

  const detail = async (orderId: number): Promise<AdminOrderDetail> => {
    const { data } = await axios.get<AdminOrderDetail>(`/api/v1/admin/orders/${orderId}`)
    return data
  }

  const updateStatus = async (
    orderId: number, status: OrderStatus, reason?: string,
  ): Promise<AdminOrderDetail> => {
    const { data } = await axios.patch<AdminOrderDetail>(
      `/api/v1/admin/orders/${orderId}/status`,
      { status, reason },
    )
    return data
  }

  const stats = async (): Promise<Record<OrderStatus, number>> => {
    const { data } = await axios.get<Record<OrderStatus, number>>('/api/v1/admin/orders/stats')
    return data
  }

  return { search, detail, updateStatus, stats }
}
```

---

## 5. `/admin/orders` — 주문 목록 · 검색 페이지

### 파일: `app/pages/admin/orders/index.vue`

**UI 요구사항**

1. **상단 통계 카드** — 상태별 카운트 (`stats()` 응답 기반). 클릭 시 해당 상태 필터 적용.
2. **검색 폼**
   - 유저 검색: `username / nickname` 부분 일치 (`keyword`)
   - 상품명 검색: `productTitle`
   - 날짜 범위: `startDate`, `endDate` (date picker)
   - 상태 다중 선택: 체크박스 그룹 (`statuses`)
   - 금액 범위: `minAmount`, `maxAmount`
   - [검색] / [초기화] 버튼
3. **목록 테이블** (데스크톱) / **카드 리스트** (모바일, Tailwind 반응형 — `md:` 분기)
   - 컬럼: 주문번호 · 회원(username/nickname) · 대표 상품 · 총액 · 상태 · 생성일 · 액션
   - 행 클릭 시 상세 페이지 이동
   - 상태 뱃지 색상은 `ORDER_STATUS_META` 재사용
4. **페이지네이션**
   - `< 1 2 3 ... 10 >` 형태 하단 중앙
   - 페이지 크기 선택 (10/20/50, 기본 20)
5. **URL 쿼리 동기화**
   - 검색 조건을 `route.query`에 반영 → 새로고침·뒤로가기·공유 시 동일 결과 재현
6. **신규 주문 실시간 배지**
   - 관리자 SSE (`NOTI_ADMIN_ORDER_CREATED`) 수신 시 상단 토스트 + "새 주문 N건" 플로팅 배너 표시 (클릭 시 현재 리스트 재조회)

### 구현 스켈레톤

```vue
<template>
  <div class="max-w-7xl mx-auto px-4 py-8">
    <!-- 헤더 -->
    <header class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900">주문 관리</h1>
      <button @click="reload"
        class="text-sm text-indigo-600 hover:underline">↻ 새로고침</button>
    </header>

    <!-- 통계 카드 -->
    <div class="grid grid-cols-2 md:grid-cols-6 gap-3 mb-6">
      <button v-for="s in statCards" :key="s.key"
        @click="applyStatusFilter(s.status)"
        :class="['p-4 rounded-xl border text-left transition',
                 isStatusActive(s.status)
                   ? 'border-indigo-500 bg-indigo-50'
                   : 'border-gray-200 bg-white hover:border-gray-300']">
        <p class="text-xs text-gray-500 mb-1">{{ s.label }}</p>
        <p class="text-2xl font-bold text-gray-900">{{ s.count }}</p>
      </button>
    </div>

    <!-- 검색 폼 -->
    <form @submit.prevent="onSearch"
      class="bg-white border border-gray-200 rounded-xl p-5 mb-6 grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="md:col-span-2">
        <label class="block text-xs text-gray-500 mb-1">회원 (ID/닉네임)</label>
        <input v-model="form.keyword" type="text" placeholder="예: johndoe"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500" />
      </div>
      <div class="md:col-span-2">
        <label class="block text-xs text-gray-500 mb-1">상품명</label>
        <input v-model="form.productTitle" type="text" placeholder="예: Nocturne"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500" />
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">시작일</label>
        <input v-model="form.startDate" type="date"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">종료일</label>
        <input v-model="form.endDate" type="date"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">최소 금액</label>
        <input v-model.number="form.minAmount" type="number" min="0"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">최대 금액</label>
        <input v-model.number="form.maxAmount" type="number" min="0"
          class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
      </div>
      <div class="md:col-span-4">
        <label class="block text-xs text-gray-500 mb-2">상태 (다중 선택)</label>
        <div class="flex flex-wrap gap-2">
          <label v-for="s in allStatuses" :key="s"
            :class="['cursor-pointer px-3 py-1.5 rounded-full text-xs font-medium border',
                     form.statuses.includes(s)
                       ? 'bg-indigo-600 text-white border-indigo-600'
                       : 'bg-white text-gray-600 border-gray-300']">
            <input type="checkbox" :value="s" v-model="form.statuses" class="hidden" />
            {{ ORDER_STATUS_META[s].label }}
          </label>
        </div>
      </div>
      <div class="md:col-span-4 flex justify-end gap-2">
        <button type="button" @click="onReset"
          class="px-4 py-2 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50">
          초기화
        </button>
        <button type="submit"
          class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700">
          검색
        </button>
      </div>
    </form>

    <!-- 신규 주문 플로팅 배너 -->
    <div v-if="newOrdersCount > 0"
      class="mb-4 bg-indigo-600 text-white rounded-lg px-4 py-3 flex items-center justify-between shadow-sm">
      <span class="text-sm font-medium">🔔 새 주문 {{ newOrdersCount }}건이 들어왔습니다.</span>
      <button @click="acceptNewOrders"
        class="text-xs bg-white/20 hover:bg-white/30 px-3 py-1.5 rounded-md font-semibold">
        목록 갱신
      </button>
    </div>

    <!-- 결과 테이블 -->
    <div v-if="loading" class="py-20 flex justify-center">
      <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin" />
    </div>
    <div v-else-if="page && page.content.length === 0"
      class="py-16 text-center text-gray-500">
      검색 결과가 없습니다.
    </div>
    <div v-else-if="page" class="bg-white border border-gray-200 rounded-xl overflow-hidden">
      <!-- 데스크톱 테이블 -->
      <table class="hidden md:table w-full text-sm">
        <thead class="bg-gray-50 text-gray-600 text-left">
          <tr>
            <th class="px-4 py-3 font-medium">#</th>
            <th class="px-4 py-3 font-medium">회원</th>
            <th class="px-4 py-3 font-medium">대표 상품</th>
            <th class="px-4 py-3 font-medium text-right">금액</th>
            <th class="px-4 py-3 font-medium">상태</th>
            <th class="px-4 py-3 font-medium">생성일</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="o in page.content" :key="o.orderId"
            class="hover:bg-gray-50 cursor-pointer"
            @click="$router.push(`/admin/orders/${o.orderId}`)">
            <td class="px-4 py-3 font-mono">#{{ o.orderId }}</td>
            <td class="px-4 py-3">
              <p class="font-medium text-gray-900">{{ o.nickname }}</p>
              <p class="text-xs text-gray-500">@{{ o.username }}</p>
            </td>
            <td class="px-4 py-3 max-w-[260px] truncate">
              {{ o.firstItemTitle }}
              <span v-if="o.itemCount > 1" class="text-gray-400">외 {{ o.itemCount - 1 }}건</span>
            </td>
            <td class="px-4 py-3 text-right font-semibold">{{ formatPrice(o.totalAmount) }}원</td>
            <td class="px-4 py-3">
              <span :class="['text-xs px-2 py-0.5 rounded-full font-medium',
                             ORDER_STATUS_META[o.status].color]">
                {{ ORDER_STATUS_META[o.status].label }}
              </span>
            </td>
            <td class="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{{ formatDate(o.createdAt) }}</td>
            <td class="px-4 py-3 text-right">
              <span class="text-indigo-600 text-xs">상세 →</span>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 모바일 카드 -->
      <ul class="md:hidden divide-y divide-gray-100">
        <li v-for="o in page.content" :key="o.orderId"
          class="p-4" @click="$router.push(`/admin/orders/${o.orderId}`)">
          <div class="flex items-center justify-between mb-1">
            <span class="font-mono text-xs text-gray-500">#{{ o.orderId }}</span>
            <span :class="['text-xs px-2 py-0.5 rounded-full',
                           ORDER_STATUS_META[o.status].color]">
              {{ ORDER_STATUS_META[o.status].label }}
            </span>
          </div>
          <p class="font-medium text-gray-900">{{ o.nickname }} <span class="text-xs text-gray-400">@{{ o.username }}</span></p>
          <p class="text-sm text-gray-600 truncate">{{ o.firstItemTitle }}</p>
          <div class="flex items-center justify-between mt-2">
            <span class="text-sm font-semibold">{{ formatPrice(o.totalAmount) }}원</span>
            <span class="text-xs text-gray-400">{{ formatDate(o.createdAt) }}</span>
          </div>
        </li>
      </ul>
    </div>

    <!-- 페이지네이션 -->
    <Pagination v-if="page && page.totalPages > 1"
      :current="page.number" :total="page.totalPages"
      @change="onPageChange" class="mt-6" />
  </div>
</template>

<script setup lang="ts">
import { useAdminOrders } from '~/composables/useAdminOrders'
import { useNotificationStream } from '~/composables/useNotificationStream'
import { useUiToast } from '~/composables/useUiToast'
import { ORDER_STATUS_META, type OrderStatus } from '~/types/order'
import type { AdminOrderSummary, AdminOrderSearchParams, Page } from '~/types/admin-order'

definePageMeta({ layout: 'default', middleware: ['admin'] })

const { search, stats } = useAdminOrders()
const { toast } = useUiToast()
const route = useRoute()
const router = useRouter()

const allStatuses: OrderStatus[] = ['PENDING', 'STOCK_RESERVED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED']

interface FormState {
  keyword: string; productTitle: string
  startDate: string; endDate: string
  minAmount: number | null; maxAmount: number | null
  statuses: OrderStatus[]
}
const form = reactive<FormState>({
  keyword: String(route.query.keyword ?? ''),
  productTitle: String(route.query.productTitle ?? ''),
  startDate: String(route.query.startDate ?? ''),
  endDate: String(route.query.endDate ?? ''),
  minAmount: route.query.minAmount ? Number(route.query.minAmount) : null,
  maxAmount: route.query.maxAmount ? Number(route.query.maxAmount) : null,
  statuses: route.query.statuses ? String(route.query.statuses).split(',') as OrderStatus[] : [],
})

const page = ref<Page<AdminOrderSummary> | null>(null)
const statusStats = ref<Record<OrderStatus, number>>({
  PENDING: 0, STOCK_RESERVED: 0, PAID: 0, SHIPPED: 0, COMPLETED: 0, CANCELLED: 0,
})
const loading = ref(false)
const newOrdersCount = ref(0)

const statCards = computed(() => [
  { key: 'all',       label: '전체',       count: Object.values(statusStats.value).reduce((a, b) => a + b, 0), status: undefined },
  ...allStatuses.map(s => ({ key: s, label: ORDER_STATUS_META[s].label, count: statusStats.value[s] ?? 0, status: s })),
])

const buildParams = (pageNum = 0, size = 20): AdminOrderSearchParams => ({
  keyword: form.keyword || undefined,
  productTitle: form.productTitle || undefined,
  statuses: form.statuses.length ? form.statuses : undefined,
  startDate: form.startDate || undefined,
  endDate: form.endDate || undefined,
  minAmount: form.minAmount ?? undefined,
  maxAmount: form.maxAmount ?? undefined,
  page: pageNum, size, sort: 'createdAt,desc',
})

const syncUrl = (params: AdminOrderSearchParams) => {
  router.replace({ query: {
    ...(params.keyword      ? { keyword: params.keyword } : {}),
    ...(params.productTitle ? { productTitle: params.productTitle } : {}),
    ...(params.statuses     ? { statuses: params.statuses.join(',') } : {}),
    ...(params.startDate    ? { startDate: params.startDate } : {}),
    ...(params.endDate      ? { endDate: params.endDate } : {}),
    ...(params.minAmount != null ? { minAmount: String(params.minAmount) } : {}),
    ...(params.maxAmount != null ? { maxAmount: String(params.maxAmount) } : {}),
    ...(params.page ? { page: String(params.page) } : {}),
  }})
}

const doSearch = async (pageNum = 0) => {
  loading.value = true
  try {
    const params = buildParams(pageNum)
    syncUrl(params)
    page.value = await search(params)
  } catch (e) {
    toast.error('주문 목록을 불러오지 못했습니다.')
  } finally { loading.value = false }
}

const reload = () => doSearch(page.value?.number ?? 0)
const onSearch = () => doSearch(0)
const onReset = () => {
  Object.assign(form, { keyword: '', productTitle: '', startDate: '', endDate: '',
                        minAmount: null, maxAmount: null, statuses: [] })
  doSearch(0)
}
const onPageChange = (p: number) => doSearch(p)

const applyStatusFilter = (status?: OrderStatus) => {
  form.statuses = status ? [status] : []
  doSearch(0)
}
const isStatusActive = (s?: OrderStatus) => s ? form.statuses.includes(s) : form.statuses.length === 0

onMounted(async () => {
  await doSearch(Number(route.query.page ?? 0))
  statusStats.value = await stats()
})

// SSE 관리자 알림
const { onAdminOrderCreated } = useNotificationStream()
onAdminOrderCreated(() => {
  newOrdersCount.value += 1
  toast.info('새 주문이 들어왔습니다.')
})
const acceptNewOrders = async () => {
  newOrdersCount.value = 0
  await reload()
  statusStats.value = await stats()
}

const formatPrice = (n: number) => n.toLocaleString('ko-KR')
const formatDate = (iso: string) => new Date(iso).toLocaleDateString('ko-KR')
</script>
```

### 공용 컴포넌트: `components/Pagination.vue`

```vue
<template>
  <nav class="flex items-center justify-center gap-1">
    <button @click="$emit('change', 0)" :disabled="current === 0"
      class="px-3 py-1.5 text-sm rounded-md border border-gray-200 disabled:opacity-40">«</button>
    <button @click="$emit('change', current - 1)" :disabled="current === 0"
      class="px-3 py-1.5 text-sm rounded-md border border-gray-200 disabled:opacity-40">‹</button>
    <button v-for="p in pages" :key="p" @click="$emit('change', p)"
      :class="['px-3 py-1.5 text-sm rounded-md border',
               p === current ? 'bg-indigo-600 text-white border-indigo-600'
                             : 'border-gray-200 hover:bg-gray-50']">
      {{ p + 1 }}
    </button>
    <button @click="$emit('change', current + 1)" :disabled="current === total - 1"
      class="px-3 py-1.5 text-sm rounded-md border border-gray-200 disabled:opacity-40">›</button>
    <button @click="$emit('change', total - 1)" :disabled="current === total - 1"
      class="px-3 py-1.5 text-sm rounded-md border border-gray-200 disabled:opacity-40">»</button>
  </nav>
</template>

<script setup lang="ts">
const props = defineProps<{ current: number; total: number }>()
defineEmits<{ change: [pageNumber: number] }>()

const WINDOW = 5
const pages = computed(() => {
  const start = Math.max(0, Math.min(props.current - 2, props.total - WINDOW))
  const end = Math.min(props.total, start + WINDOW)
  return Array.from({ length: end - start }, (_, i) => start + i)
})
</script>
```

---

## 6. `/admin/orders/[id]` — 상세 + 상태 변경

### 파일: `app/pages/admin/orders/[id].vue`

**UI 요구사항**

1. 좌측: 유저 관리자용 주문 상세 (회원 정보 카드 + 아이템 + 총액 + 상태 타임라인)
2. 우측: **상태 변경 패널**
   - 현재 상태 표시
   - "다음 상태" 드롭다운 (서버가 내려준 `allowedNextStatuses` 만 노출 — 사용자가 임의로 불법 전이 시도 불가)
   - 사유 입력란 (255자)
   - [상태 변경] 버튼 → 확인 모달 → PATCH
3. 상태 변경 히스토리는 본 MVP에선 미포함 (추후 `order_status_history` 테이블로 확장)

### 스켈레톤

```vue
<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <NuxtLink to="/admin/orders"
      class="text-sm text-gray-500 hover:text-gray-700 mb-4 inline-block">← 목록으로</NuxtLink>

    <div v-if="loading" class="py-20 flex justify-center">
      <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin" />
    </div>

    <div v-else-if="order" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 좌측 2/3 -->
      <div class="lg:col-span-2 space-y-4">
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <div class="flex items-center justify-between mb-2">
            <h1 class="text-xl font-bold">주문 #{{ order.orderId }}</h1>
            <span :class="['text-xs px-2.5 py-1 rounded-full font-semibold',
                           ORDER_STATUS_META[order.status].color]">
              {{ ORDER_STATUS_META[order.status].label }}
            </span>
          </div>
          <p class="text-xs text-gray-500">생성: {{ formatDate(order.createdAt) }}</p>
          <p class="text-xs text-gray-500">업데이트: {{ formatDate(order.updatedAt) }}</p>
        </div>

        <!-- 회원 -->
        <div class="bg-white border border-gray-200 rounded-xl p-5">
          <h2 class="text-sm font-semibold text-gray-600 mb-3">회원 정보</h2>
          <dl class="text-sm grid grid-cols-2 gap-y-2">
            <dt class="text-gray-400">ID</dt><dd>{{ order.username }}</dd>
            <dt class="text-gray-400">닉네임</dt><dd>{{ order.nickname }}</dd>
            <dt class="text-gray-400">이메일</dt><dd class="truncate">{{ order.email }}</dd>
            <dt class="text-gray-400">회원번호</dt><dd class="font-mono">{{ order.memberId }}</dd>
          </dl>
        </div>

        <!-- 상품 -->
        <div class="bg-white border border-gray-200 rounded-xl">
          <h2 class="px-5 py-3 text-sm font-semibold text-gray-600 border-b border-gray-100">주문 상품</h2>
          <ul class="divide-y divide-gray-100">
            <li v-for="it in order.items" :key="it.productId"
              class="px-5 py-3 flex items-center gap-4 text-sm">
              <div class="flex-1">
                <p class="font-medium">{{ it.productTitle }}</p>
                <p class="text-xs text-gray-500">{{ formatPrice(it.unitPrice) }}원 × {{ it.quantity }}</p>
              </div>
              <p class="font-semibold">{{ formatPrice(it.unitPrice * it.quantity) }}원</p>
            </li>
          </ul>
          <div class="px-5 py-4 bg-gray-50 border-t border-gray-100 flex justify-between">
            <span class="text-sm text-gray-600">총 금액</span>
            <span class="text-lg font-bold">{{ formatPrice(order.totalAmount) }}원</span>
          </div>
        </div>
      </div>

      <!-- 우측 1/3 : 상태 변경 패널 -->
      <aside class="lg:col-span-1">
        <div class="bg-white border border-gray-200 rounded-xl p-5 sticky top-4">
          <h2 class="text-sm font-semibold text-gray-600 mb-3">상태 변경</h2>

          <div v-if="order.allowedNextStatuses.length === 0"
            class="text-sm text-gray-400 bg-gray-50 rounded-lg p-3">
            종료된 주문은 상태를 변경할 수 없습니다.
          </div>

          <template v-else>
            <label class="block text-xs text-gray-500 mb-1">다음 상태</label>
            <select v-model="nextStatus"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm mb-3">
              <option value="">선택</option>
              <option v-for="s in order.allowedNextStatuses" :key="s" :value="s">
                {{ ORDER_STATUS_META[s].label }}
              </option>
            </select>

            <label class="block text-xs text-gray-500 mb-1">사유 (선택)</label>
            <textarea v-model="reason" maxlength="255" rows="3"
              placeholder="감사 로그에 기록됩니다."
              class="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm mb-3 resize-none" />

            <button @click="onSubmit" :disabled="!nextStatus || saving"
              class="w-full px-4 py-2.5 bg-indigo-600 text-white rounded-lg font-medium hover:bg-indigo-700 disabled:opacity-50 text-sm">
              {{ saving ? '처리 중...' : '상태 변경' }}
            </button>
          </template>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAdminOrders } from '~/composables/useAdminOrders'
import { useUiToast } from '~/composables/useUiToast'
import { ORDER_STATUS_META, type OrderStatus } from '~/types/order'
import type { AdminOrderDetail } from '~/types/admin-order'

definePageMeta({ layout: 'default', middleware: ['admin'] })

const route = useRoute()
const orderId = Number(route.params.id)
const { detail, updateStatus } = useAdminOrders()
const { toast } = useUiToast()

const order = ref<AdminOrderDetail | null>(null)
const loading = ref(true)
const saving = ref(false)
const nextStatus = ref<OrderStatus | ''>('')
const reason = ref('')

const load = async () => {
  loading.value = true
  try { order.value = await detail(orderId) }
  catch { toast.error('주문을 불러오지 못했습니다.') }
  finally { loading.value = false }
}
onMounted(load)

const onSubmit = async () => {
  if (!nextStatus.value) return
  const target = ORDER_STATUS_META[nextStatus.value].label
  if (!confirm(`주문 상태를 '${target}'로 변경하시겠습니까?`)) return
  saving.value = true
  try {
    order.value = await updateStatus(orderId, nextStatus.value as OrderStatus, reason.value || undefined)
    toast.success('상태가 변경되었습니다.')
    nextStatus.value = ''; reason.value = ''
  } catch (e) {
    const msg = (e as { response?: { data?: { message?: string } } }).response?.data?.message
    toast.error(msg ?? '상태 변경에 실패했습니다.')
  } finally { saving.value = false }
}

const formatPrice = (n: number) => n.toLocaleString('ko-KR')
const formatDate = (iso: string) => new Date(iso).toLocaleString('ko-KR')
</script>
```

---

## 7. 내비게이션 업데이트

`layouts/default.vue` — 관리자일 때 표시되는 메뉴에 **"주문 관리"** 링크 추가:

```vue
<NuxtLink v-if="auth.user?.role === 'ROLE_ADMIN'"
  to="/admin/orders" class="...">주문 관리</NuxtLink>
```

---

## 8. 체크리스트 · 수용 기준

- [ ] 비관리자 접근 시 `/` 로 리다이렉트
- [ ] 검색 조건이 URL 쿼리로 동기화되고, 새로고침 시 복원
- [ ] 상태 드롭다운은 서버가 내려준 `allowedNextStatuses` 만 노출 (불법 전이 원천 차단)
- [ ] 상태 변경 성공 시 해당 주문의 유저에게도 SSE 알림이 전송됨 (백엔드 03 스펙의 도메인 이벤트 경로)
- [ ] 관리자 SSE (`NOTI_ADMIN_ORDER_CREATED`) 수신 시 상단 배너 표시
- [ ] Tailwind only, Composition API only, Axios only (헌법 §2.1)
- [ ] 403/404 에러 응답에 대한 사용자 피드백 제공
