# 새 페이지 추가 (Nuxt 3, 4)

> **참조**: `.skills/_common/00_project_context.md`

## 개요
Nuxt 파일 기반 라우팅으로 새 페이지를 추가하는 표준 절차.

## 입력
- 페이지 경로 (예: `/reviews`, `/products/[id]/reviews`)
- 필요한 API 호출
- 레이아웃 (default / admin)
- 인증 필요 여부

## 절차

### Step 1: 페이지 파일 생성

`app/pages/` 디렉토리에 파일 생성 (파일명 = 라우트 경로):

```
app/pages/reviews/index.vue      → /reviews
app/pages/reviews/[id].vue       → /reviews/:id (동적 라우트)
app/pages/admin/reviews/index.vue → /admin/reviews
```

### Step 2: 기본 페이지 구조

```vue
<template>
  <div class="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
    <!-- 헤더 -->
    <div class="mb-8 flex flex-col gap-2">
      <h1 class="text-3xl font-bold text-gray-900">페이지 제목</h1>
      <p class="text-sm text-gray-500">설명 텍스트</p>
    </div>

    <!-- 에러 표시 -->
    <div
      v-if="errorMessage"
      class="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ errorMessage }}
    </div>

    <!-- 로딩 스켈레톤 -->
    <div v-if="isLoading" class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="i in 6" :key="i"
        class="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
        <div class="aspect-square animate-pulse bg-gray-200" />
        <div class="space-y-3 p-4">
          <div class="h-4 animate-pulse rounded bg-gray-200" />
        </div>
      </div>
    </div>

    <!-- 빈 상태 -->
    <div v-else-if="items.length === 0"
      class="rounded-2xl border border-dashed border-gray-300 bg-white px-6 py-16 text-center">
      <p class="text-lg font-semibold text-gray-900">데이터가 없습니다.</p>
    </div>

    <!-- 데이터 목록 -->
    <div v-else class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <XxxCard v-for="item in items" :key="item.id" :item="item" />
    </div>
  </div>
</template>

<script setup lang="ts">
// ── Composable import ──
import { useXxx } from '~/composables/useXxx'

// ── 레이아웃 & 미들웨어 ──
definePageMeta({
  layout: 'default',          // 'admin' for 관리자 페이지
  middleware: ['auth'],        // 인증 필요 시
})

// ── 상태 ──
const { items, isLoading, errorMessage, fetchItems } = useXxx()

// ── 라이프사이클 ──
onMounted(async () => {
  await fetchItems()
})
</script>
```

### Step 3: 동적 라우트 페이지 (상세)

```vue
<script setup lang="ts">
definePageMeta({ layout: 'default', middleware: ['auth'] })

const route = useRoute()
const id = Number(route.params.id)

// 데이터 로드
const { data, isLoading, errorMessage, fetchDetail } = useXxxDetail()

onMounted(async () => {
  await fetchDetail(id)
})
</script>
```

### Step 4: 관리자 페이지

```vue
<script setup lang="ts">
definePageMeta({
  layout: 'admin',
  middleware: ['auth', 'admin'],  // 관리자 미들웨어 추가
})
</script>
```

## Middleware 참고

### auth.ts (인증 필수)
```typescript
export default defineNuxtRouteMiddleware((to) => {
  if (import.meta.server) return
  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    return navigateTo(`/auth/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }
})
```

### admin.ts (관리자 권한)
- `authStore.user.role === 'ADMIN'` 체크

## Layout 참고
- `default.vue` — 일반 사용자용 (헤더, 네비게이션, 장바구니 아이콘)
- `admin.vue` — 관리자용 (사이드바, 관리 네비게이션)

## 스타일링 규칙
- **Tailwind CSS 유틸리티만 사용** (`<style>` 블록 금지)
- 반응형: `sm:`, `md:`, `lg:` 접두사 활용
- 공통 패딩: `mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8`
- 카드: `rounded-2xl border border-gray-200 bg-white shadow-sm`
- 로딩: `animate-pulse bg-gray-200`

## 체크리스트
- [ ] `<script setup lang="ts">` 사용
- [ ] `definePageMeta()` — layout, middleware 설정
- [ ] Composable로 API 호출 분리
- [ ] 로딩/에러/빈 상태 UI 3가지 모두 구현
- [ ] Tailwind 유틸리티 클래스만 사용
- [ ] 반응형 그리드 적용
- [ ] `<style>` 블록 없음

## 관련 스킬
- `frontend/02_composable.md` — API 연동 훅
- `frontend/03_component.md` — 재사용 컴포넌트
