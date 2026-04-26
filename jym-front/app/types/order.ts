export type OrderStatus =
  | 'PENDING'
  | 'STOCK_RESERVED'
  | 'PAID'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'

export interface OrderSummary {
  id: number
  totalAmount: number
  status: OrderStatus
  createdAt: string
}

export interface OrderItemDetail {
  productId: number
  productTitle: string
  price: number
  quantity: number
}

export interface OrderDetail {
  id: number
  totalAmount: number
  status: OrderStatus
  createdAt: string
  updatedAt?: string
  items: OrderItemDetail[]
}
