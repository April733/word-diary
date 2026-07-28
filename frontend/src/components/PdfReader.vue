<template>
  <div class="pdf-viewer">
    <div
      ref="containerRef"
      class="pdf-scroll"
      :class="{ 'is-annotating': annotateMode !== 'none' }"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerCancel"
      @lostpointercapture="onLostPointerCapture"
    >
      <div class="pdf-page-list" ref="pageListRef">
        <div
          v-for="pageNum in totalPages"
          :key="pageNum"
          :id="'pdf-page-container-' + pageNum"
          class="pdf-page-container"
          :data-page="pageNum"
          :style="{ minHeight: placeholderHeights[pageNum] ? placeholderHeights[pageNum] + 'px' : undefined }"
        >
          <canvas
            :ref="(el) => setCanvasRef(pageNum, el)"
            class="pdf-canvas"
            :class="{
              'pdf-canvas--ready': renderedPages.has(pageNum),
              'pdf-canvas--text': pageTextMode[pageNum] === 'text'
            }"
          />
          <!-- 透明文字层：可用时原生划词；扫描页为空并回退框选 OCR -->
          <div
            :ref="(el) => setTextLayerRef(pageNum, el)"
            class="pdf-text-layer textLayer"
            :class="{
              'pdf-text-layer--ocr': pageTextMode[pageNum] !== 'text',
              'pdf-text-layer--disabled': annotateMode !== 'none'
            }"
          />
          <!-- 批注覆盖层：不修改原始 PDF -->
          <canvas
            :ref="(el) => setMarkupCanvasRef(pageNum, el)"
            class="pdf-markup-layer"
            :class="{ 'pdf-markup-layer--active': annotateMode !== 'none' }"
          />
          <div
            v-if="selectRect && selectPage === pageNum && annotateMode === 'none'"
            class="pdf-select-rect"
            :style="selectRectStyle"
          />
          <div
            v-if="inlineNote && inlineNote.page === pageNum"
            class="pdf-inline-note-wrap"
            :style="inlineNoteStyle"
            @pointerdown.stop="onInlineNoteDragStart"
          >
            <textarea
              ref="inlineNoteRef"
              v-model="inlineNote.text"
              class="pdf-inline-note"
              maxlength="500"
              placeholder="输入笔记…"
              rows="1"
              @pointerdown.stop="onInlineNoteDragStart"
              @mousedown.stop
              @click.stop
              @keydown="onInlineNoteKeydown"
              @blur="onInlineNoteBlur"
              @input="autosizeInlineNote"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 顶部：缩放（透明悬浮，字号偏小） -->
    <div class="pdf-zoom-bar" @pointerdown.stop @mousedown.stop>
      <t-button variant="text" size="small" title="−" @click="zoomOut">
        <t-icon name="remove" size="14px" />
      </t-button>
      <span class="pdf-zoom-value">{{ Math.round(scale * 100) }}%</span>
      <t-button variant="text" size="small" title="+" @click="zoomIn">
        <t-icon name="add" size="14px" />
      </t-button>
    </div>

    <!-- 右侧：批注工具（透明悬浮；默认划词 / 扫描页框选 OCR） -->
    <aside class="pdf-toolbar" data-guide="pdf-tools" aria-label="Tools" @pointerdown.stop @mousedown.stop>
      <span data-guide="pdf-select" class="pdf-tool-guide-anchor">
        <t-button
          variant="text"
          size="small"
          :class="{ 'tool-active': annotateMode === 'none' }"
          :title="$t('reader.pdfToolSelect')"
          @click="setAnnotateMode('none')"
        >
          <svg class="select-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <path
              d="M7 4v16M7 4h3.2c1.9 0 3.3 1.2 3.3 3.1S12.1 10 10.2 10H7"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path
              d="M14.5 14.2 17 20l1.2-3.3L21.5 15.5 14.5 14.2Z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linejoin="round"
            />
          </svg>
        </t-button>
      </span>
      <t-button
        variant="text"
        size="small"
        :class="{ 'tool-active': annotateMode === 'pen' }"
        :title="$t('reader.pdfToolPen')"
        @click="setAnnotateMode('pen')"
      >
        <t-icon name="edit" size="18px" />
      </t-button>
      <t-button
        variant="text"
        size="small"
        :class="{ 'tool-active': annotateMode === 'text' }"
        :title="$t('reader.pdfToolNote')"
        @click="setAnnotateMode('text')"
      >
        <t-icon name="keyboard" size="18px" />
      </t-button>
      <t-button
        variant="text"
        size="small"
        :class="{ 'tool-active': annotateMode === 'eraser' }"
        :title="$t('reader.pdfToolEraser')"
        @click="setAnnotateMode('eraser')"
      >
        <svg class="eraser-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path
            fill="currentColor"
            d="M12.9 4.1 19.9 11a2 2 0 0 1 0 2.8l-5.6 5.6H8.2L3.3 13.7a2 2 0 0 1 0-2.8L11 3.2a2 2 0 0 1 1.9-.1Zm-1.3 2.1L5.4 12.3l3.5 3.5h4.4l5.1-5.1-6.8-6.5ZM4 19.25h16v1.5H4v-1.5Z"
          />
        </svg>
      </t-button>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { getBookFileUrl, getBookPresignedFileUrl, getPdfMarkups } from '../api'
import {
  pdfjsLib,
  TextLayer,
  encodePdfCfi,
  decodePdfCfi,
  estimatePageHeight,
  textContentToPlain,
  isUsablePdfTextLayerForPage,
  bindTextLayerSelection,
  type PdfProgressCfi,
} from '../utils/pdfReader'
import { cropAndSharpenRegion, recognizeRegion, terminateOcrWorker } from '../utils/pdfImageOcr'
import { normalizeSelectedText } from '../utils/selectedText'
import {
  type AnnotateMode,
  type PdfMarkupDto,
  type PenGeometry,
  type TextGeometry,
  DEFAULT_MARKUP_COLOR,
  DEFAULT_STROKE,
  clamp01,
  toNorm,
  parseGeometry,
  drawMarkup,
  hitTestMarkup,
} from '../utils/pdfMarkup'
import {
  getPendingPdfMarkupCreates,
  isTransportError,
  onOfflineSynced,
  syncPdfMarkupCreate,
  syncPdfMarkupDelete,
  syncPdfMarkupUpdate,
  syncProgress,
} from '../utils/offlineSync'

const props = defineProps<{
  bookId: number
  /** 兼容旧字段：页码存在 currentChapter */
  initialPage?: number
  initialCfi?: string
}>()

const emit = defineEmits<{
  (e: 'ready', totalPages: number): void
  (e: 'toc', items: Array<{ label: string; href: string; level: number }>): void
  (e: 'chapter-highlight', tocIndex: number): void
  (e: 'noted-pages', pages: number[]): void
  (e: 'select-text', text: string): void
  (e: 'loading', value: boolean): void
  (e: 'error', message: string): void
}>()

const SCALE_STEP = 0.2
const MAX_SELECTION_LENGTH = 800
const RENDER_MARGIN = '1200px'
/** 视口外超过此缓冲的已渲染页才考虑释放 */
const KEEP_BUFFER = 6
/** 离开视口后延迟释放，避免来回滚动闪白 */
const RELEASE_DELAY_MS = 3000
const MIN_SELECT_SIZE = 8

const containerRef = ref<HTMLElement | null>(null)
const pageListRef = ref<HTMLElement | null>(null)
const totalPages = ref(0)
const scale = ref(
  typeof window !== 'undefined' && window.matchMedia('(max-width: 560px), (orientation: portrait)').matches
    ? 1
    : 1.2
)
const ocrLoading = ref(false)

let pdfDoc: any = null
const canvasMap = new Map<number, HTMLCanvasElement>()
const markupCanvasMap = new Map<number, HTMLCanvasElement>()
const textLayerMap = new Map<number, HTMLElement>()
const textLayerTasks = new Map<number, { cancel: () => void }>()
const textLayerUnbind = new Map<number, () => void>()
/** 每页取词模式：text=原生划词，ocr=框选 OCR */
const pageTextMode = reactive<Record<number, 'text' | 'ocr'>>({})
const renderedPages = reactive(new Set<number>())
const renderingPages = new Set<number>()
const placeholderHeights = reactive<Record<number, number>>({})
const pageAspect = reactive<Record<number, number>>({})
const releaseTimers = new Map<number, ReturnType<typeof setTimeout>>()
let selectionEmitTimer: ReturnType<typeof setTimeout> | null = null
let lastEmittedSelection = ''

