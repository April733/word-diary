/**
 * PDF 懒加载渲染 + 文字层划词 / 框选 OCR 回退（与 EPUB 逻辑隔离）
 */
import * as pdfjsLib from 'pdfjs-dist'

// 使用 public 下的 .js，避免线上 Nginx 把 .mjs 当成 octet-stream
pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.js'

export { pdfjsLib }
/** pdf.js 4 文字层（透明选区） */
export const TextLayer = (pdfjsLib as any).TextLayer as {
  new (opts: {
    textContentSource: unknown
    container: HTMLElement
    viewport: unknown
  }): {
    render: () => Promise<unknown>
    cancel: () => void
  }
}

/** 与 TextLayerBuilder 对齐：防止绝对定位 span 之间选区「穿透」整页 */
const textLayerEndMap = new Map<HTMLElement, HTMLElement>()
let textLayerSelectionAbort: AbortController | null = null
let textLayerPrevRange: Range | null = null
let textLayerPointerDown = false

function ensureTextLayerSelectionListener() {
  if (textLayerSelectionAbort) return
  textLayerSelectionAbort = new AbortController()
  const { signal } = textLayerSelectionAbort

  const reset = (end: HTMLElement, textLayer: HTMLElement) => {
    if (!textLayer.contains(end)) textLayer.append(end)
    end.style.width = ''
    end.style.height = ''
    textLayer.classList.remove('selecting')
  }

  document.addEventListener(
    'pointerdown',
    () => {
      textLayerPointerDown = true
    },
    { signal }
  )
  document.addEventListener(
    'pointerup',
    () => {
      textLayerPointerDown = false
      textLayerEndMap.forEach(reset)
    },
    { signal }
  )
  window.addEventListener(
    'blur',
    () => {
      textLayerPointerDown = false
      textLayerEndMap.forEach(reset)
    },
    { signal }
  )
  document.addEventListener(
    'keyup',
    () => {
      if (!textLayerPointerDown) textLayerEndMap.forEach(reset)
    },
    { signal }
  )

  let isFirefox: boolean | undefined
  document.addEventListener(
    'selectionchange',
    () => {
      const selection = document.getSelection()
      if (!selection || selection.rangeCount === 0) {
        textLayerEndMap.forEach(reset)
        textLayerPrevRange = null
        return
      }
      const active = new Set<HTMLElement>()
      for (let i = 0; i < selection.rangeCount; i++) {
        const range = selection.getRangeAt(i)
        for (const div of textLayerEndMap.keys()) {
          if (!active.has(div) && range.intersectsNode(div)) active.add(div)
        }
      }
      for (const [div, end] of textLayerEndMap) {
        if (active.has(div)) div.classList.add('selecting')
        else reset(end, div)
      }

      const sampleEnd = textLayerEndMap.values().next().value as HTMLElement | undefined
      if (!sampleEnd) return
      isFirefox ??= getComputedStyle(sampleEnd).getPropertyValue('-moz-user-select') === 'none'
      if (isFirefox) return

      const range = selection.getRangeAt(0)
      const modifyStart =
        !!textLayerPrevRange &&
        (range.compareBoundaryPoints(Range.END_TO_END, textLayerPrevRange) === 0 ||
          range.compareBoundaryPoints(Range.START_TO_END, textLayerPrevRange) === 0)
      let anchor: Node | null = modifyStart ? range.startContainer : range.endContainer
      if (anchor && anchor.nodeType === Node.TEXT_NODE) anchor = anchor.parentNode
      const parentTextLayer = (anchor as Element | null)?.parentElement?.closest?.('.textLayer') as
        | HTMLElement
        | null
        | undefined
      const endDiv = parentTextLayer ? textLayerEndMap.get(parentTextLayer) : undefined
      if (endDiv && parentTextLayer && anchor?.parentElement) {
        endDiv.style.width = parentTextLayer.style.width
        endDiv.style.height = parentTextLayer.style.height
        anchor.parentElement.insertBefore(endDiv, modifyStart ? anchor : (anchor as ChildNode).nextSibling)
      }
      textLayerPrevRange = range.cloneRange()
    },
    { signal }
  )
}

/**
 * 在 TextLayer.render() 之后调用：追加 endOfContent 并绑定划词辅助（修复串选）。
 * 返回清理函数。
 */
export function bindTextLayerSelection(container: HTMLElement): () => void {
  container.querySelectorAll('.endOfContent').forEach((n) => n.remove())
  const end = document.createElement('div')
  end.className = 'endOfContent'
  container.append(end)

  const onMouseDown = () => {
    container.classList.add('selecting')
  }
  container.addEventListener('mousedown', onMouseDown)
  textLayerEndMap.set(container, end)
  ensureTextLayerSelectionListener()

  return () => {
    container.removeEventListener('mousedown', onMouseDown)
    textLayerEndMap.delete(container)
    end.remove()
    container.classList.remove('selecting')
    if (textLayerEndMap.size === 0 && textLayerSelectionAbort) {
      textLayerSelectionAbort.abort()
      textLayerSelectionAbort = null
      textLayerPrevRange = null
    }
  }
}

export type PdfProgressCfi = {
  page: number
  /** 页内垂直偏移比例 0~1 */
  offset: number
  /** 最近一次选中文本（可选） */
  text?: string
}

