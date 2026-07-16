<template>
  <div class="reader-layout">
    <!-- 顶部栏 -->
    <header class="reader-header">
      <t-button variant="text" type="button" @click="goBack">
        <template #icon><t-icon name="chevron-left" /></template>
      </t-button>
      <span class="book-title-header">{{ book?.title }}</span>
      <div class="header-right">
        <t-button variant="outline" size="small" @click="showSettings = true">
          <template #icon><t-icon name="setting" /></template>
          {{ $t('reader.readingSettings') }}
        </t-button>
      </div>
    </header>

    <!-- 主体三栏 -->
    <div class="reader-body" :class="{ resizing: isResizing }">
      <!-- 左侧：目录 -->
      <div class="sidebar-toc-wrapper" :class="{ collapsed: !showToc }" :style="tocWrapperStyle">
        <aside class="sidebar-toc" v-show="showToc">
          <div class="sidebar-title">{{ $t('reader.toc') }}</div>
          <div class="toc-list" v-if="toc.length > 0">
            <div
              v-for="(item, i) in toc"
              :key="i"
              class="toc-item"
              :class="{ active: currentChapter === i }"
              :style="item.level !== undefined ? { paddingLeft: (item.level * 12 + 8) + 'px' } : {}"
              @click="goToChapter(i, item.href)"
            >
              {{ item.label }}
            </div>
          </div>
          <div v-else class="toc-loading">
            <t-loading size="small" /> {{ $t('common.loading') }}
          </div>
        </aside>
        <button class="toc-toggle-btn" @click="toggleToc" :title="showToc ? $t('reader.collapseToc') : $t('reader.expandToc')">
          <t-icon :name="showToc ? 'chevron-left' : 'chevron-right'" size="14px" />
        </button>
      </div>

      <div class="resize-handle resize-handle-left" v-if="showToc" @mousedown="startResizeToc" />

      <!-- 中间：阅读区 -->
      <main class="reader-main" ref="readerContainer">
        <div class="reading-area">
          <div v-if="loading" class="reader-loading">
            <t-loading size="large" :text="$t('common.loading')" />
          </div>
          <!-- EPUB 阅读器 -->
          <div v-show="book?.fileType !== 'pdf'" ref="viewerRef" class="epub-viewer"></div>
          <!-- PDF 阅读器：Canvas 懒加载 + 框选离线 OCR（与 EPUB 逻辑隔离） -->
          <PdfReader
            v-if="book?.fileType === 'pdf'"
            ref="pdfReaderRef"
            :book-id="bookId"
            :initial-page="book?.currentChapter"
            :initial-cfi="book?.currentCfi"
            @ready="onPdfReady"
            @toc="onPdfToc"
            @chapter-highlight="onPdfChapterHighlight"
            @select-text="onPdfSelectText"
            @loading="(v) => loading = v"
            @error="onPdfError"
          />
        </div>

        <!-- 翻页工具栏 (仅 EPUB) -->
        <div class="reader-toolbar" v-if="book?.fileType !== 'pdf'">
          <t-button variant="text" shape="circle" :disabled="readerSettings.flow === 'paginated' && !canGoPrev" @click="prevPageOrScroll">
            <t-icon :name="readerSettings.flow === 'paginated' ? 'chevron-left' : 'chevron-up'" />
          </t-button>
          <span class="page-info">
            {{ pageInfo.current > 0 ? pageInfo.current : '-' }} / {{ pageInfo.total > 0 ? pageInfo.total : '-' }}
          </span>
          <t-button variant="text" shape="circle" :disabled="readerSettings.flow === 'paginated' && !canGoNext" @click="nextPageOrScroll">
            <t-icon :name="readerSettings.flow === 'paginated' ? 'chevron-right' : 'chevron-down'" />
          </t-button>
        </div>
      </main>

      <div class="resize-handle resize-handle-right" @mousedown="startResizeAi" />

      <!-- 右侧：AI 对话框 -->
      <aside class="sidebar-ai" :style="{ width: aiWidth + 'px' }">
        <div class="ai-header">
          <span>{{ $t('reader.aiAssistant') }}</span>
          <div class="ai-header-actions">
            <t-button
              v-if="aiSessions.length > 0"
              variant="text"
              size="small"
              @click="showSessionList = !showSessionList"
            >
              <template #icon><t-icon name="chat" size="14px" /></template>
              {{ currentSessionId ? truncate(aiSessions.find(s => s.id === currentSessionId)?.title || '', 8) : $t('reader.newSession') }}
              <t-icon :name="showSessionList ? 'chevron-up' : 'chevron-down'" size="12px" />
            </t-button>
            <t-button
              v-if="currentSessionId && aiMessages.length > 0"
              variant="text"
              size="small"
              @click="newSession"
            >
              <template #icon><t-icon name="add" size="14px" /></template>
            </t-button>
          </div>
        </div>

        <!-- 会话列表下拉 -->
        <div v-if="showSessionList && aiSessions.length > 0" class="ai-session-list">
          <div
            v-for="session in aiSessions"
            :key="session.id"
            class="ai-session-item"
            :class="{ 'ai-session-item--active': session.id === currentSessionId }"
            @click="switchSession(session.id)"
          >
            <span class="ai-session-title">{{ session.title }}</span>
            <t-button
              variant="text"
              size="small"
              shape="square"
              @click="(e: Event) => handleDeleteSession(session.id, e)"
            >
              <t-icon name="delete" size="12px" />
            </t-button>
          </div>
        </div>

        <!-- 未选文字时的引导状态 -->
        <div v-if="!selectedContext && aiMessages.length === 0 && !currentSessionId" class="ai-empty-state">
          <div class="ai-empty-icon">
            <t-icon name="chat" size="32px" />
          </div>
          <div class="ai-empty-title">{{ $t('reader.aiEmptyTitle') }}</div>
          <div class="ai-empty-desc">{{ $t('reader.aiEmptyDesc') }}</div>
        </div>

        <!-- 选中文字后的内容区 -->
        <template v-else>
          <!-- 选中文本展示（可折叠） -->
          <div v-if="selectedContext" class="ai-context" :class="{ 'ai-context--collapsed': contextCollapsed }" style="width:100%;box-sizing:border-box;">
            <div class="ai-context-label">
              <t-icon name="quote" size="14px" />
              <span>{{ $t('reader.selectedText') }}</span>
              <div class="ai-context-actions">
                <t-button
                  variant="text"
                  size="small"
                  shape="square"
                  @click="contextCollapsed = !contextCollapsed"
                  v-if="selectedContext.length > 80"
                >
                  <t-icon :name="contextCollapsed ? 'chevron-down' : 'chevron-up'" size="14px" />
                </t-button>
                <t-button variant="text" size="small" shape="square" @click="clearSelectedContext">
                  <t-icon name="close" size="14px" />
                </t-button>
              </div>
            </div>
            <div class="ai-context-text" v-if="!contextCollapsed" style="width:100%;box-sizing:border-box;white-space:normal;word-break:break-word;">{{ selectedContext }}</div>
            <div class="ai-context-text ai-context-preview" v-else style="width:100%;box-sizing:border-box;white-space:normal;word-break:break-word;">
              {{ selectedContext.substring(0, 80) }}...
            </div>
          </div>

          <!-- AI 功能标签 -->
          <div v-if="selectedContext" class="ai-feature-panel">
            <div class="ai-feature-header">
              <span class="ai-feature-label">{{ $t('reader.aiFeatures') }}</span>
              <div class="ai-feature-header-actions">
                <t-button
                  size="small"
                  variant="text"
                  theme="primary"
                  @click="openAiPrefDialog"
                >
                  <template #icon><t-icon name="setting" size="12px" /></template>
                  {{ $t('reader.aiPrefTitle') }}
                </t-button>
              </div>
            </div>
            <div class="ai-feature-tags">
              <span
                v-for="feature in preferredAiFeatures"
                :key="feature.featureKey"
                class="ai-feature-tag"
                :class="{ 'ai-feature-tag--active': selectedFeatureKeysForQuery.includes(feature.featureKey) }"
                @click="toggleFeatureKeyForQuery(feature.featureKey)"
              >
                {{ $t('reader.aiFeature_' + feature.featureKey) }}
              </span>
            </div>
            <t-button
              size="small"
              variant="outline"
              class="ai-feature-send"
              :loading="aiLoading"
              :disabled="!selectedContext || selectedFeatureKeysForQuery.length === 0"
              @click="sendAiFeatures"
            >
              <template v-if="!aiLoading">{{ $t('reader.aiSend') }}</template>
            </t-button>
          </div>

          <!-- AI 消息区 -->
          <div class="ai-messages" ref="aiMessagesRef" @scroll="onAiMessagesScroll">
            <!-- 加载更多历史记录 -->
            <div v-if="messagesLoadingMore" class="ai-loading-more">
              <t-loading size="small" />
            </div>
            <div v-else-if="messagesHasMore" class="ai-loading-more ai-loading-more--hint">
              {{ $t('reader.scrollForMore') }}
            </div>
            <div v-if="aiMessages.length === 0" class="ai-empty">
              {{ $t('reader.aiHint1') }}
            </div>
            <div v-for="(msg, i) in aiMessages" :key="i" class="ai-msg" :class="msg.role">
              <div class="ai-msg-content" v-html="renderMarkdown(msg.content)"></div>
              <t-button
                v-if="msg.role === 'assistant'"
                variant="text"
                size="small"
                class="ai-msg-copy"
                @click="copyText(msg.content)"
              >
                <t-icon name="file-copy" size="12px" />
              </t-button>
            </div>
            <div v-if="aiLoading" class="ai-msg assistant">
              <t-loading size="small" />
            </div>
          </div>
        </template>

        <div class="ai-input">
          <t-textarea
            v-model="aiInput"
            :placeholder="$t('reader.aiInputPlaceholder')"
            :autosize="{ minRows: 2, maxRows: 4 }"
            @keydown.enter.ctrl="sendAiMessage"
          />
          <t-button
            class="ai-send-btn"
            size="small"
            :disabled="!aiInput.trim() || aiLoading"
            @click="sendAiMessage"
          >
            <template v-if="!aiLoading">{{ $t('reader.aiSend') }}</template>
            <t-loading v-else size="small" />
          </t-button>
        </div>
      </aside>
    </div>

    <!-- 笔记编辑弹窗 -->
    <t-dialog
      v-model:visible="noteDialog.visible"
      :header="$t('reader.editNote')"
      :confirm-btn="{ content: $t('common.save'), loading: noteDialog.saving }"
      @confirm="saveNoteDialog"
    >
      <t-form label-width="60px">
        <t-form-item :label="$t('reader.originalText')">
          <span class="note-dialog-text">{{ noteDialog.textContent }}</span>
        </t-form-item>
        <t-form-item :label="$t('reader.note')">
          <t-textarea v-model="noteDialog.note" :placeholder="$t('reader.notePlaceholder')" :autosize="{ minRows: 3, maxRows: 8 }" />
        </t-form-item>
        <t-form-item :label="$t('reader.color')">
          <t-radio-group v-model="noteDialog.color">
            <t-radio value="#FFD700">{{ $t('reader.colorYellow') }}</t-radio>
            <t-radio value="#90EE90">{{ $t('reader.colorGreen') }}</t-radio>
            <t-radio value="#87CEEB">{{ $t('reader.colorBlue') }}</t-radio>
            <t-radio value="#FFB6C1">{{ $t('reader.colorPink') }}</t-radio>
          </t-radio-group>
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 阅读设置弹窗 -->
    <t-dialog
      v-model:visible="showSettings"
      :header="$t('reader.readingSettings')"
      :confirm-btn="{ content: $t('common.confirm') }"
      @confirm="showSettings = false"
    >
      <div class="settings-panel">
        <div class="setting-item">
          <div class="setting-label">{{ $t('reader.fontSize') }}</div>
          <div class="setting-control">
            <t-slider
              :value="readerSettings.fontSize"
              :min="12"
              :max="24"
              :step="1"
              @change="onFontSizeChange"
            />
            <span class="setting-value">{{ readerSettings.fontSize }}px</span>
          </div>
        </div>
        <div class="setting-item">
          <div class="setting-label">{{ $t('reader.lineHeight') }}</div>
          <div class="setting-control">
            <t-slider
              :value="readerSettings.lineHeight"
              :min="1.2"
              :max="2.8"
              :step="0.1"
              @change="onLineHeightChange"
            />
            <span class="setting-value">{{ readerSettings.lineHeight }}</span>
          </div>
        </div>
        <div class="setting-item">
          <div class="setting-label">{{ $t('reader.fontWeight') }}</div>
          <div class="setting-control">
            <t-switch v-model="readerSettings.boldWeight" @change="onFontWeightChange" />
          </div>
        </div>
        <div class="setting-item">
          <div class="setting-label">{{ $t('reader.pageMode') }}</div>
          <div class="setting-control">
            <t-radio-group v-model="readerSettings.flow" @change="applyFlow">
              <t-radio value="paginated">{{ $t('reader.pageModePaginated') }}</t-radio>
              <t-radio value="scrolled-doc">{{ $t('reader.pageModeScroll') }}</t-radio>
            </t-radio-group>
          </div>
        </div>
      </div>
    </t-dialog>

    <!-- AI 功能偏好设置弹窗 -->
    <t-dialog
      v-model:visible="showAiPrefDialog"
      :header="$t('reader.aiPrefTitle')"
      :confirm-btn="{ content: $t('common.save'), theme: 'primary', loading: aiPrefSaving }"
      :cancel-btn="{ content: $t('common.cancel') }"
      :close-on-overlay-click="true"
      @confirm="saveAiPreferences"
      @close="onAiPrefDialogClose"
    >
      <div class="ai-pref-panel">
        <div class="ai-pref-sort-hint">{{ $t('reader.aiPrefSortHint') }}</div>
        <div class="ai-pref-list">
          <div
            v-for="(f, index) in orderedAllFeatures"
            :key="f.featureKey"
            class="ai-pref-item"
            draggable="true"
            @dragstart="handleDragStart($event, index)"
            @dragover.prevent="handleDragOver($event, index)"
            @drop="handleDrop($event, index)"
            @dragend="handleDragEnd"
          >
            <span class="ai-pref-drag-handle">
              <t-icon name="move" size="14px" />
            </span>
            <t-checkbox
              :checked="selectedFeatureKeys.includes(f.featureKey)"
              @change="(checked: boolean) => toggleFeatureKey(f.featureKey, checked)"
            />
            <span class="ai-pref-name">{{ $t('reader.aiFeature_' + f.featureKey) }}</span>
            <span class="ai-pref-desc">{{ f.description }}</span>
          </div>
        </div>
      </div>
    </t-dialog>

    <!-- 问题报告弹窗 -->
    <t-dialog
      v-model:visible="showReportDialog"
      :header="$t('reader.reportTitle')"
      :confirm-btn="{ content: $t('reader.reportSend'), theme: 'primary', loading: reportSending }"
      :cancel-btn="{ content: $t('common.cancel') }"
      @confirm="submitReport"
    >
      <div class="report-panel">
        <p class="report-desc">{{ $t('reader.reportDesc') }}</p>
        <t-textarea
          v-model="reportDescription"
          :placeholder="$t('reader.reportPlaceholder')"
          :autosize="{ minRows: 3, maxRows: 6 }"
          :maxlength="500"
          show-limit-number
        />
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import ePub from 'epubjs'
import type { Book, Rendition, Location, NavItem } from 'epubjs'
import axios from 'axios'
import {
  getBookDetail, getBookFileUrl, updateProgress,
  getChapterAnnotations, addAnnotation, updateAnnotation, deleteAnnotation,
  getReaderSettings, saveReaderSettings,
  getAiFeatures, getAiFeaturePreferences, saveAiFeaturePreferences,
  getAiSessions, getAiMessages, getAiMessagesPaged, createAiSession, deleteAiSession
} from '../api'

