import { defineStore } from 'pinia'
import type { AxiosInstance } from 'axios'
import type { CartItem, CartResponse } from '~/types/cart'

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  const cartId = ref<number | null>(null)

  const totalCount = computed(() =>
    items.value.reduce((sum, i) => sum + i.quantity, 0),
  )
  const totalPrice = computed(() =>
    items.value.reduce((sum, i) => sum + i.price * i.quantity, 0),
  )

  const { $axios } = useNuxtApp()
  const axios = $axios as AxiosInstance

  async function fetchCart(): Promise<void> {
    const res = await axios.get<CartResponse>('/api/v1/cart')
    cartId.value = res.data.cartId
    items.value = res.data.items
  }

  async function addItem(productId: number, quantity: number): Promise<void> {
    const res = await axios.post<CartResponse>('/api/v1/cart/items', {
      productId,
      quantity,
    })
    cartId.value = res.data.cartId
    items.value = res.data.items
  }

  async function updateItemQuantity(
    cartItemId: number,
    quantity: number,
  ): Promise<void> {
    const res = await axios.put<CartResponse>(
      `/api/v1/cart/items/${cartItemId}`,
      { quantity },
    )
    cartId.value = res.data.cartId
    items.value = res.data.items
  }

  async function removeItem(cartItemId: number): Promise<void> {
    await axios.delete(`/api/v1/cart/items/${cartItemId}`)
    items.value = items.value.filter((i) => i.cartItemId !== cartItemId)
  }

  async function clearCart(): Promise<void> {
    await axios.delete('/api/v1/cart')
    items.value = []
  }

  return {
    items,
    cartId,
    totalCount,
    totalPrice,
    fetchCart,
    addItem,
    updateItemQuantity,
    removeItem,
    clearCart,
  }
})
