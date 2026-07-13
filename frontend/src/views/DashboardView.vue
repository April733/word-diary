<template>
  <div class="dashboard">
    <header class="dash-header">
      <div class="header-left">
        <h1>{{ $t('common.appName') }}</h1>
        <span class="greeting">{{ $t('dashboard.greeting') }}{{ userStore.user?.nickname }}</span>
      </div>
      <div class="header-right">
        <LocaleSwitcher />
        <t-button variant="outline" @click="handleLogout">{{ $t('dashboard.logout') }}</t-button>
      </div>
    </header>

    <div class="dash-content">
      <h2>{{ $t('nav.dashboard') }}</h2>
      <div class="stats-grid">
        <div class="stat-card blue">
          <div class="stat-num">{{ stats.totalWords }}</div>
          <div class="stat-label">{{ $t('dashboard.totalWords') }}</div>
        </div>
        <div class="stat-card yellow">
          <div class="stat-num">{{ stats.newWords }}</div>
          <div class="stat-label">{{ $t('dashboard.toLearn') }}</div>
        </div>
        <div class="stat-card green">
          <div class="stat-num">{{ stats.reviewingWords }}</div>
          <div class="stat-label">{{ $t('dashboard.reviewing') }}</div>
        </div>
        <div class="stat-card purple">
          <div class="stat-num">{{ stats.masteredWords }}</div>
          <div class="stat-label">{{ $t('dashboard.mastered') }}</div>
        </div>
      </div>

      <div class="action-section">
        <t-card :bordered="false" class="action-card" @click="router.push('/study')">
          <div class="action-icon">📝</div>
          <div class="action-info">
            <h3>{{ $t('nav.study') }}</h3>
            <p>{{ $t('dashboard.todayReview', { count: stats.todayReviewCount }) }}</p>
          </div>
          <t-icon name="chevron-right" size="24px" />
        </t-card>

        <t-card :bordered="false" class="action-card" @click="router.push('/bookshelf')">
          <div class="action-icon">📖</div>
          <div class="action-info">
            <h3>{{ $t('nav.reading') }}</h3>
            <p>{{ $t('dashboard.readingDesc') }}</p>
          </div>
          <t-icon name="chevron-right" size="24px" />
        </t-card>

        <t-card :bordered="false" class="action-card" @click="router.push('/notebooks')">
          <div class="action-icon">📚</div>
          <div class="action-info">
            <h3>{{ $t('nav.wordbook') }}</h3>
            <p>{{ $t('dashboard.wordbookDesc') }}</p>
          </div>
          <t-icon name="chevron-right" size="24px" />
        </t-card>

        <t-card :bordered="false" class="action-card" @click="router.push('/records')">
          <div class="action-icon">📊</div>
          <div class="action-info">
            <h3>{{ $t('nav.records') }}</h3>
            <p>{{ $t('dashboard.recordsDesc') }}</p>
          </div>
          <t-icon name="chevron-right" size="24px" />
        </t-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getStats } from '../api'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'

const router = useRouter()
const userStore = useUserStore()

const stats = ref({ totalWords: 0, newWords: 0, reviewingWords: 0, masteredWords: 0, todayReviewCount: 0 })

onMounted(async () => {
  userStore.loadFromStorage()
  try {
    const res = await getStats()
    stats.value = res.data.data
  } catch (e) {
    // stats stay at 0
  }
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.dashboard {
  min-height: 100vh;
  background: #f5f7fa;
}

.dash-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 32px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.header-left h1 {
  font-size: 22px;
  color: #1a1a2e;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.greeting {
  color: #6b7280;
  font-size: 14px;
  margin-left: 16px;
}

.dash-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 32px 24px;
}

.dash-content h2 {
  font-size: 20px;
  margin-bottom: 20px;
  color: #1a1a2e;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 36px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.stat-num {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: #6b7280;
}

.stat-card.blue .stat-num { color: #4f46e5; }
.stat-card.yellow .stat-num { color: #d97706; }
.stat-card.green .stat-num { color: #059669; }
.stat-card.purple .stat-num { color: #7c3aed; }

.action-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-card {
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.action-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}

.action-card :deep(.t-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
}

.action-icon {
  font-size: 32px;
}

.action-info h3 {
  font-size: 16px;
  color: #1a1a2e;
  margin-bottom: 4px;
}

.action-info p {
  font-size: 13px;
  color: #6b7280;
}
</style>
