<template>
  <div>
    <!-- 구분선 -->
    <div class="flex items-center my-6">
      <div class="flex-1 border-t border-gray-300" />
      <span class="px-4 text-sm text-gray-500">또는</span>
      <div class="flex-1 border-t border-gray-300" />
    </div>

    <!-- Google -->
    <button
      type="button"
      class="w-full flex items-center justify-center gap-3 py-3 px-4 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors duration-200"
      @click="loginWithGoogle"
    >
      <img src="/icons/google.svg" alt="Google" class="w-5 h-5" />
      <span class="text-sm font-medium text-gray-700">Google로 로그인</span>
    </button>

    <!-- Kakao -->
    <button
      type="button"
      class="w-full flex items-center justify-center gap-3 py-3 px-4 rounded-lg transition-colors duration-200 mt-3"
      :style="{ backgroundColor: '#FEE500' }"
      @click="loginWithKakao"
    >
      <img src="/icons/kakao.svg" alt="Kakao" class="w-5 h-5" />
      <span class="text-sm font-medium" :style="{ color: '#3C1E1E' }">Kakao로 로그인</span>
    </button>
  </div>
</template>

<script setup lang="ts">
// OAuth 인가 흐름은 브라우저 전체 리다이렉트가 필요하므로 SPA 라우팅이 아닌 window.location.href 사용
const GATEWAY_URL = 'http://localhost:8080'

const loginWithGoogle = () => {
  window.location.href = `${GATEWAY_URL}/api/v1/auth/oauth2/google`
}

const loginWithKakao = () => {
  window.location.href = `${GATEWAY_URL}/api/v1/auth/oauth2/kakao`
}
</script>
