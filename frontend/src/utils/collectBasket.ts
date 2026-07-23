
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

/** 允许「固定搭配：」/ **固定搭配** / 固定搭配： 等版式（模型常加直角引号） */
const HEADING_WRAP = String.raw`(?:#{0,6}\s*)?[「"“]?(?:\*\*)?`
const HEADING_WRAP_END = String.raw`(?:\*\*)?\s*[:：]?[」"”]?\s*`

const COLLOCATION_HEADING = new RegExp(
  `^${HEADING_WRAP}(固定搭配|固搭|短语(?:用法|讲解|解析)?|常用搭配|词组|分解解释|搭配讲解|collocations?|fixed\\s+phrases?|phrasal\\s+verbs?)${HEADING_WRAP_END}$`,
  'i'
)

const WORD_HEADING = new RegExp(
  `^${HEADING_WRAP}(单词|单词讲解|词汇|words?|vocabulary)${HEADING_WRAP_END}$`,
  'i'
)

const ANY_SECTION_HEADING = new RegExp(
  `^${HEADING_WRAP}(翻译|释义|整体释义|解析|单词|单词讲解|词汇|固搭|固定搭配|语法|语法结构|句式|词根词缀|例句|分解解释|短语讲解|短语解析|搭配讲解|words?|vocabulary|collocations?|fixed\\s+phrases?|phrasal\\s+verbs?|grammar|translation|analysis|breakdown)${HEADING_WRAP_END}$`,
  'i'
)

const OTHER_HEADING = /^#{1,6}\s+\S/

/** 列表项：- **lemma**: meaning */
const LEMMA_BULLET =
  /^\s*(?:[-*•]|\d+[.)])\s*(?:\*\*)?([^:*：—–\n]{1,120}?)(?:\*\*)?\s*[:：—–\-]\s*(.{1,300})$/

/**
 * 无列表符的讲解行（模型常写）：get involved in：参与…
 * 英文词/短语 + 中文释义
 */
const LEMMA_PLAIN =
  /^(?:\*\*)?([a-z][a-z0-9'’./…\-]*(?:\s+(?:\.\.\.|…|[a-z][a-z0-9'’./…\-]*)){0,8})(?:\*\*)?\s*[:：—–\-]\s*(.{1,300})$/i

/**
 * 无冒号版式：- **keep records of** 做…的记录
 * （版式提示只要求加粗时模型常省略冒号）
 */
const LEMMA_SPACE_CJK =
  /^\s*(?:[-*•]|\d+[.)])?\s*(?:\*\*)?([a-z][a-z0-9'’./…\-]*(?:\s+(?:\.\.\.|…|[a-z][a-z0-9'’./…\-]*)){0,8})(?:\*\*)?\s+([\u4e00-\u9fff].{0,300})$/i

