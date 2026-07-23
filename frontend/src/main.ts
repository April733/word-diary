import { createApp } from 'vue'
import { createPinia } from 'pinia'
import TDesign from 'tdesign-vue-next'
import 'tdesign-vue-next/es/style/index.css'
import App from './App.vue'
import router from './router'
import i18n, { setStoredLocale, type SupportedLocale } from './i18n'
import { useUserStore } from './stores/user'
import { startHealthWatch } from './utils/serviceStatus'
import { startOfflineSyncWatch } from './utils/offlineSync'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(TDesign)

// 恢复用户登录状态和语言偏好
const userStore = useUserStore()
userStore.loadFromStorage()
// 根据用户偏好 locale 覆盖 i18n 初始值
if (userStore.user?.preferredLocale) {
  const loc = userStore.user.preferredLocale as SupportedLocale
  setStoredLocale(loc)
  i18n.global.locale.value = loc
}

// 同步设置 html lang，避免浏览器自动提示翻译
document.documentElement.lang = i18n.global.locale.value

router.isReady().then(() => {
  app.mount('#app')
  startHealthWatch()
  startOfflineSyncWatch()
})
