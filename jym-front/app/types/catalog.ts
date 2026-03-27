export interface ProductSummary {
  id: number
  title: string
  artist: string
  price: number
  thumbnailUrl: string | null
}

export interface ProductSearchParams {
  keyword?: string
  categoryId?: number | null
  minPrice?: number
  maxPrice?: number
  page?: number
  size?: number
  sort?: string
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
  categoryId: number
  imageKey?: string | null
  imageUrl: string | null
}

export interface ProductCreateRequest {
  title: string
  artist: string
  description: string
  price: number
  stockQuantity: number
  categoryId: number
  imageKey: string | null
}

export interface ProductUpdateRequest {
  title: string
  artist: string
  description: string
  price: number
  stockQuantity: number
  categoryId: number
  imageKey: string | null
}

export interface PresignedUrlRequest {
  filename: string
  contentType: string
}

export interface PresignedUrlResponse {
  presignedUrl: string
  objectKey: string
}