/** 「你问的短语 X 意思是：Y」整句兜底 */
const ASKED_PHRASE =
  /(?:短语|搭配|词组|表达|说法)\s+([a-z][a-z0-9'’.\-\s/…]{1,80}?)\s*(?:意思是|意为|指的是|意思为)\s*[：:「"']?\s*[「"']?([^」"'\n。；;]+)[」"']?/i

const WORD_PHONETIC_MEANING =
  /^(?:美|US)\s*(\/[^/\n]{1,40}\/)\s*[,，]?\s*(?:英|UK)\s*(\/[^/\n]{1,40}\/)\s*[;；]?\s*(.*)$/i

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

function looksLikeEnglishWord(proto: string): boolean {
  const p = proto.trim()
  if (!p || looksLikeEnglishPhrase(p)) return false
  return /^[a-z][a-z0-9'’]*$/i.test(p)
}

function hasCjkMeaning(s: string): boolean {
  return /[\u4e00-\u9fff]/.test(s)
}

function cleanProto(raw: string): string {
  return raw.replace(/\*+/g, '').replace(/\u00A0/g, ' ').trim().replace(/\s+/g, ' ')
}

function matchLemmaLine(line: string): { proto: string; meaning: string } | null {
  const bullet = line.match(LEMMA_BULLET)
  if (bullet) {
    return { proto: cleanProto(bullet[1]), meaning: bullet[2].replace(/\*+/g, '').trim() }
  }
  const plain = line.match(LEMMA_PLAIN)
  if (plain) {
    return { proto: cleanProto(plain[1]), meaning: plain[2].replace(/\*+/g, '').trim() }
  }
  const spaceCjk = line.match(LEMMA_SPACE_CJK)
  if (spaceCjk) {
    return { proto: cleanProto(spaceCjk[1]), meaning: spaceCjk[2].replace(/\*+/g, '').trim() }
  }
  return null
}

function isSectionExit(line: string, inWordSection: boolean): boolean {
  if (OTHER_HEADING.test(line)) {
    return inWordSection ? !WORD_HEADING.test(line) : !COLLOCATION_HEADING.test(line)
  }
  if (!ANY_SECTION_HEADING.test(line)) return false
  return inWordSection ? !WORD_HEADING.test(line) : !COLLOCATION_HEADING.test(line)
}

/** 从讲解正文「单词」等小节提取单词 */
export function extractWordsFromProse(displayText: string): Omit<CollectEntry, 'basketKey'>[] {
  if (!displayText?.trim()) return []
  const out: Omit<CollectEntry, 'basketKey'>[] = []
  let inSection = false
  for (const rawLine of displayText.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.length > MAX_PROSE_LINE_LEN) continue
    if (WORD_HEADING.test(line)) {
      inSection = true
      continue
    }
    if (inSection && isSectionExit(line, true)) {
      inSection = false
      continue
    }
    if (!inSection) continue
    const hit = matchLemmaLine(line)
    if (!hit) continue
    const wordProto = hit.proto
    let rawMeaning = hit.meaning
    if (!wordProto || !rawMeaning || !looksLikeEnglishWord(wordProto)) continue

    let usPhonetic: string | undefined
    let ukPhonetic: string | undefined
    let partSpeechMean = rawMeaning
    const ph = rawMeaning.match(WORD_PHONETIC_MEANING)
    if (ph) {
      usPhonetic = ph[1]
      ukPhonetic = ph[2]
      partSpeechMean = (ph[3] || '').trim()
    }
    if (!partSpeechMean) continue
    out.push({
      entryType: 'word',
      wordProto,
      partSpeechMean,
      usPhonetic,
      ukPhonetic
    })
  }
  return out
}

/** 从讲解正文「固定搭配 / 分解解释」等小节提取短语 */
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
    if (inSection && isSectionExit(line, false)) {
      inSection = false
      continue
    }
    if (!inSection) continue
    const hit = matchLemmaLine(line)
    if (!hit) continue
    const wordProto = hit.proto
    let partSpeechMean = hit.meaning
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

/**
 * 自由「解析」回复兜底：无标准小节时，仍从「短语意思是」与「lemma：中文」行抽取。
 */
export function extractLooseAnalysisEntries(displayText: string): Omit<CollectEntry, 'basketKey'>[] {
  if (!displayText?.trim()) return []
  const out: Omit<CollectEntry, 'basketKey'>[] = []
  const seen = new Set<string>()

  const asked = displayText.match(ASKED_PHRASE)
  if (asked) {
    const wordProto = cleanProto(asked[1])
    const meaning = asked[2].replace(/\*+/g, '').trim()
    if (wordProto && meaning && (looksLikeEnglishPhrase(wordProto) || looksLikeEnglishWord(wordProto))) {
      const entryType = looksLikeEnglishPhrase(wordProto) ? 'phrase' : 'word'
      const key = `${entryType}:${wordProto.toLowerCase()}`
      if (!seen.has(key)) {
        seen.add(key)
        out.push({
          entryType,
          wordProto,
          partSpeechMean: entryType === 'phrase' && !meaning.startsWith('短语')
            ? `短语. ${meaning}`
            : meaning
        })
      }
    }
  }

  for (const rawLine of displayText.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.length > MAX_PROSE_LINE_LEN) continue
    if (ANY_SECTION_HEADING.test(line) || OTHER_HEADING.test(line)) continue
    const hit = matchLemmaLine(line)
    if (!hit || !hasCjkMeaning(hit.meaning)) continue
    const wordProto = hit.proto
    const meaning = hit.meaning
    if (!wordProto || !meaning) continue

    if (looksLikeEnglishPhrase(wordProto)) {
      const key = `phrase:${wordProto.toLowerCase()}`
      if (seen.has(key)) continue
      seen.add(key)
      out.push({
        entryType: 'phrase',
        wordProto,
        partSpeechMean: meaning.startsWith('短语') || /^phrase/i.test(meaning)
          ? meaning
          : `短语. ${meaning}`
      })
    } else if (looksLikeEnglishWord(wordProto)) {
      const key = `word:${wordProto.toLowerCase()}`
      if (seen.has(key)) continue
      seen.add(key)
      out.push({
        entryType: 'word',
        wordProto,
        partSpeechMean: meaning
      })
    }
  }
  return out
}

function normalizeRawEntry(e: any, sourceSnippet = ''): CollectEntry | null {
  const wordProto = cleanProto(String(e?.word || e?.wordProto || ''))
  const partSpeechMean = String(e?.meaning || e?.partSpeechMean || '').trim()
  if (!wordProto || !partSpeechMean) return null
  let entryType: 'word' | 'phrase' = e.entryType === 'phrase' ? 'phrase' : 'word'
  if (looksLikeEnglishPhrase(wordProto)) entryType = 'phrase'
  return {
    basketKey: makeBasketKey({ entryType, wordProto }),
    entryType,
    wordProto,
    partSpeechMean,
    usPhonetic: e.usPhonetic,
    ukPhonetic: e.ukPhonetic,
    sentenceExample: sourceSnippet || undefined,
    rootAffix: e.rootAffix,
    sourceSnippet: sourceSnippet || undefined
  }
}

