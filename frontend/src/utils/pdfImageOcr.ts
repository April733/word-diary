import { createWorker, type Worker } from 'tesseract.js'

/** 轻度锐化卷积核（提升模糊扫描件 OCR 准确率，计算量可控） */
const SHARPEN_KERNEL = [
  0, -0.5, 0,
  -0.5, 3, -0.5,
  0, -0.5, 0,
]

/** 传给 worker 的图像过大会触发 RangeError（Too many properties to enumerate） */
const OCR_MAX_SIDE = 1600
const OCR_MAX_PIXELS = 2_200_000

/** 只要文本，避免 blocks/hocr/tsv 巨型回传 */
const TEXT_ONLY_OUTPUT = {
  text: true,
  blocks: false,
  layoutBlocks: false,
  hocr: false,
  tsv: false,
  box: false,
  unlv: false,
  osd: false,
  pdf: false,
  imageColor: false,
  imageGrey: false,
  imageBinary: false,
  debug: false,
} as const

const WORKER_OPTS = {
  workerPath: '/tesseract/worker.min.js',
  // 目录路径：让 tesseract 按设备自动选 simd / lstm 变体
  corePath: '/tesseract',
  langPath: '/tesseract/lang-data',
  gzip: false,
  logger: () => {},
}

let ocrWorker: Worker | null = null
let ocrReady: Promise<Worker> | null = null
let engOcrWorker: Worker | null = null
let engOcrReady: Promise<Worker> | null = null

/** 串行化 OCR，避免同一 worker 并发 recognize */
let ocrChain: Promise<unknown> = Promise.resolve()

function enqueueOcr<T>(task: () => Promise<T>): Promise<T> {
  const run = ocrChain.then(task, task)
  ocrChain = run.then(
    () => undefined,
    () => undefined
  )
  return run
}

function applyMildSharpen(imageData: ImageData): ImageData {
  const { width, height, data } = imageData
  const src = new Uint8ClampedArray(data)
  const out = new ImageData(width, height)
  const dst = out.data
  const k = SHARPEN_KERNEL

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4
      if (x === 0 || y === 0 || x === width - 1 || y === height - 1) {
        dst[i] = src[i]
        dst[i + 1] = src[i + 1]
        dst[i + 2] = src[i + 2]
        dst[i + 3] = src[i + 3]
        continue
      }
      let r = 0
      let g = 0
      let b = 0
      let ki = 0
      for (let ky = -1; ky <= 1; ky++) {
        for (let kx = -1; kx <= 1; kx++) {
          const si = ((y + ky) * width + (x + kx)) * 4
          const w = k[ki++]
          r += src[si] * w
          g += src[si + 1] * w
          b += src[si + 2] * w
        }
      }
      dst[i] = Math.min(255, Math.max(0, r))
      dst[i + 1] = Math.min(255, Math.max(0, g))
      dst[i + 2] = Math.min(255, Math.max(0, b))
      dst[i + 3] = 255
    }
  }
  return out
}

/**
 * 从页面 canvas 裁剪局部区域并轻度锐化，返回可用于 OCR 的 canvas。
 * 坐标为相对 canvas 显示尺寸的 CSS 像素。
 */
export function cropAndSharpenRegion(
  sourceCanvas: HTMLCanvasElement,
  rect: { x: number; y: number; w: number; h: number }
): HTMLCanvasElement {
  const cssW = sourceCanvas.clientWidth || sourceCanvas.width
  const cssH = sourceCanvas.clientHeight || sourceCanvas.height
  if (!cssW || !cssH || !sourceCanvas.width || !sourceCanvas.height) {
    throw new Error('PAGE_NOT_READY')
  }

  const scaleX = sourceCanvas.width / cssW
  const scaleY = sourceCanvas.height / cssH

  const sx = Math.max(0, Math.floor(rect.x * scaleX))
  const sy = Math.max(0, Math.floor(rect.y * scaleY))
  const sw = Math.max(1, Math.min(sourceCanvas.width - sx, Math.ceil(rect.w * scaleX)))
  const sh = Math.max(1, Math.min(sourceCanvas.height - sy, Math.ceil(rect.h * scaleY)))

  const tmp = document.createElement('canvas')
  tmp.width = sw
  tmp.height = sh
  const ctx = tmp.getContext('2d')!
  ctx.drawImage(sourceCanvas, sx, sy, sw, sh, 0, 0, sw, sh)

  // 过大区域跳过锐化，避免主线程卡顿
  if (sw * sh <= 2_500_000) {
    const imageData = ctx.getImageData(0, 0, sw, sh)
    const sharpened = applyMildSharpen(imageData)
    ctx.putImageData(sharpened, 0, 0)
  }
  return tmp
}

