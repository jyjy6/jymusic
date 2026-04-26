# Composable (API 연동 훅) 작성

> **참조**: `.skills/_common/00_project_context.md`

## 개요
`app/composables/` 에 API 연동 로직을 재사용 가능한 Vue 3 Composition API 훅으로 작성하는 표준.

## 입력
- API 엔드포인트, 요청/응답 타입, 필요한 상태 (목록/단건/CRUD)

## 기본 패턴 — 목록 조회

```typescript
// app/composables/useReviews.ts
import type { AxiosInstance } from 'axios'
import type { Review, ReviewListResponse } from '~/types/review'

export const useReviews = () => {
  const { $axios } = useNuxtApp()

  // useState → SSR/CSR 간 상태 공유 (Nuxt 전용)
  const reviews = useState<Review[]>('reviews-data', () => [])
  const totalElements = useState<number>('reviews-total', () => 0)
  const totalPages = useState<number>('reviews-pages', () => 0)
  const currentPage = useState<number>('reviews-page', () => 0)
  const isLoading = useState<boolean>('reviews-loading', () => false)
  const errorMessage = useState<string>('reviews-error', () => '')

  const fetchReviews = async (page: number = 0, size: number = 10) => {
    isLoading.value = true
    errorMessage.value = ''

    try {
      const response = await ($axios as AxiosInstance).get<ReviewListResponse>(
        '/api/v1/reviews',
        { params: { page, size } }
      )
      reviews.value = response.data.content
      totalElements.value = response.data.totalElements
      totalPages.value = response.data.totalPages
      currentPage.value = page
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } }
      reviews.value = []
      totalElements.value = 0
      totalPages.value = 0
      errorMessage.value =
        error.response?.data?.message ?? '리뷰를 불러오지 못했습니다.'
    } finally {
      isLoading.value = false
    }
  }

  return {
    reviews, totalElements, totalPages, currentPage,
    isLoading, errorMessage, fetchReviews,
  }
}
```

## CRUD Composable 패턴

```typescript
export const useReviewAdmin = () => {
  const { $axios } = useNuxtApp()
  const isSubmitting = ref(false)

  const createReview = async (data: CreateReviewRequest): Promise<Review> => {
    isSubmitting.value = true
    try {
      const response = await ($axios as AxiosInstance).post<Review>(
        '/api/v1/reviews', data
      )
      return response.data
    } finally {
      isSubmitting.value = false
    }
  }

  const updateReview = async (id: number, data: UpdateReviewRequest): Promise<Review> => {
    isSubmitting.value = true
    try {
      const response = await ($axios as AxiosInstance).put<Review>(
        `/api/v1/reviews/${id}`, data
      )
      return response.data
    } finally {
      isSubmitting.value = false
    }
  }

  const deleteReview = async (id: number): Promise<void> => {
    isSubmitting.value = true
    try {
      await ($axios as AxiosInstance).delete(`/api/v1/reviews/${id}`)
    } finally {
      isSubmitting.value = false
    }
  }

  return { isSubmitting, createReview, updateReview, deleteReview }
}
```

## 단건 조회 패턴

```typescript
export const useReviewDetail = async (id: number): Promise<ReviewDetail> => {
  const { $axios } = useNuxtApp()
  const response = await ($axios as AxiosInstance).get<ReviewDetail>(
    `/api/v1/reviews/${id}`
  )
  return response.data
}
```

## TypeScript 타입 정의

`app/types/review.ts`:
```typescript
export interface Review {
  id: number
  productId: number
  memberId: number
  rating: number
  content: string
  createdAt: string
}

export interface ReviewListResponse {
  content: Review[]
  totalElements: number
  totalPages: number
}

export interface CreateReviewRequest {
  productId: number
  rating: number
  content: string
}
```

## 핵심 패턴 정리

| 패턴 | 설명 |
|------|------|
| `useNuxtApp().$axios` | Axios 인스턴스 접근 (플러그인에서 주입) |
| `useState<T>(key, init)` | SSR/CSR 상태 공유 (Nuxt 전용, 목록 데이터용) |
| `ref<T>(init)` | 클라이언트 전용 상태 (isSubmitting 등) |
| `($axios as AxiosInstance)` | TypeScript 타입 캐스팅 필수 |
| 에러 추출 | `error.response?.data?.message` 체이닝 |

## 체크리스트
- [ ] `useNuxtApp().$axios` 패턴 사용
- [ ] `useState`로 SSR/CSR 상태 공유 (key는 컴포저블별 고유)
- [ ] isLoading, errorMessage 상태 관리 포함
- [ ] try/catch/finally 에러 핸들링
- [ ] 타입 정의 `app/types/` 에 분리
- [ ] AxiosInstance 타입 캐스팅

## 관련 스킬
- `frontend/01_new_page.md` — 페이지에서 Composable 사용
- `frontend/03_component.md` — 컴포넌트에서 Composable 사용
