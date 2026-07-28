<template>
  <div class="reader-layout" :class="{ 'is-portrait': isPortrait }">
    <!-- 顶部栏 -->
    <header class="reader-header">
      <div class="header-left">
        <t-button variant="text" type="button" @click="goBack">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <span class="book-title-header" :title="book?.title || undefined">{{ book?.title }}</span>
      </div>
      <div class="header-center">
        <span class="header-brand">{{ $t('reader.aiAssistant') }}</span>
      </div>
      <div class="header-right">
        <t-button
          variant="outline"
          size="small"
          class="header-action-btn"
          :title="activeNotebook?.notebookName || $t('reader.notebookLinkTitle')"
          @click="onHeaderNotebookClick"
        >
          <template #icon><t-icon name="book" /></template>
          <span class="header-action-label">
            {{
              activeNotebook
                ? truncate(activeNotebook.notebookName, 14)
                : $t('reader.notebookLinkTitle')
            }}
          </span>
        </t-button>
        <t-button
          variant="outline"
          size="small"
          class="header-action-btn"
          @click="openOperationGuide"
        >
          <template #icon><t-icon name="info-circle" /></template>
          <span class="header-action-label">{{ $t('reader.operationGuide') }}</span>
        </t-button>
        <t-button
          variant="outline"
          size="small"
          class="header-action-btn"
          @click="showSettings = true"
        >
          <template #icon><t-icon name="setting" /></template>
          <span class="header-action-label">{{ $t('reader.readingSettings') }}</span>
        </t-button>
      </div>
    </header>

    <div v-if="isPortrait" class="portrait-hint" role="status">
      <t-icon name="info-circle" size="16px" />
      <span>{{ $t('reader.portraitHint') }}</span>
    </div>

    <!-- 主体三栏 -->
    <div class="reader-body" :class="{ resizing: isResizing }">
      <!-- 左侧：目录（竖屏隐藏） -->
      <div
        v-show="!isPortrait"
        class="sidebar-toc-wrapper"
        :class="{ collapsed: !showToc }"
        :style="tocWrapperStyle"
      >
        <aside class="sidebar-toc" v-show="showToc">
          <div class="sidebar-title">{{ $t('reader.toc') }}</div>
          <div class="toc-list" v-if="toc.length > 0">
            <div
              v-for="(item, i) in toc"
              :key="i"
              class="toc-item"
              :class="{ active: currentChapter === i, 'has-note': tocItemHasNote(i) }"
              :style="item.level !== undefined ? { paddingLeft: (item.level * 12 + 8) + 'px' } : {}"
              :title="tocItemHasNote(i) ? $t('reader.tocHasNote') : undefined"
            >
              <span class="toc-item-label" @click="goToChapter(i, item.href)">{{ item.label }}</span>
              <span v-if="tocItemHasNote(i)" class="toc-note-dot" aria-hidden="true" />
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

      <div class="resize-handle resize-handle-left" v-if="showToc && !isPortrait" @mousedown="startResizeToc" />

      <!-- 中间：阅读区 -->
      <main class="reader-main" ref="readerContainer">
        <div class="reading-area" data-guide="pdf-page">
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
            @noted-pages="onPdfNotedPages"
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

      <div class="resize-handle resize-handle-right" v-if="showAi && !isPortrait" @mousedown="startResizeAi" />

      <!-- 右侧：AI 对话框（竖屏隐藏） -->
      <div
        v-show="!isPortrait"
        class="sidebar-ai-wrapper"
        :class="{ collapsed: !showAi }"
        :style="aiWrapperStyle"
      >
        <button
          class="ai-toggle-btn"
          @click="toggleAi"
          :title="showAi ? $t('reader.collapseAi') : $t('reader.expandAi')"
        >
          <t-icon :name="showAi ? 'chevron-right' : 'chevron-left'" size="14px" />
        </button>
        <aside class="sidebar-ai" data-guide="ai-panel" v-show="showAi">
        <!-- 未选文字时的引导状态 -->
        <div v-if="!selectedContext && aiMessages.length === 0" class="ai-empty-state">
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
              <span class="ai-context-hint">{{ $t('reader.targetWordsHint') }}</span>
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
            <div
              v-if="!contextCollapsed"
              class="ai-context-text"
              style="width:100%;box-sizing:border-box;white-space:normal;word-break:break-word;"
            >
              <template v-for="(seg, i) in contextSegments" :key="i">
                <span
                  v-if="seg.type === 'word'"
                  class="ai-context-word"
                  :class="{ 'ai-context-word--active': isTargetSegmentSelected(i) }"
                  @dblclick.prevent.stop="onContextWordDblClick(i)"
                >{{ seg.value }}</span>
                <template v-else>{{ seg.value }}</template>
              </template>
            </div>
            <div class="ai-context-text ai-context-preview" v-else style="width:100%;box-sizing:border-box;white-space:normal;word-break:break-word;">
              {{ selectedContext.substring(0, 80) }}...
            </div>
          </div>

          <!-- AI 功能标签 -->
          <div v-if="selectedContext" class="ai-feature-panel">
            <div class="ai-feature-tags-row">
              <div class="ai-feature-tags">
                <span
                  v-for="feature in preferredAiFeatures"
                  :key="feature.featureKey"
                  class="ai-feature-tag"
                  :class="{ 'ai-feature-tag--active': selectedFeatureKeysForQuery.includes(feature.featureKey) }"
                  @click="toggleFeatureKeyForQuery(feature.featureKey)"
                >
                  {{ featureLabel(feature) }}
                </span>
              </div>
              <t-button
                class="ai-feature-pref-btn"
                size="small"
                variant="text"
                theme="primary"
                shape="square"
                :title="$t('reader.aiPrefTitle')"
                @click="openAiPrefDialog"
              >
                <t-icon name="setting" size="16px" />
              </t-button>
            </div>
            <div class="ai-feature-send-row">
              <t-button
                size="small"
                theme="primary"
                variant="outline"
                class="ai-feature-send"
                :loading="aiLoading && activeFeatureKey === 'multi'"
                :disabled="!selectedContext || selectedFeatureKeysForQuery.length === 0 || (aiLoading && activeFeatureKey !== 'multi')"
                @click="sendAiFeatures"
              >
                <template v-if="!(aiLoading && activeFeatureKey === 'multi')">{{ $t('reader.aiAnalyze') }}</template>
              </t-button>
            </div>
          </div>

          <!-- AI 消息区 -->
          <div class="ai-messages" ref="aiMessagesRef" @scroll="onAiMessagesScroll">
            <div v-if="messagesLoadingMore" class="ai-loading-more">
              <t-loading size="small" />
            </div>
            <div v-else-if="messagesHasMore" class="ai-loading-more ai-loading-more--hint">
              {{ $t('reader.scrollForMore') }}
            </div>
            <div v-if="aiMessages.length === 0" class="ai-empty">
              {{ $t('reader.aiHint1') }}
            </div>
            <div v-for="(msg, i) in aiMessages" :key="msg.id ?? i" class="ai-msg" :class="msg.role">
              <div
                class="ai-msg-content"
                :class="isAnalyzeAssistantMessage(msg) ? 'ai-msg-content--analyze' : 'ai-msg-content--chat'"
                v-html="renderAiMessageHtml(msg)"
              ></div>
              <div
                v-if="isAnalyzeAssistantMessage(msg) && (msg.content || msg.analyzeResult || looksLikeAnalyzeJson(msg.content))"
                class="ai-msg-actions"
              >
                <t-button
                  variant="text"
                  theme="primary"
                  size="small"
                  class="ai-msg-collect"
                  @click="openCollectForMessage(msg)"
                >
                  <template #icon><t-icon name="bookmark-add" size="12px" /></template>
                  {{ $t('reader.collectFromReply') }}
                  <span
                    v-if="collectableCount(msg) > 0"
                    class="ai-msg-collect-count"
                  >{{ collectableCount(msg) }}</span>
                </t-button>
              </div>
            </div>
            <div v-if="aiLoading" class="ai-msg assistant">
              <t-loading size="small" />
            </div>
          </div>
        </template>

        <div class="ai-composer" data-guide="ai-composer">
          <t-textarea
            class="ai-composer-input"
            v-model="aiInput"
            :placeholder="$t('reader.aiInputPlaceholder')"
            :autosize="{ minRows: 2, maxRows: 2 }"
            @keydown="onAiComposerKeydown"
          />
          <t-button
            class="ai-composer-send"
            size="small"
            :disabled="!aiInput.trim() || aiLoading"
            @click="sendAiMessage"
          >
            <template v-if="!(aiLoading && !activeFeatureKey)">
              <t-icon name="send" size="14px" />
              <span>{{ $t('reader.aiSend') }}</span>
            </template>
            <t-loading v-else size="small" />
          </t-button>
        </div>
      </aside>
      </div>
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
            <span class="ai-pref-name">{{ featureLabel(f) }}</span>
            <span class="ai-pref-desc">{{ featureDesc(f) }}</span>
          </div>
        </div>
      </div>
    </t-dialog>

    <!-- PDF 首次操作引导 -->
    <ReaderGuideTour
      v-model:visible="showPdfSelectTip"
      :steps="pdfGuideSteps"
      @finish="dismissPdfSelectTip"
    />

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

    <ReaderCollectDrawer
      v-model:visible="showCollectDrawer"
      :book-id="bookId"
      :current-entries="currentCollectEntries"
      :linked-notebooks="linkedNotebooks"
      :source-snippet="collectSourceSnippet"
      :source-location="collectSourceLocation"
      :preferred-view-mode="collectDrawerPreferredMode"
      :target-notebook-id="collectTargetNotebookId"
      @open-link-dialog="showNotebookLinkDialog = true"
      @select-notebook="onDrawerSelectNotebook"
    />
    <ReaderNotebookLinkDialog
      v-model:visible="showNotebookLinkDialog"
      v-model:active-notebook-id="activeNotebookId"
      :book-id="bookId"
      :book-title="book?.title"
      @changed="onNotebookLinksChanged"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, defineAsyncComponent, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import ePub from 'epubjs'
