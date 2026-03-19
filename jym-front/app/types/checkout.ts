export type PaymentMethod = 'CARD' | 'VIRTUAL_ACCOUNT' | 'KAKAO_PAY' | 'NAVER_PAY'

export interface ShippingInfo {
  recipientName: string
  phone: string
  address: string
  addressDetail: string
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
  status: string
  items: OrderItemDetail[]
  createdAt: string
}

export interface PaymentPrepareRequest {
  orderId: number
  amount: number
}

export interface PaymentPrepareResponse {
  clientKey: string
  orderId: number
  amount: number
}

export interface PaymentConfirmRequest {
  paymentKey: string
  orderId: number
  amount: number
}

export interface PaymentConfirmResponse {
  paymentId: number
  transactionId: string
  status: 'SUCCESS' | 'FAILED'
  method: PaymentMethod
  paidAmount: number
  paidAt: string
}
