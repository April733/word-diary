<template>
  <div ref="containerRef" class="pdf-viewer">
    <div class="pdf-zoom-bar">
      <t-button variant="text" size="small" @click="zoomOut" title="缩小">
        <t-icon name="remove" size="14px" />
      </t-button>
      <span class="pdf-zoom-value">{{ Math.round(scale * 100) }}%</span>
      <t-button variant="text" size="small" @click="zoomIn" title="放大">
        <t-icon name="add" size="14px" />
      </t-button>
      <span class="pdf-page-count">{{ totalPages }} 页</span>
      <span class="pdf-ocr-hint">拖拽框选文字</span>
      <span v-if="ocrLoading" class="pdf-ocr-status">识别中…</span>
    </div>

    <div
      class="pdf-page-list"
      ref="pageListRef"
      @mousedown="onSelectStart"
      @mousemove="onSelectMove"
      @mouseup="onSelectEnd"
      @mouseleave="onSelectCancel"
    >
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
        <div
          v-if="selectRect && selectPage === pageNum"
          class="pdf-select-rect"
          :style="selectRectStyle"
        />
        <div v-if="ocrLoading && selectPage === pageNum" class="pdf-ocr-overlay">
          <t-loading size="small" text="识别中…" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import axios from 'axios'
import { getBookFileUrl, updateProgress } from '../api'
import { pdfjsLib, encodePdfCfi, decodePdfCfi, estimatePageHeight, type PdfProgressCfi } from '../utils/pdfReader'
import { cropAndSharpenRegion, recognizeRegion, terminateOcrWorker } from '../utils/pdfImageOcr'

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
  (e: 'select-text', text: string): void
  (e: 'loading', value: boolean): void
  (e: 'error', message: string): void
}>()

const SCALE_STEP = 0.2
const MAX_SELECTION_LENGTH = 800
const RENDER_MARGIN = '600px'
/** 视口外超过此缓冲的已渲染页释放 canvas，控制内存 */
const KEEP_BUFFER = 3
const MIN_SELECT_SIZE = 8

const containerRef = ref<HTMLElement | null>(null)
const pageListRef = ref<HTMLElement | null>(null)
const totalPages = ref(0)
const scale = ref(1.2)
const ocrLoading = ref(false)

let pdfDoc: any = null
const canvasMap = new Map<number, HTMLCanvasElement>()
const renderedPages = reactive(new Set<number>())
const renderingPages = new Set<number>()
const placeholderHeights = reactive<Record<number, number>>({})
const pageAspect = reactive<Record<number, number>>({})

let observer: IntersectionObserver | null = null
let saveTimer: ReturnType<typeof setTimeout> | null = null
let currentPage = 1
let destroyed = false

// 框选状态
const selectPage = ref<number | null>(null)
const selectRect = ref<{ x: number; y: number; w: number; h: number } | null>(null)
let selectStart: { page: number; x: number; y: number } | null = null
let selecting = false
let lastSelectedText = ''

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

function setCanvasRef(pageNum: number, el: unknown) {
  if (el instanceof HTMLCanvasElement) {
    canvasMap.set(pageNum, el)
  }
}

function getContainerWidth(): number {
  return (containerRef.value?.clientWidth || 700)
}

async function loadDocument() {
  emit('loading', true)
  const fileUrl = getBookFileUrl(props.bookId)
  try {
    const token = localStorage.getItem('token')
    const response = await axios.get(fileUrl, {
      responseType: 'arraybuffer',
      timeout: 120000,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.data || response.data.byteLength === 0) {
      throw new Error('下载的 PDF 文件为空')
    }

    const loadingTask = pdfjsLib.getDocument({ data: response.data })
    pdfDoc = await loadingTask.promise
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

function scheduleRender(pageNum: number) {
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

    // 高 DPI 清晰渲染
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    canvas.width = Math.floor(viewport.width * dpr)
    canvas.height = Math.floor(viewport.height * dpr)
    canvas.style.width = Math.floor(viewport.width) + 'px'
    canvas.style.height = Math.floor(viewport.height) + 'px'
    placeholderHeights[pageNum] = Math.floor(viewport.height)

    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

    const task = page.render({ canvasContext: ctx, viewport })
    await task.promise
    if (destroyed) return
    renderedPages.add(pageNum)
  } catch (e) {
    console.warn('[PdfReader] 渲染第', pageNum, '页失败:', e)
  }
}

function maybeReleasePage(pageNum: number) {
  if (!renderedPages.has(pageNum)) return
  if (Math.abs(pageNum - currentPage) <= KEEP_BUFFER) return
  const canvas = canvasMap.get(pageNum)
  if (canvas) {
    const ctx = canvas.getContext('2d')
    if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height)
    canvas.width = 0
    canvas.height = 0
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
  emit('chapter-highlight', page)
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
  renderedPages.clear()
  initPlaceholders()
  await nextTick()
  setupObserver()
  for (let i = Math.max(1, currentPage - 1); i <= Math.min(totalPages.value, currentPage + 1); i++) {
    scheduleRender(i)
  }
}

// -------- 框选 OCR --------

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
    let finalText = text
    if (finalText.length > MAX_SELECTION_LENGTH) {
      MessagePlugin.warning(`选中内容过长，已截取前 ${MAX_SELECTION_LENGTH} 字`)
      finalText = finalText.slice(0, MAX_SELECTION_LENGTH)
    }
    lastSelectedText = finalText
    emit('select-text', finalText)
    saveProgress()
  } catch (err: any) {
    console.error('[PdfReader] OCR 失败:', err)
    MessagePlugin.error('文字识别失败，请重试')
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
})

onBeforeUnmount(() => {
  destroyed = true
  if (saveTimer) clearTimeout(saveTimer)
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
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #525659;
  user-select: none;
}

.pdf-zoom-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 0 0 8px 8px;
  margin-bottom: 12px;
}

.pdf-zoom-value {
  color: #fff;
  font-size: 13px;
  min-width: 48px;
  text-align: center;
}

.pdf-zoom-bar :deep(.t-button) {
  color: #ccc;
}
.pdf-zoom-bar :deep(.t-button:hover) {
  color: #fff;
}

.pdf-page-count {
  color: #aaa;
  font-size: 12px;
  margin-left: 12px;
}

.pdf-ocr-hint {
  color: #8ab4f8;
  font-size: 12px;
  margin-left: 16px;
}

.pdf-ocr-status {
  color: #ffd666;
  font-size: 12px;
  margin-left: 8px;
}

.pdf-page-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-bottom: 24px;
  width: 100%;
}

.pdf-canvas {
  display: block;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  background: #fff;
  cursor: crosshair;
  max-width: 100%;
}

.pdf-canvas--ready {
  background: #fff;
}

.pdf-page-container {
  position: relative;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  background: #3a3d40;
}

.pdf-select-rect {
  position: absolute;
  border: 2px solid #4da3ff;
  background: rgba(77, 163, 255, 0.18);
  pointer-events: none;
  z-index: 5;
  box-sizing: border-box;
}

.pdf-ocr-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.25);
  z-index: 6;
}
</style>
