<template>
  <div class="app-shell" :class="shellClass">
    <div v-if="showBrandBg" class="app-brand-bg" aria-hidden="true">
      <div class="bg-wash" />
    </div>
    <div class="app-view">
      <router-view :key="$route.fullPath" />
    </div>
    <ServiceTransitionOverlay />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ServiceTransitionOverlay from './components/ServiceTransitionOverlay.vue'

const route = useRoute()

/** 阅读器全屏，不叠品牌底，避免干扰正文 */
const isReader = computed(() => route.path.startsWith('/reader'))

/** 登录页自有主图编排，避免双份装饰 */
const isLogin = computed(() => route.path.startsWith('/login'))

const showBrandBg = computed(() => !isReader.value && !isLogin.value)

const shellClass = computed(() => ({
  'app-shell--reader': isReader.value,
  'app-shell--login': isLogin.value
}))
</script>

<style scoped>
.app-shell {
  position: relative;
  min-height: 100vh;
  background: var(--brand-belly);
}

.app-shell--reader {
  background: #fff;
}

.app-shell--login {
  background: transparent;
}

.app-brand-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-wash {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 70% 50% at 50% -8%, rgba(168, 181, 159, 0.34), transparent 60%),
    radial-gradient(ellipse 45% 35% at 100% 100%, rgba(168, 181, 159, 0.16), transparent 55%);
}

.app-view {
  position: relative;
  z-index: 1;
  min-height: 100vh;
}
</style>
