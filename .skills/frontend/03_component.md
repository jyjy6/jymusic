# 재사용 Vue 컴포넌트 작성

> **참조**: `.skills/_common/00_project_context.md`

## 개요
`app/components/` 에 도메인별 재사용 가능한 Vue 컴포넌트를 설계하는 표준.

## 입력
- 컴포넌트 용도, Props 목록, Emits 목록, 도메인 카테고리

## 디렉토리 구조 & 자동 import

```
app/components/
├── common/          # 범용 컴포넌트 (FileUpload, Spinner 등)
├── products/        # 상품 도메인
│   ├── ProductCard.vue
│   ├── CategoryTabs.vue
│   └── Pagination.vue
├── cart/            # 장바구니 도메인
├── orders/          # 주문 도메인
├── auth/            # 인증 도메인
├── checkout/        # 결제 도메인
└── notifications/   # 알림 도메인
```

Nuxt는 디렉토리 기반 자동 import를 지원합니다:
- `components/products/ProductCard.vue` → `<ProductCard />` 또는 `<ProductsProductCard />`

## 기본 컴포넌트 패턴

```vue
<template>
  <NuxtLink
    :to="`/products/${product.id}`"
    class="group block overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg"
  >
    <div class="aspect-square overflow-hidden bg-gray-100">
      <img
        :src="imageSrc"
        :alt="product.title"
        class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
        @error="handleImageError"
      />
    </div>
    <div class="space-y-2 p-4">
      <h3 class="truncate text-base font-semibold text-gray-900">
        {{ product.title }}
      </h3>
      <p class="text-lg font-semibold text-indigo-600">
        {{ formattedPrice }}
      </p>
    </div>
  </NuxtLink>
</template>

<script setup lang="ts">
import type { ProductSummary } from '~/types/catalog'

const FALLBACK_IMAGE = '/images/default-album.svg'

const props = defineProps<{
  product: ProductSummary
}>()

const imageSrc = ref(props.product.thumbnailUrl ?? FALLBACK_IMAGE)

const formattedPrice = computed(
  () => `₩ ${new Intl.NumberFormat('ko-KR').format(props.product.price)}`
)

watch(
  () => props.product.thumbnailUrl,
  (next) => { imageSrc.value = next ?? FALLBACK_IMAGE },
)

const handleImageError = () => {
  imageSrc.value = FALLBACK_IMAGE
}
</script>
```

## Props & Emits 패턴

### TypeScript 타입 기반 Props
```vue
<script setup lang="ts">
// 타입 기반 defineProps (제네릭)
const props = defineProps<{
  modelValue: number | null    // v-model 지원
  categories: Category[]
  disabled?: boolean           // optional prop
}>()

// 기본값 설정
withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg'
  variant?: 'primary' | 'secondary'
}>(), {
  size: 'md',
  variant: 'primary',
})
```

### Emits
```vue
<script setup lang="ts">
const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  'change': [categoryId: number]
  'delete': [id: number]
}>()

// 사용
emit('update:modelValue', newValue)
```

### v-model 양방향 바인딩
```vue
<!-- 부모 -->
<CategoryTabs :model-value="selectedId" @update:model-value="handleChange" />
<!-- 또는 -->
<CategoryTabs v-model="selectedId" />
```

## Slot 패턴
```vue
<!-- 컴포넌트 정의 -->
<template>
  <div class="card">
    <div class="card-header">
      <slot name="header" />
    </div>
    <div class="card-body">
      <slot />  <!-- 기본 슬롯 -->
    </div>
    <div v-if="$slots.footer" class="card-footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<!-- 사용 -->
<Card>
  <template #header>제목</template>
  본문 내용
  <template #footer>하단</template>
</Card>
```

## 스타일링 가이드라인

| 요소 | Tailwind 클래스 |
|------|-----------------|
| 카드 | `rounded-2xl border border-gray-200 bg-white shadow-sm` |
| 호버 효과 | `transition hover:-translate-y-1 hover:shadow-lg` |
| 이미지 줌 | `transition duration-300 group-hover:scale-105` |
| 버튼 Primary | `rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700` |
| 버튼 Secondary | `rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm text-gray-700 hover:bg-gray-50` |
| 입력 필드 | `rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500` |
| 텍스트 말줄임 | `truncate` |
| 스켈레톤 | `animate-pulse bg-gray-200` |

## 체크리스트
- [ ] `<script setup lang="ts">` 사용
- [ ] `defineProps<T>()` 타입 기반
- [ ] `defineEmits<T>()` 타입 기반
- [ ] Tailwind 유틸리티만 사용 (`<style>` 블록 없음)
- [ ] 도메인별 디렉토리에 배치 (`components/{도메인}/`)
- [ ] 이미지 fallback 처리 (`@error`)
- [ ] 반응형 고려 (`sm:`, `md:`, `lg:`)

## 관련 스킬
- `frontend/01_new_page.md` — 페이지에서 컴포넌트 사용
- `frontend/02_composable.md` — 컴포넌트 내 API 호출