const PdfReader = defineAsyncComponent(() => import('../components/PdfReader.vue'))

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const bookId = Number(route.params.id)

const book = ref<any>(null)
const toc = ref<NavItem[]>([])
const currentChapter = ref(0)
const showToc = ref(true)

// 三栏宽度与拖拽调整
const tocWidth = ref(200)
const aiWidth = ref(360)
const MIN_TOC_WIDTH = 160
const MAX_TOC_WIDTH = 400
const MIN_AI_WIDTH = 280
const MAX_AI_WIDTH = 600
const isResizing = ref(false)

const tocWrapperStyle = computed(() => {
  if (!showToc.value) return { width: '32px' }
  return { width: tocWidth.value + 'px' }
})

function startResizeToc(e: MouseEvent) {
  e.preventDefault()
  isResizing.value = true
  const startX = e.clientX
  const startWidth = tocWidth.value
  const handleEl = e.currentTarget as HTMLElement
  handleEl.classList.add('resizing')
  document.body.style.userSelect = 'none'

  const onMove = (ev: MouseEvent) => {
    const delta = ev.clientX - startX
    tocWidth.value = Math.min(MAX_TOC_WIDTH, Math.max(MIN_TOC_WIDTH, startWidth + delta))
  }
  const onUp = () => {
    isResizing.value = false
    handleEl.classList.remove('resizing')
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function startResizeAi(e: MouseEvent) {
  e.preventDefault()
  isResizing.value = true
  const startX = e.clientX
  const startWidth = aiWidth.value
  const handleEl = e.currentTarget as HTMLElement
  handleEl.classList.add('resizing')
  document.body.style.userSelect = 'none'

  const onMove = (ev: MouseEvent) => {
    const delta = startX - ev.clientX
    aiWidth.value = Math.min(MAX_AI_WIDTH, Math.max(MIN_AI_WIDTH, startWidth + delta))
  }
  const onUp = () => {
    isResizing.value = false
    handleEl.classList.remove('resizing')
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function toggleToc() {
  const collapsedWidth = 32
  if (showToc.value) {
    const released = Math.max(0, tocWidth.value - collapsedWidth)
    aiWidth.value = Math.min(MAX_AI_WIDTH, aiWidth.value + released)
  } else {
    const returned = Math.max(0, tocWidth.value - collapsedWidth)
    aiWidth.value = Math.max(MIN_AI_WIDTH, aiWidth.value - returned)
  }
  showToc.value = !showToc.value
}

const loading = ref(false)

let epubBook: Book | null = null
let rendition: Rendition | null = null
const viewerRef = ref<HTMLElement | null>(null)
const readerContainer = ref<HTMLElement | null>(null)

// PDF 相关（逻辑在 PdfReader 组件内，此处仅做桥接）
const pdfReaderRef = ref<{ goToPage: (pageNum: number) => void } | null>(null)
const pdfTotalPages = ref(0)

// 选中文字
let selectedCfi = { start: '', end: '' }
let selectedText = ''
const MAX_SELECTION_LENGTH = 800

// 笔记数据
const currentNotes = ref<any[]>([])
// 跟踪已渲染到 EPUB 中的高亮
const renderedHighlights = ref<any[]>([])

// 右侧 AI 助手：当前选中的文本（实时同步显示）
const selectedContext = ref('')

// AI 功能列表（从数据库动态加载）
const aiFeatures = ref<{ featureKey: string; featureName: string; description?: string }[]>([])
const aiFeaturesLoading = ref(false)

// AI 功能偏好
const allAiFeatures = ref<{ featureKey: string; featureName: string; description?: string }[]>([])
const preferredFeatureKeys = ref<string[]>([])
const selectedFeatureKeys = ref<string[]>([])
const selectedFeatureKeysForQuery = ref<string[]>([])
const featureOrder = ref<string[]>([])
const showAiPrefDialog = ref(false)
const aiPrefSaving = ref(false)

// 问题报告
const showReportDialog = ref(false)
const reportDescription = ref('')
const reportSending = ref(false)
const lastErrorDetail = ref('')

// 拖拽排序相关
const dragIndex = ref<number | null>(null)

// 弹窗中按用户自定义顺序显示的功能列表（可拖拽排序）
const orderedAllFeatures = computed(() => {
  const order = featureOrder.value.length > 0 ? featureOrder.value : allAiFeatures.value.map(f => f.featureKey)
  const featureMap = new Map(allAiFeatures.value.map(f => [f.featureKey, f]))
  const result: typeof allAiFeatures.value = []
  for (const key of order) {
    const f = featureMap.get(key)
    if (f) result.push(f)
  }
  // 补充新添加的功能
  for (const f of allAiFeatures.value) {
    if (!order.includes(f.featureKey)) result.push(f)
  }
  return result
})

// 根据偏好过滤后的功能列表（按用户自定义顺序）
const preferredAiFeatures = computed(() => {
  const keys = new Set(preferredFeatureKeys.value)
  return orderedAllFeatures.value.filter(f => keys.has(f.featureKey))
})

// 页码信息
const pageInfo = ref({ current: 0, total: 0 })
const canGoPrev = computed(() => pageInfo.value.current > 1)
const canGoNext = computed(() => pageInfo.value.total > 0 && pageInfo.value.current < pageInfo.value.total)

// 笔记弹窗
const noteDialog = ref({
  visible: false,
  saving: false,
  id: null as number | null,
  textContent: '',
  note: '',
  color: '#FFD700'
})

// 阅读设置
const showSettings = ref(false)
const readerSettings = ref({
  fontSize: 16,
  lineHeight: 1.6,
  flow: 'paginated' as 'paginated' | 'scrolled-doc',
  boldWeight: false
})

let settingsSaveTimer: ReturnType<typeof setTimeout> | null = null

async function loadAiFeatures() {
  aiFeaturesLoading.value = true
  try {
    const prefRes = await getAiFeaturePreferences()
    console.log('[Reader] getAiFeaturePreferences 响应:', prefRes.data)
    const data = prefRes.data.data
    console.log('[Reader] 偏好数据:', { allFeaturesCount: data.allFeatures?.length, preferredKeys: data.preferredKeys, featureOrder: data.featureOrder })
    allAiFeatures.value = data.allFeatures || []
    preferredFeatureKeys.value = data.preferredKeys || []
    featureOrder.value = data.featureOrder || []
  // 初始化查询多选框默认全选用户偏好的功能（仅当前页面有效，不保存到数据库）
  selectedFeatureKeysForQuery.value = [...preferredFeatureKeys.value]
  } catch (e) {
    console.error('[Reader] 加载AI功能偏好失败，降级加载全部功能:', e)
    // 降级：加载全部功能
    try {
      const res = await getAiFeatures()
      aiFeatures.value = res.data.data || []
      allAiFeatures.value = aiFeatures.value
      preferredFeatureKeys.value = allAiFeatures.value.map(f => f.featureKey)
      featureOrder.value = allAiFeatures.value.map(f => f.featureKey)
      selectedFeatureKeysForQuery.value = [...preferredFeatureKeys.value]
    } catch (e2) {
      console.warn('[Reader] 加载AI功能列表失败:', e2)
      aiFeatures.value = []
      allAiFeatures.value = []
      preferredFeatureKeys.value = []
      featureOrder.value = []
      selectedFeatureKeysForQuery.value = []
    }
  } finally {
    aiFeaturesLoading.value = false
  }
}

/** 加载会话列表并自动恢复最近会话 */
async function loadAiSessions() {
  sessionsLoading.value = true
  try {
    const res = await getAiSessions(bookId)
    aiSessions.value = res.data.data || []
    // 自动选中最近会话
    if (aiSessions.value.length > 0 && currentSessionId.value === null) {
      const latest = aiSessions.value[0]
      currentSessionId.value = latest.id
      await loadSessionMessages(latest.id)
    }
  } catch (e) {
    console.warn('[Reader] 加载AI会话列表失败:', e)
  } finally {
    sessionsLoading.value = false
  }
}

/** 加载指定会话的最近消息（最新10条），支持向上滚动加载更多 */
async function loadSessionMessages(sessionId: number) {
  try {
    const res = await getAiMessagesPaged(sessionId, 0, MESSAGE_PAGE_SIZE)
    const data = res.data.data
    // messages 是倒序返回的（最新在前），需要反转为正序显示
    const list: any[] = data.messages || []
    aiMessages.value = list.reverse().map((m: any) => ({
      role: m.role,
      content: m.content
    }))
    messagesTotal.value = data.total || 0
    messagesHasMore.value = data.hasMore || false
    await nextTick()
    scrollAiBottom()
  } catch (e) {
    console.warn('[Reader] 加载会话消息失败:', e)
  }
}

/** 向上滚动加载更早的历史消息 */
async function loadMoreMessages() {
  if (messagesLoadingMore.value || !messagesHasMore.value || !currentSessionId.value) return
  messagesLoadingMore.value = true
  const prevScrollHeight = aiMessagesRef.value?.scrollHeight || 0
  try {
    const currentCount = aiMessages.value.length
    const res = await getAiMessagesPaged(currentSessionId.value, currentCount, MESSAGE_PAGE_SIZE)
    const data = res.data.data
    const list: any[] = data.messages || []
    const olderMessages = list.reverse().map((m: any) => ({
      role: m.role,
      content: m.content
    }))
    aiMessages.value = [...olderMessages, ...aiMessages.value]
    messagesTotal.value = data.total || 0
    messagesHasMore.value = data.hasMore || false
    await nextTick()
    // 保持滚动位置
    if (aiMessagesRef.value) {
      aiMessagesRef.value.scrollTop = aiMessagesRef.value.scrollHeight - prevScrollHeight
    }
  } catch (e) {
    console.warn('[Reader] 加载更多消息失败:', e)
  } finally {
    messagesLoadingMore.value = false
  }
}

/** 监听消息区滚动，滚动到顶部时加载更多 */
function onAiMessagesScroll() {
  const el = aiMessagesRef.value
  if (!el || messagesLoadingMore.value || !messagesHasMore.value) return
  if (el.scrollTop <= 50) {
    loadMoreMessages()
  }
}

/** 切换会话 */
async function switchSession(sessionId: number) {
  if (sessionId === currentSessionId.value) return
  currentSessionId.value = sessionId
  await loadSessionMessages(sessionId)
  showSessionList.value = false
}

/** 新建会话 */
async function newSession() {
  currentSessionId.value = null
  aiMessages.value = []
  messagesTotal.value = 0
  messagesHasMore.value = false
  showSessionList.value = false
}

/** 删除会话 */
async function handleDeleteSession(sessionId: number, event: Event) {
  event.stopPropagation()
  try {
    await deleteAiSession(sessionId)
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      aiMessages.value = []
    }
    await loadAiSessions()
  } catch (e) {
    MessagePlugin.error(t('common.error'))
  }
}

/** 打开偏好设置对话框 */
function openAiPrefDialog() {
  selectedFeatureKeys.value = [...preferredFeatureKeys.value]
  showAiPrefDialog.value = true
}

/** 主页面切换功能选中状态（仅修改当前页面查询选中状态，不与数据库交互） */
function toggleFeatureKeyForQuery(key: string) {
  const idx = selectedFeatureKeysForQuery.value.indexOf(key)
  if (idx >= 0) {
    selectedFeatureKeysForQuery.value.splice(idx, 1)
  } else {
    selectedFeatureKeysForQuery.value.push(key)
  }
  console.log('[Reader] toggleFeatureKeyForQuery:', key, '当前选中:', selectedFeatureKeysForQuery.value)
}

/** 勾选/取消勾选功能（偏好设置弹窗内使用，仅修改弹窗内状态） */
function toggleFeatureKey(key: string, checked: boolean) {
  if (checked) {
    if (!selectedFeatureKeys.value.includes(key)) {
      selectedFeatureKeys.value.push(key)
    }
  } else {
    selectedFeatureKeys.value = selectedFeatureKeys.value.filter(k => k !== key)
  }
}

/** 自动保存 AI 功能偏好（弹窗内排序/勾选变更后，由用户点击保存按钮统一提交） */
let autoSavePending = false
function autoSaveAiPreferences() {
  if (autoSavePending) {
    console.log('[Reader] autoSaveAiPreferences 跳过（pending中）')
    return
  }
  autoSavePending = true
  console.log('[Reader] 保存AI功能偏好，请求参数:', { featureKeys: selectedFeatureKeys.value, featureOrder: featureOrder.value })
  saveAiFeaturePreferences({
    featureKeys: selectedFeatureKeys.value,
    featureOrder: featureOrder.value
  }).then((res) => {
    preferredFeatureKeys.value = [...selectedFeatureKeys.value]
    selectedFeatureKeysForQuery.value = [...selectedFeatureKeys.value]
    console.log('[Reader] AI功能偏好已保存，响应:', res.data, '当前 selectedFeatureKeys:', selectedFeatureKeys.value)
  }).catch(e => {
    console.error('[Reader] 自动保存AI功能偏好失败:', e)
  }).finally(() => {
    autoSavePending = false
  })
}

/** 弹窗关闭时立即保存 */
function onAiPrefDialogClose() {
  if (autoSavePending) return // 已有请求在进行中，跳过
  autoSavePending = true
  saveAiFeaturePreferences({
    featureKeys: selectedFeatureKeys.value,
    featureOrder: featureOrder.value
  }).then(() => {
    preferredFeatureKeys.value = [...selectedFeatureKeys.value]
    selectedFeatureKeysForQuery.value = [...selectedFeatureKeys.value]
  }).catch(e => {
    console.warn('[Reader] 自动保存AI功能偏好失败:', e)
  }).finally(() => {
    autoSavePending = false
  })
}

/** 拖拽开始 */
function handleDragStart(e: DragEvent, index: number) {
  dragIndex.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(index))
  }
}

/** 拖拽经过 */
function handleDragOver(e: DragEvent, index: number) {
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'move'
  }
}

