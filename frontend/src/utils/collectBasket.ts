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

export function stripCollectMarker(text: string): string {
  const idx = text.indexOf(COLLECT_JSON_MARKER)
  return idx >= 0 ? text.substring(0, idx).trim() : text
}

/** 从入库的完整 AI 回复中解析可收录词条（历史会话恢复用） */
export function parseCollectEntriesFromText(text: string, sourceSnippet = ''): CollectEntry[] {
  if (!text || !text.includes(COLLECT_JSON_MARKER)) return []
  const jsonPart = text.substring(text.indexOf(COLLECT_JSON_MARKER) + COLLECT_JSON_MARKER.length).trim()
  const start = jsonPart.indexOf('{')
  const end = jsonPart.lastIndexOf('}')
  if (start < 0 || end <= start) return []
  try {
    const root = JSON.parse(jsonPart.substring(start, end + 1))
    const entries = Array.isArray(root?.entries) ? root.entries : []
    return entries
      .filter((e: any) => e?.wordProto && e?.partSpeechMean)
      .map((e: any) => ({
        basketKey: makeBasketKey(e),
        entryType: e.entryType === 'phrase' ? 'phrase' as const : 'word' as const,
        wordProto: String(e.wordProto).trim(),
        partSpeechMean: String(e.partSpeechMean).trim(),
        usPhonetic: e.usPhonetic,
        ukPhonetic: e.ukPhonetic,
        sentenceExample: e.sentenceExample || sourceSnippet || undefined,
        rootAffix: e.rootAffix,
        sourceSnippet: sourceSnippet || undefined
      }))
  } catch {
    return []
  }
}

/** 历史消息 → 展示正文 + 可选收录条目 */
export function hydrateStoredAiMessage(role: string, content: string, sourceSnippet = '') {
  const raw = content || ''
  const entries = role === 'assistant' ? parseCollectEntriesFromText(raw, sourceSnippet) : []
  return {
    role,
    content: stripCollectMarker(raw),
    collectEntries: entries.length > 0 ? entries : undefined,
    collectSourceSnippet: entries.length > 0 ? sourceSnippet : undefined
  }
}
