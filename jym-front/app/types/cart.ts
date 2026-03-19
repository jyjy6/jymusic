export interface CartItem {
  cartItemId: number
  productId: number
  title: string
  artist: string
  thumbnailUrl: string | null
  price: number
  quantity: number
  stockQuantity: number
}

export interface CartResponse {
  cartId: number
  items: CartItem[]
}

export interface AddToCartRequest {
  productId: number
  quantity: number
}

export interface UpdateCartItemRequest {
  quantity: number
}