/** 纯净化 OCR 文本：合并空白、去掉多余换行噪声 */
export function cleanOcrText(raw: string): string {
  return raw
    .replace(/\u00ad/g, '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/[ \t]{2,}/g, ' ')
    .replace(/^\s+|\s+$/g, '')
    .trim()
}

/** 缩小画布，避免 postMessage 大图触发 RangeError */
function prepareCanvasForOcr(source: HTMLCanvasElement): HTMLCanvasElement {
  const w = source.width
  const h = source.height
  if (!w || !h) return source

  const sideScale = OCR_MAX_SIDE / Math.max(w, h)
  const pixelScale = Math.sqrt(OCR_MAX_PIXELS / (w * h))
  const scale = Math.min(1, sideScale, pixelScale)
  if (scale >= 0.999) return source

  const out = document.createElement('canvas')
  out.width = Math.max(1, Math.round(w * scale))
  out.height = Math.max(1, Math.round(h * scale))
  const ctx = out.getContext('2d')
  if (!ctx) return source
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(source, 0, 0, out.width, out.height)
  return out
}

async function getOcrWorker(): Promise<Worker> {
  if (ocrWorker) return ocrWorker
  if (ocrReady) return ocrReady

  ocrReady = (async () => {
    const worker = await createWorker('chi_sim+eng', 1, WORKER_OPTS)
    await worker.setParameters({
      tessedit_pageseg_mode: '6' as any,
      preserve_interword_spaces: '1',
    })
    ocrWorker = worker
    return worker
  })()

  try {
    return await ocrReady
  } catch (e) {
    ocrReady = null
    throw e
  }
}

async function getEngOcrWorker(): Promise<Worker> {
  if (engOcrWorker) return engOcrWorker
  if (engOcrReady) return engOcrReady
  engOcrReady = (async () => {
    const worker = await createWorker('eng', 1, WORKER_OPTS)
    await worker.setParameters({
      tessedit_pageseg_mode: '4' as any,
      preserve_interword_spaces: '1',
    })
    engOcrWorker = worker
    return worker
  })()
  try {
    return await engOcrReady
  } catch (e) {
    engOcrReady = null
    throw e
  }
}

async function recognizePrepared(
  image: HTMLCanvasElement,
  lang: 'eng' | 'chi_sim+eng'
): Promise<string> {
  const prepared = prepareCanvasForOcr(image)
  const worker = lang === 'eng' ? await getEngOcrWorker() : await getOcrWorker()
  try {
    const { data } = await worker.recognize(prepared, {}, TEXT_ONLY_OUTPUT as any)
    return cleanOcrText(data.text || '')
  } catch (e) {
    // worker 可能已损坏，下次重建
    await terminateOcrWorker().catch(() => {})
    throw e
  }
}

/**
 * 对裁剪后的局部图像做离线 OCR（仅手动触发时调用）。
 * 默认 eng：雅思等英文页用中英混排模型会严重串扰（乱码）。
 */
export async function recognizeRegion(
  image: HTMLCanvasElement,
  options?: { lang?: 'eng' | 'chi_sim+eng' }
): Promise<string> {
  const lang = options?.lang || 'eng'
  return enqueueOcr(() => recognizePrepared(image, lang))
}

export async function terminateOcrWorker(): Promise<void> {
  const jobs: Promise<unknown>[] = []
  if (ocrWorker) {
    jobs.push(ocrWorker.terminate().catch(() => {}))
    ocrWorker = null
    ocrReady = null
  }
  if (engOcrWorker) {
    jobs.push(engOcrWorker.terminate().catch(() => {}))
    engOcrWorker = null
    engOcrReady = null
  }
  await Promise.all(jobs)
}