let observer: IntersectionObserver | null = null
let resizeObserver: ResizeObserver | null = null
let resizeTimer: ReturnType<typeof setTimeout> | null = null
let lastContainerWidth = 0
let saveTimer: ReturnType<typeof setTimeout> | null = null
let currentPage = 1
let destroyed = false
let lastHighlightEmit = 0
let lastHighlightPage = -1

// 框选 OCR 状态
const selectPage = ref<number | null>(null)
const selectRect = ref<{ x: number; y: number; w: number; h: number } | null>(null)
let selectStart: { page: number; x: number; y: number } | null = null
let selecting = false
let lastSelectedText = ''

// 批注
const annotateMode = ref<AnnotateMode>('none')
const markups = ref<PdfMarkupDto[]>([])
const draftPage = ref<number | null>(null)
let penPoints: Array<[number, number]> = []
let annotDrawing = false
let annotStart: { page: number; x: number; y: number; w: number; h: number } | null = null
/** 当前捕获的指针（兼容鼠标 / 触控 / Apple Pencil） */
let activePointerId: number | null = null
let scribbleGuardBound = false

/** 页内透明笔记输入框（点击落点即现，无弹窗） */
const inlineNote = ref<{
  page: number
  nx: number
  ny: number
  x: number
  y: number
  text: string
  /** 编辑已有批注时带上 id；新建为 undefined */
  editId?: number
} | null>(null)
const inlineNoteRef = ref<HTMLTextAreaElement | null>(null)
let inlineNoteCommitting = false
let suppressInlineNoteBlur = false

/** 拖动已确认的文本批注（单击拖拽；双击才进入编辑） */
let draggingText: {
  id: number
  page: number
  origX: number
  origY: number
  startNx: number
  startNy: number
  w: number
  h: number
  moved: boolean
} | null = null

const TEXT_DRAG_MOVE_THRESHOLD = 0.008
const TEXT_DBLCLICK_MS = 380
const INLINE_NOTE_DRAG_THRESHOLD_PX = 4

/** 用于识别「双击编辑」：两次短点同一笔记 */
let lastTextTap: { id: number; at: number } | null = null

/** 拖动输入中的笔记 */
let draggingInlineNote: {
  page: number
  startClientX: number
  startClientY: number
  origX: number
  origY: number
  w: number
  h: number
  pointerId: number
  active: boolean
} | null = null

const selectRectStyle = computed(() => {
  const r = selectRect.value
  if (!r) return {}
  return {
    left: r.x + 'px',
    top: r.y + 'px',
    width: r.w + 'px',
    height: r.h + 'px',
  }
})

const inlineNoteStyle = computed(() => {
  const n = inlineNote.value
  if (!n) return {}
  return {
    left: n.x + 'px',
    top: n.y + 'px',
  }
})

function setAnnotateMode(mode: AnnotateMode) {
  // 再点一次已激活工具 → 回到取词；笔/笔记/擦除均未选中时即为划词/OCR 取词
  const next = mode === 'none'
    ? 'none'
    : annotateMode.value === mode
      ? 'none'
      : mode
  if (next !== 'text') {
    void commitInlineNote()
  }
  annotateMode.value = next
  onSelectCancel()
  clearDraft()
}

function clearDraft() {
  annotDrawing = false
  annotStart = null
  penPoints = []
  draftPage.value = null
  draggingText = null
}

function setCanvasRef(pageNum: number, el: unknown) {
  if (el instanceof HTMLCanvasElement) {
    canvasMap.set(pageNum, el)
  } else {
    canvasMap.delete(pageNum)
  }
}

function setTextLayerRef(pageNum: number, el: unknown) {
  if (el instanceof HTMLElement) {
    textLayerMap.set(pageNum, el)
  } else {
    textLayerMap.delete(pageNum)
  }
}

function setMarkupCanvasRef(pageNum: number, el: unknown) {
  if (el instanceof HTMLCanvasElement) {
    markupCanvasMap.set(pageNum, el)
  } else {
    markupCanvasMap.delete(pageNum)
  }
}

function isTextLayerPage(pageNum: number) {
  return pageTextMode[pageNum] === 'text'
}

function clearTextLayer(pageNum: number) {
  const unbind = textLayerUnbind.get(pageNum)
  if (unbind) {
    try {
      unbind()
    } catch { /* ignore */ }
    textLayerUnbind.delete(pageNum)
  }
  const task = textLayerTasks.get(pageNum)
  if (task) {
    try {
      task.cancel()
    } catch { /* ignore */ }
    textLayerTasks.delete(pageNum)
  }
  const el = textLayerMap.get(pageNum)
  if (el) {
    el.replaceChildren()
    el.classList.remove('selecting')
    el.style.removeProperty('--scale-factor')
  }
  delete pageTextMode[pageNum]
}

function getContainerWidth(): number {
  const w = containerRef.value?.clientWidth || 0
  if (w > 0) return w
  // 布局未就绪时勿用过大兜底，避免窄屏/竖屏渲染成超宽页
  const vw = typeof window !== 'undefined' ? window.innerWidth : 700
  return Math.max(280, Math.min(vw, 900))
}

function isNarrowContainer(width = getContainerWidth()): boolean {
  return width > 0 && width < 560
}

/** 首次进入窄屏时铺满宽度；用户主动放大后不在同一次窄屏会话里强行改回 */
function preferFitWidthIfNarrow() {
  if (isNarrowContainer() && scale.value > 1) {
    scale.value = 1
  }
}

function markupsForPage(pageNum: number): PdfMarkupDto[] {
  return markups.value.filter((m) => m.pageNum === pageNum)
}

async function loadMarkups() {
  try {
    const res = await getPdfMarkups(props.bookId)
    const server = (res.data?.data || []) as PdfMarkupDto[]
    const pending = getPendingPdfMarkupCreates(props.bookId)
    // 服务端结果 + 尚未上传的本地批注
    markups.value = [...server, ...pending]
    for (const page of renderedPages) {
      redrawMarkupLayer(page)
    }
  } catch (e) {
    // 弱网：至少展示本地待同步批注
    const pending = getPendingPdfMarkupCreates(props.bookId)
    if (pending.length) {
      markups.value = pending
      for (const page of renderedPages) redrawMarkupLayer(page)
    }
    console.warn('[PdfReader] 加载批注失败:', e)
  }
}

watch(
  markups,
  (list) => {
    const pages = [...new Set(list.map((m) => m.pageNum).filter((p): p is number => p != null && p > 0))]
    pages.sort((a, b) => a - b)
    emit('noted-pages', pages)
  },
  { deep: true }
)

function syncMarkupCanvasSize(pageNum: number) {
  const pdfCanvas = canvasMap.get(pageNum)
  const layer = markupCanvasMap.get(pageNum)
  if (!pdfCanvas || !layer) return null
  const w = pdfCanvas.clientWidth
  const h = pdfCanvas.clientHeight
  if (w <= 0 || h <= 0) return null
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  layer.width = Math.floor(w * dpr)
  layer.height = Math.floor(h * dpr)
  layer.style.width = w + 'px'
  layer.style.height = h + 'px'
  const ctx = layer.getContext('2d')
  if (!ctx) return null
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  return { ctx, w, h }
}

function redrawMarkupLayer(pageNum: number) {
  const sized = syncMarkupCanvasSize(pageNum)
  if (!sized) return
  const { ctx, w, h } = sized
  ctx.clearRect(0, 0, w, h)
  const editingId = inlineNote.value?.editId
  for (const m of markupsForPage(pageNum)) {
    // 编辑中的文本批注改由 textarea 展示，避免叠字
    if (editingId != null && m.id === editingId) continue
    drawMarkup(ctx, m, w, h)
  }
  // 绘制中的笔迹预览
  if (annotDrawing && annotateMode.value === 'pen' && draftPage.value === pageNum && penPoints.length > 1) {
    ctx.save()
    ctx.strokeStyle = DEFAULT_MARKUP_COLOR
    ctx.lineWidth = Math.max(1, DEFAULT_STROKE * w)
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.beginPath()
    penPoints.forEach(([nx, ny], i) => {
      const x = nx * w
      const y = ny * h
      if (i === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    })
    ctx.stroke()
    ctx.restore()
  }
}

function buildProxyDocParams(token: string | null) {
  return {
    url: getBookFileUrl(props.bookId),
    httpHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  }
}

function isLocalDevHost() {
  const h = window.location.hostname
  return h === 'localhost' || h === '127.0.0.1' || h === '[::1]'
}

async function openPdfDocument(docParams: Record<string, unknown>) {
  const loadingTask = pdfjsLib.getDocument({
    ...docParams,
    disableAutoFetch: false,
    disableStream: false,
    rangeChunkSize: 1024 * 1024,
  })
  return loadingTask.promise
}

/** 经后端 /file 整包拉取再交给 PDF.js（避开 COS CORS，也避开代理 Range 半截流） */
async function openPdfViaBackendProxy(token: string | null) {
  const url = getBookFileUrl(props.bookId)
  const res = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) {
    throw new Error(`后端文件接口失败 HTTP ${res.status}`)
  }
  const data = await res.arrayBuffer()
  if (!data.byteLength) {
    throw new Error('后端返回空文件')
  }
  console.log('[PdfReader] 后端 /file 已拉取', data.byteLength, 'bytes，交 PDF.js 解析')
  return openPdfDocument({ data })
}

