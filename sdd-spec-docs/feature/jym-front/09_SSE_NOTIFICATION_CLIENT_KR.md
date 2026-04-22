# 09_SSE_NOTIFICATION_CLIENT — 프론트 실시간 알림 (EventSource)

> **대상**: `jym-front`
> **백엔드 스펙**: `sdd-spec-docs/feature/jym-order-service/03_SSE_NOTIFICATION_KR.md`
> **핵심 파일**:
> - `stores/notifications.ts` (Pinia)
> - `composables/useNotificationStream.ts`
> - `components/notifications/NotificationBell.vue`
> - `plugins/notification-stream.client.ts`

---

## 1. 요구사항 요약

1. 로그인된 사용자는 **전역 SSE 연결**을 유지하고, 주문 상태 변경 이벤트를 실시간 수신한다.
2. 관리자는 추가로 "신규 주문 알림" 스트림도 수신한다.
3. 헤더(네비게이션 바)에 **알림 벨 아이콘** — 미확인 개수 뱃지, 클릭 시 드롭다운으로 최근 알림 표시.
4. 주문 페이지(`/me/orders`, `/me/orders/[id]`, `/admin/orders/*`)는 **이벤트 구독 훅**을 통해 로컬 상태를 실시간 갱신한다.
5. 네트워크 단절 / 토큰 만료 / 탭 비활성화 시 **안정적 재연결** 처리.

---

## 2. 인증 정책 — EventSource의 한계 극복

`EventSource` API는 **커스텀 헤더(Authorization) 설정 불가**. 해결 방법 2가지 중 택1.

| 방법 | 설명 | 채택 |
|---|---|---|
| **A. `event-source-polyfill`** | npm `event-source-polyfill` 사용 → 헤더 주입 가능. 브라우저 네이티브 EventSource를 대체 | **권장** |
| B. `withCredentials: true` + HttpOnly 쿠키 | 백엔드가 Access Token을 쿠키로도 허용해야 함. Gateway CORS 조정 필요 | 복잡 — 미채택 |

### 2.1 라이브러리 설치

```bash
pnpm add event-source-polyfill
pnpm add -D @types/event-source-polyfill
```

`package.json`에 고정 버전 명시 (헌법 §2.2 — 의존성 버전 명시).

---

## 3. Pinia 스토어 — `stores/notifications.ts`

전역 알림 큐·카운트·연결 상태를 관리한다.

```ts
import { defineStore } from 'pinia'
import type { OrderStatus } from '~/types/order'

export interface NotificationItem {
  id: string                 // `${orderId}-${occurredAt}` uniq
  type: 'ORDER_STATUS_CHANGED' | 'ADMIN_ORDER_CREATED'
  orderId: number
  title: string
  message: string
  status?: OrderStatus
  occurredAt: string
  read: boolean
}

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref<NotificationItem[]>([])
  const connected = ref(false)

  const unreadCount = computed(() => items.value.filter(n => !n.read).length)

  const push = (n: Omit<NotificationItem, 'id' | 'read'>) => {
    const id = `${n.orderId}-${n.occurredAt}-${n.type}`
    if (items.value.some(x => x.id === id)) return     // 멱등성 — 중복 제거
    items.value.unshift({ ...n, id, read: false })
    if (items.value.length > 50) items.value.pop()      // 최대 50개 유지
  }

  const markAllRead = () => items.value.forEach(n => (n.read = true))
  const markRead = (id: string) => {
    const n = items.value.find(x => x.id === id); if (n) n.read = true
  }
  const clear = () => { items.value = [] }

  const setConnected = (v: boolean) => { connected.value = v }

  return { items, connected, unreadCount, push, markAllRead, markRead, clear, setConnected }
})
```

---

## 4. SSE 매니저 — `composables/useNotificationStream.ts`

**싱글턴 연결**을 유지하며, 타입별 리스너를 페이지에서 구독/해제할 수 있게 한다.

