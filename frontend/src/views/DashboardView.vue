<template>
  <div class="home">
    <header class="top">
      <div class="brand-row" @click="router.push({ path: '/study', query: { sourceType: activeTab === 'listen' ? '听力' : '阅读' } })">
        <img class="brand-logo" src="/brand/logo-circle-64.png" width="36" height="36" alt="" decoding="async" />
        <div>
          <div class="brand">{{ $t('common.appName') }}</div>
          <div class="hello">{{ $t('dashboard.greeting') }}{{ userStore.user?.nickname }}</div>
        </div>
      </div>
      <div class="top-right">
        <AiRegionSwitcher />
        <LocaleSwitcher />
        <UserDropdown />
      </div>
    </header>

    <main v-if="loading" class="body center">
      <t-loading />
    </main>

    <main v-else class="body">
      <!-- Tab 切换 -->
      <div class="tab-bar">
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'read' }"
          @click="activeTab = 'read'"
        >
          {{ $t('dashboard.tabRead') }}
        </button>
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'listen' }"
          @click="activeTab = 'listen'"
        >
          {{ $t('dashboard.tabListen') }}
        </button>
      </div>

      <!-- 读写训练：展示书籍 -->
      <section v-if="activeTab === 'read'" class="block">
        <template v-if="focusBook">
          <h1 class="book-name">{{ focusBook.title }}</h1>
          <p class="muted">{{ bookStatusText }}</p>
          <t-button theme="primary" size="large" block class="btn" @click="openBook">
            {{ bookButtonText }}
          </t-button>
        </template>
        <template v-else>
          <h1 class="book-name">{{ $t('dashboard.homeNoBookTitle') }}</h1>
          <p class="muted">{{ $t('dashboard.homeNoBookHint') }}</p>
          <t-button theme="primary" size="large" block class="btn" @click="router.push('/bookshelf')">
            {{ $t('dashboard.importBook') }}
          </t-button>
        </template>
      </section>

      <!-- 听说训练：白名单用户展示内容，非白名单显示建设中 -->
      <section v-else class="block">
        <template v-if="latestShadowing">
          <h1 class="book-name">{{ latestShadowing.title }}</h1>
          <p class="muted">{{ $t('dashboard.continueShadowing') }}</p>
          <t-button theme="primary" size="large" block class="btn" @click="openShadowing(latestShadowing.id)">
            {{ $t('dashboard.continueShadowing') }}
          </t-button>
        </template>
        <template v-else>
          <h1 class="book-name">{{ $t('dashboard.noShadowing') }}</h1>
          <t-button theme="primary" size="large" block class="btn" @click="router.push('/shadowing')">
            {{ $t('dashboard.goShadowing') }}
          </t-button>
        </template>
      </section>

      <!-- 中间菜单：按 Tab 分组 -->
      <nav class="nav">
        <!-- 读写训练菜单 -->
        <template v-if="activeTab === 'read'">
          <button type="button" @click="router.push({ path: '/study', query: { sourceType: '阅读', tab: 'read' } })">
            <t-icon name="calendar" size="18px" class="nav-icon" />
            <span>{{ $t('study.sessionTitle') }}</span>
          </button>
          <button type="button" @click="router.push({ path: '/translation', query: { tab: 'read' } })">
            <t-icon name="translate" size="18px" class="nav-icon" />
            <span>{{ $t('nav.translation') }}</span>
          </button>
          <button type="button" @click="router.push({ path: '/spelling', query: { tab: 'read' } })">
            <t-icon name="task" size="18px" class="nav-icon" />
            <span>{{ $t('nav.spelling') }}</span>
          </button>
          <button type="button" @click="router.push({ path: '/writing', query: { tab: 'read' } })">
            <t-icon name="edit" size="18px" class="nav-icon" />
            <span>{{ $t('nav.writing') }}</span>
          </button>
          <button type="button" @click="router.push({ path: '/bookshelf', query: { tab: 'read' } })">
            <t-icon name="book-open" size="18px" class="nav-icon" />
            <span>{{ $t('bookshelf.title') }}</span>
          </button>
        </template>
        <!-- 听说训练菜单 -->
        <template v-else>
          <button type="button" @click="router.push({ path: '/study', query: { sourceType: '听力', tab: 'listen' } })">
            <t-icon name="calendar" size="18px" class="nav-icon" />
            <span>{{ $t('study.sessionTitle') }}</span>
          </button>
          <button
            type="button"
            @click="router.push({ path: '/oral', query: { sourceType: '听力', tab: 'listen' } })"
          >
            <t-icon name="chat" size="18px" class="nav-icon" />
            <span>{{ $t('nav.oral') }}</span>
          </button>
          <button type="button" @click="router.push({ path: '/shadowing', query: { tab: 'listen' } })">
            <t-icon name="sound" size="18px" class="nav-icon" />
            <span>{{ $t('nav.shadowing') }}</span>
          </button>
        </template>
      </nav>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '../stores/user'