async function loadDocument() {
  emit('loading', true)
  try {
    const token = localStorage.getItem('token')
    pdfDoc = null

    // 本地开发：COS 桶通常未放行 localhost CORS，直连必挂；直接走后端代理
    const skipCos = isLocalDevHost()
    if (!skipCos) {
      try {
        const urlRes = await getBookPresignedFileUrl(props.bookId)
        const signedUrl = urlRes.data?.data?.url
        if (signedUrl) {
          console.log('[PdfReader] 使用 COS 预签名直链加载')
          pdfDoc = await openPdfDocument({ url: signedUrl })
        }
      } catch (e) {
        console.warn('[PdfReader] COS 直链加载失败（多为 CORS），回退后端 /file', e)
        pdfDoc = null
      }
    } else {
      console.log('[PdfReader] 本地开发跳过 COS 直链，走后端 /file')
    }

    if (!pdfDoc) {
      try {
        pdfDoc = await openPdfViaBackendProxy(token)
      } catch (proxyErr) {
        console.warn('[PdfReader] 整包拉取失败，再试 PDF.js 流式 /file', proxyErr)
        pdfDoc = await openPdfDocument(buildProxyDocParams(token))
      }
    }

    if (destroyed) {
      pdfDoc.destroy()
      pdfDoc = null
      return
    }

    totalPages.value = pdfDoc.numPages
    await nextTick()
    // 等侧栏折叠/竖屏布局完成后再量宽，否则会按错误宽度出图
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
    })
    preferFitWidthIfNarrow()
    lastContainerWidth = getContainerWidth()
    initPlaceholders()
    await loadOutline()
    emit('ready', totalPages.value)

    await nextTick()
    setupObserver()
    setupScrollListener()
    setupResizeObserver()
    await restorePosition()
    await loadMarkups()
    emit('loading', false)
  } catch (err: any) {
    console.error('[PdfReader] 加载失败:', err)
    emit('error', err?.message || 'Unknown error')
    emit('loading', false)
  }
}

function initPlaceholders() {
  const w = getContainerWidth()
  for (let i = 1; i <= totalPages.value; i++) {
    placeholderHeights[i] = estimatePageHeight(w, scale.value, pageAspect[i] || 1.414)
  }
}

async function loadOutline() {
  if (!pdfDoc) return
  try {
    const outline = await pdfDoc.getOutline()
    if (!outline || outline.length === 0) {
      emit('toc', Array.from({ length: totalPages.value }, (_, i) => ({
        label: `第 ${i + 1} 页`,
        href: String(i + 1),
        level: 0,
      })))
      return
    }

    const flat: Array<{ label: string; dest: any; level: number }> = []
    const walk = (items: any[], level: number) => {
      for (const item of items) {
        flat.push({ label: item.title, dest: item.dest, level })
        if (item.items?.length) walk(item.items, level + 1)
      }
    }
    walk(outline, 0)

    const toc = await Promise.all(flat.map(async (item) => {
      let pageNumber = 0
      try {
        let dest = item.dest
        if (typeof dest === 'string') dest = await pdfDoc.getDestination(dest)
        if (dest && Array.isArray(dest) && dest[0]) {
          const pageIndex = await pdfDoc.getPageIndex(dest[0])
          pageNumber = pageIndex + 1
        }
      } catch { /* ignore */ }
      return { label: item.label, href: String(pageNumber), level: item.level }
    }))

    const filtered = toc.filter(item => parseInt(item.href, 10) > 0)
    emit('toc', filtered.length ? filtered : Array.from({ length: totalPages.value }, (_, i) => ({
      label: `第 ${i + 1} 页`,
      href: String(i + 1),
      level: 0,
    })))
  } catch (e) {
    console.warn('[PdfReader] 目录加载失败:', e)
    emit('toc', Array.from({ length: totalPages.value }, (_, i) => ({
      label: `第 ${i + 1} 页`,
      href: String(i + 1),
      level: 0,
    })))
  }
}

function setupObserver() {
  if (!containerRef.value) return
  observer?.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const pageNum = Number((entry.target as HTMLElement).dataset.page)
        if (!pageNum) continue
        if (entry.isIntersecting) {
          scheduleRender(pageNum)
        } else {
          maybeReleasePage(pageNum)
        }
      }
    },
    { root: containerRef.value, rootMargin: RENDER_MARGIN, threshold: 0 }
  )

  const pages = containerRef.value.querySelectorAll('.pdf-page-container')
  pages.forEach((el) => observer!.observe(el))
}

function setupResizeObserver() {
  if (!containerRef.value) return
  resizeObserver?.disconnect()
  lastContainerWidth = getContainerWidth()
  resizeObserver = new ResizeObserver(() => {
    if (destroyed) return
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      if (destroyed || !pdfDoc || totalPages.value <= 0) return
      const w = getContainerWidth()
      if (!w || Math.abs(w - lastContainerWidth) < 12) return
      const prev = lastContainerWidth
      lastContainerWidth = w
      // 从宽屏切到窄屏/竖屏：自动 fit 宽度
      if (prev >= 560 && w < 560) {
        scale.value = 1
      }
      void rerenderVisible()
    }, 140)
  })
  resizeObserver.observe(containerRef.value)
}

function cancelPendingRelease(pageNum: number) {
  const timer = releaseTimers.get(pageNum)
  if (timer) {
    clearTimeout(timer)
    releaseTimers.delete(pageNum)
  }
}

function scheduleRender(pageNum: number) {
  cancelPendingRelease(pageNum)
  if (renderedPages.has(pageNum) || renderingPages.has(pageNum) || !pdfDoc) return
  renderingPages.add(pageNum)
  renderPage(pageNum).finally(() => renderingPages.delete(pageNum))
}

async function renderPage(pageNum: number) {
  if (!pdfDoc || destroyed) return
  try {
    const page = await pdfDoc.getPage(pageNum)
    const baseViewport = page.getViewport({ scale: 1 })
    pageAspect[pageNum] = baseViewport.height / baseViewport.width

    const maxWidth = Math.max(120, getContainerWidth() - (isNarrowContainer() ? 16 : 32))
    const renderScale = (maxWidth / baseViewport.width) * scale.value
    const viewport = page.getViewport({ scale: renderScale })

    const canvas = canvasMap.get(pageNum)
    if (!canvas) return

    // 离屏渲染完成后再贴到可见 canvas，避免清空瞬间闪白
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const cssW = Math.floor(viewport.width)
    const cssH = Math.floor(viewport.height)
    const offscreen = document.createElement('canvas')
    offscreen.width = Math.floor(cssW * dpr)
    offscreen.height = Math.floor(cssH * dpr)
    const offCtx = offscreen.getContext('2d')
    if (!offCtx) return
    offCtx.setTransform(dpr, 0, 0, dpr, 0, 0)

    const task = page.render({ canvasContext: offCtx, viewport })
    await task.promise
    if (destroyed) return

    const prevH = placeholderHeights[pageNum]
    if (!prevH || Math.abs(prevH - cssH) > 2) {
      placeholderHeights[pageNum] = cssH
    }

    canvas.width = offscreen.width
    canvas.height = offscreen.height
    canvas.style.width = cssW + 'px'
    canvas.style.height = cssH + 'px'
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(offscreen, 0, 0)

    renderedPages.add(pageNum)
    redrawMarkupLayer(pageNum)
    await mountTextLayer(page, pageNum, viewport)
  } catch (e) {
    console.warn('[PdfReader] 渲染第', pageNum, '页失败:', e)
  }
}

