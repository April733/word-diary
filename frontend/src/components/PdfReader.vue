<template>
  <div class="pdf-viewer">
    <div
      ref="containerRef"
      class="pdf-scroll"
      :class="{ 'is-annotating': annotateMode !== 'none' }"
      @mousedown="onPointerDown"
      @mousemove="onPointerMove"
      @mouseup="onPointerUp"
      @mouseleave="onPointerCancel"
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
            :class="{ 'pdf-canvas--ready': renderedPages.has(pageNum) }"
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
          <textarea
            v-if="inlineNote && inlineNote.page === pageNum"
            ref="inlineNoteRef"
            v-model="inlineNote.text"
            class="pdf-inline-note"
            :style="inlineNoteStyle"
            maxlength="500"
            placeholder="输入笔记…"
            rows="1"
            @mousedown.stop
            @click.stop
            @keydown="onInlineNoteKeydown"
            @blur="onInlineNoteBlur"
          />
        </div>
      </div>
    </div>

    <!-- 顶部：缩放（透明悬浮，字号偏小） -->
    <div class="pdf-zoom-bar" @mousedown.stop>
      <t-button variant="text" size="small" title="−" @click="zoomOut">
        <t-icon name="remove" size="14px" />
      </t-button>
      <span class="pdf-zoom-value">{{ Math.round(scale * 100) }}%</span>
      <t-button variant="text" size="small" title="+" @click="zoomIn">
        <t-icon name="add" size="14px" />
      </t-button>
    </div>

    <!-- 右侧：批注工具（透明悬浮；框选为默认模式） -->
    <aside class="pdf-toolbar" data-guide="pdf-tools" aria-label="Tools" @mousedown.stop>
      <span data-guide="pdf-select" class="pdf-tool-guide-anchor">
        <t-button
          variant="text"
          size="small"
          :class="{ 'tool-active': annotateMode === 'none' }"
          :title="$t('reader.pdfToolSelect')"
          @click="setAnnotateMode('none')"
        >
          <svg class="select-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <rect
              x="4.5"
              y="4.5"
              width="15"
              height="15"
              rx="1.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-dasharray="3.2 2.4"
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
import { getBookFileUrl, getBookPresignedFileUrl, updateProgress, getPdfMarkups, addPdfMarkup, deletePdfMarkup } from '../api'
import { pdfjsLib, encodePdfCfi, decodePdfCfi, estimatePageHeight, type PdfProgressCfi } from '../utils/pdfReader'
import { cropAndSharpenRegion, recognizeRegion, terminateOcrWorker } from '../utils/pdfImageOcr'
import { normalizeSelectedText } from '../utils/selectedText'
import {
  type AnnotateMode,
  type PdfMarkupDto,
  type PenGeometry,
  DEFAULT_MARKUP_COLOR,
  DEFAULT_STROKE,
  toNorm,
  drawMarkup,
  hitTestMarkup,
} from '../utils/pdfMarkup'

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
const scale = ref(1.2)
const ocrLoading = ref(false)

let pdfDoc: any = null
const canvasMap = new Map<number, HTMLCanvasElement>()
const markupCanvasMap = new Map<number, HTMLCanvasElement>()
const renderedPages = reactive(new Set<number>())
const renderingPages = new Set<number>()
const placeholderHeights = reactive<Record<number, number>>({})
const pageAspect = reactive<Record<number, number>>({})
const releaseTimers = new Map<number, ReturnType<typeof setTimeout>>()

let observer: IntersectionObserver | null = null
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

/** 页内透明笔记输入框（点击落点即现，无弹窗） */
const inlineNote = ref<{
  page: number
  nx: number
  ny: number
  x: number
  y: number
  text: string
} | null>(null)
const inlineNoteRef = ref<HTMLTextAreaElement | null>(null)
let inlineNoteCommitting = false
let suppressInlineNoteBlur = false

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
  // 再点一次已激活工具 → 回到框选；笔/笔记/擦除均未选中时即为框选
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
}

function setCanvasRef(pageNum: number, el: unknown) {
  if (el instanceof HTMLCanvasElement) {
    canvasMap.set(pageNum, el)
  }
}

function setMarkupCanvasRef(pageNum: number, el: unknown) {
  if (el instanceof HTMLCanvasElement) {
    markupCanvasMap.set(pageNum, el)
  }
}

function getContainerWidth(): number {
  return (containerRef.value?.clientWidth || 700)
}

function markupsForPage(pageNum: number): PdfMarkupDto[] {
  return markups.value.filter((m) => m.pageNum === pageNum)
}