/** 判断入库正文是否为解析 JSON，并还原为按 feature_key 分包的结构 */
export function parseAnalyzeResultFromStored(text: string): Record<string, unknown> | null {
  if (!text?.trim()) return null
  let body = text.trim()
  if (body.startsWith('```')) {
    body = body.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim()
  }
  if (body.includes(COLLECT_JSON_MARKER) && !body.trimStart().startsWith('{')) {
    return null
  }
  const start = body.indexOf('{')
  const end = body.lastIndexOf('}')
  if (start < 0 || end <= start) return null
  try {
    let slice = body.substring(start, end + 1)
    slice = slice.replace(/,(\s*[}\]])/g, '$1')
    let obj: unknown = JSON.parse(slice)
    // 兼容双重 JSON 编码（字符串里再包一层对象）
    if (typeof obj === 'string') {
      const inner = obj.trim().replace(/,(\s*[}\]])/g, '$1')
      if (inner.startsWith('{')) {
        try { obj = JSON.parse(inner) } catch { return null }
      } else {
        return null
      }
    }
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null
    const keys = Object.keys(obj as object)
    if (!keys.length) return null
    return obj as Record<string, unknown>
  } catch {
    return null
  }
}

/** 从入库的完整 AI 回复中解析可收录词条（结构化 JSON 优先，旧 Markdown 兜底） */
export function parseCollectEntriesFromText(text: string, sourceSnippet = ''): CollectEntry[] {
  if (!text) return []
  const map = new Map<string, CollectEntry>()

  const analyze = parseAnalyzeResultFromStored(text)
  if (analyze) {
    for (const raw of Object.values(analyze)) {
      if (!Array.isArray(raw)) continue
      for (const e of raw) {
        if (!e || typeof e !== 'object') continue
        const word = String((e as any).word || (e as any).wordProto || '')
        if (!word && !(e as any).meaning && !(e as any).partSpeechMean) continue
        const entryType = (e as any).entryType || (word.includes(' ') ? 'phrase' : 'word')
        const normalized = normalizeRawEntry({ ...(e as Record<string, unknown>), entryType }, sourceSnippet)
        if (normalized) map.set(normalized.basketKey, normalized)
      }
    }
    if (map.size) return [...map.values()]
  }

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

  for (const item of [
    ...extractWordsFromProse(stripCollectMarker(text)),
    ...extractPhrasesFromProse(stripCollectMarker(text)),
    ...extractLooseAnalysisEntries(stripCollectMarker(text))
  ]) {
    const normalized = normalizeRawEntry(item, sourceSnippet)
    if (!normalized) continue
    const existing = map.get(normalized.basketKey)
    if (!existing) {
      map.set(normalized.basketKey, normalized)
    } else {
      // JSON 常只留语境义；正文多词性时补全
      map.set(normalized.basketKey, enrichFromProse(existing, normalized))
    }
  }

  return [...map.values()]
}

/** 同词条：释义取词性更全的一方，音标/词根缺则补 */
export function enrichFromProse(fromJson: CollectEntry, fromProse: CollectEntry): CollectEntry {
  return {
    ...fromJson,
    partSpeechMean: preferRicherMeaning(fromJson.partSpeechMean, fromProse.partSpeechMean),
    usPhonetic: fromJson.usPhonetic || fromProse.usPhonetic,
    ukPhonetic: fromJson.ukPhonetic || fromProse.ukPhonetic,
    rootAffix: fromJson.rootAffix || fromProse.rootAffix,
    sentenceExample: fromJson.sentenceExample || fromProse.sentenceExample
  }
}

export function preferRicherMeaning(a: string, b: string): string {
  if (!a?.trim()) return b || ''
  if (!b?.trim()) return a
  const ca = countPosMarkers(a)
  const cb = countPosMarkers(b)
  if (cb > ca) return b.trim()
  if (ca > cb) return a.trim()
  return b.trim().length > a.trim().length ? b.trim() : a.trim()
}

function countPosMarkers(meaning: string): number {
  const matches = meaning.match(/\b(?:n|v|adj|adv|prep|conj|pron|det|num|int|phr|vt|vi)\.|短语\.?|\bphrase\.?/gi)
  return matches?.length ?? 0
}

