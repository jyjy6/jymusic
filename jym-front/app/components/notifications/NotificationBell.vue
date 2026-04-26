<template>
  <div class="relative">
    <button
      type="button"
      class="relative rounded-lg p-2 text-gray-600 transition hover:bg-gray-100 hover:text-indigo-600"
      @click="toggle"
    >
      🔔
      <span
        v-if="notificationStore.unreadCount > 0"
        class="absolute -right-1 -top-1 inline-flex min-h-5 min-w-5 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white"
      >
        {{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}
      </span>
    </button>

    <div
      v-if="open"
      class="absolute right-0 top-11 z-50 w-80 rounded-xl border border-gray-200 bg-white shadow-xl"
    >
      <div class="flex items-center justify-between border-b border-gray-100 px-3 py-2">
        <p class="text-sm font-semibold text-gray-900">실시간 알림</p>
        <button
          type="button"
          class="text-xs text-indigo-600 hover:text-indigo-700"
          @click="notificationStore.markAllRead()"
        >
          모두 읽음
        </button>
      </div>

      <ul class="max-h-96 overflow-y-auto">
        <li
          v-for="item in notificationStore.items.slice(0, 20)"
          :key="`${item.type}:${item.orderId}:${item.occurredAt}`"
          class="border-b border-gray-100 px-3 py-2 last:border-none"
        >
          <p class="text-sm font-medium text-gray-900">{{ item.title }}</p>
          <p class="mt-1 text-xs text-gray-600">{{ item.message }}</p>
          <NuxtLink
            :to="orderLink(item.orderId)"
            class="mt-1 inline-block text-xs text-indigo-600 hover:text-indigo-700"
          >
            주문 보기
          </NuxtLink>
        </li>
        <li v-if="notificationStore.items.length === 0" class="px-3 py-6 text-center text-sm text-gray-500">
          아직 알림이 없습니다.
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { useNotificationStore } from '~/stores/notifications'

const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const open = ref(false)

const toggle = () => {
  open.value = !open.value
  if (open.value) {
    notificationStore.markAllRead()
  }
}

const orderLink = (orderId: number) => {
  if (authStore.user?.role === 'ROLE_ADMIN') {
    return `/admin/orders/${orderId}`
  }
  return `/me/orders/${orderId}`
}
</script>
