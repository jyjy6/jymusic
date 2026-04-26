import type { OrderItemDetail, OrderStatus } from './order'

export interface AdminOrderSearchRequest {
  keyword?: string
  productTitle?: string
  status?: OrderStatus
  statuses?: OrderStatus[]
  startDate?: string
  endDate?: string
  minAmount?: number
  maxAmount?: number
}

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
  email?: string
  items: OrderItemDetail[]
  allowedNextStatuses: OrderStatus[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface AdminStatusUpdateRequest {
  status: OrderStatus
  reason?: string
}
