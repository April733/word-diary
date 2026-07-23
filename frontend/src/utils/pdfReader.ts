/**
 * PDF 懒加载渲染 + 框选 OCR 相关工具（与 EPUB 逻辑隔离）
 */
import * as pdfjsLib from 'pdfjs-dist'

// 使用 public 下的 .js，避免线上 Nginx 把 .mjs 当成 octet-stream
pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.js'

export { pdfjsLib }

export type PdfProgressCfi = {
  page: number
  /** 页内垂直偏移比例 0~1 */
  offset: number
  /** 最近一次 OCR 选中文本（可选） */
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
  if (!Array.isArray(rawItems) || !rawItems.length) return ''

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

/** 文字层是否足够可信（扫描件常为空或乱码；劣质内嵌 OCR 常出现 Th. / mad. 残词） */
export function isUsablePdfTextLayer(text: string): boolean {
  const t = (text || '').trim()
  if (t.length < 200) return false
  const letters = (t.match(/[A-Za-z\u4e00-\u9fff]/g) || []).length
  if (letters / t.length < 0.45) return false
  const words = t.split(/\s+/).filter(Boolean)
  if (words.length < 40) return false
  const avgLen = words.reduce((s, w) => s + w.replace(/[^A-Za-z]/g, '').length, 0) / words.length
  if (avgLen < 3.8) return false
  // 残片句：很短且以句号结尾（Th. / of th.）
  const stubEnds = (t.match(/\b[A-Za-z]{1,3}\./g) || []).length
  if (stubEnds > words.length * 0.08) return false
  // 至少像英文散文
  const lower = t.toLowerCase()
  const hits = [' the ', ' and ', ' of ', ' to ', ' in '].filter((w) => lower.includes(w)).length
  return hits >= 3
}