import { getBooks, listShadowingClips } from '../api'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'
import AiRegionSwitcher from '../components/AiRegionSwitcher.vue'
import UserDropdown from '../components/UserDropdown.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const userStore = useUserStore()

// 听说功能已全量开放
const listenEnabled = computed(() => true)

const loading = ref(true)
const books = ref<any[]>([])
const shadowingClips = ref<any[]>([])
const activeTab = ref<'read' | 'listen'>('read')

function resolveTab(tab: unknown): 'read' | 'listen' {
  const t = String(tab || '').toLowerCase()
  if (t === 'listen' && listenEnabled.value) return 'listen'
  return 'read'
}

function progressOf(b: any) {
  return Math.round(Number(b.progressPercent ?? 0))
}

function updatedAt(b: any) {
  return new Date(b.updateTime || b.createTime || 0).getTime()
}

/** 优先：有进度的最近在读；否则最近一本 */
const focusBook = computed(() => {
  if (!books.value.length) return null
  const reading = books.value
    .filter(b => progressOf(b) > 0)
    .sort((a, b) => updatedAt(b) - updatedAt(a))
  if (reading.length) return reading[0]
  return [...books.value].sort((a, b) => updatedAt(b) - updatedAt(a))[0]
})

const bookStarted = computed(() => focusBook.value != null && progressOf(focusBook.value) > 0)

const bookStatusText = computed(() => {
  if (!focusBook.value) return ''
  if (!bookStarted.value) return t('dashboard.readingNotStarted')
  return t('bookshelf.progress', { percent: progressOf(focusBook.value) })
})

const bookButtonText = computed(() =>
  bookStarted.value ? t('dashboard.continueReading') : t('dashboard.startReading')
)

/** 最近一个跟读文件（按 updateTime 降序） */
const latestShadowing = computed(() => {
  if (!shadowingClips.value.length) return null
  return [...shadowingClips.value]
    .sort((a, b) => {
      const ta = new Date(a.updateTime || a.createTime || 0).getTime()
      const tb = new Date(b.updateTime || b.createTime || 0).getTime()
      return tb - ta
    })[0]
})

function openBook() {
  if (focusBook.value) router.push(`/reader/${focusBook.value.id}`)
}

function openShadowing(id: number) {
  router.push(`/shadowing/player/${id}`)
}

onMounted(async () => {
  userStore.loadFromStorage()
  activeTab.value = resolveTab(route.query.tab)
  try {
    const [booksRes, shadowingRes] = await Promise.all([
      getBooks().catch(() => null),
      listShadowingClips().catch(() => null)
    ])
    books.value = booksRes?.data?.data || []
    shadowingClips.value = shadowingRes?.data?.data || []
  } finally {
    loading.value = false
  }
})

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = resolveTab(tab)
  }
)

onBeforeUnmount(() => {
})
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: transparent;
  color: var(--brand-ink);
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 24px 8px;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
}

.brand {
  font-size: 18px;
  font-weight: 700;
}

.hello {
  margin-top: 4px;
  font-size: 14px;
  color: #666;
}

.top-right {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 4px;
  max-width: min(100%, 420px);
}

.body {
  max-width: 440px;
  margin: 0 auto;
  padding: 16px 24px 24px;
}

.center {
  display: flex;
  justify-content: center;
  padding-top: 80px;
}

/* Tab 切换栏 */
.tab-bar {
  display: flex;
  gap: 0;
  margin-bottom: 20px;
  border-bottom: 2px solid color-mix(in srgb, var(--brand-lizard) 30%, var(--brand-white));
}

.tab-btn {
  flex: 1;
  padding: 10px 0;
  border: none;
  background: transparent;
  font-size: 15px;
  font-weight: 600;
  color: var(--td-text-color-secondary);
  cursor: pointer;
  position: relative;
  transition: color 0.2s;
}

.tab-btn.active {
  color: var(--brand-ink);
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--brand-ink);
}

.tab-btn:hover {
  color: var(--brand-ink);
}

.block {
  padding-bottom: 8px;
}

.muted {
  margin: 0 0 8px;
  font-size: 13px;
  color: #888;
}

.book-name {
  margin: 0 0 8px;
  font-size: 22px;
  line-height: 1.35;
  font-weight: 700;
  /* 长书名最多两行 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.btn {
  margin-top: 12px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-top: 24px;
}

.nav button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 0;
  border: none;
  border-bottom: 1px solid color-mix(in srgb, var(--brand-lizard) 50%, var(--brand-white));
  background: transparent;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
  color: #333;
}

.nav-icon {
  flex-shrink: 0;
  color: var(--brand-ink);
  opacity: 0.72;
}

.nav button:hover {
  color: var(--brand-ink);
}

.nav button:hover .nav-icon {
  opacity: 1;
}



@media (max-width: 480px) {
  .body {
    padding: 12px 20px 48px;
  }
}

.under-construction {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0 32px;
  text-align: center;
}

.uc-icon {
  color: var(--td-text-color-placeholder);
  margin-bottom: 16px;
}
</style>
