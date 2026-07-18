<template>
  <div class="page">
    <header class="page-header">
      <div class="header-left">
        <t-button variant="text" @click="router.push('/')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ $t('study.sessionTitle') }}</h1>
      </div>
      <div class="header-actions">
        <t-button variant="text" size="small" @click="router.push('/study/browse')">{{ $t('study.browse') }}</t-button>
        <t-button variant="text" size="small" @click="router.push('/study/settings')">
          <template #icon><t-icon name="setting" /></template>
        </t-button>
      </div>
    </header>

    <div class="page-body">
      <div class="today-stats">
        <div
          class="stat-item stat-item--clickable"
          role="button"
          tabindex="0"
          :title="$t('study.viewQueueList')"
          @click="openQueueList('review')"
          @keydown.enter="openQueueList('review')"
        >
          <span class="stat-label">{{ $t('study.reviewPending') }}</span>
          <span class="stat-value">{{ today.reviewPending }}</span>
        </div>
        <div
          class="stat-item stat-item--clickable"
          :class="{ locked: !today.reviewPhaseDone }"
          role="button"
          tabindex="0"
          :title="$t('study.viewQueueList')"
          @click="openQueueList('new')"
          @keydown.enter="openQueueList('new')"
        >
          <span class="stat-label">{{ $t('study.newPending') }}</span>
          <span class="stat-value">{{ today.newPending }}</span>
        </div>
        <div
          class="stat-item stat-item--clickable"
          role="button"
          tabindex="0"
          :title="$t('study.viewQueueList')"
          @click="openCompletedList"
          @keydown.enter="openCompletedList"
        >
          <span class="stat-label">{{ $t('study.completed') }}</span>
          <span class="stat-value">{{ completedWords.length }}</span>
        </div>
      </div>

      <p v-if="!today.reviewPhaseDone && today.reviewPending > 0" class="lock-hint">
        {{ $t('study.reviewFirstHint') }}
      </p>

      <div v-if="currentWord" class="study-area">
        <WordCard :word="currentWord" :show-answer="showAnswer" @toggle="showAnswer = true" />

        <div v-if="showAnswer" class="study-actions">
          <t-button theme="danger" size="large" @click="handleAnswer(0)">{{ $t('study.forget') }}</t-button>
          <t-button theme="warning" size="large" @click="handleAnswer(2)">{{ $t('study.fuzzy') }}</t-button>
          <t-button theme="primary" size="large" @click="handleAnswer(1)">{{ $t('study.know') }}</t-button>
        </div>

        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
        <p class="progress-text">{{ progressText }}</p>
      </div>

      <t-empty v-else-if="!loading" :description="$t('study.allDone')" />

      <t-collapse
        v-if="todayWords.length > 0 || completedWords.length > 0"
        class="today-words-section"
        :default-value="['today', 'completed']"
      >
        <t-collapse-panel
          v-if="todayWords.length > 0"
          value="today"
          :header="$t('study.todayWords', { count: todayWords.length })"
        >
          <div v-for="word in todayWords" :key="'t-' + word.planId" class="completed-item">
            <span class="completed-word">{{ word.wordProto }}</span>
            <span class="completed-mean">{{ word.partSpeechMean || '—' }}</span>
          </div>
        </t-collapse-panel>
        <t-collapse-panel
          v-if="completedWords.length > 0"
          value="completed"
          :header="$t('study.todayCompleted', { count: completedWords.length })"
        >
          <div v-for="word in completedWords" :key="'c-' + word.planId" class="completed-item">
            <span class="completed-word">{{ word.wordProto }}</span>
            <span class="completed-mean">{{ word.partSpeechMean || '—' }}</span>
          </div>
        </t-collapse-panel>
      </t-collapse>
    </div>

    <t-drawer
      v-model:visible="queueListVisible"
      size="400px"
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
        <div v-for="(item, idx) in queueListItems" :key="item.planId" class="queue-item">
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
const completedWords = ref<any[]>([])
const todayWords = ref<any[]>([])
const today = ref({
  reviewPending: 0,
  reviewDone: 0,
  newPending: 0,
  newDone: 0,
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
const queueItemId = computed(() => session.value?.queueItemId || null)
const phase = computed(() => session.value?.phase || today.value.currentPhase)

const progressText = computed(() => {
  if (phase.value === 'new') {
    const total = today.value.newDone + today.value.newPending
    return t('study.newProgress', { done: today.value.newDone, total })
  }
  const total = today.value.reviewDone + today.value.reviewPending
  return t('study.reviewProgress', { done: today.value.reviewDone, total })
})

const progressPercent = computed(() => {
  if (phase.value === 'new') {
    const total = today.value.newDone + today.value.newPending
    return total ? (today.value.newDone / total) * 100 : 100
  }
  const total = today.value.reviewDone + today.value.reviewPending
  return total ? (today.value.reviewDone / total) * 100 : 100
})

onMounted(async () => {
  await Promise.all([loadSession(), loadCompleted(), loadTodayWords()])
})

async function loadCompleted() {
  try {
    const res = await getTodayCompleted()
    completedWords.value = res.data.data || []
  } catch { /* ignore */ }
}

async function loadTodayWords() {
  try {
    const res = await getStudySessionQueue()
    todayWords.value = res.data?.data || []
  } catch {
    todayWords.value = []
  }
}

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
  if (!currentWord.value || !queueItemId.value) return
  try {
    const res = await submitStudyAnswer({
      planId: currentWord.value.planId,
      queueItemId: queueItemId.value,
      feedback,
      phase: phase.value
    })
    const data = res.data.data
    if (data.today) {
      today.value = data.today
    }
    if (data.phaseSwitched) {
      MessagePlugin.success(t('study.reviewDoneStartNew'))
    }
    if (data.next) {
      session.value = {
        ...session.value,
        current: data.next,
        queueItemId: data.queueItemId,
        phase: data.phase
      }
    } else {
      session.value = { ...session.value, current: null, queueItemId: null }
      MessagePlugin.success(t('study.sessionDone'))
    }
    showAnswer.value = false
    await Promise.all([loadCompleted(), loadTodayWords()])
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.msg || t('study.actionFailed'))
  }
}