/** 拖拽放下 */
function handleDrop(e: DragEvent, dropIndex: number) {
  e.preventDefault()
  const startIndex = dragIndex.value
  if (startIndex === null || startIndex === dropIndex) return

  const items = [...orderedAllFeatures.value]
  const [moved] = items.splice(startIndex, 1)
  items.splice(dropIndex, 0, moved)

  // 更新排序（仅弹窗内状态，不自动保存到数据库）
  featureOrder.value = items.map(f => f.featureKey)
  dragIndex.value = null
}

/** 拖拽结束 */
function handleDragEnd() {
  dragIndex.value = null
}

/** 保存 AI 功能偏好（手动保存，保留兼容） */
async function saveAiPreferences() {
  aiPrefSaving.value = true
  try {
    await saveAiFeaturePreferences({
      featureKeys: selectedFeatureKeys.value,
      featureOrder: featureOrder.value
    })
    preferredFeatureKeys.value = [...selectedFeatureKeys.value]
    // 同步更新查询多选框的选中状态
    selectedFeatureKeysForQuery.value = [...selectedFeatureKeys.value]
    showAiPrefDialog.value = false
    MessagePlugin.success(t('common.saved'))
  } catch (e) {
    console.warn('[Reader] 保存AI功能偏好失败:', e)
    MessagePlugin.error(t('common.saveFailed'))
  } finally {
    aiPrefSaving.value = false
  }
}