```ts
import { EventSourcePolyfill } from 'event-source-polyfill'
import type { OrderStatus } from '~/types/order'
import { useNotificationStore } from '~/stores/notifications'
import { useAuthStore } from '~/stores/auth'

export interface OrderStatusChangedPayload {
  type: 'ORDER_STATUS_CHANGED'
  orderId: number
  title: string
  message: string
  status: OrderStatus
  occurredAt: string
}

export interface AdminOrderCreatedPayload {
  type: 'ADMIN_ORDER_CREATED'
  orderId: number
  title: string
  message: string
  occurredAt: string
}

type Handler<T> = (payload: T) => void

// 모듈 스코프 — Nuxt 앱 전역 싱글턴
let userStream: EventSourcePolyfill | null = null
let adminStream: EventSourcePolyfill | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const orderHandlers = new Set<Handler<OrderStatusChangedPayload>>()
const adminHandlers = new Set<Handler<AdminOrderCreatedPayload>>()

const RECONNECT_DELAYS = [1000, 2000, 5000, 10000, 30000]  // 지수 백오프
let reconnectAttempt = 0

export const useNotificationStream = () => {
  const auth = useAuthStore()
  const store = useNotificationStore()
  const config = useRuntimeConfig()
  const baseUrl = config.public.apiBase as string    // "http://localhost:8080"

  const connect = () => {
    if (!auth.accessToken) return
    disconnect()

    userStream = new EventSourcePolyfill(`${baseUrl}/api/v1/notifications/stream`, {
      headers: { Authorization: `Bearer ${auth.accessToken}` },
      heartbeatTimeout: 60_000,       // 서버 heartbeat 15초 × 4 여유
      withCredentials: false,
    })

    userStream.addEventListener('CONNECTED', () => {
      reconnectAttempt = 0
      store.setConnected(true)
    })
    userStream.addEventListener('PING', () => { /* keepalive */ })

    userStream.addEventListener('NOTI_ORDER_STATUS_CHANGED', (ev) => {
      try {
        const payload = JSON.parse((ev as MessageEvent).data) as OrderStatusChangedPayload
        store.push(payload)
        orderHandlers.forEach(h => h(payload))
      } catch (e) { console.error('SSE parse error', e) }
    })

    userStream.onerror = () => {
      store.setConnected(false)
      scheduleReconnect()
    }

    if (auth.user?.role === 'ROLE_ADMIN') {
      adminStream = new EventSourcePolyfill(`${baseUrl}/api/v1/notifications/admin/stream`, {
        headers: { Authorization: `Bearer ${auth.accessToken}` },
        heartbeatTimeout: 60_000,
      })
      adminStream.addEventListener('NOTI_ADMIN_ORDER_CREATED', (ev) => {
        try {
          const payload = JSON.parse((ev as MessageEvent).data) as AdminOrderCreatedPayload
          store.push(payload)
          adminHandlers.forEach(h => h(payload))
        } catch (e) { console.error('SSE parse error', e) }
      })
      adminStream.onerror = () => scheduleReconnect()
    }
  }

  const scheduleReconnect = () => {
    if (reconnectTimer) return
    const delay = RECONNECT_DELAYS[Math.min(reconnectAttempt, RECONNECT_DELAYS.length - 1)]
    reconnectAttempt++
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      if (auth.isLoggedIn) connect()
    }, delay)
  }

  const disconnect = () => {
    userStream?.close(); userStream = null
    adminStream?.close(); adminStream = null
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
    store.setConnected(false)
  }

  // 페이지 레벨 구독 훅 — onUnmounted에서 자동 해제
  const onOrderStatusChanged = (handler: Handler<OrderStatusChangedPayload>) => {
    orderHandlers.add(handler)
    if (getCurrentInstance()) onUnmounted(() => orderHandlers.delete(handler))
  }
  const onAdminOrderCreated = (handler: Handler<AdminOrderCreatedPayload>) => {
    adminHandlers.add(handler)
    if (getCurrentInstance()) onUnmounted(() => adminHandlers.delete(handler))
  }

  return { connect, disconnect, onOrderStatusChanged, onAdminOrderCreated }
}
```

> **설계 포인트**
> - `userStream` / `adminStream` 은 **모듈 스코프 싱글턴** — 동일 브라우저 탭에서 중복 연결 생성을 방지.
> - 핸들러 Set은 페이지 언마운트 시 자동 정리 (`onUnmounted`) — 메모리 누수 방지.
> - 재연결은 지수 백오프(1s → 2s → 5s → 10s → 30s).
> - Pinia 스토어에 push된 알림은 **id로 중복 제거** (헌법 §멱등성 정책 반영).