export function encodePdfCfi(data: PdfProgressCfi): string {
  try {
    return JSON.stringify({
      page: data.page,
      offset: Math.round(data.offset * 10000) / 10000,
      ...(data.text ? { text: data.text.slice(0, 800) } : {}),
    })
  } catch {
    return String(data.page)
  }
}

export function decodePdfCfi(raw: string | undefined | null): PdfProgressCfi | null {
  if (!raw) return null
  try {
    if (raw.startsWith('{')) {
      const obj = JSON.parse(raw)
      if (obj && typeof obj.page === 'number') {
        return {
          page: obj.page,
          offset: typeof obj.offset === 'number' ? obj.offset : 0,
          text: typeof obj.text === 'string' ? obj.text : undefined,
        }
      }
    }
    // 兼容旧格式：纯页码字符串 "5" 或 "5:0.3"
    if (raw.includes(':')) {
      const [p, o] = raw.split(':')
      return { page: parseInt(p, 10) || 1, offset: parseFloat(o) || 0 }
    }
    const page = parseInt(raw, 10)
    if (!isNaN(page) && page > 0) return { page, offset: 0 }
  } catch {
    /* ignore */
  }
  return null
}

/** 估算未渲染页的占位高度，避免滚动跳动 */
export function estimatePageHeight(containerWidth: number, scale: number, aspect = 1.414): number {
  const w = Math.max(200, containerWidth - 32)
  return Math.round(w * aspect * scale)
}

/**
 * 将 pdf.js getTextContent() 结果拼成按行纯文本（不依赖屏幕渲染）。
 * PDF 坐标系 y 轴向上，按从上到下、左到右归并行。
 */
export function textContentToPlain(textContent: { items?: unknown[] } | null | undefined): string {
  const rawItems = textContent?.items
  if (!Array.isArray(rawItems) || rawItems.length === 0) return ''

  type Row = { str: string; x: number; y: number; h: number }
  const rows: Row[] = []
  for (const item of rawItems) {
    if (!item || typeof item !== 'object') continue
    const it = item as { str?: string; transform?: number[] }
    if (typeof it.str !== 'string' || !it.str) continue
    const t = it.transform
    if (!Array.isArray(t) || t.length < 6) continue
    const h = Math.hypot(t[2] || 0, t[3] || 0) || 12
    rows.push({ str: it.str, x: t[4], y: t[5], h })
  }
  if (!rows.length) return ''

  rows.sort((a, b) => {
    const dy = b.y - a.y
    if (Math.abs(dy) > Math.min(a.h, b.h) * 0.55) return dy
    return a.x - b.x
  })

  const lines: string[] = []
  let cur = ''
  let lastY: number | null = null
  let lastH = 12
  let lastXEnd = 0
  for (const it of rows) {
    const newLine = lastY != null && Math.abs(lastY - it.y) > lastH * 0.55
    if (newLine) {
      if (cur.trim()) lines.push(cur.trim())
      cur = it.str
    } else {
      const gap = lastY == null ? 0 : it.x - lastXEnd
      if (cur && gap > lastH * 0.15 && !cur.endsWith(' ') && !it.str.startsWith(' ')) {
        cur += ' '
      }
      cur += it.str
    }
    lastY = it.y
    lastH = it.h
    lastXEnd = it.x + it.str.length * (it.h * 0.45)
  }
  if (cur.trim()) lines.push(cur.trim())
  return lines.join('\n').replace(/\n{3,}/g, '\n\n').trim()
}

/** 全文/多样本：文字层是否足够可信（扫描件常为空或乱码） */
export function isUsablePdfTextLayer(text: string): boolean {
  const t = (text || '').trim()
  if (t.length < 200) return false
  const letters = (t.match(/[A-Za-z\u4e00-\u9fff]/g) || []).length
  if (letters / t.length < 0.45) return false
  const words = t.split(/\s+/).filter(Boolean)
  if (words.length < 40) return false
  const avgLen = words.reduce((s, w) => s + w.replace(/[^A-Za-z]/g, '').length, 0) / words.length
  if (avgLen < 3.8) return false
  const stubEnds = (t.match(/\b[A-Za-z]{1,3}\./g) || []).length
  if (stubEnds > words.length * 0.08) return false
  const lower = t.toLowerCase()
  const hits = [' the ', ' and ', ' of ', ' to ', ' in '].filter((w) => lower.includes(w)).length
  return hits >= 3
}

/**
 * 单页文字层是否可用于划词（阈值低于全文检测，兼容短页与中文页）。
 * 劣质内嵌 OCR 残词（Th. / mad.）仍会判为不可用 → 回退框选 OCR。
 */
export function isUsablePdfTextLayerForPage(text: string): boolean {
  const t = (text || '').trim()
  if (t.length < 32) return false
  const letters = (t.match(/[A-Za-z\u4e00-\u9fff]/g) || []).length
  if (letters / t.length < 0.32) return false
  const cjk = (t.match(/[\u4e00-\u9fff]/g) || []).length
  if (cjk >= 16) return true
  const words = t.split(/\s+/).filter(Boolean)
  if (words.length < 6) return false
  const avgLen = words.reduce((s, w) => s + w.replace(/[^A-Za-z]/g, '').length, 0) / words.length
  if (avgLen < 2.2) return false
  const stubEnds = (t.match(/\b[A-Za-z]{1,3}\./g) || []).length
  if (stubEnds > words.length * 0.14) return false
  return true
}
