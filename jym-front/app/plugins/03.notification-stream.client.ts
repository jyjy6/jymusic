import { useNotificationStream } from '~/composables/useNotificationStream'
import { useAuthStore } from '~/stores/auth'
import { useNotificationStore } from '~/stores/notifications'

export default defineNuxtPlugin(() => {
  const authStore = useAuthStore()
  const notificationStore = useNotificationStore()
  const { start, disconnectAll } = useNotificationStream()

  watch(
    () => authStore.isLoggedIn,
    (isLoggedIn) => {
      if (isLoggedIn) {
        start()
      } else {
        disconnectAll()
        notificationStore.clear()
      }
    },
    { immediate: true },
  )
})