async function loadReaderSettings() {
  try {
    const res = await getReaderSettings()
    const s = res.data.data
    if (s) {
      readerSettings.value.fontSize = s.fontSize || 16
      readerSettings.value.lineHeight = s.lineHeight || 1.6
      readerSettings.value.flow = (s.flow === 'scrolled-doc' ? 'scrolled-doc' : 'paginated') as 'paginated' | 'scrolled-doc'
      readerSettings.value.boldWeight = s.boldWeight === true
      console.log('[Reader] 加载阅读设置成功:', readerSettings.value)
    }
  } catch (e) {
    console.warn('[Reader] 加载阅读设置失败，使用默认值:', e)
  }
}

function scheduleSaveSettings() {
  if (settingsSaveTimer) clearTimeout(settingsSaveTimer)
  settingsSaveTimer = setTimeout(async () => {
    try {
      await saveReaderSettings({
        fontSize: readerSettings.value.fontSize,
        lineHeight: readerSettings.value.lineHeight,
        flow: readerSettings.value.flow,
        boldWeight: readerSettings.value.boldWeight
      })
      console.log('[Reader] 阅读设置已保存')
    } catch (e) {
      console.warn('[Reader] 保存阅读设置失败:', e)
    }
  }, 800)
}

// AI 对话
const aiMessages = ref<{ role: string; content: string }[]>([])
const aiInput = ref('')
const aiLoading = ref(false)
const aiMessagesRef = ref<HTMLElement | null>(null)
const activeFeatureKey = ref<string>('')

// AI 会话管理
const aiSessions = ref<any[]>([])
const currentSessionId = ref<number | null>(null)
const showSessionList = ref(false)
const sessionsLoading = ref(false)

// 消息分页加载
const MESSAGE_PAGE_SIZE = 10
const messagesLoadingMore = ref(false)
const messagesHasMore = ref(false)
const messagesTotal = ref(0)

// 选中文本折叠
const contextCollapsed = ref(false)

function goBack() {
  console.log('[Reader] goBack 被调用, 当前路径:', route.path)
  // 使用 router 导航到书架，避免原生 history.back() 与 vue-router 不同步
  router.push('/bookshelf').then(() => {
    console.log('[Reader] 已导航到书架:', router.currentRoute.value.path)
  }).catch((err) => {
    console.error('[Reader] 导航到书架失败:', err)
    // 兜底：强制跳转
    window.location.href = '/bookshelf'
  })
}

/** 从后端获取当前书籍全部笔记，并在 EPUB 中渲染高亮 */
async function renderAllHighlights() {
  if (!rendition) return
  try {
    const res = await getChapterAnnotations(bookId, currentChapter.value)
    const notes: any[] = res.data.data || []
    // 先清除旧的高亮
    clearAllHighlights()
    // 逐个渲染
    notes.forEach((note: any) => {
      if (note.cfiStart && note.cfiEnd) {
        try {
          const hl = rendition!.annotations.highlight(
            note.cfiStart,
            note.cfiEnd,
            {},
            () => {},
            'highlight',
            { fill: note.color || '#FFD700' }
          )
          renderedHighlights.value.push(hl)
        } catch (e) {
          // 某些 CFI 可能已失效
        }
      }
    })
    console.log('[Reader] 笔记模式: 渲染了', renderedHighlights.value.length, '条划线')
  } catch (e) {
    console.warn('[Reader] 加载笔记失败:', e)
  }
}

/** 清除 EPUB 中所有划线 */
function clearAllHighlights() {
  if (!rendition) return
  try {
    rendition.annotations.remove('highlight')
    renderedHighlights.value = []
    console.log('[Reader] 纯净阅读: 已清除所有划线')
  } catch (e) {
    console.warn('[Reader] 清除划线失败:', e)
  }
}

onMounted(async () => {
  console.log('[Reader] onMounted 开始, bookId:', bookId)
  await loadAiFeatures()
  await loadAiSessions()
  await loadReaderSettings()
  await loadBook()
  console.log('[Reader] loadBook 完成, book:', !!book.value, 'fileType:', book.value?.fileType)
  if (book.value) {
    if (book.value.fileType === 'pdf') {
      console.log('[Reader] 检测到 PDF，由 PdfReader 组件初始化')
      // PdfReader 通过 v-if 挂载后自行加载
    } else {
      console.log('[Reader] 调用 initEpub...')
      initEpub()
    }
  } else {
    console.error('[Reader] book 为空，无法初始化阅读器')
  }
})

onBeforeUnmount(() => {
  if (settingsSaveTimer) clearTimeout(settingsSaveTimer)
  if (rendition) {
    const keyHandler = (rendition as any).__keyHandler
    if (keyHandler) {
      document.removeEventListener('keydown', keyHandler)
    }
    const clickHandler = (rendition as any).__clickHandler
    if (clickHandler) {
      document.removeEventListener('click', clickHandler)
    }
    rendition.destroy()
  }
})

async function loadBook() {
  console.log('[Reader] 加载书籍详情, bookId:', bookId)
  try {
    const res = await getBookDetail(bookId)
    book.value = res.data.data
    console.log('[Reader] 书籍详情:', { id: book.value.id, title: book.value.title, filePath: book.value.filePath, fileType: book.value.fileType, currentCfi: book.value.currentCfi })
    currentChapter.value = book.value.currentChapter || 0
  } catch (e: any) {
    console.error('[Reader] 加载书籍失败:', e)
    MessagePlugin.error(t('reader.loadError'))
  }
}

async function initEpub() {
  if (!viewerRef.value) {
    console.error('[Reader] viewerRef 不存在，无法初始化阅读器')
    return
  }

  const fileUrl = getBookFileUrl(bookId)
  console.log('[Reader] 开始下载 EPUB 文件:', fileUrl)
  loading.value = true

  const downloadStart = performance.now()
  console.log('[Reader] 下载开始时间:', new Date().toISOString())

  try {
    const token = localStorage.getItem('token')
    console.log('[Reader] 准备发起 axios 请求, 带 token:', !!token)

    const controller = new AbortController()
    const timeoutId = setTimeout(() => {
      console.error('[Reader] 下载超时(30s), 主动 abort')
      controller.abort()
    }, 30000)

    const response = await axios.get(fileUrl, {
      responseType: 'arraybuffer',
      timeout: 30000,
      signal: controller.signal,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      onDownloadProgress: (progressEvent) => {
        const total = progressEvent.total || 0
        const loaded = progressEvent.loaded || 0
        if (total > 0) {
          console.log(`[Reader] 下载进度: ${loaded}/${total} (${Math.round(loaded/total*100)}%)`)
        } else {
          console.log(`[Reader] 下载进度: ${loaded} bytes (total 未知)`)
        }
      }
    })

    clearTimeout(timeoutId)

    const downloadDuration = Math.round(performance.now() - downloadStart)
    console.log('[Reader] 下载完成, 耗时:', downloadDuration + 'ms')
    console.log('[Reader] 响应状态:', response.status, response.statusText)
    console.log('[Reader] Content-Type:', response.headers['content-type'])
    console.log('[Reader] Content-Length:', response.headers['content-length'])
    console.log('[Reader] 响应数据类型:', typeof response.data, 'byteLength:', response.data?.byteLength)
    console.log('[Reader] 响应 data 是否为空:', !response.data || response.data.byteLength === 0)

    if (!response.data || response.data.byteLength === 0) {
      throw new Error('下载的文件为空 (0 bytes)，请检查后端文件是否保存成功')
    }

    const buffer = response.data
    console.log('[Reader] ArrayBuffer 大小:', buffer.byteLength, 'bytes')

    console.log('[Reader] 开始解析 EPUB...')
    epubBook = ePub(buffer)
    console.log('[Reader] EPUB 解析完成，挂载渲染器')
    mountRendition()
  } catch (err: any) {
    const downloadDuration = Math.round(performance.now() - downloadStart)
    console.error('[Reader] EPUB 加载失败, 耗时:', downloadDuration + 'ms')
    console.error('[Reader] 错误类型:', err?.constructor?.name)
    console.error('[Reader] 错误消息:', err?.message)
    console.error('[Reader] 错误详情:', err)
    if (err?.response) {
      console.error('[Reader] HTTP 错误状态:', err.response.status)
      console.error('[Reader] HTTP 错误数据:', err.response.data)
    }
    if (axios.isCancel(err) || err?.code === 'ERR_CANCELED') {
      MessagePlugin.error(t('reader.timeoutError'))
    } else if (err?.code === 'ERR_NETWORK') {
      MessagePlugin.error(t('reader.networkError'))
    } else {
      MessagePlugin.error(t('reader.loadEpubError', { msg: err?.message || 'Unknown error' }))
    }
    loading.value = false
  }
}

