<template>
  <div class="page">
    <PageHeader :title="$t('study.sessionTitle')" :back-to="backToHome">
      <button type="button" class="btn btn-text btn-icon" :disabled="!currentWord" @click="openEditCurrent">
        <t-icon name="edit" size="16px" />
      </button>
      <button type="button" class="btn btn-secondary btn-sm" @click="goBrowse">
        {{ $t('study.browse') }}
      </button>
      <button type="button" class="btn btn-text btn-icon" @click="goSettings">
        <t-icon name="setting" size="16px" />
      </button>
    </PageHeader>

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
          <button
            type="button"
            class="act act--pass"
            :disabled="answering"
            @click="handleAnswer(0)"
          >
            {{ $t('study.forget') }}
          </button>
          <button
            type="button"
            class="act act--know"
            :disabled="answering"
            @click="handleAnswer(1)"
          >
            {{ $t('study.know') }}
          </button>
        </div>
      </div>
      <!-- 进度区下方禁止再挂列表/折叠等功能模块 -->
    </div>

    <!-- 编辑当前单词弹窗 -->
    <t-dialog
      v-model:visible="editVisible"
      :header="$t('study.editCurrentWord')"
      :footer="false"
      width="420px"
      :close-on-overlay-click="true"
    >
      <div v-if="!currentWord" class="edit-empty">{{ $t('study.noCurrentWord') }}</div>
      <div v-else class="edit-form">
        <div v-if="currentWord.notebookName" class="edit-notebook">
          <t-icon name="book-open" />
          <span>{{ currentWord.notebookName }}</span>
        </div>
        <div class="form-item">
          <label>{{ $t('study.word') }}</label>
          <t-input v-model="editForm.wordProto" />
        </div>
        <div class="form-item">
          <label>{{ $t('study.definition') }}</label>
          <t-input v-model="editForm.partSpeechMean" />
        </div>
        <div class="edit-actions">
          <t-popconfirm :content="$t('study.deleteWordConfirm')" @confirm="deleteCurrent">
            <t-button size="small" variant="outline" theme="danger" :loading="deleting">
              {{ $t('study.deleteWord') }}
            </t-button>
          </t-popconfirm>
          <t-button theme="primary" size="small" :loading="saving" @click="saveEdit">
            {{ $t('study.save') }}
          </t-button>
        </div>
      </div>
    </t-dialog>

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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { openStudySession, submitStudyAnswer, getTodayCompleted, getStudySessionQueue, updateStudyWord, deleteStudyWord } from '../api'
import WordCard from '../components/WordCard.vue'
import {
  createStudyDurationTracker,
  type StudyDurationTracker
} from '../utils/studyDurationTracker'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const sourceType = computed(() => (route.query.sourceType as string) || undefined)
const tab = computed(() => (route.query.tab as string) || (sourceType.value === '听力' ? 'listen' : 'read'))
const backToHome = computed(() => ({ path: '/', query: { tab: tab.value } }))
const loading = ref(true)
const answering = ref(false)
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

// 编辑当前单词
const editVisible = ref(false)
const saving = ref(false)
const deleting = ref(false)
const editForm = ref({ wordProto: '', partSpeechMean: '' })

/** 今日背诵时长（无单一业务 ID） */
let studyDurationTracker: StudyDurationTracker | null = null

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

onMounted(() => {
  studyDurationTracker = createStudyDurationTracker({ actionType: 'study' })
  loadSession()
})

onBeforeUnmount(() => {
  studyDurationTracker?.dispose()
  studyDurationTracker = null
})

function goBrowse() {
  router.push({
    path: '/study/browse',
    query: {
      ...(sourceType.value ? { sourceType: sourceType.value } : {}),
      ...(tab.value ? { tab: tab.value } : {})
    }
  })
}

function goSettings() {
  router.push({
    path: '/study/settings',
    query: {
      ...(sourceType.value ? { sourceType: sourceType.value } : {}),
      ...(tab.value ? { tab: tab.value } : {})
    }
  })
}