---

## 5. Nuxt 플러그인 — `plugins/notification-stream.client.ts`

앱 부팅 시 인증 상태를 감시하여 SSE를 자동 시작/종료한다.

```ts
export default defineNuxtPlugin(() => {
  if (import.meta.server) return

  const auth = useAuthStore()
  const { connect, disconnect } = useNotificationStream()

  // 초기 접속 시
  if (auth.isLoggedIn) connect()

  // 로그인/로그아웃 반응
  watch(() => auth.isLoggedIn, (v) => {
    if (v) connect()
    else disconnect()
  })

  // 탭 비활성 → 유지 (SSE는 백그라운드에서도 동작 가능)
  // 탭 종료 → 브라우저가 자동 close
})
```

---

## 6. 알림 벨 컴포넌트 — `components/notifications/NotificationBell.vue`

헤더(`layouts/default.vue`) 우측 상단에 배치.

```vue
<template>
  <div class="relative" ref="root">
    <button @click="toggle"
      class="relative p-2 rounded-full hover:bg-gray-100 transition"
      aria-label="알림">
      <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-gray-600" fill="none"
        viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round"
          d="M15 17h5l-1.4-1.4A2 2 0 0118 14.2V11a6 6 0 10-12 0v3.2a2 2 0 01-.6 1.4L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
      </svg>
      <span v-if="store.unreadCount > 0"
        class="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 flex items-center justify-center
               rounded-full bg-red-500 text-[10px] font-bold text-white">
        {{ store.unreadCount > 99 ? '99+' : store.unreadCount }}
      </span>
      <span v-if="!store.connected" class="absolute top-2 right-2 w-1.5 h-1.5 rounded-full bg-gray-300"
        title="연결 끊김" />
    </button>

    <!-- 드롭다운 -->
    <div v-if="open"
      class="absolute right-0 mt-2 w-80 bg-white rounded-xl shadow-lg border border-gray-100 z-50 overflow-hidden">
      <div class="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <h3 class="text-sm font-semibold">알림</h3>
        <div class="flex gap-2 text-xs">
          <button v-if="store.unreadCount > 0" @click="store.markAllRead"
            class="text-indigo-600 hover:underline">모두 읽음</button>
          <button v-if="store.items.length > 0" @click="store.clear"
            class="text-gray-400 hover:text-gray-600">비우기</button>
        </div>
      </div>

      <div v-if="store.items.length === 0" class="px-4 py-8 text-center text-sm text-gray-400">
        새로운 알림이 없습니다.
      </div>

      <ul v-else class="max-h-96 overflow-y-auto divide-y divide-gray-50">
        <li v-for="n in store.items" :key="n.id"
          @click="onClickItem(n)"
          :class="['px-4 py-3 cursor-pointer hover:bg-gray-50 transition',
                   !n.read ? 'bg-indigo-50/40' : '']">
          <div class="flex items-start gap-2">
            <div :class="['mt-1 w-1.5 h-1.5 rounded-full shrink-0',
                          n.read ? 'bg-gray-300' : 'bg-indigo-500']" />
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-gray-900 truncate">{{ n.title }}</p>
              <p class="text-xs text-gray-500 truncate">{{ n.message }}</p>
              <p class="text-[10px] text-gray-400 mt-0.5">{{ formatRelative(n.occurredAt) }}</p>
            </div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useNotificationStore, type NotificationItem } from '~/stores/notifications'

const store = useNotificationStore()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

const toggle = () => { open.value = !open.value }

const onClickItem = async (n: NotificationItem) => {
  store.markRead(n.id)
  open.value = false
  if (n.type === 'ORDER_STATUS_CHANGED') {
    await navigateTo(`/me/orders/${n.orderId}`)
  } else if (n.type === 'ADMIN_ORDER_CREATED') {
    await navigateTo(`/admin/orders/${n.orderId}`)
  }
}

// 외부 클릭으로 닫기
const onOutside = (e: MouseEvent) => {
  if (open.value && root.value && !root.value.contains(e.target as Node)) open.value = false
}
onMounted(() => document.addEventListener('click', onOutside))
onBeforeUnmount(() => document.removeEventListener('click', onOutside))

const formatRelative = (iso: string) => {
  const diff = (Date.now() - new Date(iso).getTime()) / 1000
  if (diff < 60)        return '방금 전'
  if (diff < 3600)      return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400)     return `${Math.floor(diff / 3600)}시간 전`
  return new Date(iso).toLocaleDateString('ko-KR')
}
</script>
```