// ============== PDF 阅读器桥接（实现见 PdfReader.vue，禁止改 EPUB） ==============

function onPdfReady(pages: number) {
  pdfTotalPages.value = pages
  console.log('[Reader] PdfReader ready, pages:', pages)
}

function onPdfToc(items: Array<{ label: string; href: string; level: number }>) {
  toc.value = items as any
  console.log('[Reader] PDF 目录加载完成, 共', items.length, '条')
}

/** PDF 滚动页码 → 目录高亮（与旧逻辑一致：取最后一个页码 ≤ 当前页的目录项） */
function onPdfChapterHighlight(pageNum: number) {
  let targetIndex = -1
  for (let i = 0; i < toc.value.length; i++) {
    const p = parseInt(toc.value[i].href, 10)
    if (p <= pageNum) targetIndex = i
  }
  if (targetIndex >= 0 && currentChapter.value !== targetIndex) {
    currentChapter.value = targetIndex
  }
}

function onPdfSelectText(text: string) {
  selectedText = text
  selectedContext.value = text
  contextCollapsed.value = false
}

function onPdfError(msg: string) {
  MessagePlugin.error(t('reader.loadEpubError', { msg }))
}

// ============== EPUB 阅读器 ==============

function mountRendition(initialCfi?: string) {
  if (!epubBook || !viewerRef.value) {
    console.error('[Reader] mountRendition 失败: epubBook=', !!epubBook, 'viewerRef=', !!viewerRef.value)
    return
  }
  // 清理旧的事件监听和渲染器
  if (rendition) {
    const keyHandler = (rendition as any).__keyHandler
    if (keyHandler) {
      document.removeEventListener('keydown', keyHandler)
    }
    const clickHandler = (rendition as any).__clickHandler
    if (clickHandler) {
      document.removeEventListener('click', clickHandler)
    }
    rendition.destroy()
  }

  // 确保容器有有效高度，epubjs 需要非零尺寸容器
  const containerHeight = readerContainer.value?.clientHeight || window.innerHeight
  console.log('[Reader] 挂载渲染器, viewerRef 尺寸:', viewerRef.value.clientWidth, 'x', viewerRef.value.clientHeight, '容器高度:', containerHeight)
  rendition = epubBook.renderTo(viewerRef.value, {
    width: '100%',
    height: '100%',
    flow: readerSettings.value.flow,
    spread: 'none'
  })
  console.log('[Reader] 渲染器已创建，flow=', readerSettings.value.flow)

  // 应用阅读样式
  applyReaderStyles()

  // 监听内容渲染完成，关闭 loading，并注入双击选中句子功能
  rendition.hooks.content.register((contents: any) => {
    console.log('[Reader] hooks.content 触发, contents:', !!contents)
    console.log('[Reader] 首屏内容已渲染，关闭 loading')
    loading.value = false

    // 双击选中句子：以句号(.或。)为断点，自动选择当前句子而非整个段落
    const doc = contents.document
    if (doc && !(doc as any).__sentenceSelectionInjected) {
      (doc as any).__sentenceSelectionInjected = true
      doc.addEventListener('dblclick', () => {
        const win = contents.window
        const sel = win.getSelection()
        if (!sel || sel.isCollapsed || !sel.rangeCount) return

        const range = sel.getRangeAt(0)
        const selectedText = range.toString().trim()
        if (!selectedText) return

        // 找到最近的块级父元素，获取完整文本用于句子边界检测
        let blockParent: Node = range.commonAncestorContainer
        while (blockParent && blockParent.nodeType === 3) {
          blockParent = blockParent.parentNode!
        }
        // 向上找到块级元素（p, div, section, article, body）
        const BLOCK_TAGS = new Set(['P', 'DIV', 'SECTION', 'ARTICLE', 'BODY', 'LI', 'TD', 'TH', 'BLOCKQUOTE'])
        while (blockParent && !BLOCK_TAGS.has((blockParent as Element).tagName || '')) {
          blockParent = blockParent.parentNode!
        }
        if (!blockParent) return

        const fullText = blockParent.textContent || ''
        if (!fullText) return

        // 计算选中文字在完整文本中的起始偏移
        const treeWalker = doc.createTreeWalker(blockParent, NodeFilter.SHOW_TEXT)
        const textNodes: Text[] = []
        let node: Text | null
        while ((node = treeWalker.nextNode() as Text | null)) {
          textNodes.push(node)
        }

        // 计算选中文本在全文中的偏移
        let globalOffset = 0
        let selectedStartInFull = -1
        let selectedEndInFull = -1
        for (const tn of textNodes) {
          const nodeText = tn.textContent || ''
          const nodeStart = globalOffset
          const nodeEnd = globalOffset + nodeText.length
          if (tn === range.startContainer) {
            selectedStartInFull = nodeStart + range.startOffset
          }
          if (tn === range.endContainer) {
            selectedEndInFull = nodeStart + range.endOffset
          }
          globalOffset = nodeEnd
        }
        if (selectedStartInFull < 0 || selectedEndInFull < 0) return

        // 句子分隔符：英文句号、中文句号
        const SENTENCE_BREAK = /[.。]/

        // 向前搜索句子起点
        let sentenceStart = 0  // 默认段落开头，如果找到前面的句号则从句号后开始
        let searchStart = selectedStartInFull - 1
        let foundPrevBreak = false
        while (searchStart >= 0) {
          if (SENTENCE_BREAK.test(fullText.charAt(searchStart))) {
            sentenceStart = searchStart + 1
            // 跳过句号后面的空白
            while (sentenceStart < fullText.length && /\s/.test(fullText.charAt(sentenceStart))) {
              sentenceStart++
            }
            foundPrevBreak = true
            break
          }
          searchStart--
        }
        // 如果没找到前面的句号，说明这是段落第一个句子，从段落开头开始

        // 向后搜索句子终点
        let sentenceEnd = selectedEndInFull
        while (sentenceEnd < fullText.length) {
          if (SENTENCE_BREAK.test(fullText.charAt(sentenceEnd))) {
            sentenceEnd++ // 包含句号本身
            break
          }
          sentenceEnd++
        }
        if (sentenceEnd > fullText.length) sentenceEnd = fullText.length

        // 如果句子长度与当前选中长度相近（差异<5字符），说明已经是单句，不需要调整
        const sentenceLength = sentenceEnd - sentenceStart
        if (Math.abs(sentenceLength - selectedText.length) < 5) return

        // 将全文偏移转换回 DOM Range
        function offsetToRange(root: Node, targetStart: number, targetEnd: number): Range {
          const r = doc.createRange()
          const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT)
          let currentOffset = 0
          let tn: Text | null
          while ((tn = walker.nextNode() as Text | null)) {
            const len = (tn.textContent || '').length
            const nodeStart = currentOffset
            const nodeEnd = currentOffset + len
            if (targetStart >= nodeStart && targetStart <= nodeEnd && r.startContainer === doc) {
              r.setStart(tn, targetStart - nodeStart)
            }
            if (targetEnd >= nodeStart && targetEnd <= nodeEnd) {
              r.setEnd(tn, targetEnd - nodeStart)
              break
            }
            currentOffset = nodeEnd
          }
          return r
        }

        const newRange = offsetToRange(blockParent, sentenceStart, sentenceEnd)
        sel.removeAllRanges()
        sel.addRange(newRange)
      })
    }
  })

  // 加载目录
  epubBook.loaded.navigation.then((nav: any) => {
    console.log('[Reader] 目录加载完成, 条目数:', nav.toc?.length || 0)
    toc.value = nav.toc || []
  })

  // 恢复到上次阅读位置
  const targetCfi = initialCfi || book.value?.currentCfi
  if (targetCfi) {
    console.log('[Reader] 恢复到位置:', targetCfi)
    const displayPromise = rendition.display(targetCfi)
    console.log('[Reader] rendition.display(cfi) 已调用, 返回类型:', typeof displayPromise)
    if (displayPromise && typeof displayPromise.catch === 'function') {
      displayPromise.catch((e: any) => {
        console.error('[Reader] rendition.display(cfi) 失败:', e)
        loading.value = false
      })
    }
  } else {
    console.log('[Reader] 从头开始显示')
    const displayPromise = rendition.display()
    console.log('[Reader] rendition.display() 已调用, 返回类型:', typeof displayPromise)
    if (displayPromise && typeof displayPromise.catch === 'function') {
      displayPromise.catch((e: any) => {
        console.error('[Reader] rendition.display() 失败:', e)
        loading.value = false
      })
    }
  }

  // 监听章节切换
  rendition.on('relocated', (location: Location) => {
    if (location.start) {
      const spineIndex = location.start.index ?? 0
      const cfi = location.start.cfi
      let href = (location.start as any).href || ''
      // 如果 location 没有 href，尝试从 spine 获取
      if (!href && epubBook) {
        const spineItem = (epubBook as any).spine?.get(spineIndex)
        href = spineItem?.href || spineItem?.url || ''
      }
      console.log('[Reader] 章节切换: spineIndex=', spineIndex, 'href=', href, 'cfi=', cfi)

      // 目录高亮用 toc 索引，而不是 spine 索引（两者不一致）
      const tocIndex = findTocIndexByHref(href)
      if (tocIndex >= 0) {
        console.log('[Reader] 当前目录索引:', tocIndex, toc.value[tocIndex]?.label)
        currentChapter.value = tocIndex
      }

      // 更新页码信息
      const displayed = (location.start as any).displayed
      if (displayed && displayed.page != null && displayed.total != null) {
        pageInfo.value = {
          current: displayed.page,
          total: displayed.total
        }
        console.log('[Reader] 页码更新:', pageInfo.value.current, '/', pageInfo.value.total)
      }

      const percent = epubBook?.locations?.percentageFromCfi(cfi) || 0
      // 保存进度（chapterIndex 用 spineIndex 保持后端一致）
      updateProgress(bookId, {
        chapterIndex: spineIndex,
        cfi: cfi,
        percent: Math.round(percent * 100) / 100
      }).catch(() => {})
      // 加载本章笔记
      loadChapterNotes(spineIndex)
    }
  })

  // 选中文字事件 - 实时同步选中文字到右侧 AI 助手
  rendition.on('selected', (cfiRange: string, contents: any) => {
    const win = contents.window
    const selection = win.getSelection()
    if (!selection || selection.isCollapsed) {
      selectedText = ''
      selectedContext.value = ''
      return
    }
    // 规范化文本：去除首尾空白，合并内部换行/多余空白为单个空格
    const text = selection.toString().replace(/\s+/g, ' ').trim()
    if (!text) {
      selectedText = ''
      selectedContext.value = ''
      return
    }

    // 限制选中长度，避免过长文本导致 AI 处理效果下降
    if (text.length > MAX_SELECTION_LENGTH) {
      selectedText = text.substring(0, MAX_SELECTION_LENGTH)
      MessagePlugin.warning(t('reader.selectionTooLong', { max: MAX_SELECTION_LENGTH, actual: text.length }))
    } else {
      selectedText = text
    }
    selectedCfi = {
      start: cfiRange.split(',')[0] || cfiRange,
      end: cfiRange.split(',')[1] || cfiRange
    }

    // 实时同步选中文字到右侧 AI 助手面板
    selectedContext.value = selectedText
  })

  // 点击其他地方清除选中文本的右侧显示
  const clickHandler = () => {
    // 当用户点击空白区域取消选中时，清除右侧同步显示
    setTimeout(() => {
      const iframe = viewerRef.value?.querySelector('iframe') as HTMLIFrameElement | null
      const win = iframe?.contentWindow
      const sel = win?.getSelection()
      if (!sel || sel.isCollapsed) {
        selectedText = ''
        selectedContext.value = ''
      }
    }, 50)
  }
  document.addEventListener('click', clickHandler)
  // 保存引用以便卸载时移除
  ;(rendition as any).__clickHandler = clickHandler

  // 键盘翻页
  const keyHandler = (e: KeyboardEvent) => {
    if (readerSettings.value.flow === 'paginated') {
      if (e.key === 'ArrowLeft') {
        e.preventDefault()
        prevPage()
      } else if (e.key === 'ArrowRight') {
        e.preventDefault()
        nextPage()
      }
    } else {
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        scrollPage('up')
      } else if (e.key === 'ArrowDown') {
        e.preventDefault()
        scrollPage('down')
      }
    }
  }
  document.addEventListener('keydown', keyHandler)
  // 保存引用以便卸载时移除
  ;(rendition as any).__keyHandler = keyHandler
}