/** 挂载/重建 pdf.js 透明文字层；不可用则标记为 OCR 回退 */
async function mountTextLayer(page: any, pageNum: number, viewport: any) {
  const container = textLayerMap.get(pageNum)
  if (!container || destroyed) return
  clearTextLayer(pageNum)
  // clearTextLayer 清掉了 mode，容器还需在
  if (!textLayerMap.has(pageNum)) textLayerMap.set(pageNum, container)

  try {
    const textContent = await page.getTextContent()
    if (destroyed) return
    const plain = textContentToPlain(textContent)
    if (!isUsablePdfTextLayerForPage(plain) || !TextLayer) {
      pageTextMode[pageNum] = 'ocr'
      return
    }
    // 必须设置，否则 span 的 calc(var(--scale-factor)*…) 失效，文字叠成一团，划词会串选整页
    container.style.setProperty('--scale-factor', String(viewport.scale))
    const layer = new TextLayer({
      textContentSource: textContent,
      container,
      viewport,
    })
    textLayerTasks.set(pageNum, layer)
    await layer.render()
    if (destroyed) {
      try {
        layer.cancel()
      } catch { /* ignore */ }
      return
    }
    // pdf.js TextLayerBuilder 同款：endOfContent + selecting，避免绝对定位 span 之间选区穿透
    textLayerUnbind.set(pageNum, bindTextLayerSelection(container))
    pageTextMode[pageNum] = 'text'
  } catch (e) {
    console.warn('[PdfReader] 文字层第', pageNum, '页失败，回退 OCR:', e)
    pageTextMode[pageNum] = 'ocr'
    container.replaceChildren()
  }
}

function maybeReleasePage(pageNum: number) {
  if (!renderedPages.has(pageNum)) return
  if (Math.abs(pageNum - currentPage) <= KEEP_BUFFER) return
  if (releaseTimers.has(pageNum)) return
  releaseTimers.set(
    pageNum,
    setTimeout(() => {
      releaseTimers.delete(pageNum)
      doReleasePage(pageNum)
    }, RELEASE_DELAY_MS)
  )
}

function doReleasePage(pageNum: number) {
  if (!renderedPages.has(pageNum)) return
  if (Math.abs(pageNum - currentPage) <= KEEP_BUFFER) return
  const canvas = canvasMap.get(pageNum)
  if (canvas) {
    const ctx = canvas.getContext('2d')
    if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height)
    canvas.width = 1
    canvas.height = 1
  }
  const layer = markupCanvasMap.get(pageNum)
  if (layer) {
    layer.width = 1
    layer.height = 1
  }
  clearTextLayer(pageNum)
  renderedPages.delete(pageNum)
}

function setupScrollListener() {
  const container = containerRef.value
  if (!container) return
  const onScroll = () => {
    updateCurrentPageFromScroll()
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => saveProgress(), 2000)
  }
  container.addEventListener('scroll', onScroll, { passive: true })
  ;(container as any).__scrollHandler = onScroll
}

function updateCurrentPageFromScroll() {
  const container = containerRef.value
  if (!container) return
  const pages = container.querySelectorAll('.pdf-page-container')
  let page = 1
  const top = container.getBoundingClientRect().top
  for (const el of pages) {
    const rect = el.getBoundingClientRect()
    if (rect.top < top + 80) {
      page = Number((el as HTMLElement).dataset.page) || page
    }
  }
  currentPage = page
  // 节流目录高亮，避免滚动时频繁触发父组件重绘
  const now = Date.now()
  if (page !== lastHighlightPage && now - lastHighlightEmit > 120) {
    lastHighlightPage = page
    lastHighlightEmit = now
    emit('chapter-highlight', page)
  }
}

function getPageOffsetRatio(pageNum: number): number {
  const container = containerRef.value
  const el = container?.querySelector(`#pdf-page-container-${pageNum}`) as HTMLElement | null
  if (!container || !el) return 0
  const cRect = container.getBoundingClientRect()
  const pRect = el.getBoundingClientRect()
  const visibleTop = cRect.top - pRect.top
  const h = pRect.height || 1
  return Math.min(1, Math.max(0, visibleTop / h))
}

function saveProgress() {
  const offset = getPageOffsetRatio(currentPage)
  const percent = totalPages.value > 0
    ? Math.round(((currentPage - 1 + offset) / totalPages.value) * 10000) / 100
    : 0
  const cfi = encodePdfCfi({
    page: currentPage,
    offset,
    text: lastSelectedText || undefined,
  })
  void syncProgress(props.bookId, {
    chapterIndex: currentPage,
    cfi,
    percent,
  }).catch(() => {})
  // 同步本地记忆选中文本
  try {
    if (lastSelectedText) {
      sessionStorage.setItem(`pdf-sel-${props.bookId}`, lastSelectedText)
    }
  } catch { /* ignore */ }
}

async function restorePosition() {
  await nextTick()
  await new Promise(r => requestAnimationFrame(r))

  let pos: PdfProgressCfi | null = decodePdfCfi(props.initialCfi)
  if (!pos && props.initialPage && props.initialPage > 0) {
    pos = { page: props.initialPage, offset: 0 }
  }
  if (!pos) return

  const page = Math.min(Math.max(1, pos.page), totalPages.value)
  currentPage = page
  // 先渲染目标页附近
  for (let i = Math.max(1, page - 1); i <= Math.min(totalPages.value, page + 1); i++) {
    await renderPage(i)
  }

  const el = containerRef.value?.querySelector(`#pdf-page-container-${page}`) as HTMLElement | null
  if (el && containerRef.value) {
    const offsetPx = (el.offsetHeight || 0) * (pos.offset || 0)
    containerRef.value.scrollTop = el.offsetTop + offsetPx
  }

  if (pos.text) {
    lastSelectedText = pos.text
    emit('select-text', pos.text)
  } else {
    try {
      const cached = sessionStorage.getItem(`pdf-sel-${props.bookId}`)
      if (cached) {
        lastSelectedText = cached
        emit('select-text', cached)
      }
    } catch { /* ignore */ }
  }
}

function zoomIn() {
  scale.value = Math.min(3, scale.value + SCALE_STEP)
  rerenderVisible()
}

function zoomOut() {
  scale.value = Math.max(0.5, scale.value - SCALE_STEP)
  rerenderVisible()
}

async function rerenderVisible() {
  // 释放全部 canvas，按新缩放懒加载
  for (const pageNum of [...releaseTimers.keys()]) {
    cancelPendingRelease(pageNum)
  }
  renderedPages.clear()
  initPlaceholders()
  await nextTick()
  setupObserver()
  for (let i = Math.max(1, currentPage - 1); i <= Math.min(totalPages.value, currentPage + 1); i++) {
    scheduleRender(i)
  }
}

// -------- 指针：OCR 框选 / 批注（Pointer Events：鼠标 / 触控 / Apple Pencil） --------

function pageFromPoint(
  clientX: number,
  clientY: number
): { page: number; canvas: HTMLCanvasElement; localX: number; localY: number } | null {
  // 用坐标命中：setPointerCapture 后 e.target 会变成捕获节点，不能依赖 closest
  const hit = document.elementFromPoint(clientX, clientY) as HTMLElement | null
  const target = hit?.closest?.('.pdf-page-container') as HTMLElement | null
  if (!target || !containerRef.value?.contains(target)) return null
  const page = Number(target.dataset.page)
  const canvas = canvasMap.get(page)
  if (!canvas || !renderedPages.has(page)) return null
  const rect = canvas.getBoundingClientRect()
  return {
    page,
    canvas,
    localX: clientX - rect.left,
    localY: clientY - rect.top,
  }
}

function pageFromEvent(e: PointerEvent) {
  return pageFromPoint(e.clientX, e.clientY)
}

function isPrimaryDrawPointer(e: PointerEvent) {
  if (!e.isPrimary) return false
  // 框选模式：手指留给系统滚动，只响应鼠标与 Apple Pencil
  if (annotateMode.value === 'none' && e.pointerType === 'touch') return false
  return e.button === 0 || e.pointerType === 'pen' || e.pointerType === 'touch'
}

function capturePointer(e: PointerEvent) {
  activePointerId = e.pointerId
  const el = containerRef.value
  if (el && el.setPointerCapture) {
    try {
      el.setPointerCapture(e.pointerId)
    } catch { /* ignore */ }
  }
  // iPadOS Scribble 会吞掉快速 Pencil 点击；touchmove + preventDefault 可规避
  if (e.pointerType === 'pen' && el && !scribbleGuardBound) {
    el.addEventListener('touchmove', preventTouchDefault, { passive: false })
    scribbleGuardBound = true
  }
}

