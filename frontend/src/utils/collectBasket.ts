export interface CollectEntry {
  basketKey: string
  entryType: 'word' | 'phrase'
  wordProto: string
  partSpeechMean: string
  usPhonetic?: string
  ukPhonetic?: string
  sentenceExample?: string
  rootAffix?: string
  sourceSnippet?: string
}

const STORAGE_PREFIX = 'wd-collect-basket-'

/** 一书一本：bookId + notebookId */
function storageKey(bookId: number, notebookId: number) {
  return `${STORAGE_PREFIX}${bookId}-nb-${notebookId}`
}

/** 旧版：整本书共用一个篮 */
function legacyStorageKey(bookId: number) {
  return STORAGE_PREFIX + bookId
}

export function loadBasket(bookId: number, notebookId: number): CollectEntry[] {
  try {
    const raw = sessionStorage.getItem(storageKey(bookId, notebookId))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveBasket(bookId: number, notebookId: number, items: CollectEntry[]) {
  const key = storageKey(bookId, notebookId)
  if (!items.length) {
    sessionStorage.removeItem(key)
    return
  }
  sessionStorage.setItem(key, JSON.stringify(items))
}

export function makeBasketKey(entry: { entryType?: string; wordProto: string }) {
  const type = entry.entryType === 'phrase' ? 'phrase' : 'word'
  return `${type}:${entry.wordProto.trim().toLowerCase()}`
}

export function mergeIntoBasket(
  bookId: number,
  notebookId: number,
  incoming: Omit<CollectEntry, 'basketKey'>[]
): CollectEntry[] {
  const existing = loadBasket(bookId, notebookId)
  const map = new Map(existing.map(item => [item.basketKey, item]))
  for (const item of incoming) {
    const key = makeBasketKey(item)
    if (!map.has(key)) {
      map.set(key, { ...item, basketKey: key })
    }
  }
  const merged = Array.from(map.values())
  saveBasket(bookId, notebookId, merged)
  return merged
}

export function clearBasket(bookId: number, notebookId: number) {
  sessionStorage.removeItem(storageKey(bookId, notebookId))
}

export function basketCount(bookId: number, notebookId: number): number {
  return loadBasket(bookId, notebookId).length
}

/** 各关联单词本的待收录数量 */
export function loadBasketCounts(bookId: number, notebookIds: number[]): Record<number, number> {
  const counts: Record<number, number> = {}
  for (const id of notebookIds) {
    counts[id] = basketCount(bookId, id)
  }
  return counts
}

const ACTIVE_NB_PREFIX = 'wd-reader-active-nb-'

function activeNotebookStorageKey(bookId: number) {
  return ACTIVE_NB_PREFIX + bookId
}

/** 本书当前单词本（顶栏展示 / 默认收录目标） */
export function loadActiveNotebookId(bookId: number): number | null {
  try {
    const raw = localStorage.getItem(activeNotebookStorageKey(bookId))
    if (!raw) return null
    const id = Number(raw)
    return Number.isFinite(id) && id > 0 ? id : null
  } catch {
    return null
  }
}

export function saveActiveNotebookId(bookId: number, notebookId: number) {
  localStorage.setItem(activeNotebookStorageKey(bookId), String(notebookId))
}

/** 在关联列表变化后校正当前本：优先保留原选择，否则取第一本 */
export function resolveActiveNotebookId(
  bookId: number,
  notebookIds: number[],
  preferred?: number | null
): number | undefined {
  if (!notebookIds.length) return undefined
  const candidates = [preferred, loadActiveNotebookId(bookId)].filter(
    (id): id is number => id != null && notebookIds.includes(id)
  )
  const chosen = candidates[0] ?? notebookIds[0]
  saveActiveNotebookId(bookId, chosen)
  return chosen
}

/**
 * 若仍有旧版整书篮，且当前只关联了一个单词本，则迁入该本并删除旧键。
 */
export function migrateLegacyBasketIfNeeded(bookId: number, notebookIds: number[]) {
  if (notebookIds.length !== 1) return
  const legacyRaw = sessionStorage.getItem(legacyStorageKey(bookId))
  if (!legacyRaw) return
  try {
    const parsed = JSON.parse(legacyRaw)
    if (!Array.isArray(parsed) || parsed.length === 0) {
      sessionStorage.removeItem(legacyStorageKey(bookId))
      return
    }
    const notebookId = notebookIds[0]
    const existing = loadBasket(bookId, notebookId)
    if (existing.length === 0) {
      saveBasket(bookId, notebookId, parsed)
    } else {
      mergeIntoBasket(bookId, notebookId, parsed)
    }
    sessionStorage.removeItem(legacyStorageKey(bookId))
  } catch {
    sessionStorage.removeItem(legacyStorageKey(bookId))
  }
}

export const COLLECT_JSON_MARKER = '---COLLECT_JSON---'

const COLLOCATION_HEADING =
  /^(?:#{0,6}\s*)?(?:\*\*)?(固定搭配|固搭|短语(?:用法)?|常用搭配|词组|collocations?|fixed\s+phrases?|phrasal\s+verbs?)(?:\*\*)?\s*[:：]?\s*$/i

const OTHER_HEADING = /^#{1,6}\s+\S/

/** 避免嵌套量词 ReDoS：先宽抓，再校验英文短语形态 */
const COLLOCATION_BULLET =
  /^\s*(?:[-*•]|\d+[.)])\s*(?:\*\*)?([^:*：—–\n]{1,120}?)(?:\*\*)?\s*[:：—–\-]\s*(.{1,300})$/

const MAX_PROSE_LINE_LEN = 400

/** 去掉收录标记及其后的 JSON；流式拆包时也去掉末尾不完整前缀 */
export function stripCollectMarker(text: string): string {
  if (!text) return ''
  const idx = text.indexOf(COLLECT_JSON_MARKER)
  if (idx >= 0) return text.substring(0, idx).trimEnd()

  // 完整标记尚未到齐时，模型可能已吐出 ---COLLECT_JSON 等前缀
  const soft = text.indexOf('---COLLECT_JSON')
  if (soft >= 0) return text.substring(0, soft).trimEnd()

  for (let len = Math.min(COLLECT_JSON_MARKER.length - 1, text.length); len >= 3; len--) {
    if (text.endsWith(COLLECT_JSON_MARKER.slice(0, len))) {
      return text.slice(0, -len).trimEnd()
    }
  }
  return text
}

function looksLikeEnglishPhrase(proto: string): boolean {
  const p = proto.trim()
  if (!/^[a-z][a-z0-9'’./…\-]*(?:\s+(?:\.\.\.|…|[a-z][a-z0-9'’./…\-]*))*$/i.test(p)) return false
  if (p.includes(' ') || p.includes('...') || p.includes('…')) return true
  return /^(?:[a-z]+(?:-[a-z]+)+)$/i.test(p)
}