/** 结构化解析结果 → 入库 JSON（二次编辑保存用）；按 feature_key 原样序列化 */
export function serializeAnalyzeResultToJson(result: Record<string, unknown>): string {
  const obj: Record<string, unknown> = {}
  for (const [key, val] of Object.entries(result || {})) {
    if (key === 'raw') continue
    if (val == null) continue
    if (typeof val === 'string' && !val.trim()) continue
    if (Array.isArray(val) && val.length === 0) continue
    obj[key] = val
  }
  return JSON.stringify(obj)
}

/** 历史消息 → 结构化解析结果或 Markdown 正文 + 可选收录条目 */
export function hydrateStoredAiMessage(
  role: string,
  content: string,
  sourceSnippet = '',
  id?: number,
  featureKey?: string | null
) {
  const raw = content || ''
  const withId = id != null ? { id } : {}
  const featureKeys = featureKey
    ? String(featureKey)
        .split(',')
        .map((k) => k.trim())
        .filter(Boolean)
    : undefined
  const withFeatures = featureKeys?.length ? { featureKeys } : {}
  try {
    if (role === 'assistant') {
      let analyzeResult = parseAnalyzeResultFromStored(raw)
      // 仅 { raw } 时再拆一层
      if (
        analyzeResult &&
        typeof analyzeResult.raw === 'string' &&
        Object.keys(analyzeResult).every(
          (k) => k === 'raw' || analyzeResult![k] == null || analyzeResult![k] === ''
        )
      ) {
        const inner =
          parseAnalyzeResultFromStored(String(analyzeResult.raw)) ||
          (() => {
            try {
              const s = String(analyzeResult!.raw)
                .trim()
                .replace(/,(\s*[}\]])/g, '$1')
              const start = s.indexOf('{')
              const end = s.lastIndexOf('}')
              if (start < 0 || end <= start) return null
              const obj = JSON.parse(s.substring(start, end + 1))
              return obj && typeof obj === 'object' && !Array.isArray(obj)
                ? (obj as Record<string, unknown>)
                : null
            } catch {
              return null
            }
          })()
        if (inner) {
          delete inner.raw
          analyzeResult = inner
        }
      }
      if (analyzeResult && !(Object.keys(analyzeResult).length === 1 && analyzeResult.raw != null)) {
        const entries = parseCollectEntriesFromText(
          Object.keys(analyzeResult).length ? JSON.stringify(analyzeResult) : raw,
          sourceSnippet
        )
        return {
          ...withId,
          ...withFeatures,
          role,
          content: '',
          analyzeResult,
          collectEntries: entries.length > 0 ? entries : undefined,
          collectSourceSnippet: entries.length > 0 ? (sourceSnippet || undefined) : undefined
        }
      }
    }
    const entries = role === 'assistant' ? parseCollectEntriesFromText(raw, sourceSnippet) : []
    return {
      ...withId,
      ...withFeatures,
      role,
      content: stripCollectMarker(raw),
      collectEntries: entries.length > 0 ? entries : undefined,
      collectSourceSnippet: entries.length > 0 ? (sourceSnippet || undefined) : undefined
    }
  } catch (e) {
    console.warn('[collect] hydrateStoredAiMessage failed', e)
    return {
      ...withId,
      ...withFeatures,
      role,
      content: stripCollectMarker(raw)
    }
  }
}

/**
 * 从用户侧消息还原选中原文。
 * 格式：【翻译 + 单词】Do the following… · statements, agree with
 */
export function extractContextFromUserAiMessage(content: string): string {
  if (!content) return ''
  const m = content.trim().match(/^【[^】]+】(.*)$/s)
  if (!m) return ''
  let body = m[1]
  const idx = body.lastIndexOf(' · ')
  if (idx >= 0) {
    const after = body.slice(idx + 3).trim()
    const items = after.split(/,\s*/).filter(Boolean)
    const looksLikeTargets =
      items.length > 0 &&
      after.length <= 200 &&
      items.every(
        (w) =>
          w.length <= 60 &&
          /^[A-Za-z][A-Za-z'’\-/]*(?:\s+[A-Za-z][A-Za-z'’\-/]*){0,6}$/.test(w)
      )
    if (looksLikeTargets) {
      body = body.slice(0, idx)
    }
  }
  return body.trim()
}

/** 按时间序还原会话：assistant 收录条目挂上前一条 user 的选中原文 */
export function hydrateAiMessageList(
  messages: { id?: number; role: string; content: string; featureKey?: string | null }[]
): ReturnType<typeof hydrateStoredAiMessage>[] {
  let lastUserSnippet = ''
  return messages.map((m) => {
    if (m.role === 'user') {
      lastUserSnippet = extractContextFromUserAiMessage(m.content || '')
      return hydrateStoredAiMessage(m.role, m.content || '', '', m.id, m.featureKey)
    }
    return hydrateStoredAiMessage(m.role, m.content || '', lastUserSnippet, m.id, m.featureKey)
  })
}
