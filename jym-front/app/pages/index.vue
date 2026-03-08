<template>
  <div>
    <!-- 히어로 섹션 -->
    <section class="bg-gradient-to-br from-indigo-950 via-purple-900 to-indigo-900 text-white">
      <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-28 text-center">
        <div class="text-7xl mb-6 animate-bounce">🎵</div>
        <h1 class="text-5xl sm:text-6xl font-extrabold mb-4 tracking-tight">
          Jymusic
        </h1>
        <p class="text-xl text-indigo-200 mb-10 max-w-2xl mx-auto leading-relaxed">
          당신의 음악 앨범을 발견하고 소장하세요.<br class="hidden sm:block" />
          최고의 음악 앨범 이커머스 플랫폼.
        </p>

        <div class="flex flex-col sm:flex-row gap-4 justify-center">
          <NuxtLink
            to="/products"
            class="inline-flex items-center justify-center gap-2 px-8 py-3.5 bg-white text-indigo-700 font-semibold rounded-xl hover:bg-indigo-50 transition-colors shadow-lg"
          >
            <span>💿</span> 상품 둘러보기
          </NuxtLink>
          <template v-if="authStore.isLoggedIn">
            <NuxtLink
              to="/me"
              class="inline-flex items-center justify-center gap-2 px-8 py-3.5 border border-white/30 text-white font-semibold rounded-xl hover:bg-white/10 transition-colors"
            >
              <span>👤</span> 내 프로필 보기
            </NuxtLink>
          </template>
          <template v-else>
            <NuxtLink
              to="/auth/register"
              class="inline-flex items-center justify-center gap-2 px-8 py-3.5 bg-white text-indigo-700 font-semibold rounded-xl hover:bg-indigo-50 transition-colors shadow-lg"
            >
              <span>🚀</span> 지금 시작하기
            </NuxtLink>
            <NuxtLink
              to="/auth/login"
              class="inline-flex items-center justify-center gap-2 px-8 py-3.5 border border-white/30 text-white font-semibold rounded-xl hover:bg-white/10 transition-colors"
            >
              로그인
            </NuxtLink>
          </template>
        </div>
      </div>
    </section>

    <!-- 피처 섹션 -->
    <section class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
      <h2 class="text-3xl font-bold text-center text-gray-900 mb-3">왜 Jymusic인가요?</h2>
      <p class="text-center text-gray-500 mb-12">음악을 사랑하는 모든 분들을 위한 플랫폼</p>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div
          v-for="feature in features"
          :key="feature.title"
          class="p-6 rounded-2xl bg-white border border-gray-100 shadow-sm hover:shadow-md hover:-translate-y-1 transition-all duration-200"
        >
          <div class="text-4xl mb-4">{{ feature.icon }}</div>
          <h3 class="text-lg font-semibold text-gray-900 mb-2">{{ feature.title }}</h3>
          <p class="text-gray-500 text-sm leading-relaxed">{{ feature.desc }}</p>
        </div>
      </div>
    </section>

    <!-- CTA 섹션 (비로그인 시만 표시) -->
    <section
      v-if="!authStore.isLoggedIn"
      class="bg-indigo-600 text-white"
    >
      <div class="max-w-4xl mx-auto px-4 py-16 text-center">
        <h2 class="text-3xl font-bold mb-4">지금 바로 시작해보세요</h2>
        <p class="text-indigo-200 mb-8">무료 회원가입으로 Jymusic의 모든 서비스를 이용해보세요.</p>
        <NuxtLink
          to="/auth/register"
          class="inline-block px-8 py-3.5 bg-white text-indigo-600 font-semibold rounded-xl hover:bg-indigo-50 transition-colors"
        >
          무료 회원가입
        </NuxtLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '~/stores/auth';

definePageMeta({ layout: 'default' })

const authStore = useAuthStore()

const features = [
  {
    icon: '🎶',
    title: '방대한 앨범 컬렉션',
    desc: '국내외 최신 앨범부터 클래식 명반까지, 취향에 맞는 음악을 찾아보세요.',
  },
  {
    icon: '🚀',
    title: '빠른 배송',
    desc: '주문 후 빠른 배송으로 소장하고 싶은 앨범을 바로 받아보세요.',
  },
  {
    icon: '🔒',
    title: '안전한 결제',
    desc: '다양한 결제 수단과 안전한 결제 시스템으로 편리하게 구매하세요.',
  },
]
</script>