function releasePointer(e?: PointerEvent) {
  const el = containerRef.value
  const id = e?.pointerId ?? activePointerId
  if (el && id != null && el.hasPointerCapture?.(id)) {
    try {
      el.releasePointerCapture(id)
    } catch { /* ignore */ }
  }
  activePointerId = null
  if (el && scribbleGuardBound) {
    el.removeEventListener('touchmove', preventTouchDefault)
    scribbleGuardBound = false
  }
}

function preventTouchDefault(ev: TouchEvent) {
  if (annotDrawing || selecting || draggingText || annotateMode.value !== 'none') {
    ev.preventDefault()
  }
}

function onPointerDown(e: PointerEvent) {
  if (ocrLoading.value || !isPrimaryDrawPointer(e)) return
  if (annotateMode.value !== 'none') {
    onAnnotStart(e)
  } else {
    // 取词模式：优先拖动已有文本批注；文字层页交给原生划词，扫描页框选 OCR
    if (tryStartTextDrag(e)) return
    const info = pageFromEvent(e)
    if (info && isTextLayerPage(info.page)) {
      return
    }
    // 点在文字层内也不启动框选（避免误拖）
    const t = e.target as Element | null
    if (t?.closest?.('.pdf-text-layer') && !t.closest('.pdf-text-layer--ocr')) {
      return
    }
    onSelectStart(e)
  }
}

function onPointerMove(e: PointerEvent) {
  if (activePointerId != null && e.pointerId !== activePointerId) return
  if (draggingText) {
    onTextDragMove(e)
    return
  }
  if (annotateMode.value !== 'none') {
    onAnnotMove(e)
  } else {
    onSelectMove(e)
  }
}

async function onPointerUp(e: PointerEvent) {
  if (activePointerId != null && e.pointerId !== activePointerId) return
  try {
    if (draggingText) {
      await onTextDragEnd()
      return
    }
    if (annotateMode.value !== 'none') {
      await onAnnotEnd(e)
    } else {
      await onSelectEnd()
    }
  } finally {
    releasePointer(e)
  }
}

function onPointerCancel(e?: PointerEvent) {
  if (e && activePointerId != null && e.pointerId !== activePointerId) return
  if (draggingText) {
    const page = draggingText.page
    draggingText = null
    redrawMarkupLayer(page)
  } else if (annotateMode.value !== 'none') {
    clearDraft()
    if (draftPage.value) redrawMarkupLayer(draftPage.value)
  } else {
    onSelectCancel()
  }
  releasePointer(e)
}

/** 捕获丢失且仍在绘制时视为中断（正常 pointerup 会先清 activePointerId） */
function onLostPointerCapture(e: PointerEvent) {
  if (activePointerId == null || e.pointerId !== activePointerId) return
  onPointerCancel(e)
}

function onAnnotStart(e: PointerEvent) {
  if (ocrLoading.value) return
  e.preventDefault()
  const info = pageFromEvent(e)
  if (!info) return
  const { page, canvas, localX, localY } = info
  const [nx, ny] = toNorm(localX, localY, canvas.clientWidth, canvas.clientHeight)

  if (annotateMode.value === 'eraser') {
    capturePointer(e)
    const hit = hitTestMarkup(
      markupsForPage(page),
      nx,
      ny,
      canvas.clientWidth,
      canvas.clientHeight
    )
    if (hit != null) {
      eraseMarkup(hit, page)
    }
    return
  }

  if (annotateMode.value === 'text') {
    // 点中已有文本批注 → 拖动；空白处 → 新建输入框（勿 capture，以免抢走 textarea 焦点）
    if (tryStartTextDrag(e, info)) return
    void openInlineNote(page, nx, ny, localX, localY)
    return
  }

  capturePointer(e)
  annotDrawing = true
  annotStart = { page, x: localX, y: localY, w: canvas.clientWidth, h: canvas.clientHeight }
  draftPage.value = page
  if (annotateMode.value === 'pen') {
    penPoints = [[nx, ny]]
  }
}

/** 尝试开始拖动文本批注；成功则返回 true */
function tryStartTextDrag(
  e: PointerEvent,
  info?: { page: number; canvas: HTMLCanvasElement; localX: number; localY: number }
): boolean {
  const hitInfo = info || pageFromEvent(e)
  if (!hitInfo) return false
  const { page, canvas, localX, localY } = hitInfo
  const [nx, ny] = toNorm(localX, localY, canvas.clientWidth, canvas.clientHeight)
  const hitId = hitTestMarkup(
    markupsForPage(page),
    nx,
    ny,
    canvas.clientWidth,
    canvas.clientHeight
  )
  if (hitId == null) return false
  const m = markups.value.find((x) => x.id === hitId)
  if (!m || m.markupType !== 'text') return false
  const g = parseGeometry<TextGeometry>(m.geometryJson)
  if (!g) return false

  e.preventDefault()
  capturePointer(e)
  draggingText = {
    id: m.id,
    page,
    origX: g.x,
    origY: g.y,
    startNx: nx,
    startNy: ny,
    w: canvas.clientWidth,
    h: canvas.clientHeight,
    moved: false,
  }
  return true
}

function onTextDragMove(e: PointerEvent) {
  if (!draggingText) return
  e.preventDefault()
  const info = pageFromEvent(e)
  if (!info || info.page !== draggingText.page) return
  const [nx, ny] = toNorm(info.localX, info.localY, info.canvas.clientWidth, info.canvas.clientHeight)
  const dx = nx - draggingText.startNx
  const dy = ny - draggingText.startNy
  if (!draggingText.moved && Math.abs(dx) + Math.abs(dy) >= TEXT_DRAG_MOVE_THRESHOLD) {
    draggingText.moved = true
  }
  if (!draggingText.moved) return
  const nextX = clamp01(draggingText.origX + dx)
  const nextY = clamp01(draggingText.origY + dy)
  const geometryJson = JSON.stringify({ x: nextX, y: nextY })
  markups.value = markups.value.map((m) =>
    m.id === draggingText!.id ? { ...m, geometryJson } : m
  )
  redrawMarkupLayer(draggingText.page)
}

async function onTextDragEnd() {
  const drag = draggingText
  draggingText = null
  if (!drag) return
  const m = markups.value.find((x) => x.id === drag.id)
  if (!m) return

  // 几乎没动：单击忽略；短时间内再点同一笔记 → 双击编辑
  if (!drag.moved) {
    const now = Date.now()
    if (lastTextTap && lastTextTap.id === drag.id && now - lastTextTap.at <= TEXT_DBLCLICK_MS) {
      lastTextTap = null
      const canvas = canvasMap.get(drag.page)
      const [x, y] = canvas
        ? [drag.origX * canvas.clientWidth, drag.origY * canvas.clientHeight]
        : [0, 0]
      await openInlineNote(drag.page, drag.origX, drag.origY, x, y, {
        editId: m.id,
        text: m.noteText || '',
      })
      return
    }
    lastTextTap = { id: drag.id, at: now }
    return
  }

  lastTextTap = null
  try {
    await syncPdfMarkupUpdate(props.bookId, m.id, { geometryJson: m.geometryJson })
  } catch (err: any) {
    // 失败回滚到拖动起点
    const rollback = JSON.stringify({ x: drag.origX, y: drag.origY })
    markups.value = markups.value.map((x) =>
      x.id === drag.id ? { ...x, geometryJson: rollback } : x
    )
    redrawMarkupLayer(drag.page)
    if (!isTransportError(err)) {
      MessagePlugin.error(err?.response?.data?.message || '移动笔记失败')
    }
  }
}

function onAnnotMove(e: PointerEvent) {
  if (!annotDrawing || !annotStart) return
  e.preventDefault()
  const info = pageFromEvent(e)
  if (!info || info.page !== annotStart.page) return
  const x2 = Math.max(0, Math.min(info.canvas.clientWidth, info.localX))
  const y2 = Math.max(0, Math.min(info.canvas.clientHeight, info.localY))

  if (annotateMode.value === 'pen') {
    const [nx, ny] = toNorm(x2, y2, info.canvas.clientWidth, info.canvas.clientHeight)
    const last = penPoints[penPoints.length - 1]
    if (!last || Math.abs(last[0] - nx) + Math.abs(last[1] - ny) > 0.002) {
      penPoints.push([nx, ny])
      redrawMarkupLayer(info.page)
    }
  }
}