async function loadSession() {
  loading.value = true
  try {
    const res = await openStudySession(sourceType.value ? { sourceType: sourceType.value } : undefined)
    applySession(res.data.data)
  } catch (e: any) {
    // 无可用单词时静默处理，不弹错误提示
    const code = e?.response?.data?.code
    const msg = e?.response?.data?.msg || e?.message || ''
    const isEmptyState = code === 404 || msg.includes('无') || msg.includes('empty') || msg.includes('no word') || msg.includes('暂无')
    if (!isEmptyState) {
      MessagePlugin.error(t('study.loadFailed'))
    }
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
  if (!currentWord.value || !showAnswer.value || answering.value) return
  answering.value = true
  const wordId = currentWord.value.wordId
  const answeredPhase = phase.value
  try {
    const res = await submitStudyAnswer({
      wordId,
      feedback,
      phase: answeredPhase
    }, sourceType.value ? { sourceType: sourceType.value } : undefined)
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
    // 切到下个词时再收起释义；请求期间只靠 answering 禁点，避免闪屏
    if (data.next) {
      session.value = {
        ...session.value,
        current: data.next,
        phase: nextPhase
      }
      showAnswer.value = false
    } else {
      session.value = { ...session.value, current: null, phase: nextPhase }
      showAnswer.value = false
      MessagePlugin.success(t('study.sessionDone'))
    }
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.msg || t('study.actionFailed'))
  } finally {
    answering.value = false
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
    const res = await getStudySessionQueue({ phase: phaseKey, ...(sourceType.value ? { sourceType: sourceType.value } : {}) })
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
    const res = await getTodayCompleted(sourceType.value ? { sourceType: sourceType.value } : undefined)
    queueListItems.value = res.data?.data || []
  } catch {
    MessagePlugin.error(t('study.loadFailed'))
  } finally {
    queueListLoading.value = false
  }
}

function openEditCurrent() {
  if (!currentWord.value) return
  editForm.value = {
    wordProto: currentWord.value.wordProto || '',
    partSpeechMean: currentWord.value.partSpeechMean || ''
  }
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.value.wordProto.trim()) {
    MessagePlugin.warning(t('study.wordRequired'))
    return
  }
  saving.value = true
  try {
    await updateStudyWord(currentWord.value.wordId, {
      wordProto: editForm.value.wordProto.trim(),
      partSpeechMean: editForm.value.partSpeechMean.trim() || undefined,
    })
    MessagePlugin.success(t('study.editSuccess'))
    editVisible.value = false
    // 重新加载会话以获取下一个词
    await loadSession()
  } catch (e: any) {
    MessagePlugin.error(e?.response?.data?.msg || e?.message || t('study.editFailed'))
  } finally {
    saving.value = false
  }
}

async function deleteCurrent() {
  deleting.value = true
  try {
    await deleteStudyWord(currentWord.value.wordId)
    MessagePlugin.success(t('study.deleteSuccess'))
    editVisible.value = false
    await loadSession()
  } catch (e: any) {
    MessagePlugin.error(e?.response?.data?.msg || e?.message || t('study.deleteFailed'))
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: transparent;
  color: var(--brand-ink);
}

.header-actions {
  max-width: 42%;
}

.back-btn :deep(.t-icon) {
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

.act:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  pointer-events: none;
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

/* 编辑当前单词 */
.edit-empty {
  text-align: center;
  padding: 24px 0;
  color: #888;
}

.edit-form {
  padding: 4px 0;
}

.edit-notebook {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  margin-bottom: 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--brand-lizard) 10%, #fff);
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-ink);
}

.edit-notebook :deep(.t-icon) {
  font-size: 15px;
  opacity: 0.7;
}

.form-item {
  margin-bottom: 12px;
}

.form-item label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #6b6b6b;
  margin-bottom: 4px;
}

.edit-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