async function loadMarkups() {
  try {
    const res = await getPdfMarkups(props.bookId)
    markups.value = (res.data?.data || []) as PdfMarkupDto[]
    for (const page of renderedPages) {
      redrawMarkupLayer(page)
    }
  } catch (e) {
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
  for (const m of markupsForPage(pageNum)) {
    drawMarkup(ctx, m, w, h)
  }
  // 绘制中的笔迹预览
  if (annotDrawing && annotateMode.value === 'pen' && draftPage.value === pageNum && penPoints.length > 1) {
    ctx.save()
    ctx.strokeStyle = DEFAULT_MARKUP_COLOR
    ctx.lineWidth = Math.max(1.5, DEFAULT_STROKE * w)
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

async function openPdfDocument(docParams: Record<string, unknown>) {
  const loadingTask = pdfjsLib.getDocument({
    ...docParams,
    disableAutoFetch: false,
    disableStream: false,
    rangeChunkSize: 1024 * 1024,
  })
  return loadingTask.promise
}

async function loadDocument() {
  emit('loading', true)
  try {
    const token = localStorage.getItem('token')
    let usedCos = false

    // 1) 优先 COS 预签名直链（大文件更快）；CORS 未配好时回退后端代理
    try {
      const urlRes = await getBookPresignedFileUrl(props.bookId)
      const signedUrl = urlRes.data?.data?.url
      if (signedUrl) {
        console.log('[PdfReader] 使用 COS 预签名直链加载')
        usedCos = true
        pdfDoc = await openPdfDocument({ url: signedUrl })
      }
    } catch (e) {
      console.warn(
        usedCos
          ? '[PdfReader] COS 直链加载失败（多为 CORS），回退后端 /file'
          : '[PdfReader] 预签名失败，回退后端 /file',
        e,
      )
      pdfDoc = null
    }

    // 2) 回退：PDF.js 请求同源 /file（带 Token）
    if (!pdfDoc) {
      console.log('[PdfReader] 使用后端 /file 流式加载')
      pdfDoc = await openPdfDocument(buildProxyDocParams(token))
    }

    if (destroyed) {
      pdfDoc.destroy()
      pdfDoc = null
      return
    }

    totalPages.value = pdfDoc.numPages
    initPlaceholders()
    await loadOutline()
    emit('ready', totalPages.value)

    await nextTick()
    setupObserver()
    setupScrollListener()
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

    const maxWidth = getContainerWidth() - 32
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
  } catch (e) {
    console.warn('[PdfReader] 渲染第', pageNum, '页失败:', e)
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
  updateProgress(props.bookId, {
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

// -------- 指针：OCR 框选 / 批注 --------

function pageFromEvent(e: MouseEvent): { page: number; canvas: HTMLCanvasElement; localX: number; localY: number } | null {
  const target = (e.target as HTMLElement)?.closest?.('.pdf-page-container') as HTMLElement | null
  if (!target) return null
  const page = Number(target.dataset.page)
  const canvas = canvasMap.get(page)
  if (!canvas || !renderedPages.has(page)) return null
  const rect = canvas.getBoundingClientRect()
  return {
    page,
    canvas,
    localX: e.clientX - rect.left,
    localY: e.clientY - rect.top,
  }
}

function onPointerDown(e: MouseEvent) {
  if (annotateMode.value !== 'none') {
    onAnnotStart(e)
  } else {
    onSelectStart(e)
  }
}

function onPointerMove(e: MouseEvent) {
  if (annotateMode.value !== 'none') {
    onAnnotMove(e)
  } else {
    onSelectMove(e)
  }
}

async function onPointerUp(e: MouseEvent) {
  if (annotateMode.value !== 'none') {
    await onAnnotEnd(e)
  } else {
    await onSelectEnd()
  }
}

function onPointerCancel() {
  if (annotateMode.value !== 'none') {
    clearDraft()
    if (draftPage.value) redrawMarkupLayer(draftPage.value)
  } else {
    onSelectCancel()
  }
}

function onAnnotStart(e: MouseEvent) {
  if (e.button !== 0 || ocrLoading.value) return
  e.preventDefault()
  const info = pageFromEvent(e)
  if (!info) return
  const { page, canvas, localX, localY } = info
  const [nx, ny] = toNorm(localX, localY, canvas.clientWidth, canvas.clientHeight)

  if (annotateMode.value === 'eraser') {
    const hit = hitTestMarkup(markupsForPage(page), nx, ny, canvas.clientWidth)
    if (hit != null) {
      eraseMarkup(hit, page)
    }
    return
  }

  if (annotateMode.value === 'text') {
    void openInlineNote(page, nx, ny, localX, localY)
    return
  }

  annotDrawing = true
  annotStart = { page, x: localX, y: localY, w: canvas.clientWidth, h: canvas.clientHeight }
  draftPage.value = page
  if (annotateMode.value === 'pen') {
    penPoints = [[nx, ny]]
  }
}

function onAnnotMove(e: MouseEvent) {
  if (!annotDrawing || !annotStart) return
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

async function onAnnotEnd(_e: MouseEvent) {
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
    MessagePlugin.error(err?.response?.data?.message || '保存批注失败')
  } finally {
    clearDraft()
    redrawMarkupLayer(page)
  }
}

async function persistMarkup(
  pageNum: number,
  markupType: string,
  geometryJson: string,
  noteText?: string
) {
  const res = await addPdfMarkup(props.bookId, {
    pageNum,
    markupType,
    geometryJson,
    color: DEFAULT_MARKUP_COLOR,
    noteText,
  })
  const saved = res.data?.data as PdfMarkupDto
  if (saved?.id) {
    markups.value = [...markups.value, saved]
    pushUndo({ kind: 'create', markup: saved })
    redrawMarkupLayer(pageNum)
  }
}

async function openInlineNote(page: number, nx: number, ny: number, x: number, y: number) {
  if (inlineNote.value) {
    suppressInlineNoteBlur = true
    await commitInlineNote()
    suppressInlineNoteBlur = false
  }
  inlineNote.value = { page, nx, ny, x, y, text: '' }
  await nextTick()
  inlineNoteRef.value?.focus()
}

function discardInlineNote() {
  suppressInlineNoteBlur = true
  inlineNote.value = null
  suppressInlineNoteBlur = false
}

async function commitInlineNote() {
  if (inlineNoteCommitting) return
  const note = inlineNote.value
  if (!note) return
  const text = note.text.trim()
  if (!text) {
    inlineNote.value = null
    return
  }
  inlineNoteCommitting = true
  suppressInlineNoteBlur = true
  try {
    await persistMarkup(note.page, 'text', JSON.stringify({ x: note.nx, y: note.ny }), text)
    inlineNote.value = null
  } catch (err: any) {
    MessagePlugin.error(err?.response?.data?.message || '保存笔记失败')
  } finally {
    inlineNoteCommitting = false
    // 等 DOM 更新后再允许 blur，避免销毁 textarea 时误提交
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

async function eraseMarkup(id: number, page: number) {
  const removed = markups.value.find((m) => m.id === id)
  try {
    await deletePdfMarkup(props.bookId, id)
    markups.value = markups.value.filter((m) => m.id !== id)
    if (removed) pushUndo({ kind: 'delete', markup: removed })
    redrawMarkupLayer(page)
  } catch {
    MessagePlugin.error('擦除失败')
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
      await deletePdfMarkup(props.bookId, entry.markup.id)
      markups.value = markups.value.filter((m) => m.id !== entry.markup.id)
      redrawMarkupLayer(entry.markup.pageNum)
    } else {
      const m = entry.markup
      const res = await addPdfMarkup(props.bookId, {
        pageNum: m.pageNum,
        markupType: m.markupType,
        geometryJson: m.geometryJson,
        color: m.color || DEFAULT_MARKUP_COLOR,
        noteText: m.noteText || undefined,
      })
      const saved = res.data?.data as PdfMarkupDto
      if (saved?.id) {
        markups.value = [...markups.value, saved]
        redrawMarkupLayer(saved.pageNum)
      }
    }
  } catch {
    undoStack.push(entry)
    MessagePlugin.error('撤销失败')
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

function onSelectStart(e: MouseEvent) {
  if (e.button !== 0 || ocrLoading.value) return
  // 避免拖拽时选中页面文字（已无文本层，兜底）
  e.preventDefault()
  const info = pageFromEvent(e)
  if (!info) return
  selecting = true
  selectStart = { page: info.page, x: info.localX, y: info.localY }
  selectPage.value = info.page
  selectRect.value = { x: info.localX, y: info.localY, w: 0, h: 0 }
}

function onSelectMove(e: MouseEvent) {
  if (!selecting || !selectStart) return
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
    if (/wasm|fetch|404|Failed to load|network/i.test(msg)) {
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

function goToPage(pageNum: number) {
  const el = document.getElementById('pdf-page-container-' + pageNum)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    currentPage = pageNum
    scheduleRender(pageNum)
  }
}

defineExpose({ goToPage })

onMounted(() => {
  loadDocument()
  window.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  destroyed = true
  window.removeEventListener('keydown', onKeyDown)
  if (saveTimer) clearTimeout(saveTimer)
  for (const timer of releaseTimers.values()) clearTimeout(timer)
  releaseTimers.clear()
  observer?.disconnect()
  observer = null
  const container = containerRef.value
  if (container) {
    const handler = (container as any).__scrollHandler
    if (handler) container.removeEventListener('scroll', handler)
  }
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
  /* 允许手指左右/上下滑动浏览放大后的页面 */
  touch-action: pan-x pan-y;
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

.pdf-page-container {
  position: relative;
  display: inline-block;
  overflow: hidden;
  background: #3a3d40;
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

.pdf-inline-note {
  position: absolute;
  z-index: 7;
  min-width: 120px;
  max-width: 42%;
  min-height: 1.4em;
  padding: 2px 4px;
  margin: 0;
  border: 1px solid rgba(229, 57, 53, 0.45);
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.22);
  color: #e53935;
  font-size: 14px;
  line-height: 1.35;
  font-family: inherit;
  resize: both;
  outline: none;
  box-shadow: none;
  backdrop-filter: blur(1px);
}

.pdf-inline-note::placeholder {
  color: rgba(229, 57, 53, 0.45);
}

.pdf-inline-note:focus {
  border-color: rgba(229, 57, 53, 0.75);
  background: rgba(255, 255, 255, 0.32);
}
</style>