async function onAnnotEnd(_e: PointerEvent) {
  if (!annotDrawing || !annotStart) {
    clearDraft()
    return
  }
  const page = annotStart.page
  const mode = annotateMode.value

  try {
    if (mode === 'pen') {
      if (penPoints.length < 2) {
        clearDraft()
        redrawMarkupLayer(page)
        return
      }
      const geometry: PenGeometry = { points: penPoints.slice(), strokeWidth: DEFAULT_STROKE }
      await persistMarkup(page, 'pen', JSON.stringify(geometry))
    }
  } catch (err: any) {
    if (!isTransportError(err)) {
      MessagePlugin.error(err?.response?.data?.message || '保存批注失败')
    }
  } finally {
    clearDraft()
    redrawMarkupLayer(page)
  }
}

function nextTempMarkupId() {
  return -Date.now() - Math.floor(Math.random() * 1000)
}

async function persistMarkup(
  pageNum: number,
  markupType: string,
  geometryJson: string,
  noteText?: string
) {
  const clientId = nextTempMarkupId()
  const local: PdfMarkupDto = {
    id: clientId,
    bookId: props.bookId,
    userId: 0,
    pageNum,
    markupType: markupType as PdfMarkupDto['markupType'],
    geometryJson,
    color: DEFAULT_MARKUP_COLOR,
    noteText: noteText ?? null,
  }
  markups.value = [...markups.value, local]
  pushUndo({ kind: 'create', markup: local })
  redrawMarkupLayer(pageNum)

  try {
    const result = await syncPdfMarkupCreate(props.bookId, clientId, {
      pageNum,
      markupType,
      geometryJson,
      color: DEFAULT_MARKUP_COLOR,
      noteText,
    })
    if (result.status === 'ok') {
      replaceMarkupId(clientId, result.markup)
    }
  } catch (err) {
    markups.value = markups.value.filter((m) => m.id !== clientId)
    const last = undoStack[undoStack.length - 1]
    if (last?.kind === 'create' && last.markup.id === clientId) undoStack.pop()
    redrawMarkupLayer(pageNum)
    throw err
  }
}

function replaceMarkupId(clientId: number, server: PdfMarkupDto) {
  markups.value = markups.value.map((m) => (m.id === clientId ? server : m))
  for (let i = 0; i < undoStack.length; i++) {
    const e = undoStack[i]
    if (e.markup.id === clientId) {
      undoStack[i] = { ...e, markup: { ...e.markup, id: server.id } }
    }
  }
  redrawMarkupLayer(server.pageNum)
}

async function openInlineNote(
  page: number,
  nx: number,
  ny: number,
  x: number,
  y: number,
  opts?: { editId?: number; text?: string }
) {
  if (inlineNote.value) {
    suppressInlineNoteBlur = true
    await commitInlineNote()
    suppressInlineNoteBlur = false
  }
  inlineNote.value = {
    page,
    nx,
    ny,
    x,
    y,
    text: opts?.text ?? '',
    editId: opts?.editId,
  }
  if (opts?.editId != null) {
    redrawMarkupLayer(page)
  }
  await nextTick()
  // ref 写在 v-for 页容器内时，运行时可能是数组；需取真实 textarea 再 focus
  const el = inlineNoteRef.value as HTMLTextAreaElement | HTMLTextAreaElement[] | null
  const node = Array.isArray(el) ? el.find((x): x is HTMLTextAreaElement => !!x && typeof x.focus === 'function') : el
  if (node && typeof node.focus === 'function') {
    node.focus()
    // 编辑已有内容时把光标放到末尾
    const len = node.value.length
    try {
      node.setSelectionRange(len, len)
    } catch { /* ignore */ }
    autosizeInlineNote()
  }
}

function discardInlineNote() {
  const page = inlineNote.value?.page
  const wasEditing = inlineNote.value?.editId != null
  suppressInlineNoteBlur = true
  inlineNote.value = null
  suppressInlineNoteBlur = false
  if (wasEditing && page != null) redrawMarkupLayer(page)
}

async function commitInlineNote() {
  if (inlineNoteCommitting) return
  const note = inlineNote.value
  if (!note) return
  const text = note.text.trim()
  const editId = note.editId

  // 新建且空 → 直接关掉；编辑且空 → 删除该批注
  if (!text) {
    inlineNote.value = null
    if (editId != null) {
      await eraseMarkup(editId, note.page)
    } else if (note.page) {
      redrawMarkupLayer(note.page)
    }
    return
  }

  inlineNoteCommitting = true
  suppressInlineNoteBlur = true
  try {
    const geometryJson = JSON.stringify({ x: note.nx, y: note.ny })
    if (editId != null) {
      markups.value = markups.value.map((m) =>
        m.id === editId ? { ...m, geometryJson, noteText: text } : m
      )
      inlineNote.value = null
      redrawMarkupLayer(note.page)
      await syncPdfMarkupUpdate(props.bookId, editId, { geometryJson, noteText: text })
    } else {
      await persistMarkup(note.page, 'text', geometryJson, text)
      inlineNote.value = null
    }
  } catch (err: any) {
    if (!isTransportError(err)) {
      MessagePlugin.error(err?.response?.data?.message || '保存笔记失败')
    } else {
      inlineNote.value = null
    }
    if (editId != null) redrawMarkupLayer(note.page)
  } finally {
    inlineNoteCommitting = false
    await nextTick()
    suppressInlineNoteBlur = false
  }
}

function onInlineNoteBlur() {
  if (suppressInlineNoteBlur) return
  void commitInlineNote()
}

function onInlineNoteKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.preventDefault()
    discardInlineNote()
    return
  }
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void commitInlineNote()
  }
}

function autosizeInlineNote() {
  const el = inlineNoteRef.value as HTMLTextAreaElement | HTMLTextAreaElement[] | null
  const node = Array.isArray(el) ? el.find((x): x is HTMLTextAreaElement => !!x) : el
  if (!node) return
  node.style.height = 'auto'
  node.style.height = `${Math.max(node.scrollHeight, 20)}px`
  // 宽度随内容略扩，避免固定宽框感
  const canvas = canvasMap.get(inlineNote.value?.page || 0)
  const maxW = Math.max(80, (canvas?.clientWidth || 400) * 0.42)
  node.style.width = 'auto'
  const probe = document.createElement('span')
  probe.textContent = node.value || node.placeholder || '输入笔记…'
  probe.style.cssText =
    'position:absolute;visibility:hidden;white-space:pre;font:14px sans-serif;padding:0 4px'
  document.body.appendChild(probe)
  const w = Math.min(maxW, Math.max(48, probe.offsetWidth + 8))
  document.body.removeChild(probe)
  node.style.width = `${w}px`
}

function onInlineNoteDragStart(e: PointerEvent) {
  const note = inlineNote.value
  if (!note || e.button !== 0) return
  // 阈值开始：超过阈值后才真正拖动，短按仍可点选编辑文字
  suppressInlineNoteBlur = true
  const canvas = canvasMap.get(note.page)
  draggingInlineNote = {
    page: note.page,
    startClientX: e.clientX,
    startClientY: e.clientY,
    origX: note.x,
    origY: note.y,
    w: canvas?.clientWidth || 1,
    h: canvas?.clientHeight || 1,
    pointerId: e.pointerId,
    active: false,
  }
  const target = e.currentTarget as HTMLElement
  try {
    target.setPointerCapture(e.pointerId)
  } catch { /* ignore */ }
  target.addEventListener('pointermove', onInlineNoteDragMove)
  target.addEventListener('pointerup', onInlineNoteDragEnd)
  target.addEventListener('pointercancel', onInlineNoteDragEnd)
}

function onInlineNoteDragMove(e: Event) {
  const pe = e as PointerEvent
  const drag = draggingInlineNote
  const note = inlineNote.value
  if (!drag || !note || pe.pointerId !== drag.pointerId) return
  const dist = Math.abs(pe.clientX - drag.startClientX) + Math.abs(pe.clientY - drag.startClientY)
  if (!drag.active) {
    if (dist < INLINE_NOTE_DRAG_THRESHOLD_PX) return
    drag.active = true
    pe.preventDefault()
  } else {
    pe.preventDefault()
  }
  const dx = pe.clientX - drag.startClientX
  const dy = pe.clientY - drag.startClientY
  const x = Math.max(0, Math.min(drag.w - 24, drag.origX + dx))
  const y = Math.max(0, Math.min(drag.h - 24, drag.origY + dy))
  const [nx, ny] = toNorm(x, y, drag.w, drag.h)
  inlineNote.value = { ...note, x, y, nx, ny }
}

