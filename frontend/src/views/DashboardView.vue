<template>
  <div class="home">
    <header class="top">
      <div class="brand-row">
        <img class="brand-logo" src="/brand/logo-circle-64.png" width="36" height="36" alt="" decoding="async" />
        <div>
          <div class="brand">{{ $t('common.appName') }}</div>
          <div class="hello">{{ $t('dashboard.greeting') }}{{ userStore.user?.nickname }}</div>
        </div>
      </div>
      <div class="top-right">
        <AiRegionSwitcher />
        <LocaleSwitcher />
        <t-button variant="text" size="small" class="top-action-btn" @click="router.push('/report')">
          {{ $t('login.reportLink') }}
        </t-button>
        <t-button variant="text" size="small" class="top-action-btn" @click="handleLogout">
          {{ $t('dashboard.logout') }}
        </t-button>
      </div>
    </header>

    <main v-if="loading" class="body center">
      <t-loading />
    </main>

    <main v-else class="body">
      <!-- 有书：只推一本书、一个按钮（不要「阅读」小标题） -->
      <section v-if="focusBook" class="block">
        <h1 class="book-name">{{ focusBook.title }}</h1>
        <p class="muted">{{ bookStatusText }}</p>
        <t-button theme="primary" size="large" block class="btn" @click="openBook">
          {{ bookButtonText }}
        </t-button>
      </section>

      <!-- 没书 -->
      <section v-else class="block">
        <h1 class="book-name">{{ $t('dashboard.homeNoBookTitle') }}</h1>
        <p class="muted">{{ $t('dashboard.homeNoBookHint') }}</p>
        <t-button theme="primary" size="large" block class="btn" @click="router.push('/bookshelf')">
          {{ $t('dashboard.importBook') }}
        </t-button>
      </section>

      <nav class="nav">
        <button type="button" @click="router.push('/study')">
          <t-icon name="calendar" size="18px" class="nav-icon" />
          <span>{{ $t('study.sessionTitle') }}</span>
        </button>
        <button type="button" @click="router.push('/translation')">
          <t-icon name="translate" size="18px" class="nav-icon" />
          <span>{{ $t('nav.translation') }}</span>
        </button>
        <button type="button" @click="router.push('/writing')">
          <t-icon name="edit" size="18px" class="nav-icon" />
          <span>{{ $t('nav.writing') }}</span>
        </button>
        <button type="button" @click="router.push('/notebooks')">
          <t-icon name="root-list" size="18px" class="nav-icon" />
          <span>{{ $t('nav.wordbook') }}</span>
        </button>
        <button type="button" @click="router.push('/bookshelf')">
          <t-icon name="book-open" size="18px" class="nav-icon" />
          <span>{{ $t('bookshelf.title') }}</span>
        </button>
      </nav>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '../stores/user'
import { getBooks } from '../api'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'
import AiRegionSwitcher from '../components/AiRegionSwitcher.vue'

const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(true)
const books = ref<any[]>([])

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

function openBook() {
  if (focusBook.value) router.push(`/reader/${focusBook.value.id}`)
}

onMounted(async () => {
  userStore.loadFromStorage()
  try {
    const booksRes = await getBooks().catch(() => null)
    books.value = booksRes?.data?.data || []
  } finally {
    loading.value = false
  }
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
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

.top-action-btn {
  color: var(--td-text-color-secondary);
  font-weight: 500;
  transition: color 0.2s;
}

.top-action-btn:hover {
  color: var(--td-text-color-primary);
  background: transparent;
}

.body {
  max-width: 440px;
  margin: 0 auto;
  padding: 16px 24px 56px;
}

.center {
  display: flex;
  justify-content: center;
  padding-top: 80px;
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
</style>
