export interface ProductSummary {
  id: number
  title: string
  artist: string
  price: number
  thumbnailUrl: string | null
}

export interface ProductListResponse {
  content: ProductSummary[]
  totalElements: number
  totalPages: number
}

export interface Category {
  id: number
  name: string
}

export interface ProductDetail {
  id: number
  title: string
  artist: string
  description: string
  price: number
  stockQuantity: number
  imageUrl: string | null
}
