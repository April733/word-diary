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