async function loadChapterNotes(chapterIndex: number) {
  try {
    const res = await getChapterAnnotations(bookId, chapterIndex)
    currentNotes.value = res.data.data || []
    // 同步渲染本章高亮，方便用户看到已有笔记位置
    renderAllHighlights()
  } catch (e) {
    // 静默失败
  }
}

function findTocIndexByHref(href: string): number {
  if (!toc.value.length || !href) return -1
  const normalize = (h: string) => {
    try {
      // 去掉 hash 和查询参数，只保留 pathname
      return new URL(h, 'http://x').pathname
    } catch {
      return h.split('#')[0].split('?')[0]
    }
  }
  const target = normalize(href)

  // 精确匹配
  let idx = toc.value.findIndex(item => normalize(item.href) === target)
  if (idx >= 0) return idx

  // 退化匹配：找到 href 是当前位置前缀或包含的最大索引项
  // 例如 toc 项是 xhtml/chapter1.xhtml，当前位置是 xhtml/chapter1.xhtml#p1
  for (let i = toc.value.length - 1; i >= 0; i--) {
    const itemHref = normalize(toc.value[i].href)
    if (itemHref && (target.includes(itemHref) || itemHref.includes(target))) {
      return i
    }
  }
  return -1
}

function goToChapter(index: number, href: string) {
  // PDF 模式：通过页码滚动到对应页
  if (book.value?.fileType === 'pdf') {
    const pageNum = parseInt(href, 10)
    if (!isNaN(pageNum) && pageNum > 0) {
      pdfReaderRef.value?.goToPage(pageNum)
      currentChapter.value = index
    }
    return
  }
  // EPUB 模式
  if (rendition) {
    rendition.display(href)
    currentChapter.value = index
  }
}

function prevPage() {
  if (rendition && canGoPrev.value) {
    console.log('[Reader] 上一页')
    rendition.prev()
  }
}

function nextPage() {
  if (rendition && canGoNext.value) {
    console.log('[Reader] 下一页')
    rendition.next()
  }
}

function prevPageOrScroll() {
  if (readerSettings.value.flow === 'paginated') {
    prevPage()
  } else {
    scrollPage('up')
  }
}

function nextPageOrScroll() {
  if (readerSettings.value.flow === 'paginated') {
    nextPage()
  } else {
    scrollPage('down')
  }
}

function scrollPage(direction: 'up' | 'down') {
  const iframe = viewerRef.value?.querySelector('iframe') as HTMLIFrameElement | null
  if (!iframe || !iframe.contentWindow) return
  const win = iframe.contentWindow
  const amount = win.innerHeight * 0.8
  win.scrollBy({
    top: direction === 'up' ? -amount : amount,
    behavior: 'smooth'
  })
  console.log('[Reader] 上下滚动:', direction)
}

function applyReaderStyles() {
  if (!rendition) return
  try {
    rendition.themes.fontSize(readerSettings.value.fontSize + 'px')
    const fontWeight = readerSettings.value.boldWeight ? 'bold' : 'normal'
    rendition.themes.default({
      'body': {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      },
      'p': {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      },
      'div': {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      }
    })
  } catch (e: any) {
    console.warn('[Reader] 应用阅读样式失败:', e.message)
  }
}

function onFontSizeChange(value: number) {
  readerSettings.value.fontSize = value
  applyFontSize()
  scheduleSaveSettings()
}

function applyFontSize() {
  if (!rendition) return
  rendition.themes.fontSize(readerSettings.value.fontSize + 'px')
  console.log('[Reader] 字体大小调整为:', readerSettings.value.fontSize)
}

function onLineHeightChange(value: number) {
  readerSettings.value.lineHeight = value
  applyLineHeight()
  scheduleSaveSettings()
}

function applyLineHeight() {
  if (!rendition) return
  try {
    const fontWeight = readerSettings.value.boldWeight ? 'bold' : 'normal'
    rendition.themes.default({
      body: {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      },
      p: {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      },
      div: {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      }
    })
    console.log('[Reader] 行间距调整为:', readerSettings.value.lineHeight)
  } catch (e: any) {
    console.warn('[Reader] 调整行间距失败:', e.message)
  }
}

function onFontWeightChange(value: boolean) {
  readerSettings.value.boldWeight = value
  applyFontWeight()
  scheduleSaveSettings()
}

function applyFontWeight() {
  if (!rendition) return
  try {
    const fontWeight = readerSettings.value.boldWeight ? 'bold' : 'normal'
    rendition.themes.default({
      body: {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      },
      p: {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      },
      div: {
        'line-height': String(readerSettings.value.lineHeight),
        'font-weight': fontWeight
      }
    })
    console.log('[Reader] 字体加粗:', readerSettings.value.boldWeight)
  } catch (e: any) {
    console.warn('[Reader] 字体加粗设置失败:', e.message)
  }
}

function applyFlow() {
  if (!epubBook || !rendition) {
    console.warn('[Reader] 无法切换翻页方式：阅读器未初始化')
    return
  }
  const currentCfi = (rendition.location?.start as any)?.cfi
  console.log('[Reader] 切换翻页方式为:', readerSettings.value.flow, '当前位置:', currentCfi)
  mountRendition(currentCfi)
  scheduleSaveSettings()
}

/** 清除 AI 提示词上下文 */
function clearSelectedContext() {
  selectedContext.value = ''
}

async function saveNoteDialog() {
  noteDialog.value.saving = true
  try {
    if (noteDialog.value.id) {
      await updateAnnotation(bookId, noteDialog.value.id, {
        note: noteDialog.value.note,
        color: noteDialog.value.color
      })
    } else {
      await addAnnotation(bookId, {
        bookId,
        chapterIndex: currentChapter.value,
        cfiStart: selectedCfi.start,
        cfiEnd: selectedCfi.end,
        textContent: noteDialog.value.textContent,
        note: noteDialog.value.note,
        color: noteDialog.value.color
      })
    }
    MessagePlugin.success(t('reader.noteSaveSuccess'))
    noteDialog.value.visible = false
    // 刷新笔记数据和高亮
    await loadChapterNotes(currentChapter.value)
  } catch (e: any) {
    MessagePlugin.error(t('reader.noteSaveError'))
  } finally {
    noteDialog.value.saving = false
  }
}