async function openQueueList(phaseKey: 'review' | 'new') {
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
  try {
    await loadCompleted()
    queueListItems.value = [...completedWords.value]
  } finally {
    queueListLoading.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: transparent;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.page-header h1 {
  font-size: 20px;
}

.page-body {
  max-width: 640px;
  margin: 24px auto;
  padding: 0 24px;
}

.today-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.stat-item {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  text-align: center;
}

.stat-item--clickable {
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.stat-item--clickable:hover {
  box-shadow: 0 2px 8px rgba(49, 92, 52, 0.12);
}

.stat-item--clickable:active {
  transform: scale(0.98);
}

.stat-item.locked {
  opacity: 0.55;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}

.stat-value.phase {
  font-size: 16px;
}

.lock-hint {
  text-align: center;
  color: #d97706;
  font-size: 14px;
  margin-bottom: 16px;
}

.study-area {
  padding: 12px 0 24px;
}

.study-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 32px;
  flex-wrap: wrap;
}

.study-actions .t-button {
  min-width: 100px;
}

.progress-bar {
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  margin-top: 32px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #4f46e5;
  border-radius: 3px;
  transition: width 0.3s;
}

.progress-text {
  text-align: center;
  color: #6b7280;
  font-size: 13px;
  margin-top: 8px;
}

.queue-drawer-title {
  font-size: 16px;
  font-weight: 600;
  padding-right: 28px;
}

.queue-loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.queue-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f3f4f6;
}

.queue-idx {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 11px;
  background: #f0f4f0;
  color: #315C34;
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

.queue-word {
  font-weight: 600;
  font-size: 15px;
  color: #111827;
}

.queue-mean {
  margin-top: 2px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.4;
}

.queue-nb {
  margin-top: 4px;
  font-size: 12px;
  color: #9ca3af;
}

.today-words-section {
  margin-top: 24px;
}

.completed-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}

.completed-word {
  font-weight: 600;
  flex-shrink: 0;
}

.completed-mean {
  color: #6b7280;
  font-size: 14px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
}
</style>
