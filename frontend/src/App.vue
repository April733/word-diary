<template>
  <div class="app-shell" :class="shellClass">
    <div v-if="showBrandBg" class="app-brand-bg" aria-hidden="true">
      <div class="bg-wash" />
    </div>
    <div class="app-view">
      <router-view :key="$route.fullPath" />
    </div>
    <ServiceTransitionOverlay />
    <footer class="app-footer">
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">黑ICP备2026008953号-1</a>
      <span class="footer-sep">|</span>
      <a href="http://www.beian.gov.cn/portal/registerSystemInfo?recordcode=7f4e88e7333aea1a0af0d5e7fd62f41c" target="_blank" rel="noopener noreferrer" class="footer-gongan">
        <img src="/brand/gongan-icon.svg" alt="" class="gongan-icon" />
        黑公网安备23010402000389号
      </a>
    </footer>
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

/** 跟读播放器页面，隐藏页脚 */
const isShadowingPlayer = computed(() => route.path.startsWith('/shadowing/player'))

const showBrandBg = computed(() => !isReader.value && !isLogin.value)

const shellClass = computed(() => ({
  'app-shell--reader': isReader.value,
  'app-shell--login': isLogin.value,
  'app-shell--shadowing-player': isShadowingPlayer.value
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

.app-shell--reader .app-footer {
  display: none;
}

.app-shell--shadowing-player {
  background: var(--brand-belly);
}

.app-shell--shadowing-player .app-footer {
  display: none;
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
  padding-bottom: 32px;
}

.app-footer {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 10px 16px;
  font-size: 12px;
  color: var(--td-text-color-secondary, #999);
}

.app-footer a {
  color: var(--td-text-color-secondary, #999);
  text-decoration: none;
}

.app-footer a:hover {
  color: var(--td-brand-color, #315c34);
  text-decoration: underline;
}

.footer-sep {
  margin: 0 8px;
  color: #ccc;
}

.footer-gongan {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.gongan-icon {
  width: 14px;
  height: 14px;
  vertical-align: middle;
}
</style>