import type { Book, Rendition, Location } from 'epubjs'
import axios from 'axios'
import {
  getBookDetail, getBookFileUrl,
  getChapterAnnotations, getAnnotations,
  getReaderSettings, saveReaderSettings,
  getAiFeaturePreferences, saveAiFeaturePreferences,
  getAiSessions, getAiMessagesPaged,
  getBookNotebookLinks
} from '../api'
import ReaderCollectDrawer from '../components/ReaderCollectDrawer.vue'
import ReaderNotebookLinkDialog from '../components/ReaderNotebookLinkDialog.vue'
import ReaderGuideTour, { type GuideStep } from '../components/ReaderGuideTour.vue'
import {
  extractContextFromUserAiMessage,
  hydrateAiMessageList,
  makeBasketKey,
  parseAnalyzeResultFromStored,
  parseCollectEntriesFromText,
  resolveActiveNotebookId,
  saveActiveNotebookId,
  stripCollectMarker,
  type CollectEntry
} from '../utils/collectBasket'
import { normalizeSelectedText, pickSentenceForLemma, tokenizeContextSegments } from '../utils/selectedText'
import {
  AI_FEATURE_CATALOG,
  AI_FEATURE_KEYS,
  normalizeAiFeatureKeys,
  type AiFeatureDef
} from '../utils/aiFeatures'
import {
  formatAnalyzeResultMarkdown,
  normalizeDisplayFeatureKeys,
  renderAnalyzeMarkdown,
  renderChatMarkdown
} from '../utils/aiMarkdown'
import {
  isTransportError,
  syncAnnotationCreate,
  syncAnnotationDelete,
  syncAnnotationUpdate,
  syncProgress,
} from '../utils/offlineSync'
import { aiRegionHeaders } from '../utils/aiRegion'
import {
  createStudyDurationTracker,
  type StudyDurationTracker
} from '../utils/studyDurationTracker'

const PdfReader = defineAsyncComponent(() => import('../components/PdfReader.vue'))

const route = useRoute()
const router = useRouter()
const { t, te, locale } = useI18n()
const bookId = Number(route.params.id)

/** 阅读有效时长分段（切标签/空闲结段；对用户不可见） */
let studyDurationTracker: StudyDurationTracker | null = null

const book = ref<any>(null)
const toc = ref<any[]>([])
const currentChapter = ref(0)
/** PDF：有批注的页码；EPUB：有划线笔记的目录下标 */
const notedPages = ref<number[]>([])
const notedChapterIndexes = ref<Set<number>>(new Set())
const showToc = ref(true)
const showAi = ref(true)
/** 竖屏：只留图书，隐藏目录与 AI */
const isPortrait = ref(false)
let orientationMq: MediaQueryList | null = null

function syncPortraitMode() {
  isPortrait.value = orientationMq?.matches ?? false
}

// 三栏宽度与拖拽调整
const tocWidth = ref(200)
const aiWidth = ref(360)
const MIN_TOC_WIDTH = 160
const MAX_TOC_WIDTH = 400
const MIN_AI_WIDTH = 280
const MAX_AI_WIDTH = 600
const isResizing = ref(false)

const tocWrapperStyle = computed(() => {
  if (isPortrait.value) return { width: '0' }
  if (!showToc.value) return { width: '32px' }
  return { width: tocWidth.value + 'px' }
})

