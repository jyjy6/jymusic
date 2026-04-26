import { defineStore } from 'pinia'

export interface NotificationItem {
  type: string
  orderId: number
  title: string
  message: string
  status?: string
  occurredAt: string
}

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref<NotificationItem[]>([])
  const seenKeys = ref<Set<string>>(new Set())
  const unreadCount = ref(0)

  const push = (notification: NotificationItem) => {
    const dedupeKey = `${notification.type}:${notification.orderId}:${notification.occurredAt}`
    if (seenKeys.value.has(dedupeKey)) {
      return
    }
    seenKeys.value.add(dedupeKey)
    items.value = [notification, ...items.value].slice(0, 100)
    unreadCount.value += 1
  }

  const markAllRead = () => {
    unreadCount.value = 0
  }

  const clear = () => {
    items.value = []
    seenKeys.value = new Set()
    unreadCount.value = 0
  }

  return {
    items,
    unreadCount,
    push,
    markAllRead,
    clear,
  }
})
