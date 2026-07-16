import { createWorker, type Worker } from 'tesseract.js'

/** 轻度锐化卷积核（提升模糊扫描件 OCR 准确率，计算量可控） */
const SHARPEN_KERNEL = [
  0, -0.5, 0,
  -0.5, 3, -0.5,
  0, -0.5, 0,
]

let ocrWorker: Worker | null = null
let ocrReady: Promise<Worker> | null = null

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
 * 从页面 canvas 裁剪局部区域并轻度锐化，返回可用于 OCR 的 ImageBitmap/canvas。
 * 坐标为相对 canvas 显示尺寸的 CSS 像素。
 */
export function cropAndSharpenRegion(
  sourceCanvas: HTMLCanvasElement,
  rect: { x: number; y: number; w: number; h: number }
): HTMLCanvasElement {
  const scaleX = sourceCanvas.width / sourceCanvas.clientWidth
  const scaleY = sourceCanvas.height / sourceCanvas.clientHeight

  const sx = Math.max(0, Math.floor(rect.x * scaleX))
  const sy = Math.max(0, Math.floor(rect.y * scaleY))
  const sw = Math.max(1, Math.min(sourceCanvas.width - sx, Math.ceil(rect.w * scaleX)))
  const sh = Math.max(1, Math.min(sourceCanvas.height - sy, Math.ceil(rect.h * scaleY)))

  const tmp = document.createElement('canvas')
  tmp.width = sw
  tmp.height = sh
  const ctx = tmp.getContext('2d')!
  ctx.drawImage(sourceCanvas, sx, sy, sw, sh, 0, 0, sw, sh)

  const imageData = ctx.getImageData(0, 0, sw, sh)
  const sharpened = applyMildSharpen(imageData)
  ctx.putImageData(sharpened, 0, 0)
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

async function getOcrWorker(): Promise<Worker> {
  if (ocrWorker) return ocrWorker
  if (ocrReady) return ocrReady

  ocrReady = (async () => {
    const worker = await createWorker('chi_sim+eng', 1, {
      workerPath: '/tesseract/worker.min.js',
      corePath: '/tesseract/tesseract-core.wasm.js',
      langPath: '/tesseract/lang-data',
      gzip: false,
      logger: () => {},
    })
    // 学习场景：中英混排 + 尽量保留音标符号
    await worker.setParameters({
      tessedit_pageseg_mode: '6' as any, // 假定为统一文本块
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

/** 对裁剪后的局部图像做离线 OCR（仅手动触发时调用） */
export async function recognizeRegion(image: HTMLCanvasElement): Promise<string> {
  const worker = await getOcrWorker()
  const { data } = await worker.recognize(image)
  return cleanOcrText(data.text || '')
}

export async function terminateOcrWorker(): Promise<void> {
  if (ocrWorker) {
    await ocrWorker.terminate()
    ocrWorker = null
    ocrReady = null
  }
}