const aiWrapperStyle = computed(() => {
  if (isPortrait.value) return { width: '0' }
  if (!showAi.value) return { width: '32px' }
  return { width: aiWidth.value + 'px' }
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
  if (isPortrait.value) return
  showToc.value = !showToc.value
}

function toggleAi() {
  if (isPortrait.value) return
  showAi.value = !showAi.value
}

const loading = ref(false)

let epubBook: Book | null = null
let rendition: Rendition | null = null
const viewerRef = ref<HTMLElement | null>(null)
const readerContainer = ref<HTMLElement | null>(null)

// PDF 相关（逻辑在 PdfReader 组件内，此处仅做桥接）
const pdfReaderRef = ref<{
  goToPage: (pageNum: number) => void
  getCurrentPage?: () => number
} | null>(null)

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
/** 双击选中的目标词：按片段下标（同一词多次出现互不影响） */
const targetWordIndexes = ref<number[]>([])

const contextSegments = computed(() => tokenizeContextSegments(selectedContext.value || ''))

/** 发给后端的去重词形（保留首次点击时的写法） */
const targetWords = computed(() => {
  const segs = contextSegments.value
  const seen = new Set<string>()
  const out: string[] = []
  for (const i of targetWordIndexes.value) {
    const seg = segs[i]
    if (!seg || seg.type !== 'word') continue
    const cleaned = normalizeWordToken(seg.value)
    if (!cleaned) continue
    const key = cleaned.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    out.push(cleaned)
  }
  return out
})

watch(selectedContext, () => {
  const segs = tokenizeContextSegments(selectedContext.value || '')
  const wordIndexes = segs
    .map((seg, i) => (seg.type === 'word' ? i : -1))
    .filter((i) => i >= 0)
  // 选区只有一个词时自动选中该位置
  targetWordIndexes.value = wordIndexes.length === 1 ? [wordIndexes[0]] : []
})

function normalizeWordToken(w: string) {
  return (w || '').replace(/^[’']+|[’'\-]+$/g, '')
}

function isTargetSegmentSelected(i: number) {
  return targetWordIndexes.value.includes(i)
}

function onContextWordDblClick(i: number) {
  const seg = contextSegments.value[i]
  if (!seg || seg.type !== 'word') return
  const idx = targetWordIndexes.value.indexOf(i)
  if (idx >= 0) {
    targetWordIndexes.value = targetWordIndexes.value.filter((x) => x !== i)
  } else {
    targetWordIndexes.value = [...targetWordIndexes.value, i]
  }
  window.getSelection()?.removeAllRanges()
}

// AI 功能：前端固定目录 + 用户偏好（wd_user_ai_preference）
const aiFeaturesLoading = ref(false)
const preferredFeatureKeys = ref<string[]>([...AI_FEATURE_KEYS])
const selectedFeatureKeys = ref<string[]>([])
const selectedFeatureKeysForQuery = ref<string[]>([...AI_FEATURE_KEYS])
const featureOrder = ref<string[]>([...AI_FEATURE_KEYS])
const showAiPrefDialog = ref(false)
const aiPrefSaving = ref(false)

const PDF_SELECT_TIP_KEY = 'aikoread.reader.pdfSelectTip.v4'
const showPdfSelectTip = ref(false)
/** 本次进入阅读器已关过引导则不再自动弹出，避免误关后重开到第 1 步 */
const pdfGuideClosedThisSession = ref(false)

const pdfGuideSteps = computed<GuideStep[]>(() => [
  {
    selector: '[data-guide="pdf-select"]',
    title: t('reader.guideStep1Title'),
    body: t('reader.guideStep1Body'),
    placement: 'left',
    demo: 'tap',
  },
  {
    selector: '[data-guide="pdf-page"]',
    title: t('reader.guideStep2Title'),
    body: t('reader.guideStep2Body'),
    placement: 'bottom',
    demo: 'drag',
  },
  {
    selector: '[data-guide="ai-panel"]',
    title: t('reader.guideStep3Title'),
    body: t('reader.guideStep3Body'),
    placement: 'left',
    demo: 'dblclick',
  },
  {
    selector: '[data-guide="ai-panel"]',
    title: t('reader.guideStep4Title'),
    body: t('reader.guideStep4Body'),
    placement: 'left',
    demo: 'analyze',
  },
  {
    selector: '[data-guide="ai-composer"]',
    title: t('reader.guideStep5Title'),
    body: t('reader.guideStep5Body'),
    placement: 'left',
    demo: 'ask',
  },
])

function maybeShowPdfSelectTip() {
  if (pdfGuideClosedThisSession.value || showPdfSelectTip.value) return
  try {
    if (localStorage.getItem(PDF_SELECT_TIP_KEY)) return
  } catch {
    /* ignore */
  }
  // 等工具栏渲染完再开引导
  nextTick(() => {
    setTimeout(() => {
      if (pdfGuideClosedThisSession.value || showPdfSelectTip.value) return
      try {
        if (localStorage.getItem(PDF_SELECT_TIP_KEY)) return
      } catch {
        /* ignore */
      }
      showPdfSelectTip.value = true
    }, 280)
  })
}

function dismissPdfSelectTip(payload?: { dontShowAgain?: boolean }) {
  showPdfSelectTip.value = false
  pdfGuideClosedThisSession.value = true
  if (!payload?.dontShowAgain) return
  try {
    localStorage.setItem(PDF_SELECT_TIP_KEY, '1')
  } catch {
    /* ignore */
  }
}

// 问题报告
const showReportDialog = ref(false)
const reportDescription = ref('')
const reportSending = ref(false)
const lastErrorDetail = ref('')

// 阅读收录
const showCollectDrawer = ref(false)
const showNotebookLinkDialog = ref(false)
const linkedNotebooks = ref<{ notebookId: number; notebookName: string }[]>([])
const currentCollectEntries = ref<CollectEntry[]>([])
const collectSourceSnippet = ref('')
const collectSourceLocation = ref('')
const collectDrawerPreferredMode = ref<'current' | 'notebook'>('current')
const collectTargetNotebookId = ref<number | undefined>()
const activeNotebookId = ref<number | undefined>()

const activeNotebook = computed(() =>
  linkedNotebooks.value.find(n => n.notebookId === activeNotebookId.value)
)

type AiAnalyzeResult = Record<string, unknown>

type AiMessage = {
  id?: number
  role: string
  content: string
  /** 本次解析勾选的功能（顺序 = 展示顺序） */
  featureKeys?: string[]
  analyzeResult?: AiAnalyzeResult
  collectEntries?: CollectEntry[]
  collectSourceSnippet?: string
  collectSourceLocation?: string
}

function attachMessageId(answerIndex: number, messageId?: number) {
  if (messageId == null) return
  const msg = aiMessages.value[answerIndex]
  if (!msg) return
  aiMessages.value[answerIndex] = { ...msg, id: messageId }
}

watch(activeNotebookId, (id) => {
  if (id != null) saveActiveNotebookId(bookId, id)
})

/** 顶栏单词本：有本则打开词表（可切换/新建）；无本则打开新建 */
function onHeaderNotebookClick() {
  if (!linkedNotebooks.value.length) {
    showNotebookLinkDialog.value = true
    return
  }
  const id = activeNotebookId.value || linkedNotebooks.value[0].notebookId
  openNotebookWords(id)
}

function onDrawerSelectNotebook(notebookId: number) {
  activeNotebookId.value = notebookId
}

/** 手动打开操作引导（不受「下次不再提醒」限制） */
function openOperationGuide() {
  pdfGuideClosedThisSession.value = false
  if (showPdfSelectTip.value) {
    showPdfSelectTip.value = false
    nextTick(() => {
      showPdfSelectTip.value = true
    })
  } else {
    showPdfSelectTip.value = true
  }
}

/** 顶栏：点当前单词本 → 查看该本词表 */
function openNotebookWords(notebookId: number) {
  currentCollectEntries.value = []
  collectSourceSnippet.value = ''
  collectSourceLocation.value = ''
  collectTargetNotebookId.value = notebookId
  collectDrawerPreferredMode.value = 'notebook'
  showCollectDrawer.value = true
}

/** 仅单词 / 固定搭配（短语）可加入单词本 */
function collectableEntriesOf(msg: AiMessage): CollectEntry[] {
  const list = msg.collectEntries || []
  return list.filter(e => e.entryType === 'word' || e.entryType === 'phrase')
}

/** 合并 analyzeResult 分包词条 / collectEntries；旧 Markdown 正文仅作兜底 */
function mergeCollectEntriesFromMessage(msg: AiMessage, snippet = ''): CollectEntry[] {
  const map = new Map<string, CollectEntry>()
  const snip = snippet || msg.collectSourceSnippet || ''
  const mergeEntry = (e: CollectEntry) => {
    const existing = map.get(e.basketKey)
    if (!existing) {
      map.set(e.basketKey, e)
    } else {
      // 同词条多条目：合并释义（AI 可能分多条返回同一词的不同含义）
      const merged = existing.partSpeechMean + '；' + e.partSpeechMean
      map.set(e.basketKey, { ...existing, partSpeechMean: merged })
    }
  }
  for (const e of collectableEntriesOf(msg)) {
    mergeEntry(e)
  }
  if (msg.analyzeResult) {
    for (const e of collectEntriesFromKeyBasedResult(msg.analyzeResult, snip)) {
      mergeEntry(e)
    }
  }
  if (!msg.analyzeResult && msg.content) {
    for (const e of parseCollectEntriesFromText(msg.content, snip)) {
      if (e.entryType !== 'word' && e.entryType !== 'phrase') continue
      mergeEntry(e)
    }
  }
  return [...map.values()]
}

/** 任意 feature_key 下的词条数组均可收录；不按 key 名硬编码分流 */
function collectEntriesFromKeyBasedResult(result: AiAnalyzeResult, snippet: string): CollectEntry[] {
  const out: CollectEntry[] = []
  for (const raw of Object.values(result || {})) {
    if (!Array.isArray(raw) || !raw.length) continue
    const rows = raw.filter((e) => e && typeof e === 'object' && (('word' in (e as object)) || ('wordProto' in (e as object))))
    if (!rows.length) continue
    out.push(
      ...normalizeCollectEntries(
        rows.map((e: any) => {
          const word = String(e?.word || e?.wordProto || '')
          const entryType = e?.entryType || (word.includes(' ') ? 'phrase' : 'word')
          return { ...e, entryType }
        }),
        snippet
      )
    )
  }
  return out
}

function collectableCount(msg: AiMessage): number {
  return mergeCollectEntriesFromMessage(msg).length
}

/** 某条 AI 回复旁：加入单词本（仅单词/短语） */
function openCollectForMessage(msg: AiMessage) {
  // 例句来自选中原文；历史消息可能丢了 snippet，从前一条 user 或当前选区补回
  let snippet = msg.collectSourceSnippet || selectedContext.value || ''
  if (!snippet) {
    const idx = aiMessages.value.findIndex(m => m === msg)
    for (let i = idx - 1; i >= 0; i--) {
      const prev = aiMessages.value[i]
      if (prev.role === 'user') {
        snippet = extractContextFromUserAiMessage(prev.content || '')
        break
      }
    }
  }

  const entries = mergeCollectEntriesFromMessage(msg, snippet)
  if (!entries.length) {
    MessagePlugin.info(t('reader.collectNothing'))
    return
  }
  if (linkedNotebooks.value.length === 0) {
    MessagePlugin.warning(t('reader.collectNeedLink'))
    showNotebookLinkDialog.value = true
    return
  }
  currentCollectEntries.value = entries.map(e => {
    const example = pickSentenceForLemma(
      snippet || e.sourceSnippet || e.sentenceExample || '',
      e.wordProto
    )
    return {
      ...e,
      sentenceExample: example || e.sentenceExample,
      sourceSnippet: snippet || e.sourceSnippet
    }
  })
  collectSourceSnippet.value = snippet
  collectSourceLocation.value = msg.collectSourceLocation || ''
  collectTargetNotebookId.value = activeNotebookId.value || linkedNotebooks.value[0]?.notebookId
  collectDrawerPreferredMode.value = 'current'
  showCollectDrawer.value = true
}

async function loadNotebookLinks() {
  try {
    const res = await getBookNotebookLinks(bookId)
    linkedNotebooks.value = res.data.data || []
    const ids = linkedNotebooks.value.map(n => n.notebookId)
    activeNotebookId.value = resolveActiveNotebookId(bookId, ids, activeNotebookId.value)
  } catch {
    linkedNotebooks.value = []
    activeNotebookId.value = undefined
  }
}

async function onNotebookLinksChanged() {
  await loadNotebookLinks()
  if (showCollectDrawer.value && activeNotebookId.value) {
    collectTargetNotebookId.value = activeNotebookId.value
  }
}

function normalizeCollectEntries(rawEntries: any[], snippet: string): CollectEntry[] {
  const out: CollectEntry[] = []
  for (const e of rawEntries || []) {
    const wordProto = String(e?.word || e?.wordProto || '').trim()
    const partSpeechMean = String(e?.meaning || e?.partSpeechMean || '').trim()
    if (!wordProto || !partSpeechMean) continue
    const entryType: CollectEntry['entryType'] =
      e.entryType === 'phrase' || wordProto.includes(' ') ? 'phrase' : 'word'
    const example = snippet ? pickSentenceForLemma(snippet, wordProto) : ''
    out.push({
      basketKey: makeBasketKey({ entryType, wordProto }),
      entryType,
      wordProto,
      partSpeechMean,
      usPhonetic: e.usPhonetic != null ? String(e.usPhonetic) : undefined,
      ukPhonetic: e.ukPhonetic != null ? String(e.ukPhonetic) : undefined,
      sentenceExample: example || snippet || undefined,
      rootAffix: e.rootAffix != null ? String(e.rootAffix) : undefined,
      sourceSnippet: snippet || undefined
    })
  }
  return out
}

function attachCollectEntriesToMessage(answerIndex: number, rawEntries: any[], snippetOverride?: string) {
  const snippet = (snippetOverride ?? selectedContext.value) || ''
  const location = selectedCfi.start || ''
  const entries = normalizeCollectEntries(rawEntries, snippet)
  const msg = aiMessages.value[answerIndex]
  if (!msg || entries.length === 0) return
  // 已有 analyzeResult 时不覆盖 collectEntries，统一由 mergeCollectEntriesFromMessage 合并，避免重复
  if (msg.analyzeResult) return
  aiMessages.value[answerIndex] = {
    ...msg,
    collectEntries: entries,
    collectSourceSnippet: snippet || msg.collectSourceSnippet,
    collectSourceLocation: location || msg.collectSourceLocation
  }
}

function attachAnalyzeResultToMessage(
  answerIndex: number,
  payload: AiAnalyzeResult | string,
  snippetOverride?: string,
  featureKeys?: string[]
) {
  const msg = aiMessages.value[answerIndex]
  if (!msg) return
  const snippet = (snippetOverride ?? selectedContext.value) || ''
  const keys = normalizeDisplayFeatureKeys(featureKeys ?? msg.featureKeys)
  let source: AiAnalyzeResult = {}
  if (typeof payload === 'string') {
    const parsed = parseAnalyzeResultFromStored(payload)
    if (parsed) {
      source = { ...parsed }
    } else {
      try {
        const once = JSON.parse(payload)
        if (typeof once === 'string') {
          const twice = parseAnalyzeResultFromStored(once)
          if (twice) source = { ...twice }
          else source = { raw: once }
        } else if (once && typeof once === 'object' && !Array.isArray(once)) {
          source = { ...once }
        } else {
          source = { raw: payload }
        }
      } catch {
        source = { raw: payload }
      }
    }
  } else {
    source = { ...(payload || {}) }
  }
  normalizeAnalyzeKeyAliases(source)
  // 后端解析失败时可能只有 raw：再试一次从 raw 还原结构化
  if (typeof source.raw === 'string') {
    const fromRaw = repairAnalyzeJson(String(source.raw)) || parseAnalyzeResultFromStored(source.raw)
    if (fromRaw) {
      normalizeAnalyzeKeyAliases(fromRaw)
      const { raw: _drop, ...rest } = source
      source = { ...fromRaw, ...rest }
      delete source.raw
    }
  }

  const pruned: AiAnalyzeResult = {}
  if (keys.length) {
    for (const k of keys) {
      if (source[k] != null && source[k] !== '') pruned[k] = source[k]
    }
  }
  if (!Object.keys(pruned).filter((k) => k !== 'raw').length) {
    Object.assign(pruned, source)
  }
  delete pruned.raw

  const entries = collectEntriesFromKeyBasedResult(pruned, snippet)
  const hasDisplay = Object.keys(pruned).some(
    (k) => pruned[k] != null && pruned[k] !== '' && !(Array.isArray(pruned[k]) && !(pruned[k] as any[]).length)
  )
  // 始终清掉 content 里的 JSON；有结构就走 analyzeResult，避免右侧刷原始 JSON
  // 有 analyzeResult 时不再设 collectEntries，统一由 mergeCollectEntriesFromMessage 合并，避免重复
  aiMessages.value[answerIndex] = {
    ...msg,
    featureKeys: keys.length ? keys : msg.featureKeys,
    content: hasDisplay ? '' : '',
    analyzeResult: hasDisplay ? pruned : undefined,
    collectEntries: hasDisplay ? undefined : (entries.length ? entries : msg.collectEntries),
    collectSourceSnippet: snippet || msg.collectSourceSnippet
  }
  // 若仍无结构，把原始文本放 content，但展示层还会再尝试 parse
  if (!hasDisplay) {
    const fallback =
      typeof payload === 'string'
        ? payload
        : typeof (payload as any)?.raw === 'string'
          ? String((payload as any).raw)
          : msg.content || ''
    aiMessages.value[answerIndex].content = fallback
  }
}

type SseHandlers = {
  onChunk?: (text: string) => void
  onError?: (msg: string) => void
  onDone?: (data: any) => void
  onCollectEntries?: (entries: any[]) => void
  onAnalyzeResult?: (payload: AiAnalyzeResult) => void
}

/**
 * @param keepDataLeadingSpace Aiko 问答专用：Spring 发 data:<payload> 无协议空格，
 *   模型分片常以空格开头（" Bedouin"），剥掉会丢词间空格。
 *   解析场景勿开：解析不拼 chunk 正文，展示走 analyze-result，保持原逻辑。
 */
function parseSseEventBlock(
  eventBlock: string,
  options?: { keepDataLeadingSpace?: boolean }
): { eventType: string; dataLines: string[] } {
  let eventType = ''
  const dataLines: string[] = []
  const keepSpace = !!options?.keepDataLeadingSpace
  for (const line of eventBlock.split('\n')) {
    if (line.startsWith('event:')) eventType = line.substring('event:'.length).trim()
    else if (line.startsWith('data:')) {
      let data = line.substring('data:'.length)
      // 默认仍按 HTML SSE「data: 后可选一空格」剥离；仅问答保留
      if (!keepSpace && data.startsWith(' ')) data = data.slice(1)
      dataLines.push(data)
    }
  }
  return { eventType, dataLines }
}

function dispatchSseEvent(eventType: string, dataLines: string[], handlers: SseHandlers) {
  const rawData = dataLines.join('\n')

  if (eventType === 'chunk' && dataLines.length > 0) {
    handlers.onChunk?.(rawData)
  }
  if (eventType === 'error' && dataLines.length > 0) {
    handlers.onError?.(rawData)
  }
  if (eventType === 'done' && dataLines.length > 0) {
    try {
      handlers.onDone?.(JSON.parse(dataLines[0]))
    } catch {
      handlers.onDone?.({})
    }
  }
  if (eventType === 'collect-entries' && dataLines.length > 0) {
    try {
      const payload = unwrapSseJson(rawData)
      handlers.onCollectEntries?.(payload?.entries || [])
    } catch (e) {
      console.warn('[Reader] collect-entries parse failed', e)
    }
  }
  if (eventType === 'analyze-result' && dataLines.length > 0) {
    try {
      const payload = unwrapSseJson(rawData)
      if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
        handlers.onAnalyzeResult?.(payload as AiAnalyzeResult)
      }
    } catch (e) {
      console.warn('[Reader] analyze-result parse failed', e)
    }
  }
}