function onInlineNoteDragEnd(e: Event) {
  const pe = e as PointerEvent
  const drag = draggingInlineNote
  if (!drag || pe.pointerId !== drag.pointerId) return
  const target = pe.currentTarget as HTMLElement
  target.removeEventListener('pointermove', onInlineNoteDragMove)
  target.removeEventListener('pointerup', onInlineNoteDragEnd)
  target.removeEventListener('pointercancel', onInlineNoteDragEnd)
  try {
    if (target.hasPointerCapture?.(pe.pointerId)) {
      target.releasePointerCapture(pe.pointerId)
    }
  } catch { /* ignore */ }
  draggingInlineNote = null
  // 拖动松手后，避免 blur 立刻提交；下一帧再允许
  nextTick(() => {
    suppressInlineNoteBlur = false
    if (!drag.active) {
      // 未拖动：把焦点交回输入框
      const el = inlineNoteRef.value as HTMLTextAreaElement | HTMLTextAreaElement[] | null
      const node = Array.isArray(el) ? el.find((x): x is HTMLTextAreaElement => !!x && typeof x.focus === 'function') : el
      node?.focus?.()
    }
  })
}

async function eraseMarkup(id: number, page: number) {
  const removed = markups.value.find((m) => m.id === id)
  markups.value = markups.value.filter((m) => m.id !== id)
  if (removed) pushUndo({ kind: 'delete', markup: removed })
  redrawMarkupLayer(page)
  try {
    await syncPdfMarkupDelete(props.bookId, id)
  } catch (err) {
    if (!isTransportError(err)) {
      if (removed) {
        markups.value = [...markups.value, removed]
        undoStack.pop()
        redrawMarkupLayer(page)
      }
      MessagePlugin.error('擦除失败')
    }
  }
}

type UndoEntry =
  | { kind: 'create'; markup: PdfMarkupDto }
  | { kind: 'delete'; markup: PdfMarkupDto }

const undoStack: UndoEntry[] = []
const MAX_UNDO = 50

function pushUndo(entry: UndoEntry) {
  undoStack.push(entry)
  if (undoStack.length > MAX_UNDO) undoStack.shift()
}

async function undoLastMarkup() {
  const entry = undoStack.pop()
  if (!entry) {
    MessagePlugin.info('没有可撤销的批注')
    return
  }
  try {
    if (entry.kind === 'create') {
      markups.value = markups.value.filter((m) => m.id !== entry.markup.id)
      redrawMarkupLayer(entry.markup.pageNum)
      await syncPdfMarkupDelete(props.bookId, entry.markup.id)
    } else {
      const m = entry.markup
      const clientId = m.id > 0 ? nextTempMarkupId() : m.id
      const local: PdfMarkupDto = { ...m, id: clientId }
      markups.value = [...markups.value, local]
      redrawMarkupLayer(local.pageNum)
      const result = await syncPdfMarkupCreate(props.bookId, clientId, {
        pageNum: m.pageNum,
        markupType: m.markupType,
        geometryJson: m.geometryJson,
        color: m.color || DEFAULT_MARKUP_COLOR,
        noteText: m.noteText || undefined,
      })
      if (result.status === 'ok') {
        replaceMarkupId(clientId, result.markup)
      }
    }
  } catch (err) {
    if (!isTransportError(err)) {
      undoStack.push(entry)
      MessagePlugin.error('撤销失败')
    }
  }
}

function onKeyDown(e: KeyboardEvent) {
  const isUndo = (e.ctrlKey || e.metaKey) && !e.shiftKey && (e.key === 'z' || e.key === 'Z')
  if (!isUndo) return
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable) {
    return
  }
  e.preventDefault()
  void undoLastMarkup()
}

function onSelectStart(e: PointerEvent) {
  if (ocrLoading.value) return
  // 避免拖拽时选中页面文字（已无文本层，兜底）；Pencil 也需 preventDefault 防 Scribble
  e.preventDefault()
  const info = pageFromEvent(e)
  if (!info) return
  capturePointer(e)
  selecting = true
  selectStart = { page: info.page, x: info.localX, y: info.localY }
  selectPage.value = info.page
  selectRect.value = { x: info.localX, y: info.localY, w: 0, h: 0 }
}

function onSelectMove(e: PointerEvent) {
  if (!selecting || !selectStart) return
  e.preventDefault()
  const info = pageFromEvent(e)
  if (!info || info.page !== selectStart.page) return
  const x1 = selectStart.x
  const y1 = selectStart.y
  const x2 = Math.max(0, Math.min(info.canvas.clientWidth, info.localX))
  const y2 = Math.max(0, Math.min(info.canvas.clientHeight, info.localY))
  selectRect.value = {
    x: Math.min(x1, x2),
    y: Math.min(y1, y2),
    w: Math.abs(x2 - x1),
    h: Math.abs(y2 - y1),
  }
}

async function onSelectEnd() {
  if (!selecting || !selectStart || !selectRect.value) {
    selecting = false
    return
  }
  selecting = false
  const rect = selectRect.value
  const page = selectStart.page
  selectStart = null

  if (rect.w < MIN_SELECT_SIZE || rect.h < MIN_SELECT_SIZE) {
    selectRect.value = null
    selectPage.value = null
    return
  }

  const canvas = canvasMap.get(page)
  if (!canvas) {
    selectRect.value = null
    selectPage.value = null
    return
  }

  ocrLoading.value = true
  try {
    const cropped = cropAndSharpenRegion(canvas, rect)
    const text = await recognizeRegion(cropped)
    if (!text) {
      MessagePlugin.warning('未识别到文字，请放大后重新框选')
      return
    }
    let finalText = normalizeSelectedText(text, { trimPartialEdges: true })
    if (!finalText) {
      MessagePlugin.warning('未识别到文字，请放大后重新框选')
      return
    }
    if (finalText.length > MAX_SELECTION_LENGTH) {
      MessagePlugin.warning(`选中内容过长，已截取前 ${MAX_SELECTION_LENGTH} 字`)
      finalText = finalText.slice(0, MAX_SELECTION_LENGTH)
    }
    lastSelectedText = finalText
    emit('select-text', finalText)
    saveProgress()
  } catch (err: any) {
    console.error('[PdfReader] OCR 失败:', err)
    const msg = String(err?.message || err || '')
    if (/PAGE_NOT_READY/i.test(msg)) {
      MessagePlugin.warning('页面尚未渲染完成，请稍后再框选')
    } else if (/Too many properties|RangeError|out of memory|OOM/i.test(msg)) {
      MessagePlugin.warning('选区过大，请缩小框选范围后重试')
    } else if (/wasm|fetch|404|Failed to load|network|importScripts/i.test(msg)) {
      MessagePlugin.error('识别引擎加载失败，请检查 /tesseract 静态资源或刷新重试')
    } else {
      MessagePlugin.error('文字识别失败，请重试')
    }
  } finally {
    ocrLoading.value = false
    // 短暂保留选框提示，随后清除
    setTimeout(() => {
      selectRect.value = null
      selectPage.value = null
    }, 400)
  }
}

function onSelectCancel() {
  if (selecting) {
    selecting = false
    selectStart = null
    selectRect.value = null
    selectPage.value = null
  }
}

/** 文字层原生划词 → 与 OCR 相同出口 emit('select-text') */
function selectionInsidePdfTextLayer(sel: Selection): boolean {
  const node = sel.anchorNode
  if (!node) return false
  const el = (node.nodeType === Node.TEXT_NODE ? node.parentElement : node) as Element | null
  if (!el) return false
  const layer = el.closest('.pdf-text-layer')
  if (!layer || layer.classList.contains('pdf-text-layer--ocr')) return false
  return !!(containerRef.value && containerRef.value.contains(layer))
}

function emitTextLayerSelection() {
  if (annotateMode.value !== 'none' || ocrLoading.value) return
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed || !sel.rangeCount) return
  if (!selectionInsidePdfTextLayer(sel)) return
  let finalText = normalizeSelectedText(sel.toString(), { trimPartialEdges: true })
  if (!finalText) return
  if (finalText.length > MAX_SELECTION_LENGTH) {
    MessagePlugin.warning(`选中内容过长，已截取前 ${MAX_SELECTION_LENGTH} 字`)
    finalText = finalText.slice(0, MAX_SELECTION_LENGTH)
  }
  if (finalText === lastEmittedSelection) return
  lastEmittedSelection = finalText
  lastSelectedText = finalText
  emit('select-text', finalText)
  saveProgress()
}

