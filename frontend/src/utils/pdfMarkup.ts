/** PDF 批注：归一化坐标 (0~1) 与绘制工具 */

export type MarkupType = 'pen' | 'rect' | 'text'
/** 可切换的批注工具；rect 仅保留历史数据渲染，不再作为可选工具 */
export type AnnotateMode = 'none' | 'pen' | 'text' | 'eraser'

export interface PdfMarkupDto {
  id: number
  bookId: number
  userId: number
  pageNum: number
  markupType: MarkupType
  geometryJson: string
  color: string
  noteText?: string | null
}

export interface PenGeometry {
  points: Array<[number, number]>
  strokeWidth?: number
}

export interface RectGeometry {
  x: number
  y: number
  w: number
  h: number
  strokeWidth?: number
}

export interface TextGeometry {
  x: number
  y: number
}

export const DEFAULT_MARKUP_COLOR = '#E53935'
export const DEFAULT_STROKE = 0.0025
/** 与输入框 `.pdf-inline-note` 一致，确认后也保持小字号 */
export const TEXT_NOTE_FONT_SIZE = 14
export const TEXT_NOTE_LINE_HEIGHT = 1.35
export const TEXT_NOTE_PAD = 4
export const TEXT_NOTE_MAX_WIDTH_RATIO = 0.42

export function parseGeometry<T>(json: string): T | null {
  try {
    return JSON.parse(json) as T
  } catch {
    return null
  }
}

export function clamp01(n: number): number {
  return Math.min(1, Math.max(0, n))
}

export function toNorm(localX: number, localY: number, width: number, height: number): [number, number] {
  return [clamp01(localX / Math.max(width, 1)), clamp01(localY / Math.max(height, 1))]
}

export function fromNorm(nx: number, ny: number, width: number, height: number): [number, number] {
  return [nx * width, ny * height]
}

export function drawMarkup(
  ctx: CanvasRenderingContext2D,
  markup: PdfMarkupDto,
  width: number,
  height: number
) {
  const color = markup.color || DEFAULT_MARKUP_COLOR
  ctx.save()
  ctx.strokeStyle = color
  ctx.fillStyle = color
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'

  if (markup.markupType === 'pen') {
    const g = parseGeometry<PenGeometry>(markup.geometryJson)
    if (!g?.points?.length) {
      ctx.restore()
      return
    }
    const sw = (g.strokeWidth ?? DEFAULT_STROKE) * width
    ctx.lineWidth = Math.max(1, sw)
    ctx.beginPath()
    g.points.forEach(([nx, ny], i) => {
      const [x, y] = fromNorm(nx, ny, width, height)
      if (i === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    })
    ctx.stroke()
  } else if (markup.markupType === 'rect') {
    const g = parseGeometry<RectGeometry>(markup.geometryJson)
    if (!g) {
      ctx.restore()
      return
    }
    const sw = (g.strokeWidth ?? DEFAULT_STROKE) * width
    ctx.lineWidth = Math.max(1, sw)
    const [x, y] = fromNorm(g.x, g.y, width, height)
    ctx.strokeRect(x, y, g.w * width, g.h * height)
  } else if (markup.markupType === 'text') {
    const g = parseGeometry<TextGeometry>(markup.geometryJson)
    if (!g) {
      ctx.restore()
      return
    }
    const [x, y] = fromNorm(g.x, g.y, width, height)
    const fontSize = TEXT_NOTE_FONT_SIZE
    const pad = TEXT_NOTE_PAD
    ctx.font = `${fontSize}px sans-serif`
    ctx.textBaseline = 'top'
    const text = markup.noteText || ''
    const maxTw = width * TEXT_NOTE_MAX_WIDTH_RATIO
    const lines = wrapText(ctx, text, maxTw - pad * 2)
    const lineH = fontSize * TEXT_NOTE_LINE_HEIGHT
    const widths = lines.map((line) => ctx.measureText(line).width)
    const contentW = Math.max(fontSize * 2, ...(widths.length ? widths : [0]))
    const tw = Math.min(maxTw, contentW + pad * 2)
    const th = lines.length * lineH + pad * 2
    // 仅绘制红字，避免白底造成“白边”观感
    ctx.fillStyle = color
    lines.forEach((line, i) => {
      ctx.fillText(line, x + pad, y + pad + i * lineH)
    })
  }
  ctx.restore()
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  if (!text) return ['笔记']
  const lines: string[] = []
  let cur = ''
  for (const ch of text) {
    const test = cur + ch
    if (ctx.measureText(test).width > maxWidth && cur) {
      lines.push(cur)
      cur = ch
      if (lines.length >= 6) break
    } else {
      cur = test
    }
  }
  if (cur && lines.length < 6) lines.push(cur)
  return lines.length ? lines : ['笔记']
}

/** 估算文本批注框的归一化宽高（与绘制字号一致） */
export function estimateTextBoxNorm(
  text: string,
  pageWidth: number,
  pageHeight: number
): { w: number; h: number } {
  const fontSize = TEXT_NOTE_FONT_SIZE
  const pad = TEXT_NOTE_PAD
  const maxTwPx = pageWidth * TEXT_NOTE_MAX_WIDTH_RATIO
  const avgChar = fontSize * 0.55
  const maxCharsPerLine = Math.max(1, Math.floor((maxTwPx - pad * 2) / avgChar))
  const raw = text || '笔记'
  const lineCount = Math.min(6, Math.max(1, Math.ceil(raw.length / maxCharsPerLine)))
  const charsOnLongest = Math.min(raw.length, maxCharsPerLine)
  const contentW = Math.max(fontSize * 2, charsOnLongest * avgChar)
  const twPx = Math.min(maxTwPx, contentW + pad * 2)
  const thPx = lineCount * fontSize * TEXT_NOTE_LINE_HEIGHT + pad * 2
  return {
    w: twPx / Math.max(pageWidth, 1),
    h: thPx / Math.max(pageHeight, 1),
  }
}

/** 橡皮擦 / 拖动命中检测：返回命中的批注 id，未命中则 null */
export function hitTestMarkup(
  markups: PdfMarkupDto[],
  nx: number,
  ny: number,
  pageWidth: number,
  pageHeight = pageWidth
): number | null {
  const threshold = Math.max(0.012, DEFAULT_STROKE * 3)
  // 后画的优先（列表末尾）
  for (let i = markups.length - 1; i >= 0; i--) {
    const m = markups[i]
    if (m.markupType === 'pen') {
      const g = parseGeometry<PenGeometry>(m.geometryJson)
      if (!g?.points) continue
      for (const [px, py] of g.points) {
        const dx = px - nx
        const dy = py - ny
        if (dx * dx + dy * dy <= threshold * threshold) return m.id
      }
    } else if (m.markupType === 'rect') {
      const g = parseGeometry<RectGeometry>(m.geometryJson)
      if (!g) continue
      const pad = threshold
      if (nx >= g.x - pad && nx <= g.x + g.w + pad && ny >= g.y - pad && ny <= g.y + g.h + pad) {
        // 优先点在边框附近或框内
        return m.id
      }
    } else if (m.markupType === 'text') {
      const g = parseGeometry<TextGeometry>(m.geometryJson)
      if (!g) continue
      const { w: boxW, h: boxH } = estimateTextBoxNorm(m.noteText || '', pageWidth, pageHeight)
      const pad = threshold
      if (
        nx >= g.x - pad &&
        nx <= g.x + boxW + pad &&
        ny >= g.y - pad &&
        ny <= g.y + boxH + pad
      ) {
        return m.id
      }
    }
  }
  return null
}