function editNoteDialog(note: any) {
  noteDialog.value = {
    visible: true,
    saving: false,
    id: note.id,
    textContent: note.textContent,
    note: note.note || '',
    color: note.color || '#FFD700'
  }
}

async function handleDeleteNote(id: number) {
  try {
    await deleteAnnotation(bookId, id)
    MessagePlugin.success(t('reader.noteDeleteSuccess'))
    await loadChapterNotes(currentChapter.value)
  } catch (e: any) {
    MessagePlugin.error(t('reader.noteDeleteError'))
  }
}

// 问题报告
function showReportDialogWithError(errorMsg: string) {
  lastErrorDetail.value = errorMsg
  reportDescription.value = ''
  showReportDialog.value = true
}

async function submitReport() {
  if (!reportDescription.value.trim()) return
  reportSending.value = true
  try {
    const token = localStorage.getItem('token')
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    await fetch('/api/books/report-issue', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify({
        username: user.username || user.nickname || '',
        description: reportDescription.value,
        errorDetail: lastErrorDetail.value
      })
    })
    MessagePlugin.success(t('reader.reportSuccess'))
    showReportDialog.value = false
  } catch (e: any) {
    MessagePlugin.error(t('reader.reportFailed'))
  } finally {
    reportSending.value = false
  }
}

// AI 对话
async function sendAiMessage() {
  const question = aiInput.value.trim()
  if (!question) return
  // 如果 aiLoading 卡住，强制重置
  if (aiLoading.value) {
    console.warn('[Reader] sendAiMessage aiLoading 为 true，强制重置为 false')
    aiLoading.value = false
  }
  aiMessages.value.push({ role: 'user', content: question })
  aiInput.value = ''
  aiLoading.value = true
  activeFeatureKey.value = ''
  await nextTick()
  scrollAiBottom()

  // 记录是否是首次对话（用于后续创建会话）
  const isFirstMessage = currentSessionId.value === null

  try {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/books/ai-chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify({
        bookId,
        question,
        context: selectedContext.value || '',
        locale: locale.value,
        sessionId: currentSessionId.value
      })
    })

    if (!response.ok) throw new Error('请求失败')

    const reader = response.body?.getReader()
    if (!reader) throw new Error('不支持流式')

    const decoder = new TextDecoder()
    let fullAnswer = ''
    aiMessages.value.push({ role: 'assistant', content: '' })
    const answerIndex = aiMessages.value.length - 1
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        // 流结束时 flush TextDecoder 中的剩余多字节字符
        buffer += decoder.decode()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const eventBlock of events) {
        if (!eventBlock.trim()) continue
        let eventType = ''
        let dataLines: string[] = []
        for (const line of eventBlock.split('\n')) {
          if (line.startsWith('event:')) eventType = line.substring('event:'.length).trim()
          else if (line.startsWith('data:')) dataLines.push(line.substring('data:'.length))
        }
        if (eventType === 'chunk' && dataLines.length > 0) {
          fullAnswer += dataLines.join('\n')
          aiMessages.value[answerIndex].content = fullAnswer
          await nextTick()
          scrollAiBottom()
        }
        if (eventType === 'error' && dataLines.length > 0) {
          const errMsg = dataLines.join('\n')
          console.error('[AI] SSE error:', errMsg)
          aiMessages.value[answerIndex].content = t('login.error') + ': ' + errMsg
          showReportDialogWithError(errMsg)
          await nextTick()
          scrollAiBottom()
        }
        if (eventType === 'done' && dataLines.length > 0) {
          try {
            const doneData = JSON.parse(dataLines[0])
            if (doneData.sessionId && isFirstMessage) {
              currentSessionId.value = doneData.sessionId
              loadAiSessions()
            }
          } catch {}
        }
      }
    }
    // 处理 buffer 中残留的最后一个不完整事件块
    if (buffer.trim()) {
      const lastEvent = buffer.trim()
      let eventType = ''
      let dataLines: string[] = []
      for (const line of lastEvent.split('\n')) {
        if (line.startsWith('event:')) eventType = line.substring('event:'.length).trim()
        else if (line.startsWith('data:')) dataLines.push(line.substring('data:'.length))
      }
      if (eventType === 'chunk' && dataLines.length > 0) {
        fullAnswer += dataLines.join('\n')
        aiMessages.value[answerIndex].content = fullAnswer
        await nextTick()
        scrollAiBottom()
      }
      if (eventType === 'error' && dataLines.length > 0) {
        const errMsg = dataLines.join('\n')
        console.error('[AI] SSE error (last):', errMsg)
        aiMessages.value[answerIndex].content = t('login.error') + ': ' + errMsg
        showReportDialogWithError(errMsg)
        await nextTick()
        scrollAiBottom()
      }
    }
  } catch (e: any) {
    aiMessages.value.push({ role: 'assistant', content: t('login.error') + ': ' + e.message })
    showReportDialogWithError(e.message || 'AI 请求异常')
  } finally {
    aiLoading.value = false
    activeFeatureKey.value = ''
  }
}

/** 发送 AI 多选功能请求 */
async function sendAiFeatures() {
  console.log('[Reader] sendAiFeatures called, selectedContext:', selectedContext.value, 'selectedFeatureKeysForQuery:', selectedFeatureKeysForQuery.value)
  if (!selectedContext.value || selectedFeatureKeysForQuery.value.length === 0) {
    console.warn('[Reader] sendAiFeatures 被阻止，selectedContext为空:', !selectedContext.value, '或没有选中功能:', selectedFeatureKeysForQuery.value.length === 0)
    return
  }
  // 如果 aiLoading 卡住，强制重置
  if (aiLoading.value) {
    console.warn('[Reader] aiLoading 为 true，强制重置为 false')
    aiLoading.value = false
  }

  const selectedFeatures = preferredAiFeatures.value.filter(f => selectedFeatureKeysForQuery.value.includes(f.featureKey))
  const featureNames = selectedFeatures.map(f => t('reader.aiFeature_' + f.featureKey)).join(' + ')

  aiMessages.value.push({ role: 'user', content: `【${featureNames}】${selectedContext.value}` })
  aiLoading.value = true
  activeFeatureKey.value = 'multi'

  const isFirstMessage = currentSessionId.value === null

  await nextTick()
  scrollAiBottom()

  try {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/books/ai-chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify({
        bookId,
        featureKeys: selectedFeatureKeysForQuery.value,
        context: selectedContext.value || '',
        locale: locale.value,
        sessionId: currentSessionId.value
      })
    })

    if (!response.ok) throw new Error('请求失败')

    const reader = response.body?.getReader()
    if (!reader) throw new Error('不支持流式')

    const decoder = new TextDecoder()
    let fullAnswer = ''
    aiMessages.value.push({ role: 'assistant', content: '' })
    const answerIndex = aiMessages.value.length - 1
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        // 流结束时 flush TextDecoder 中的剩余多字节字符
        buffer += decoder.decode()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const eventBlock of events) {
        if (!eventBlock.trim()) continue
        let eventType = ''
        let dataLines: string[] = []
        for (const line of eventBlock.split('\n')) {
          if (line.startsWith('event:')) eventType = line.substring('event:'.length).trim()
          else if (line.startsWith('data:')) dataLines.push(line.substring('data:'.length))
        }
        if (eventType === 'chunk' && dataLines.length > 0) {
          fullAnswer += dataLines.join('\n')
          aiMessages.value[answerIndex].content = fullAnswer
          await nextTick()
          scrollAiBottom()
        }
        if (eventType === 'error' && dataLines.length > 0) {
          const errMsg = dataLines.join('\n')
          console.error('[AI] SSE error:', errMsg)
          aiMessages.value[answerIndex].content = t('login.error') + ': ' + errMsg
          showReportDialogWithError(errMsg)
          await nextTick()
          scrollAiBottom()
        }
        if (eventType === 'done' && dataLines.length > 0) {
          try {
            const doneData = JSON.parse(dataLines[0])
            if (doneData.sessionId && isFirstMessage) {
              currentSessionId.value = doneData.sessionId
              loadAiSessions()
            }
          } catch {}
        }
      }
    }
    // 处理 buffer 中残留的最后一个不完整事件块
    if (buffer.trim()) {
      const lastEvent = buffer.trim()
      let eventType = ''
      let dataLines: string[] = []
      for (const line of lastEvent.split('\n')) {
        if (line.startsWith('event:')) eventType = line.substring('event:'.length).trim()
        else if (line.startsWith('data:')) dataLines.push(line.substring('data:'.length))
      }
      if (eventType === 'chunk' && dataLines.length > 0) {
        fullAnswer += dataLines.join('\n')
        aiMessages.value[answerIndex].content = fullAnswer
        await nextTick()
        scrollAiBottom()
      }
      if (eventType === 'error' && dataLines.length > 0) {
        const errMsg = dataLines.join('\n')
        console.error('[AI] SSE error (last):', errMsg)
        aiMessages.value[answerIndex].content = t('login.error') + ': ' + errMsg
        showReportDialogWithError(errMsg)
        await nextTick()
        scrollAiBottom()
      }
    }
  } catch (e: any) {
    aiMessages.value.push({ role: 'assistant', content: t('login.error') + ': ' + e.message })
    showReportDialogWithError(e.message || 'AI 请求异常')
  } finally {
    aiLoading.value = false
    activeFeatureKey.value = ''
  }
}

function scrollAiBottom() {
  nextTick(() => {
    if (aiMessagesRef.value) {
      aiMessagesRef.value.scrollTop = aiMessagesRef.value.scrollHeight
    }
  })
}

function truncate(text: string, len: number) {
  if (!text) return ''
  return text.length > len ? text.substring(0, len) + '...' : text
}

