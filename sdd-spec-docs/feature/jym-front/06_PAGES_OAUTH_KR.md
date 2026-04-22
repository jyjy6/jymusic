# jym-front: OAuth 소셜 로그인 페이지 상세 설계

> **포함 도메인**: OAuth 로그인 버튼, 콜백 처리 페이지, Pinia store 확장, 기존 페이지 수정

---

## 1. 페이지 목록 (추가)

| 경로 | 페이지 | 인증 필요 | 목적 |
|:-----|:-------|:----------|:-----|
| `/auth/oauth2/success` | OAuth 콜백 처리 | 불필요 | 백엔드 리다이렉트 후 토큰 수신 및 저장 |

---

## 2. 기존 페이지 수정

### 2.1 로그인 페이지 `/auth/login` (수정)

**파일**: `pages/auth/login.vue`

#### 변경 사항
기존 로컬 로그인 폼 **하단**에 소셜 로그인 버튼 섹션 추가.

#### UI 구성 (추가 영역)

```
┌──────────────────────────────────┐
│        [기존 로그인 폼]            │
│   아이디: [____________]          │
│   비밀번호: [____________]        │
│         [로그인 버튼]              │
│   회원가입 링크                    │
├──────────────────────────────────┤
│         ── 또는 ──                │
├──────────────────────────────────┤
│   [🔵 Google로 로그인]            │
│   [🟡 Kakao로 로그인]             │
└──────────────────────────────────┘
```

#### 소셜 로그인 버튼 동작

| 버튼 | 클릭 시 동작 |
|:-----|:-------------|
| Google로 로그인 | `window.location.href = '{GATEWAY_URL}/api/v1/auth/oauth2/google'` |
| Kakao로 로그인 | `window.location.href = '{GATEWAY_URL}/api/v1/auth/oauth2/kakao'` |

> **Note**: Axios 호출이 아닌 `window.location.href` 직접 할당.
> OAuth 인가 흐름은 브라우저 전체 리다이렉트가 필요하므로 SPA 라우팅이 아닌 전체 페이지 이동을 사용합니다.

#### 구현 코드 (스켈레톤)

```vue
<script setup lang="ts">
const GATEWAY_URL = 'http://localhost:8080'

const loginWithGoogle = () => {
  window.location.href = `${GATEWAY_URL}/api/v1/auth/oauth2/google`
}

const loginWithKakao = () => {
  window.location.href = `${GATEWAY_URL}/api/v1/auth/oauth2/kakao`
}
</script>
```

#### 스타일 가이드 (Tailwind CSS)

```html
<!-- 구분선 -->
<div class="flex items-center my-6">
  <div class="flex-1 border-t border-gray-300"></div>
  <span class="px-4 text-sm text-gray-500">또는</span>
  <div class="flex-1 border-t border-gray-300"></div>
</div>

<!-- 소셜 로그인 버튼 -->
<button @click="loginWithGoogle"
  class="w-full flex items-center justify-center gap-3 py-3 px-4
         border border-gray-300 rounded-lg hover:bg-gray-50
         transition-colors duration-200">
  <img src="/icons/google.svg" alt="Google" class="w-5 h-5" />
  <span class="text-sm font-medium text-gray-700">Google로 로그인</span>
</button>

<button @click="loginWithKakao"
  class="w-full flex items-center justify-center gap-3 py-3 px-4
         bg-[#FEE500] rounded-lg hover:bg-[#FDD835]
         transition-colors duration-200 mt-3">
  <img src="/icons/kakao.svg" alt="Kakao" class="w-5 h-5" />
  <span class="text-sm font-medium text-[#3C1E1E]">Kakao로 로그인</span>
</button>
```

---

### 2.2 회원가입 페이지 `/auth/register` (수정)

**파일**: `pages/auth/register.vue`

#### 변경 사항
로그인 페이지와 동일한 소셜 로그인 버튼 섹션을 가입 폼 하단에 추가.
"이미 계정이 있으신가요?" 링크 근처에 배치.

---

## 3. 신규 페이지: OAuth 콜백 처리

### 3.1 `/auth/oauth2/success` — 토큰 수신 페이지

**파일**: `pages/auth/oauth2/success.vue`

#### 역할
백엔드가 OAuth 콜백 처리 후 리다이렉트하는 도착 페이지.
URL query parameter에서 `accessToken`을 추출하여 Pinia store에 저장합니다.

#### 연결 API
- 이 페이지 자체는 API를 호출하지 않음.
- 토큰 저장 후 `GET /api/v1/members/me`를 호출하여 사용자 프로필을 가져옴.

#### 흐름

```
1. 백엔드 콜백 → 302 리다이렉트 → /auth/oauth2/success?accessToken=eyJ...
2. 페이지 마운트 (onMounted)
3. URL에서 accessToken 추출
   ├─ 토큰 존재 → Pinia store에 저장 (setToken)
   │              → GET /api/v1/members/me 호출하여 사용자 정보 저장 (setAuth)
   │              → URL 히스토리 정리 (window.history.replaceState)
   │              → /me 페이지로 이동
   └─ 토큰 없음 → 에러 표시 + /auth/login 리다이렉트
```

