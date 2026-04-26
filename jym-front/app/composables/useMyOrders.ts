import type { AxiosInstance } from 'axios'
import type { OrderDetail, OrderSummary } from '~/types/order'

export const useMyOrders = () => {
  const { $axios } = useNuxtApp()
  const axios = $axios as AxiosInstance

  const fetchMyOrders = async () => {
    const res = await axios.get<OrderSummary[]>('/api/v1/orders')
    return res.data
  }

  const fetchMyOrderDetail = async (orderId: number) => {
    const res = await axios.get<OrderDetail>(`/api/v1/orders/${orderId}`)
    return res.data
  }

  return {
    fetchMyOrders,
    fetchMyOrderDetail,
  }
}