function cleanProto(raw: string): string {
  return raw.replace(/\*+/g, '').replace(/\u00A0/g, ' ').trim().replace(/\s+/g, ' ')
}

/** 从讲解正文「固定搭配」等小节提取短语 */
export function extractPhrasesFromProse(displayText: string): Omit<CollectEntry, 'basketKey'>[] {
  if (!displayText?.trim()) return []
  const out: Omit<CollectEntry, 'basketKey'>[] = []
  let inSection = false
  for (const rawLine of displayText.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.length > MAX_PROSE_LINE_LEN) continue
    if (COLLOCATION_HEADING.test(line)) {
      inSection = true
      continue
    }
    if (inSection && OTHER_HEADING.test(line) && !COLLOCATION_HEADING.test(line)) {
      inSection = false
      continue
    }
    if (!inSection) continue
    const m = line.match(COLLOCATION_BULLET)
    if (!m) continue
    const wordProto = cleanProto(m[1])
    let partSpeechMean = m[2].replace(/\*+/g, '').trim()
    if (!wordProto || !partSpeechMean || !looksLikeEnglishPhrase(wordProto)) continue
    if (!partSpeechMean.startsWith('短语') && !/^phrase/i.test(partSpeechMean)) {
      partSpeechMean = `短语. ${partSpeechMean}`
    }
    out.push({
      entryType: 'phrase',
      wordProto,
      partSpeechMean
    })
  }
  return out
}

function normalizeRawEntry(e: any, sourceSnippet = ''): CollectEntry | null {
  if (!e?.wordProto || !e?.partSpeechMean) return null
  const wordProto = cleanProto(String(e.wordProto))
  if (!wordProto) return null
  let entryType: 'word' | 'phrase' = e.entryType === 'phrase' ? 'phrase' : 'word'
  if (looksLikeEnglishPhrase(wordProto)) entryType = 'phrase'
  return {
    basketKey: makeBasketKey({ entryType, wordProto }),
    entryType,
    wordProto,
    partSpeechMean: String(e.partSpeechMean).trim(),
    usPhonetic: e.usPhonetic,
    ukPhonetic: e.ukPhonetic,
    sentenceExample: sourceSnippet || undefined,
    rootAffix: e.rootAffix,
    sourceSnippet: sourceSnippet || undefined
  }
}

/** 从入库的完整 AI 回复中解析可收录词条（含固定搭配正文兜底） */
export function parseCollectEntriesFromText(text: string, sourceSnippet = ''): CollectEntry[] {
  if (!text) return []
  const map = new Map<string, CollectEntry>()

  if (text.includes(COLLECT_JSON_MARKER)) {
    const jsonPart = text.substring(text.indexOf(COLLECT_JSON_MARKER) + COLLECT_JSON_MARKER.length).trim()
    const start = jsonPart.indexOf('{')
    const end = jsonPart.lastIndexOf('}')
    if (start >= 0 && end > start) {
      try {
        const root = JSON.parse(jsonPart.substring(start, end + 1))
        const entries = Array.isArray(root?.entries) ? root.entries : []
        for (const e of entries) {
          const normalized = normalizeRawEntry(e, sourceSnippet)
          if (normalized) map.set(normalized.basketKey, normalized)
        }
      } catch {
        /* ignore */
      }
    }
  }

  for (const phrase of extractPhrasesFromProse(stripCollectMarker(text))) {
    const normalized = normalizeRawEntry(phrase, sourceSnippet)
    if (normalized && !map.has(normalized.basketKey)) {
      map.set(normalized.basketKey, normalized)
    }
  }

  return [...map.values()]
}

/** 历史消息 → 展示正文 + 可选收录条目 */
export function hydrateStoredAiMessage(role: string, content: string, sourceSnippet = '') {
  const raw = content || ''
  try {
    const entries = role === 'assistant' ? parseCollectEntriesFromText(raw, sourceSnippet) : []
    return {
      role,
      content: stripCollectMarker(raw),
      collectEntries: entries.length > 0 ? entries : undefined,
      collectSourceSnippet: entries.length > 0 ? sourceSnippet : undefined
    }
  } catch (e) {
    console.warn('[collect] hydrateStoredAiMessage failed', e)
    return {
      role,
      content: stripCollectMarker(raw)
    }
  }
}