/** SSE data 可能已是对象 JSON，也可能被多包一层字符串 */
function unwrapSseJson(rawData: string): any {
  let cur: any = JSON.parse(rawData)
  // 兼容旧后端：writeValueAsString + APPLICATION_JSON 导致双重编码
  for (let i = 0; i < 2 && typeof cur === 'string'; i++) {
    const t = cur.trim()
    if (!(t.startsWith('{') || t.startsWith('['))) break
    cur = JSON.parse(t)
  }
  return cur
}

function processSseEventBlock(
  eventBlock: string,
  handlers: SseHandlers,
  options?: { keepDataLeadingSpace?: boolean }
) {
  if (!eventBlock.trim()) return
  const { eventType, dataLines } = parseSseEventBlock(eventBlock, options)
  dispatchSseEvent(eventType, dataLines, handlers)
}

// 拖拽排序相关
const dragIndex = ref<number | null>(null)

// 弹窗功能列表：按用户排序展示固定目录
const orderedAllFeatures = computed(() => {
  const map = new Map(AI_FEATURE_CATALOG.map((f) => [f.featureKey, f]))
  return featureOrder.value
    .map((k) => map.get(k))
    .filter((f): f is AiFeatureDef => !!f)
})

// 标签栏：启用项按 featureOrder（拖拽顺序）排列
const preferredAiFeatures = computed(() => {
  const map = new Map(AI_FEATURE_CATALOG.map((f) => [f.featureKey, f]))
  const enabled = new Set(preferredFeatureKeys.value)
  return featureOrder.value
    .filter((k) => enabled.has(k))
    .map((k) => map.get(k))
    .filter((f): f is AiFeatureDef => !!f)
})

