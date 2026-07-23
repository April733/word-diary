<template>
  <div class="page">
    <header class="page-header">
      <div class="header-left">
        <t-button variant="text" class="back-btn" @click="router.push('/')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ $t('study.sessionTitle') }}</h1>
      </div>
      <div class="header-actions">
        <t-button variant="text" size="small" class="nav-link" @click="router.push('/study/browse')">
          {{ $t('study.browse') }}
        </t-button>
        <t-button variant="text" size="small" class="nav-link" @click="router.push('/study/settings')">
          <template #icon><t-icon name="setting" /></template>
        </t-button>
      </div>
    </header>

    <div class="page-body">
      <div class="today-stats" role="group" :aria-label="$t('study.sessionTitle')">
        <button type="button" class="stat-item" @click="openQueueList('review')">
          <span class="stat-value">{{ today.reviewPending }}</span>
          <span class="stat-label">{{ $t('study.reviewPending') }}</span>
        </button>
        <button
          type="button"
          class="stat-item"
          :class="{ locked: !today.reviewPhaseDone }"
          @click="openQueueList('new')"
        >
          <span class="stat-value">{{ today.newPending }}</span>
          <span class="stat-label">{{ $t('study.newPending') }}</span>
        </button>
        <button type="button" class="stat-item" @click="openCompletedList">
          <span class="stat-value">{{ todayCompletedCount }}</span>
          <span class="stat-label">{{ $t('study.completed') }}</span>
        </button>
      </div>

      <div v-if="currentWord" class="study-stage" :class="{ 'study-stage--revealed': showAnswer }">
        <WordCard :word="currentWord" :show-answer="showAnswer" @toggle="showAnswer = true" />
      </div>

      <t-empty v-else-if="!loading" class="done-empty" :description="$t('study.allDone')" />

      <!-- 释义出现后固定在底部，避免长释义把按钮顶出屏幕 -->
      <div v-if="currentWord && showAnswer" class="action-bar">
        <div class="action-bar-inner">
          <button type="button" class="act act--pass" @click="handleAnswer(0)">
            {{ $t('study.forget') }}
          </button>
          <button type="button" class="act act--know" @click="handleAnswer(1)">
            {{ $t('study.know') }}
          </button>
        </div>
      </div>
      <!-- 进度区下方禁止再挂列表/折叠等功能模块 -->
    </div>

    <t-drawer
      v-model:visible="queueListVisible"
      size="min(400px, 100%)"
      :footer="false"
      :close-btn="true"
    >
      <template #header>
        <div class="queue-drawer-title">{{ queueListTitle }}</div>
      </template>
      <div v-if="queueListLoading" class="queue-loading">
        <t-loading size="small" />
      </div>
      <t-empty v-else-if="!queueListItems.length" :description="$t('study.queueListEmpty')" />
      <div v-else class="queue-list">
        <div v-for="(item, idx) in queueListItems" :key="item.wordId" class="queue-item">
          <span class="queue-idx">{{ idx + 1 }}</span>
          <div class="queue-main">
            <div class="queue-word">{{ item.wordProto }}</div>
            <div class="queue-mean">{{ item.partSpeechMean || '—' }}</div>
            <div v-if="item.notebookName" class="queue-nb">{{ item.notebookName }}</div>
          </div>
        </div>
      </div>
    </t-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { openStudySession, submitStudyAnswer, getTodayCompleted, getStudySessionQueue } from '../api'
import WordCard from '../components/WordCard.vue'

const router = useRouter()
const { t } = useI18n()
const loading = ref(true)
const showAnswer = ref(false)
const session = ref<any>(null)
const today = ref({
  reviewPending: 0,
  reviewDone: 0,
  newPending: 0,
  newDone: 0,
  newTotal: 0,
  dailyNewLimit: 0,
  reviewPhaseDone: false,
  currentPhase: 'review'
})

const queueListVisible = ref(false)
const queueListLoading = ref(false)
const queueListPhase = ref<'review' | 'new' | 'completed'>('review')
const queueListItems = ref<any[]>([])

const queueListTitle = computed(() => {
  const n = queueListItems.value.length
  if (queueListPhase.value === 'new') return t('study.queueListNewTitle', { n })
  if (queueListPhase.value === 'completed') return t('study.queueListCompletedTitle', { n })
  return t('study.queueListReviewTitle', { n })
})

const currentWord = computed(() => session.value?.current || null)
const phase = computed(() => session.value?.phase || today.value.currentPhase)
/** 今日已完成 = 复习已完成 + 新词已完成（当日满 2 遍认识） */
const todayCompletedCount = computed(() => (today.value.reviewDone || 0) + (today.value.newDone || 0))

onMounted(() => loadSession())

async function loadSession() {
  loading.value = true
  try {
    const res = await openStudySession()
    applySession(res.data.data)
  } catch {
    MessagePlugin.error(t('study.loadFailed'))
  } finally {
    loading.value = false
  }
}

function applySession(data: any) {
  session.value = data
  if (data.today) {
    today.value = data.today
  }
  showAnswer.value = false
}

