type ToastType = 'info' | 'success' | 'warning' | 'error'

let hideTimer: ReturnType<typeof setTimeout> | null = null

export const useUiToast = () => {
  const isVisible = useState<boolean>('ui-toast-visible', () => false)
  const message = useState<string>('ui-toast-message', () => '')
  const type = useState<ToastType>('ui-toast-type', () => 'info')

  const hideToast = () => {
    isVisible.value = false
  }

  const showToast = (
    nextMessage: string,
    nextType: ToastType = 'info',
    duration = 2500,
  ) => {
    message.value = nextMessage
    type.value = nextType
    isVisible.value = true

    if (import.meta.client) {
      if (hideTimer) clearTimeout(hideTimer)
      hideTimer = setTimeout(() => {
        hideToast()
      }, duration)
    }
  }

  return {
    isVisible,
    message,
    type,
    showToast,
    hideToast,
  }
}