---

## 7. 레이아웃 통합 — `layouts/default.vue` 수정 지점

네비게이션 바의 로그인 사용자 영역에 벨을 추가한다:

```vue
<div v-if="auth.isLoggedIn" class="flex items-center gap-3">
  <NotificationBell />
  <span class="text-sm text-gray-700">{{ auth.user?.nickname }}</span>
  <button @click="logout" class="...">로그아웃</button>
</div>
```

`components/` 자동 임포트 경로는 `components/notifications/NotificationBell.vue` → `<NotificationBell />` 로 자동 등록된다.

---

## 8. 토스트 연동 (선택)

알림 수신 시 짧은 토스트도 함께 띄우려면 `useUiToast` 를 스트림 초기화 지점에 연결하거나, `stores/notifications.ts` 의 `push` 안에서 호출한다. UX상 과도한 토스트는 역효과이므로 **벨 뱃지 + 주문 페이지 내 펄스** 위주 갱신을 권장한다. 관리자 신규 주문만 예외적으로 토스트 활용.

---

## 9. 구성값 — `nuxt.config.ts`

```ts
export default defineNuxtConfig({
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || 'http://localhost:8080',
    },
  },
})
```

`.env`:

```env
NUXT_PUBLIC_API_BASE=http://localhost:8080
```

---

## 10. 테스트 시나리오 (수용 기준)

| # | 시나리오 | 기대 동작 |
|---|---|---|
| 1 | 로그인 → 상품 주문 → 결제 서비스가 PAYMENT_COMPLETED 발행 | 현재 탭(`/me/orders`)의 해당 주문 카드 상태 뱃지가 "결제 완료"로 즉시 변경 + 벨 뱃지 +1 |
| 2 | 주문 상세 페이지를 열어둔 상태로 관리자가 상태를 `SHIPPED`로 변경 | 상세 페이지 타임라인이 자동 이동 + 토스트 "상품이 발송되었습니다" |
| 3 | 네트워크 단절 후 복구 | 1→2→5→10초 백오프 후 자동 재연결, 벨의 연결 인디케이터 회복 |
| 4 | Access Token 만료 → 401 | SSE `onerror` → 재연결 시 401 반복. 15초 이상 재시도 실패 시 Axios가 refresh 발동 → 로그인 상태 유지 시 재연결 성공. (별도 플로우) |
| 5 | 관리자 로그인 → 유저가 주문 생성 | 관리자 화면 우측 상단 "새 주문" 토스트 + 벨 뱃지 업데이트 |
| 6 | 두 개의 탭을 열어놓은 상태 | 두 탭 모두 동일 Pinia 상태는 공유하지 않으므로, 각 탭이 독립 SSE를 유지하고 각자 동일 이벤트를 수신함 (중복 처리는 `push()` 내 id 중복 제거로 차단) |

---

## 11. 체크리스트

- [ ] `event-source-polyfill` 설치 + 타입 선언
- [ ] Pinia 스토어 `notifications` 등록
- [ ] 클라이언트 플러그인 `notification-stream.client.ts` 배치
- [ ] 레이아웃에 `<NotificationBell />` 노출 (로그인 시에만)
- [ ] `/me/orders`, `/me/orders/[id]`, `/admin/orders`, `/admin/orders/[id]` 에서 `useNotificationStream` 훅 사용
- [ ] 페이지 언마운트 시 핸들러 자동 해제 확인
- [ ] 로그아웃 시 SSE 연결 종료 확인
- [ ] Tailwind 전용 — 모든 스타일링 유틸리티 클래스로 처리 (헌법 §2.1)