function scheduleEmitTextLayerSelection() {
  if (selectionEmitTimer) clearTimeout(selectionEmitTimer)
  selectionEmitTimer = setTimeout(() => {
    selectionEmitTimer = null
    emitTextLayerSelection()
  }, 80)
}

function onDocSelectionChange() {
  scheduleEmitTextLayerSelection()
}

function onDocMouseUp() {
  scheduleEmitTextLayerSelection()
}

function goToPage(pageNum: number) {
  const el = document.getElementById('pdf-page-container-' + pageNum)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    currentPage = pageNum
    scheduleRender(pageNum)
  }
}

function getCurrentPage(): number {
  return currentPage || 1
}

defineExpose({ goToPage, getCurrentPage })

let stopOfflineSynced: (() => void) | undefined

onMounted(() => {
  loadDocument()
  window.addEventListener('keydown', onKeyDown)
  document.addEventListener('selectionchange', onDocSelectionChange)
  document.addEventListener('mouseup', onDocMouseUp)
  document.addEventListener('touchend', onDocMouseUp, { passive: true })
  stopOfflineSynced = onOfflineSynced((detail) => {
    if (detail.kind !== 'pdf-markup-create') return
    if (detail.bookId !== props.bookId) return
    replaceMarkupId(detail.clientId, detail.markup)
  })
})

onBeforeUnmount(() => {
  destroyed = true
  stopOfflineSynced?.()
  window.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('selectionchange', onDocSelectionChange)
  document.removeEventListener('mouseup', onDocMouseUp)
  document.removeEventListener('touchend', onDocMouseUp)
  if (selectionEmitTimer) clearTimeout(selectionEmitTimer)
  for (const pageNum of [...textLayerTasks.keys()]) {
    clearTextLayer(pageNum)
  }
  if (saveTimer) clearTimeout(saveTimer)
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeObserver?.disconnect()
  resizeObserver = null
  for (const timer of releaseTimers.values()) clearTimeout(timer)
  releaseTimers.clear()
  observer?.disconnect()
  observer = null
  const container = containerRef.value
  if (container) {
    const handler = (container as any).__scrollHandler
    if (handler) container.removeEventListener('scroll', handler)
    if (scribbleGuardBound) {
      container.removeEventListener('touchmove', preventTouchDefault)
      scribbleGuardBound = false
    }
  }
  activePointerId = null
  if (pdfDoc) {
    pdfDoc.destroy()
    pdfDoc = null
  }
  terminateOcrWorker().catch(() => {})
})
</script>

<style scoped>
.pdf-viewer {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #525659;
  user-select: none;
  overflow: hidden;
}

.pdf-scroll {
  width: 100%;
  height: 100%;
  overflow-x: auto;
  overflow-y: auto;
  /* 允许手指左右/上下滑动；Pencil 走 Pointer Events，不依赖 mouse* */
  touch-action: pan-x pan-y;
  -webkit-user-select: none;
  user-select: none;
  -webkit-touch-callout: none;
}

.pdf-scroll.is-annotating {
  cursor: crosshair;
  touch-action: none;
}

/* 顶部缩放：原位置，透明悬浮、字号偏小 */
.pdf-zoom-bar {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 10px;
  background: rgba(20, 22, 24, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.18);
  pointer-events: auto;
}

.pdf-zoom-bar :deep(.t-button) {
  color: rgba(255, 255, 255, 0.78);
  width: 26px;
  height: 26px;
  min-width: 26px;
  padding: 0 !important;
}

.pdf-zoom-bar :deep(.t-button:hover) {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

.pdf-zoom-value {
  color: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  line-height: 1;
  min-width: 34px;
  text-align: center;
}

/* 右侧批注：透明悬浮 */
.pdf-toolbar {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 44px;
  padding: 8px 4px;
  background: rgba(20, 22, 24, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.22);
  pointer-events: auto;
}

.pdf-tool-guide-anchor {
  display: inline-flex;
  line-height: 0;
}

.pdf-toolbar :deep(.t-button) {
  color: rgba(255, 255, 255, 0.78);
  width: 36px;
  height: 36px;
  min-width: 36px;
  padding: 0 !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.pdf-toolbar :deep(.t-button:hover) {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

.eraser-icon {
  display: block;
  flex-shrink: 0;
}

.pdf-toolbar :deep(.tool-active) {
  color: #ff8a80 !important;
  background: rgba(229, 57, 53, 0.28);
  border-radius: 6px;
}

.pdf-page-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 0 24px;
  /* 放大后页面可宽于视口，底部出现横向滚动条左右滑动 */
  width: max-content;
  min-width: 100%;
  box-sizing: border-box;
}

@media (max-width: 560px), (orientation: portrait) {
  .pdf-page-list {
    padding: 8px 0 20px;
    gap: 6px;
  }

  .pdf-toolbar {
    right: 4px;
    width: 40px;
    padding: 6px 2px;
  }

  .pdf-toolbar :deep(.t-button) {
    width: 32px;
    height: 32px;
    min-width: 32px;
  }

  .pdf-zoom-bar {
    top: 6px;
  }
}

.pdf-page-list.is-annotating {
  cursor: crosshair;
}

.pdf-canvas {
  display: block;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  background: #fff;
  cursor: crosshair;
}

.pdf-canvas--ready {
  background: #fff;
}

.pdf-canvas--text {
  cursor: text;
}

.pdf-page-container {
  position: relative;
  display: inline-block;
  overflow: hidden;
  background: #3a3d40;
}

/* pdf.js TextLayer：透明字可划选，叠在 canvas 上（需配合 --scale-factor） */
.pdf-text-layer {
  position: absolute;
  inset: 0;
  z-index: 2;
  overflow: clip;
  line-height: 1;
  text-align: initial;
  text-size-adjust: none;
  -webkit-text-size-adjust: none;
  transform-origin: 0 0;
  caret-color: transparent;
  cursor: text;
  pointer-events: auto;
  user-select: text;
  -webkit-user-select: text;
  -webkit-touch-callout: default;
}

.pdf-text-layer :deep(span),
.pdf-text-layer :deep(br) {
  color: transparent;
  position: absolute;
  white-space: pre;
  cursor: text;
  transform-origin: 0% 0%;
  z-index: 1;
}

.pdf-text-layer :deep(span.markedContent) {
  top: 0;
  height: 0;
}

.pdf-text-layer :deep(::selection) {
  background: rgba(77, 163, 255, 0.35);
}

.pdf-text-layer :deep(br::selection) {
  background: transparent;
}

/* 关键选区「穿透」：划词时 endOfContent 铺满并挡在字下方 */
.pdf-text-layer :deep(.endOfContent) {
  display: block;
  position: absolute;
  inset: 100% 0 0;
  z-index: 0;
  cursor: default;
  user-select: none;
  -webkit-user-select: none;
}

.pdf-text-layer.selecting :deep(.endOfContent) {
  top: 0;
}

.pdf-text-layer--ocr,
.pdf-text-layer--disabled {
  pointer-events: none;
  user-select: none;
  -webkit-user-select: none;
  cursor: inherit;
}

.pdf-markup-layer {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 3;
}

.pdf-markup-layer--active {
  pointer-events: auto;
  cursor: crosshair;
}

.pdf-select-rect {
  position: absolute;
  border: 2px solid #4da3ff;
  background: rgba(77, 163, 255, 0.12);
  pointer-events: none;
  z-index: 5;
  box-sizing: border-box;
}

.pdf-inline-note-wrap {
  position: absolute;
  z-index: 7;
  display: inline-flex;
  flex-direction: column;
  min-width: 0;
  max-width: 42%;
  cursor: grab;
}

.pdf-inline-note {
  box-sizing: border-box;
  min-width: 48px;
  max-width: 100%;
  min-height: 1.35em;
  padding: 2px 4px;
  margin: 0;
  border: none;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.55);
  color: #e53935;
  font-size: 14px;
  font-weight: 400;
  line-height: 1.35;
  font-family: sans-serif;
  resize: none;
  outline: none;
  box-shadow: none;
  overflow: hidden;
  field-sizing: content;
}

.pdf-inline-note::placeholder {
  color: rgba(229, 57, 53, 0.45);
}

.pdf-inline-note:focus {
  background: rgba(255, 255, 255, 0.65);
  outline: none;
}
</style>