async function handleAnswer(feedback: number) {
  if (!currentWord.value || !showAnswer.value) return
  try {
    const res = await submitStudyAnswer({
      wordId: currentWord.value.wordId,
      feedback,
      phase: phase.value
    })
    const data = res.data.data
    if (data.today) {
      today.value = data.today
    }
    // 复习未清完时，前端也强制保持复习阶段展示，避免误跟到 new
    const nextPhase = (!today.value.reviewPhaseDone && today.value.reviewPending > 0)
      ? 'review'
      : (data.phase || today.value.currentPhase)
    if (data.phaseSwitched && today.value.reviewPhaseDone) {
      MessagePlugin.success(t('study.reviewDoneStartNew'))
    }
    if (data.next) {
      session.value = {
        ...session.value,
        current: data.next,
        phase: nextPhase
      }
    } else {
      session.value = { ...session.value, current: null, phase: nextPhase }
      MessagePlugin.success(t('study.sessionDone'))
    }
    showAnswer.value = false
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.msg || t('study.actionFailed'))
  }
}

async function openQueueList(phaseKey: 'review' | 'new') {
  if (phaseKey === 'new' && !today.value.reviewPhaseDone) {
    MessagePlugin.warning(t('study.reviewFirstHint'))
    return
  }
  queueListPhase.value = phaseKey
  queueListVisible.value = true
  queueListLoading.value = true
  queueListItems.value = []
  try {
    const res = await getStudySessionQueue({ phase: phaseKey })
    queueListItems.value = res.data?.data || []
  } catch {
    MessagePlugin.error(t('study.loadFailed'))
  } finally {
    queueListLoading.value = false
  }
}

async function openCompletedList() {
  queueListPhase.value = 'completed'
  queueListVisible.value = true
  queueListLoading.value = true
  queueListItems.value = []
  try {
    const res = await getTodayCompleted()
    queueListItems.value = res.data?.data || []
  } catch {
    MessagePlugin.error(t('study.loadFailed'))
  } finally {
    queueListLoading.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: transparent;
  color: var(--brand-ink);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: color-mix(in srgb, var(--brand-white) 72%, transparent);
  border-bottom: 1px solid color-mix(in srgb, var(--brand-lizard) 40%, transparent);
  backdrop-filter: blur(8px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.page-header h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0.02em;
  color: var(--brand-ink);
}

.back-btn :deep(.t-icon),
.nav-link {
  color: var(--brand-ink) !important;
}

.page-body {
  max-width: 520px;
  margin: 0 auto;
  padding: 20px 20px 48px;
}

.today-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  margin-bottom: 20px;
  padding: 10px 4px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--brand-white) 78%, var(--brand-lizard));
  border: 1px solid color-mix(in srgb, var(--brand-lizard) 45%, var(--brand-white));
}

.stat-item {
  appearance: none;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 8px 6px;
  text-align: center;
  color: inherit;
  transition: background 0.15s ease;
  border-radius: 10px;
}

.stat-item + .stat-item {
  border-left: 1px solid color-mix(in srgb, var(--brand-lizard) 40%, transparent);
}

.stat-item:hover {
  background: color-mix(in srgb, var(--brand-white) 70%, transparent);
}

.stat-item:active {
  transform: scale(0.98);
}

.stat-item.locked {
  opacity: 0.5;
}

.stat-value {
  display: block;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--brand-ink);
  font-variant-numeric: tabular-nums;
}

.stat-label {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: color-mix(in srgb, var(--brand-ink) 55%, var(--brand-lizard));
}

.study-stage {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.study-stage--revealed {
  padding-bottom: 88px;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  padding: 12px 20px calc(12px + env(safe-area-inset-bottom));
  background: color-mix(in srgb, var(--brand-belly) 92%, transparent);
  border-top: 1px solid color-mix(in srgb, var(--brand-lizard) 45%, transparent);
  backdrop-filter: blur(10px);
}

.action-bar-inner {
  max-width: 520px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.act {
  appearance: none;
  border: none;
  cursor: pointer;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.04em;
  transition: background 0.15s ease, transform 0.12s ease, border-color 0.15s ease;
}

.act:active {
  transform: scale(0.98);
}

.act--pass {
  background: var(--brand-white);
  color: var(--brand-ink);
  border: 1.5px solid color-mix(in srgb, var(--brand-lizard) 70%, var(--brand-ink));
}

.act--pass:hover {
  background: color-mix(in srgb, var(--brand-lizard) 28%, var(--brand-white));
}

.act--know {
  background: var(--brand-ink);
  color: var(--brand-white);
  border: 1.5px solid var(--brand-ink);
}

.act--know:hover {
  background: var(--brand-ink-hover);
  border-color: var(--brand-ink-hover);
}

.done-empty {
  margin-top: 48px;
}

.queue-drawer-title {
  font-size: 16px;
  font-weight: 600;
  padding-right: 28px;
  color: var(--brand-ink);
}

.queue-loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.queue-list {
  display: flex;
  flex-direction: column;
}

.queue-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--brand-lizard) 28%, transparent);
}

.queue-idx {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--brand-lizard) 40%, var(--brand-white));
  color: var(--brand-ink);
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}

.queue-main {
  min-width: 0;
  flex: 1;
}

.queue-word,
.queue-mean,
.queue-nb {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-word {
  font-weight: 600;
  font-size: 15px;
  color: var(--brand-ink);
}

.queue-mean {
  margin-top: 2px;
  font-size: 13px;
  color: color-mix(in srgb, var(--brand-ink) 55%, #888);
  line-height: 1.4;
}

.queue-nb {
  margin-top: 4px;
  font-size: 12px;
  color: color-mix(in srgb, var(--brand-lizard) 40%, #888);
}

@media (max-width: 480px) {
  .page-body {
    padding: 16px 16px 40px;
  }

  .act {
    height: 46px;
    font-size: 15px;
  }
}
</style>