#### 구현 코드

```vue
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '~/stores/auth'
import axios from '~/plugins/axios'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

onMounted(async () => {
  const accessToken = route.query.accessToken as string | undefined

  if (!accessToken) {
    // 토큰이 없으면 로그인 페이지로 이동
    router.replace('/auth/login')
    return
  }

  // 1. Pinia store에 Access Token 저장
  authStore.setToken(accessToken)

  // 2. URL에서 토큰 제거 (보안: 브라우저 히스토리에 토큰 노출 방지)
  window.history.replaceState({}, '', '/auth/oauth2/success')

  // 3. 사용자 프로필 조회
  try {
    const { data } = await axios.get('/api/v1/members/me')
    authStore.setAuth(accessToken, data)
    router.replace('/me')
  } catch (error) {
    // 프로필 조회 실패 시 토큰 정리 후 로그인 페이지로
    authStore.clearAuth()
    router.replace('/auth/login')
  }
})
</script>

<template>
  <div class="flex items-center justify-center min-h-screen">
    <div class="text-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
      <p class="mt-4 text-gray-600">로그인 처리 중...</p>
    </div>
  </div>
</template>
```

#### UI 구성
- 로딩 스피너 + "로그인 처리 중..." 텍스트
- 사용자가 이 페이지에 머무는 시간은 극히 짧음 (자동 리다이렉트)

---

## 4. Pinia Store 변경

### 4.1 `stores/auth.ts` — 변경 없음

기존 `setToken`, `setAuth`, `clearAuth` 메서드가 OAuth 흐름에도 그대로 사용됩니다.

> **설계 의도**: 로컬 로그인이든 OAuth 로그인이든 최종적으로 JWT를 발급받는 것은 동일하므로,
> 프론트엔드의 인증 상태 관리는 변경 없이 재사용 가능합니다.

---

## 5. Axios Plugin 변경

### 5.1 `plugins/axios.ts` — 변경 없음

기존 요청/응답 인터셉터가 OAuth 흐름에도 그대로 적용됩니다.
- Access Token 자동 주입
- 401 응답 시 토큰 갱신 (RTR)
- Refresh Token HttpOnly Cookie 자동 전송

---

## 6. Middleware 변경

### 6.1 `middleware/auth.ts` — 변경 없음

- `/auth/oauth2/success` 페이지는 인증 불필요 (토큰 수신 전이므로)
- 인증 middleware 적용 대상이 아님

---

## 7. 레이아웃 변경

### 7.1 `layouts/default.vue` (수정)

#### 변경 사항
- 로그인 상태에서 프로필 영역에 **AuthProvider 표시** 추가 (선택적)
- 예: "Google로 로그인됨", "Kakao로 로그인됨"

> 이 변경은 `GET /api/v1/members/me` 응답에 `authProvider` 필드가 포함되어야 가능합니다.

---

## 8. 정적 에셋

### 8.1 소셜 로그인 아이콘

| 파일 | 용도 |
|:-----|:-----|
| `public/icons/google.svg` | Google 로그인 버튼 아이콘 |
| `public/icons/kakao.svg` | Kakao 로그인 버튼 아이콘 |

> 각 Provider의 브랜드 가이드라인을 준수하여 공식 로고를 사용합니다.
> - Google: [Google Identity Branding Guidelines](https://developers.google.com/identity/branding-guidelines)
> - Kakao: [Kakao 로그인 디자인 가이드](https://developers.kakao.com/docs/latest/ko/kakaologin/design-guide)

---

## 9. 에러 처리

| 시나리오 | 처리 |
|:---------|:-----|
| OAuth 콜백에서 accessToken 없음 | `/auth/login`으로 리다이렉트 + 토스트 "로그인에 실패했습니다." |
| 프로필 조회 실패 (`/members/me`) | store 초기화 + `/auth/login` 리다이렉트 |
| Provider 화면에서 사용자 취소 | Provider가 error callback → 백엔드가 에러 처리 → 프론트 에러 리다이렉트 |

---

## 10. 페이지 목록 갱신 (00_OVERVIEW 반영)

`00_OVERVIEW_KR.md`의 페이지 목록에 다음 행이 추가되어야 합니다:

| 경로 | 페이지 | 인증 필요 | 목적 | 상세 스펙 |
|:-----|:-------|:----------|:-----|:----------|
| `/auth/oauth2/success` | OAuth 콜백 처리 | 불필요 | 백엔드 리다이렉트 수신, 토큰 저장 | `06_PAGES_OAUTH_KR.md` |

스펙 파일 구조에도 추가:

| 파일 | 포함 도메인 |
|:-----|:------------|
| `06_PAGES_OAUTH_KR.md` | OAuth 소셜 로그인 버튼, 콜백 처리, 소셜 로그인 에셋 |