/** 简单 markdown 渲染（加粗、代码块、换行） */
function renderMarkdown(text: string): string {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // 代码块
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  // 粗体
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  // 换行
  html = html.replace(/\n/g, '<br>')
  return html
}

/** 复制文本到剪贴板 */
async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    MessagePlugin.success(t('common.copied'))
  } catch {
    MessagePlugin.warning(t('common.copyFailed'))
  }
}
</script>

<style scoped>
.reader-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.reader-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}

.book-title-header {
  font-size: 16px;
  font-weight: 600;
  flex: 1;
}

.header-right {
  display: flex;
  gap: 8px;
}

.reader-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左侧目录 */
.sidebar-toc-wrapper {
  position: relative;
  flex-shrink: 0;
  transition: width 0.25s ease;
  display: flex;
}

.sidebar-toc-wrapper.collapsed {
  width: 32px;
}

.sidebar-toc {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-right: 1px solid #eee;
  overflow-y: auto;
  padding: 12px;
}

.sidebar-toc-wrapper.collapsed .sidebar-toc {
  padding: 0;
  overflow: hidden;
}

.toc-toggle-btn {
  position: absolute;
  right: -12px;
  top: 16px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid #ddd;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #888;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  z-index: 10;
  padding: 0;
  transition: all 0.2s ease;
}

.toc-toggle-btn:hover {
  border-color: #b0aae0;
  color: #5e57b5;
  box-shadow: 0 2px 6px rgba(94, 87, 181, 0.15);
}

/* 三栏分隔线/拖拽条 */
.resize-handle {
  width: 2px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  transition: background-color 0.2s ease;
  position: relative;
  z-index: 5;
}

.resize-handle::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 1px;
  height: 100%;
  background: #e5e7eb;
  transition: background 0.2s ease;
}

.resize-handle:hover::after,
.resize-handle.resizing::after,
.reader-body.resizing .resize-handle::after {
  background: #b0aae0;
}

.resize-handle:hover,
.resize-handle.resizing,
.reader-body.resizing .resize-handle {
  background: rgba(94, 87, 181, 0.06);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.toc-item {
  padding: 6px 8px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  border-radius: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toc-item:hover {
  background: #f3f4f6;
}

.toc-item.active {
  background: #eef2ff;
  color: #6366f1;
  font-weight: 500;
}

.toc-loading {
  font-size: 13px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 中间阅读区 */
.reader-main {
  flex: 1;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
}

.reading-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.epub-viewer {
  flex: 1;
  min-height: 0;
  position: relative;
}

/* PDF 样式已移至 PdfReader.vue */

.reader-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
}

/* 翻页工具栏 */
.reader-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 12px;
  background: #fff;
  border-top: 1px solid #eee;
  flex-shrink: 0;
}

.page-info {
  font-size: 13px;
  color: #4b5563;
  min-width: 60px;
  text-align: center;
}

/* 右侧 AI */
.sidebar-ai {
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #eee;
  display: flex;
  flex-direction: column;
  box-shadow: -2px 0 12px rgba(0, 0, 0, 0.04);
}

.ai-header {
  padding: 8px 12px;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.ai-header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

/* 会话列表 */
.ai-session-list {
  max-height: 160px;
  overflow-y: auto;
  border-bottom: 1px solid #eee;
  background: #fafafa;
}

.ai-session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
  color: #555;
  transition: background 0.15s;
}

.ai-session-item:hover {
  background: #f0f0f5;
}

.ai-session-item--active {
  background: #eae8f5;
  color: #5e57b5;
  font-weight: 500;
}

.ai-session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-empty {
  font-size: 13px;
  color: #9ca3af;
  text-align: center;
  padding: 24px 0;
}

.ai-msg {
  padding: 6px 10px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.7;
  position: relative;
}

.ai-msg.user {
  background: #f0eef7;
  color: #4a3f8a;
  margin-left: 20px;
  border-bottom-right-radius: 4px;
}

.ai-msg.assistant {
  background: #f7f8fa;
  color: #2d2d3a;
  margin-right: 20px;
  border-bottom-left-radius: 4px;
}

.ai-msg-content {
  word-break: break-word;
}

.ai-msg-content :deep(code) {
  background: #e8e8ed;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'SF Mono', 'Menlo', monospace;
}

.ai-msg-content :deep(pre) {
  background: #2d2d3a;
  color: #e0e0e0;
  padding: 10px 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 6px 0;
  font-size: 12px;
}

.ai-msg-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}

.ai-msg-content :deep(strong) {
  color: #5e57b5;
  font-weight: 600;
}

.ai-msg-copy {
  position: absolute;
  top: 4px;
  right: 4px;
  opacity: 0;
  transition: opacity 0.2s;
  color: #999;
}

.ai-msg:hover .ai-msg-copy {
  opacity: 1;
}

/* 未选文字时的引导状态 */
.ai-empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
  text-align: center;
  gap: 12px;
}
.ai-empty-icon {
  color: #d1d5db;
  margin-bottom: 4px;
}
.ai-empty-title {
  font-size: 14px;
  font-weight: 600;
  color: #4b5563;
}
.ai-empty-desc {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
  max-width: 200px;
}

/* AI 提示词上下文 */
.ai-context {
  display: block;
  width: 100%;
  align-self: stretch;
  box-sizing: border-box;
  padding: 6px 8px 6px 6px;
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.ai-context--collapsed {
  background: #fffdf5;
}

.ai-context-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #a16207;
  margin-bottom: 6px;
}

.ai-context-actions {
  display: flex;
  align-items: center;
  margin-left: auto;
  gap: 2px;
}

.ai-context-text {
  font-size: 12px;
  color: #78716c;
  line-height: 1.7;
  max-height: 120px;
  overflow-y: auto;
  white-space: normal;
  word-break: break-word;
  width: 100%;
  box-sizing: border-box;
  padding: 8px 8px 8px 8px;
  background: #fffbeb;
  border-left: 3px solid #fcd34d;
  border-radius: 0 6px 6px 0;
}

.ai-context-preview {
  font-style: italic;
  color: #a8a29e;
  border-left-color: #e5e0d0;
}

/* AI 功能多选框区域 */
.ai-feature-panel {
  padding: 6px 10px;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}

.ai-feature-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.ai-feature-label {
  font-size: 11px;
  font-weight: 600;
  color: #4b5563;
}

.ai-feature-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-feature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}

.ai-feature-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 14px;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s ease;
  background-color: #f5f5f7;
  color: #999;
  border: 1px solid #eee;
}

.ai-feature-tag:hover {
  background-color: #eaeaec;
  color: #666;
  border-color: #ddd;
}

.ai-feature-tag--active {
  background-color: #e8e7f7;
  color: #5e57b5;
  font-weight: 600;
  border-color: #c8c4e8;
}

.ai-feature-tag--active:hover {
  background-color: #deddf2;
  color: #4f479e;
  border-color: #b0aae0;
}

.ai-feature-tag-icon {
  flex-shrink: 0;
}

.ai-feature-send {
  margin-top: 2px;
  width: 100%;
  font-size: 12px;
  border-color: #5e57b5 !important;
  color: #fff !important;
  background: #5e57b5 !important;
  font-weight: 600;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.ai-feature-send:hover:not(:disabled) {
  background: #4f479e !important;
  border-color: #4f479e !important;
}

.ai-feature-send:disabled {
  border-color: #e0dfe8 !important;
  color: #c4c1d8 !important;
  background: #f5f5f7 !important;
  opacity: 1;
}

/* AI 偏好设置按钮 */
.ai-pref-btn {
  font-size: 11px;
  padding: 0 4px;
  height: 20px;
}
.ai-pref-btn :deep(.t-button__text) {
  display: flex;
  align-items: center;
  gap: 2px;
}

/* AI 偏好设置弹窗 */
.ai-pref-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}
.ai-pref-hint {
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}
.ai-pref-sort-hint {
  font-size: 12px;
  color: #b0b3b8;
  line-height: 1.5;
}
.ai-pref-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ai-pref-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: move;
  transition: background-color 0.2s;
  border: 1px solid transparent;
}
.ai-pref-item:hover {
  background-color: #f5f7fa;
  border-color: #e4e7ed;
}
.ai-pref-item[draggable="true"]:active {
  cursor: grabbing;
}
.ai-pref-drag-handle {
  color: #c0c4cc;
  cursor: move;
  padding-top: 2px;
  flex-shrink: 0;
}
.ai-pref-drag-handle:hover {
  color: #909399;
}
.ai-pref-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--td-text-color-primary);
  flex-shrink: 0;
}
.ai-pref-desc {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.5;
  flex: 1;
}

/* 问题报告弹窗 */
.report-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}
.report-desc {
  margin: 0;
  font-size: 14px;
  color: #666;
  line-height: 1.6;
}

.ai-input {
  padding: 6px 10px 8px;
  border-top: 1px solid #eee;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.ai-input :deep(.t-textarea__inner) {
  font-size: 13px;
}

.ai-send-btn {
  flex-shrink: 0;
  background: #5e57b5 !important;
  border-color: #5e57b5 !important;
  color: #fff !important;
  font-weight: 600;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.ai-send-btn:hover:not(:disabled) {
  background: #4f479e !important;
  border-color: #4f479e !important;
}

.ai-send-btn:disabled {
  background: #f0f0f5 !important;
  border-color: #e0dfe8 !important;
  color: #c4c1d8 !important;
  opacity: 1;
}

.note-dialog-text {
  font-size: 13px;
  color: #6b7280;
  font-style: italic;
}

/* 阅读设置 */
.settings-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 8px 0;
}

.setting-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

.setting-control {
  display: flex;
  align-items: center;
  gap: 16px;
}

.setting-control :deep(.t-slider) {
  flex: 1;
}

.setting-value {
  font-size: 13px;
  color: #6b7280;
  min-width: 40px;
  text-align: right;
}


</style>