/** 把弹窗内勾选 + 拖拽顺序落到页面状态（preferredKeys 必须跟 featureOrder 对齐） */
function applyLocalAiPreferences(keys: string[], order: string[]) {
  const normalized = normalizeAiFeatureKeys(keys, order)
  preferredFeatureKeys.value = normalized.preferredKeys
  featureOrder.value = normalized.featureOrder
  selectedFeatureKeysForQuery.value = [...normalized.preferredKeys]
}

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
    const prefRes = await getAiFeaturePreferences(locale.value)
    const data = prefRes.data.data || {}
    const normalized = normalizeAiFeatureKeys(data.preferredKeys, data.featureOrder)
    preferredFeatureKeys.value = normalized.preferredKeys
    featureOrder.value = normalized.featureOrder
    selectedFeatureKeysForQuery.value = [...normalized.preferredKeys]
  } catch (e) {
    console.warn('[Reader] 加载AI功能偏好失败，使用默认全部启用:', e)
    preferredFeatureKeys.value = [...AI_FEATURE_KEYS]
    featureOrder.value = [...AI_FEATURE_KEYS]
    selectedFeatureKeysForQuery.value = [...AI_FEATURE_KEYS]
  } finally {
    aiFeaturesLoading.value = false
  }
}

watch(locale, () => {
  void loadAiFeatures()
})

function featureLabel(f: { featureKey: string; nameKey?: string; featureName?: string }) {
  if (f.nameKey) return t(f.nameKey)
  const def = AI_FEATURE_CATALOG.find((x) => x.featureKey === f.featureKey)
  if (def) return t(def.nameKey)
  return f.featureName?.trim() || f.featureKey
}

function featureDesc(f: AiFeatureDef) {
  return t(f.descKey)
}

function featureTitleByKey(key: string) {
  const def = AI_FEATURE_CATALOG.find((x) => x.featureKey === key)
  return def ? t(def.nameKey) : key
}

function featureMetaForDisplay() {
  return AI_FEATURE_CATALOG.map((f) => ({
    featureKey: f.featureKey,
    featureName: t(f.nameKey)
  }))
}

/** 进入阅读器时恢复该书最近会话历史（无会话列表 / 新对话入口） */
async function loadAiSessions() {
  try {
    const res = await getAiSessions(bookId)
    const sessions = res.data.data || []
    if (sessions.length > 0 && currentSessionId.value === null) {
      const latest = sessions[0]
      currentSessionId.value = latest.id
      await loadSessionMessages(latest.id)
    }
  } catch (e) {
    console.warn('[Reader] 加载AI会话失败:', e)
  }
}

async function loadSessionMessages(sessionId: number) {
  try {
    const res = await getAiMessagesPaged(sessionId, 0, MESSAGE_PAGE_SIZE)
    const data = res.data.data
    const list: any[] = data.messages || []
    aiMessages.value = hydrateAiMessageList(list.reverse())
    messagesHasMore.value = data.hasMore || false
    await nextTick()
    scrollAiBottom()
  } catch (e) {
    console.warn('[Reader] 加载会话消息失败:', e)
  }
}

async function loadMoreMessages() {
  if (messagesLoadingMore.value || !messagesHasMore.value || !currentSessionId.value) return
  messagesLoadingMore.value = true
  const prevScrollHeight = aiMessagesRef.value?.scrollHeight || 0
  try {
    const res = await getAiMessagesPaged(currentSessionId.value, aiMessages.value.length, MESSAGE_PAGE_SIZE)
    const data = res.data.data
    const list: any[] = data.messages || []
    const older = hydrateAiMessageList(list.reverse())
    aiMessages.value = [...older, ...aiMessages.value]
    messagesHasMore.value = data.hasMore || false
    await nextTick()
    if (aiMessagesRef.value) {
      aiMessagesRef.value.scrollTop = aiMessagesRef.value.scrollHeight - prevScrollHeight
    }
  } catch (e) {
    console.warn('[Reader] 加载更多消息失败:', e)
  } finally {
    messagesLoadingMore.value = false
  }
}

