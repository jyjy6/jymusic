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

export type OrderStatus =
  | 'PENDING'          // 주문 처리 중
  | 'STOCK_RESERVED'   // 결제 대기 (재고 확보 완료)
  | 'PAID'             // 결제 완료
  | 'SHIPPED'          // 배송 중
  | 'COMPLETED'        // 구매 확정
  | 'CANCELLED'        // 주문 취소

// 상태별 표시 텍스트
export const orderStatusLabels: Record<OrderStatus, string> = {
  PENDING: '주문 처리 중',
  STOCK_RESERVED: '결제 대기',
  PAID: '결제 완료',
  SHIPPED: '배송 중',
  COMPLETED: '구매 확정',
  CANCELLED: '주문 취소',
}

export interface OrderDetail {
  id: number
  totalAmount: number
  status: OrderStatus
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
