import { EventSourcePolyfill } from 'event-source-polyfill'
import { useNotificationStore, type NotificationItem } from '~/stores/notifications'
import { useAuthStore } from '~/stores/auth'

type NotificationHandler = (payload: NotificationItem) => void

const handlers = new Set<NotificationHandler>()
let userStream: EventSourcePolyfill | null = null
let adminStream: EventSourcePolyfill | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const dispatch = (payload: NotificationItem) => {
  const notificationStore = useNotificationStore()
  notificationStore.push(payload)
  handlers.forEach((handler) => handler(payload))
}

const parseMessage = (raw: MessageEvent<string>): NotificationItem | null => {
  try {
    return JSON.parse(raw.data) as NotificationItem
  } catch {
    return null
  }
}

const clearReconnect = () => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

const connectUserStream = () => {
  if (userStream) return

  const authStore = useAuthStore()
  if (!authStore.accessToken) return

  const { public: { apiBase } } = useRuntimeConfig()
  userStream = new EventSourcePolyfill(`${apiBase}/api/v1/notifications/stream`, {
    headers: {
      Authorization: `Bearer ${authStore.accessToken}`,
    },
    withCredentials: true,
  })

  userStream.addEventListener('NOTI_ORDER_STATUS_CHANGED', (event: Event) => {
    const parsed = parseMessage(event as MessageEvent<string>)
    if (parsed) dispatch(parsed)
  })

  userStream.onerror = () => {
    userStream?.close()
    userStream = null
    clearReconnect()
    reconnectTimer = setTimeout(() => {
      connectUserStream()
    }, 3000)
  }
}

const connectAdminStream = () => {
  if (adminStream) return

  const authStore = useAuthStore()
  if (authStore.user?.role !== 'ROLE_ADMIN' || !authStore.accessToken) return

  const { public: { apiBase } } = useRuntimeConfig()
  adminStream = new EventSourcePolyfill(`${apiBase}/api/v1/notifications/admin/stream`, {
    headers: {
      Authorization: `Bearer ${authStore.accessToken}`,
    },
    withCredentials: true,
  })

  adminStream.addEventListener('NOTI_ADMIN_ORDER_CREATED', (event: Event) => {
    const parsed = parseMessage(event as MessageEvent<string>)
    if (parsed) dispatch(parsed)
  })

  adminStream.onerror = () => {
    adminStream?.close()
    adminStream = null
  }
}

const disconnectAll = () => {
  clearReconnect()
  userStream?.close()
  adminStream?.close()
  userStream = null
  adminStream = null
}

export const useNotificationStream = () => {
  const subscribe = (handler: NotificationHandler) => {
    handlers.add(handler)
    onUnmounted(() => handlers.delete(handler))
  }

  const start = () => {
    connectUserStream()
    connectAdminStream()
  }

  return {
    start,
    subscribe,
    disconnectAll,
  }
}