function onAiMessagesScroll() {
  const el = aiMessagesRef.value
  if (!el || messagesLoadingMore.value || !messagesHasMore.value) return
  if (el.scrollTop <= 50) loadMoreMessages()
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
  if (autoSavePending) return
  autoSavePending = true
  const normalized = normalizeAiFeatureKeys(selectedFeatureKeys.value, featureOrder.value)
  saveAiFeaturePreferences({
    featureKeys: normalized.preferredKeys,
    featureOrder: normalized.featureOrder
  }).then(() => {
    applyLocalAiPreferences(normalized.preferredKeys, normalized.featureOrder)
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
  const normalized = normalizeAiFeatureKeys(selectedFeatureKeys.value, featureOrder.value)
  saveAiFeaturePreferences({
    featureKeys: normalized.preferredKeys,
    featureOrder: normalized.featureOrder
  }).then(() => {
    applyLocalAiPreferences(normalized.preferredKeys, normalized.featureOrder)
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
    const normalized = normalizeAiFeatureKeys(selectedFeatureKeys.value, featureOrder.value)
    await saveAiFeaturePreferences({
      featureKeys: normalized.preferredKeys,
      featureOrder: normalized.featureOrder
    })
    applyLocalAiPreferences(normalized.preferredKeys, normalized.featureOrder)
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
const aiMessages = ref<AiMessage[]>([])
const aiInput = ref('')
const aiLoading = ref(false)
const aiMessagesRef = ref<HTMLElement | null>(null)
const activeFeatureKey = ref<string>('')

// AI 会话：进入阅读器恢复最近历史，不提供会话列表 / 新对话入口
const currentSessionId = ref<number | null>(null)
const MESSAGE_PAGE_SIZE = 10
const messagesLoadingMore = ref(false)
const messagesHasMore = ref(false)

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
          const hl = (rendition as any).annotations.highlight(
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
    ;(rendition as any).annotations.remove('highlight')
    renderedHighlights.value = []
    console.log('[Reader] 纯净阅读: 已清除所有划线')
  } catch (e) {
    console.warn('[Reader] 清除划线失败:', e)
  }
}

onMounted(async () => {
  console.log('[Reader] onMounted 开始, bookId:', bookId)
  orientationMq = window.matchMedia('(orientation: portrait)')
  syncPortraitMode()
  orientationMq.addEventListener('change', syncPortraitMode)
  // 优先加载书籍，避免 AI 会话/设置请求拖死正文
  await loadBook()
  console.log('[Reader] loadBook 完成, book:', !!book.value, 'fileType:', book.value?.fileType)
  if (book.value) {
    studyDurationTracker = createStudyDurationTracker({
      actionType: 'reading',
      refTable: 'wd_book',
      refId: bookId,
      root: readerContainer.value || undefined
    })
    if (book.value.fileType === 'pdf') {
      console.log('[Reader] 检测到 PDF，由 PdfReader 组件初始化')
    } else {
      console.log('[Reader] 调用 initEpub...')
      initEpub()
    }
  } else {
    console.error('[Reader] book 为空，无法初始化阅读器')
  }
  // 侧栏数据并行加载，互不影响
  void Promise.allSettled([
    loadNotebookLinks(),
    loadAiFeatures(),
    loadAiSessions(),
    loadReaderSettings()
  ])
})

onBeforeUnmount(() => {
  studyDurationTracker?.dispose()
  studyDurationTracker = null
  orientationMq?.removeEventListener('change', syncPortraitMode)
  orientationMq = null
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
      timeout: 600000,
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
  maybeShowPdfSelectTip()
}

function onPdfToc(items: Array<{ label: string; href: string; level: number }>) {
  toc.value = items as any
  console.log('[Reader] PDF 目录加载完成, 共', items.length, '条')
}

function onPdfNotedPages(pages: number[]) {
  notedPages.value = pages || []
}

/** 目录项是否有笔记：PDF 按页区间；EPUB 按章节下标 */
function tocItemHasNote(index: number): boolean {
  if (book.value?.fileType === 'pdf') {
    if (!notedPages.value.length) return false
    const start = parseInt(toc.value[index]?.href, 10)
    if (!Number.isFinite(start) || start <= 0) return false
    let end = Number.POSITIVE_INFINITY
    for (let j = index + 1; j < toc.value.length; j++) {
      const p = parseInt(toc.value[j].href, 10)
      if (Number.isFinite(p) && p > start) {
        end = p
        break
      }
    }
    return notedPages.value.some((page) => page >= start && page < end)
  }
  return notedChapterIndexes.value.has(index)
}

async function loadEpubNotedChapters() {
  if (book.value?.fileType === 'pdf') return
  try {
    const res = await getAnnotations(bookId)
    const list = (res.data?.data || []) as Array<{ chapterIndex?: number }>
    const set = new Set<number>()
    for (const a of list) {
      if (a.chapterIndex != null && a.chapterIndex >= 0) set.add(a.chapterIndex)
    }
    notedChapterIndexes.value = set
  } catch (e) {
    console.warn('[Reader] 加载笔记章节标记失败:', e)
  }
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
  const normalized = normalizeSelectedText(text)
  selectedText = normalized
  selectedContext.value = normalized
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
    void loadEpubNotedChapters()
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
    studyDurationTracker?.noteActivity()
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
      // 保存进度（chapterIndex 用 spineIndex 保持后端一致；弱网入队稍后同步）
      void syncProgress(bookId, {
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
    // 规范化：尽量保留多句边界（换行 / 粘连的大写开句），再压空白
    const text = normalizeSelectedText(selection.toString())
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
  targetWordIndexes.value = []
}

async function saveNoteDialog() {
  noteDialog.value.saving = true
  try {
    if (noteDialog.value.id) {
      await syncAnnotationUpdate(bookId, noteDialog.value.id, {
        note: noteDialog.value.note,
        color: noteDialog.value.color
      })
    } else {
      await syncAnnotationCreate(bookId, `local-${Date.now()}`, {
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
    // 刷新笔记数据和高亮（弱网时可能仍是旧数据，队列会在恢复后补齐）
    try {
      await loadChapterNotes(currentChapter.value)
      void loadEpubNotedChapters()
    } catch { /* ignore */ }
  } catch (e: any) {
    if (!isTransportError(e)) {
      MessagePlugin.error(t('reader.noteSaveError'))
    } else {
      MessagePlugin.success(t('reader.noteSaveSuccess'))
      noteDialog.value.visible = false
    }
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
    await syncAnnotationDelete(bookId, id)
    MessagePlugin.success(t('reader.noteDeleteSuccess'))
    try {
      await loadChapterNotes(currentChapter.value)
      void loadEpubNotedChapters()
    } catch { /* ignore */ }
  } catch (e: any) {
    if (!isTransportError(e)) {
      MessagePlugin.error(t('reader.noteDeleteError'))
    } else {
      MessagePlugin.success(t('reader.noteDeleteSuccess'))
    }
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
function onAiComposerKeydown(e: KeyboardEvent) {
  if (e.key !== 'Enter') return
  // Enter / Ctrl+Enter 发送；Shift+Enter 换行
  if (e.shiftKey) return
  e.preventDefault()
  void sendAiMessage()
}

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
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        ...aiRegionHeaders()
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
    const contextSnapshot = selectedContext.value || ''
    let buffer = ''
    const sseHandlers: SseHandlers = {
      onChunk: (text) => {
        fullAnswer += text
        const msg = aiMessages.value[answerIndex]
        if (!msg) return
        // 替换对象，确保 v-html 随流式内容刷新
        aiMessages.value[answerIndex] = {
          ...msg,
          content: stripCollectMarker(fullAnswer)
        }
        scrollAiBottom()
      },
      onError: (errMsg) => {
        console.error('[AI] SSE error:', errMsg)
        const msg = aiMessages.value[answerIndex]
        if (msg) {
          aiMessages.value[answerIndex] = {
            ...msg,
            content: t('login.error') + ': ' + errMsg
          }
        }
        showReportDialogWithError(errMsg)
        scrollAiBottom()
      },
      onDone: (doneData) => {
        if (doneData.sessionId && isFirstMessage) {
          currentSessionId.value = doneData.sessionId
        }
        attachMessageId(answerIndex, doneData.messageId)
      },
      onCollectEntries: (entries) => attachCollectEntriesToMessage(answerIndex, entries, contextSnapshot)
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        buffer += decoder.decode()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const eventBlock of events) {
        // 仅 Aiko 问答保留 data 前导空格；解析路径不走这里
        processSseEventBlock(eventBlock, sseHandlers, { keepDataLeadingSpace: true })
      }
    }
    if (buffer.trim()) {
      processSseEventBlock(buffer.trim(), sseHandlers, { keepDataLeadingSpace: true })
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
  if (!selectedContext.value || selectedFeatureKeysForQuery.value.length === 0) {
    return
  }
  // 如果 aiLoading 卡住，强制重置
  if (aiLoading.value) {
    console.warn('[Reader] aiLoading 为 true，强制重置为 false')
    aiLoading.value = false
  }

  const selectedFeatures = preferredAiFeatures.value.filter(f => selectedFeatureKeysForQuery.value.includes(f.featureKey))
  const featureNames = selectedFeatures.map(f => featureLabel(f)).join(' + ')
  // 传给后端的顺序 = 界面展示顺序（偏好排序），不是点击先后
  const orderedFeatureKeys = selectedFeatures.map(f => f.featureKey)
  const targets = targetWords.value.length ? [...targetWords.value] : []

  const targetSuffix = targets.length ? ` · ${targets.join(', ')}` : ''
  const contextSnapshot = selectedContext.value || ''
  aiMessages.value.push({
    role: 'user',
    content: `【${featureNames}】${contextSnapshot}${targetSuffix}`,
    featureKeys: [...orderedFeatureKeys]
  })
  aiLoading.value = true
  activeFeatureKey.value = 'multi'

  const isFirstMessage = currentSessionId.value === null

  await nextTick()
  scrollAiBottom()

  try {
    const token = localStorage.getItem('token')
    // 解析：写入同一 session 便于回查；模型侧不带历史（由后端保证）
    const response = await fetch('/api/books/ai-analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        ...aiRegionHeaders()
      },
      body: JSON.stringify({
        bookId,
        featureKeys: orderedFeatureKeys,
        context: contextSnapshot,
        targetWords: targets,
        locale: locale.value,
        sessionId: currentSessionId.value
      })
    })

    if (!response.ok) throw new Error('请求失败')

    const reader = response.body?.getReader()
    if (!reader) throw new Error('不支持流式')

    const decoder = new TextDecoder()
    aiMessages.value.push({ role: 'assistant', content: '', featureKeys: [...orderedFeatureKeys] })
    const answerIndex = aiMessages.value.length - 1
    let buffer = ''
    const sseHandlers: SseHandlers = {
      // 解析不刷 JSON 碎片；等 analyze-result 直接 word/meaning 展示
      onChunk: () => {},
      onError: async (errMsg) => {
        console.error('[AI] SSE error:', errMsg)
        aiMessages.value[answerIndex].content = t('login.error') + ': ' + errMsg
        showReportDialogWithError(errMsg)
        await nextTick()
        scrollAiBottom()
      },
      onDone: (doneData) => {
        if (doneData.sessionId && isFirstMessage) {
          currentSessionId.value = doneData.sessionId
        }
        attachMessageId(answerIndex, doneData.messageId)
      },
      onCollectEntries: (entries) => {
        attachCollectEntriesToMessage(answerIndex, entries, contextSnapshot)
      },
      onAnalyzeResult: (payload) => {
        attachAnalyzeResultToMessage(answerIndex, payload, contextSnapshot, orderedFeatureKeys)
        scrollAiBottom()
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        buffer += decoder.decode()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''
      for (const eventBlock of events) {
        processSseEventBlock(eventBlock, sseHandlers)
      }
    }
    if (buffer.trim()) {
      processSseEventBlock(buffer.trim(), sseHandlers)
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

/** 解析场景消息：有 featureKeys / analyzeResult，或正文是解析 JSON */
function isAnalyzeAssistantMessage(msg: AiMessage): boolean {
  if (msg.role !== 'assistant') return false
  if (msg.analyzeResult && typeof msg.analyzeResult === 'object') return true
  if (msg.featureKeys && msg.featureKeys.length > 0) return true
  return looksLikeAnalyzeJson(msg.content)
}

/** 两套展示：解析走 analyze 渲染，问答走 chat 渲染，禁止混用 */
function renderAiMessageHtml(msg: AiMessage): string {
  if (msg.role !== 'assistant') {
    return renderChatMarkdown(msg.content || '')
  }
  if (isAnalyzeAssistantMessage(msg)) {
    return renderAnalyzeMarkdown(displayAnalyzeMessageBody(msg))
  }
  return renderChatMarkdown(msg.content || '')
}

function looksLikeAnalyzeJson(text?: string): boolean {
  return !!parseAnalyzeResultFromStored(text || '')
}

/**
 * 解析场景正文：优先 analyzeResult；content 若是解析 JSON 也格式化。
 * 绝对禁止把 JSON 原文刷到右侧。仅供解析渲染，勿用于问答。
 */
function displayAnalyzeMessageBody(msg: AiMessage): string {
  const raw = msg.content || ''
  let result: Record<string, unknown> | null =
    msg.analyzeResult && typeof msg.analyzeResult === 'object' && !Array.isArray(msg.analyzeResult)
      ? { ...(msg.analyzeResult as Record<string, unknown>) }
      : null

  if (result) {
    normalizeAnalyzeKeyAliases(result)
    const onlyRaw =
      typeof result.raw === 'string' &&
      Object.keys(result).every((k) => k === 'raw' || result![k] == null || result![k] === '')
    if (onlyRaw) {
      const rawText = String(result.raw)
      result = repairAnalyzeJson(rawText) || parseAnalyzeResultFromStored(rawText)
      if (!result) {
        const t0 = rawText.trim()
        if (t0.startsWith('{') || t0.startsWith('[')) {
          return '解析结果暂时无法展示，请重新点一次「解析」。'
        }
        return rawText
      }
    }
  }
  if (!result) {
    result = repairAnalyzeJson(raw) || parseAnalyzeResultFromStored(raw)
  }
  if (result) {
    normalizeAnalyzeKeyAliases(result)
    delete result.raw
    let md = formatAnalyzeResultMarkdown(result, msg.featureKeys, featureMetaForDisplay())
    if (!md.trim()) {
      md = formatAnalyzeResultMarkdown(result, null, featureMetaForDisplay())
    }
    if (!md.trim()) {
      md = fallbackFormatAnalyzeObject(result)
    }
    if (md.trim()) return md
  }

  const t = raw.trim()
  if (t.startsWith('{') || t.startsWith('[')) {
    return '解析结果暂时无法展示，请重新点一次「解析」。'
  }
  return raw
}

/** 模型中文键/复数键 → 标准 feature_key */
function normalizeAnalyzeKeyAliases(obj: Record<string, unknown>) {
  const pairs: Array<[string, string[]]> = [
    ['collocation', ['collocations', 'phrase', 'phrases', 'fixed_phrase', '固搭', '固定搭配', '搭配']],
    ['word', ['words', 'vocabulary', '单词', '词汇']],
    ['meaning', ['translation', '译文', '翻译', '释义']],
    ['grammar', ['语法']],
    ['sentence_pattern', ['sentencePattern', '句式']],
    ['modifier_attachment', ['modifierAttachment', 'modifiers', 'modification', '修饰挂接', '修饰']],
    ['root_affix', ['rootAffix', '词根词缀']],
    ['phonetic', ['音标', '连读音标']],
  ]
  for (const [canonical, aliases] of pairs) {
    const cur = obj[canonical]
    const curBlank =
      cur == null ||
      cur === '' ||
      (Array.isArray(cur) && cur.length === 0)
    if (!curBlank) continue
    for (const alt of aliases) {
      if (obj[alt] == null) continue
      obj[canonical] = obj[alt]
      delete obj[alt]
      break
    }
  }
}

/** formatAnalyzeResultMarkdown 仍为空时的最后兜底 */
function fallbackFormatAnalyzeObject(result: Record<string, unknown>): string {
  const lines: string[] = []
  for (const [key, value] of Object.entries(result || {})) {
    if (key === 'raw' || value == null || value === '') continue
    if (Array.isArray(value) && value.length === 0) continue
    const title = featureTitleByKey(key)
    lines.push(`${title}：`)
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      lines.push(String(value), '')
      continue
    }
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item == null) continue
        if (typeof item === 'string' || typeof item === 'number') {
          lines.push(`- ${item}`)
          continue
        }
        if (typeof item === 'object') {
          const e = item as Record<string, unknown>
          const word = String(e.word || e.wordProto || '').trim()
          const meaning = String(e.meaning || e.partSpeechMean || '').trim()
          lines.push(`- ${word}${word && meaning ? ': ' : ''}${meaning}`.trim())
        }
      }
      lines.push('')
      continue
    }
    if (typeof value === 'object') {
      lines.push(JSON.stringify(value), '')
    }
  }
  return lines.join('\n').trim()
}

/** 尽力修复模型偶发的非法 JSON（尾逗号、未转义换行等） */
function repairAnalyzeJson(text: string): Record<string, unknown> | null {
  const direct = parseAnalyzeResultFromStored(text)
  if (direct) return direct
  if (!text?.trim()) return null
  let body = text.trim()
  if (body.startsWith('```')) {
    body = body.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim()
  }
  const start = body.indexOf('{')
  const end = body.lastIndexOf('}')
  if (start < 0 || end <= start) return null
  let slice = body.substring(start, end + 1)
  // 去掉对象/数组尾逗号
  slice = slice.replace(/,(\s*[}\]])/g, '$1')
  try {
    const obj = JSON.parse(slice)
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) return obj as Record<string, unknown>
  } catch {
    /* ignore */
  }
  return null
}

</script>

<style scoped>
.reader-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.portrait-hint {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 14px;
  background: color-mix(in srgb, var(--brand-lizard, #a8b59f) 28%, #f6f0e6);
  color: var(--brand-ink, #315c34);
  font-size: 13px;
  line-height: 1.45;
  text-align: center;
  border-bottom: 1px solid color-mix(in srgb, var(--brand-lizard, #a8b59f) 35%, transparent);
}

.reader-layout.is-portrait .reader-main {
  flex: 1;
  min-width: 0;
  width: 100%;
}

.reader-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  justify-self: start;
}

.book-title-header {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  max-width: 100%;
  cursor: default;
}

.header-center {
  justify-self: center;
  text-align: center;
  pointer-events: none;
}

.header-brand {
  font-size: 22px;
  font-weight: 800;
  color: #315C34;
  letter-spacing: 0.02em;
  white-space: nowrap;
  line-height: 1.2;
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  justify-self: end;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
  max-width: 100%;
}

.header-action-btn {
  flex: 0 1 auto;
  max-width: 100%;
}

.header-action-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 12em;
}

.reader-layout.is-portrait .reader-header {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
}

.reader-layout.is-portrait .header-left {
  flex: 0 0 auto;
  max-width: none;
}

.reader-layout.is-portrait .header-left .book-title-header {
  display: none;
}

.reader-layout.is-portrait .header-center {
  position: absolute;
  left: 50%;
  top: 10px;
  transform: translateX(-50%);
  justify-self: unset;
}

.reader-layout.is-portrait .header-brand {
  font-size: 16px;
}

.reader-layout.is-portrait .header-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: flex-start;
  gap: 4px;
  margin-left: auto;
  flex: 0 1 auto;
  max-width: min(62%, 240px);
}

.reader-layout.is-portrait .header-action-btn {
  width: auto;
  max-width: 100%;
}

.reader-layout.is-portrait .header-action-label {
  max-width: 9.5em;
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
  z-index: 30;
  padding: 0;
  transition: all 0.2s ease;
}

.toc-toggle-btn:hover {
  border-color: #b0aae0;
  color: #5e57b5;
  box-shadow: 0 2px 6px rgba(94, 87, 181, 0.15);
}

/* 右侧 AI */
.sidebar-ai-wrapper {
  position: relative;
  flex-shrink: 0;
  transition: width 0.25s ease;
  display: flex;
  /* 高于 PDF 批注工具条（z-index:20），避免折叠按钮被挡住 */
  z-index: 25;
}

.sidebar-ai-wrapper.collapsed {
  width: 32px;
}

.ai-toggle-btn {
  position: absolute;
  left: -12px;
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
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  z-index: 30;
  padding: 0;
  transition: all 0.2s ease;
}

.ai-toggle-btn:hover {
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
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  border-radius: 4px;
}

.toc-item-label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.toc-note-dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #E5484D;
}

.toc-item:hover {
  background: #f3f4f6;
}

.toc-item.active {
  background: #eef2ff;
  color: #6366f1;
  font-weight: 500;
}

.toc-item.has-note:not(.active) .toc-item-label {
  color: #4b5563;
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
  flex: 1;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border-left: 1px solid #eee;
  display: flex;
  flex-direction: column;
  box-shadow: -2px 0 12px rgba(0, 0, 0, 0.04);
}

.sidebar-ai-wrapper.collapsed .sidebar-ai {
  overflow: hidden;
}

.ai-messages {
  flex: 1;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-loading-more {
  display: flex;
  justify-content: center;
  padding: 6px 0 2px;
}

.ai-loading-more--hint {
  font-size: 11px;
  color: #a8a29e;
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
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.ai-msg.user {
  background: #f0eef7;
  color: #4a3f8a;
  margin-left: 12px;
  border-bottom-right-radius: 4px;
}

.ai-msg.assistant {
  background: #f7f8fa;
  color: #2d2d3a;
  margin-right: 0;
  border-bottom-left-radius: 4px;
}

.ai-msg-content {
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.65;
  color: #2d2d3a;
  max-width: 100%;
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

/* —— 解析场景：紫色小节 / 词条 —— */
.ai-msg-content--analyze :deep(strong) {
  color: #2d2d3a;
  font-weight: 600;
}

.ai-msg-content--analyze :deep(span.ai-section) {
  display: block;
  margin-top: 8px;
  margin-bottom: 0;
  color: #5e57b5;
  font-weight: 600;
  line-height: 1.4;
}

.ai-msg-content--analyze :deep(span.ai-section:first-child) {
  margin-top: 0;
}

.ai-msg-content--analyze :deep(span.ai-subsection) {
  display: block;
  margin-top: 6px;
  margin-bottom: 2px;
  color: #5e57b5;
  font-weight: 600;
  font-size: 12px;
  line-height: 1.4;
}

.ai-msg-content--analyze :deep(strong.ai-term) {
  color: #5e57b5;
  font-weight: 600;
}

.ai-msg-content--analyze :deep(.ai-bullet) {
  color: #5e57b5;
  font-weight: 600;
  margin-right: 2px;
}

.ai-msg-content--analyze :deep(.ai-word-line) {
  display: block;
  margin: 2px 0;
  line-height: 1.55;
  overflow-wrap: anywhere;
  word-break: break-word;
}

/* —— Aiko 问答：保留加粗 / 空格 / 列表 —— */
.ai-msg-content--chat :deep(strong) {
  color: #2d2d3a;
  font-weight: 600;
}

.ai-msg-content--chat :deep(strong.ai-chat-heading) {
  display: block;
  margin: 4px 0 2px;
}

.ai-msg-content--chat :deep(.ai-chat-li) {
  display: block;
}

.ai-msg-content--chat :deep(.ai-chat-bullet) {
  font-weight: 600;
}

.ai-msg-content :deep(em) {
  font-style: italic;
  color: #555;
}

.ai-msg-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 6px;
}

.ai-msg-collect {
  color: #5e57b5;
}

.ai-msg-collect-count {
  margin-left: 2px;
  opacity: 0.7;
  font-size: 11px;
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
  max-width: 100%;
  align-self: stretch;
  box-sizing: border-box;
  min-width: 0;
  overflow-x: hidden;
  padding: 4px 8px 4px 6px;
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
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #a16207;
  margin-bottom: 2px;
  min-height: 22px;
  line-height: 1.2;
}

.ai-context-actions {
  display: flex;
  align-items: center;
  margin-left: auto;
  gap: 0;
}

.ai-context-actions :deep(.t-button) {
  height: 22px;
  width: 22px;
  padding: 0;
}

.ai-context-text {
  font-size: 14px;
  font-weight: 500;
  color: #78716c;
  line-height: 1.5;
  max-height: 96px;
  overflow-x: hidden;
  overflow-y: auto;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: 2px 6px 2px 8px;
  margin: 0;
  background: #fffbeb;
  border-left: 3px solid #fcd34d;
  border-radius: 0 6px 6px 0;
}

.ai-context-preview {
  font-style: italic;
  color: #a8a29e;
  border-left-color: #e5e0d0;
}

.ai-context-hint {
  margin-left: 6px;
  font-size: 11px;
  font-weight: 400;
  color: #9ca3af;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-context-word {
  cursor: pointer;
  border-radius: 2px;
  transition: background 0.15s ease, color 0.15s ease;
}

.ai-context-word:hover {
  background: rgba(168, 181, 159, 0.25);
}

.ai-context-word--active {
  background: rgba(49, 92, 52, 0.18);
  color: #315C34;
  font-weight: 600;
}

/* AI 功能多选框区域 */
.ai-feature-panel {
  padding: 6px 10px;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
  box-sizing: border-box;
}

.ai-feature-tags-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
}

.ai-feature-pref-btn {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  padding: 0;
  margin-left: auto;
}

.ai-feature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex: 1;
  min-width: 0;
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

.ai-feature-send-row {
  display: flex;
  gap: 8px;
  margin-top: 2px;
}

.ai-feature-send-row .ai-feature-send {
  flex: 1;
  width: auto;
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

.ai-feature-send-row .ai-feature-send:last-child {
  border-color: #315c34 !important;
  background: #315c34 !important;
}

.ai-feature-send:hover:not(:disabled) {
  background: #4f479e !important;
  border-color: #4f479e !important;
}

.ai-feature-send-row .ai-feature-send:last-child:hover:not(:disabled) {
  background: #274a2a !important;
  border-color: #274a2a !important;
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

.ai-composer {
  margin: 8px 10px 10px;
  padding: 6px 8px 6px 10px;
  display: flex;
  flex-direction: row;
  align-items: flex-end;
  gap: 8px;
  background: #f6f0e6;
  border: 1px solid #e4ddd0;
  border-radius: 12px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
  flex-shrink: 0;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

.ai-composer-input {
  flex: 1;
  min-width: 0;
}

.ai-composer-input :deep(.t-textarea) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
}

.ai-composer-input :deep(.t-textarea__inner),
.ai-composer-input :deep(textarea) {
  font-size: 13px;
  line-height: 1.45;
  color: #315c34;
  padding: 2px 0 !important;
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  resize: none;
  height: 38px !important;
  max-height: 38px !important;
  min-height: 38px !important;
  overflow-y: auto !important;
}

.ai-composer-input :deep(.t-textarea__inner::placeholder),
.ai-composer-input :deep(textarea::placeholder) {
  color: #9ca38f;
}

.ai-composer-input :deep(.t-textarea__inner:focus),
.ai-composer-input :deep(textarea:focus) {
  border: none !important;
  box-shadow: none !important;
  outline: none !important;
}

.ai-composer-send {
  flex-shrink: 0;
  display: inline-flex !important;
  align-items: center;
  gap: 4px;
  height: 32px !important;
  padding: 0 10px !important;
  margin-bottom: 1px;
  background: #315c34 !important;
  border-color: #315c34 !important;
  color: #fff !important;
  font-size: 12px !important;
  font-weight: 600;
  border-radius: 999px !important;
  transition: background 0.15s ease, opacity 0.15s ease;
}

.ai-composer-send :deep(.t-button__text) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ai-composer-send:hover:not(:disabled) {
  background: #274a29 !important;
  border-color: #274a29 !important;
}

.ai-composer-send:disabled {
  background: #d5d8cc !important;
  border-color: #d5d8cc !important;
  color: #8a9080 !important;
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
