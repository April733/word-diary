import axios, { type AxiosRequestConfig } from 'axios'
import { aiRegionHeaders } from '../utils/aiRegion'

/** soft: 弱网可入队的写操作，失败不计入「服务宕机」探活，避免阅读中弹全屏遮罩 */
export type SoftRequestConfig = AxiosRequestConfig & { soft?: boolean }

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const regionHdrs = aiRegionHeaders()
  Object.assign(config.headers, regionHdrs)
  return config
})

api.interceptors.response.use(
  res => {
    // 任一 API 成功，清掉传输失败计数，避免闪断后误弹「服务升级」
    import('../utils/serviceStatus').then(({ noteApiTransportSuccess }) => {
      noteApiTransportSuccess()
    }).catch(() => { /* ignore */ })
    const body = res.data
    // 业务失败偶发以 HTTP 200 + code!=200 返回，统一当成错误
    if (body && typeof body.code === 'number' && body.code !== 200) {
      return Promise.reject({
        response: res,
        message: body.message || '请求失败'
      })
    }
    return res
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    } else if (!(err.config as SoftRequestConfig | undefined)?.soft) {
      // 延迟导入，避免与工具模块循环依赖
      import('../utils/serviceStatus').then(({ noteApiTransportFailure }) => {
        noteApiTransportFailure(err)
      }).catch(() => { /* ignore */ })
    }
    return Promise.reject(err)
  }
)

// User
export const register = (data: any) => api.post('/user/register', data)
export const login = (data: any) => api.post('/user/login', data)
export const updateUserLocale = (locale: string) => api.put('/user/locale', { locale })
export const updateUserAiRegion = (region: string) => api.put('/user/ai-region', { region })
export const checkPhone = (phone: string) => api.get('/user/check-phone', { params: { phone } })
export const checkEmail = (email: string) => api.get('/user/check-email', { params: { email } })
export const sendSms = (phone: string) => api.post('/user/send-sms', { phone })
export const sendMailCode = (email: string) => api.post('/user/send-mail-code', { email })
export const sendResetSms = (phone: string) => api.post('/user/send-reset-sms', { phone })
export const sendResetMail = (email: string) => api.post('/user/send-reset-mail', { email })
export const resetPassword = (data: {
  resetType: 'phone' | 'email'
  phone?: string
  email?: string
  code: string
  newPassword: string
}) => api.post('/user/reset-password', data)

// Notebooks
export const createNotebook = (data: any) => api.post('/notebooks', data)
export const getNotebooks = () => api.get('/notebooks')
export const updateNotebook = (id: number, data: any) => api.put(`/notebooks/${id}`, data)
export const setNotebookStudyEnabled = (id: number, enabled: boolean) =>
  api.put(`/notebooks/${id}/study-enabled`, { enabled })
export const deleteNotebook = (id: number) => api.delete(`/notebooks/${id}`)

// Words
export const addWord = (notebookId: number, data: any) => api.post(`/notebooks/${notebookId}/words`, data)
export const getWords = (notebookId: number) => api.get(`/notebooks/${notebookId}/words`)
export const updateWord = (notebookId: number, wordId: number, data: any) => api.put(`/notebooks/${notebookId}/words/${wordId}`, data)
export const deleteWord = (notebookId: number, wordId: number) => api.delete(`/notebooks/${notebookId}/words/${wordId}`)
export const aiParseWords = (notebookId: number, data: any) =>
  api.post(`/notebooks/${notebookId}/words/ai-parse`, data, { timeout: 120000 })

/** 流式 AI 解析 - 返回 fetch Response 用于读取 SSE 流 */
export const aiParseWordsStream = (notebookId: number, data: any): Promise<Response> => {
  const token = localStorage.getItem('token')
  return fetch(`/api/notebooks/${notebookId}/words/ai-parse-stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
      ...aiRegionHeaders()
    },
    body: JSON.stringify(data)
  })
}

export const batchAddWords = (notebookId: number, data: any) => api.post(`/notebooks/${notebookId}/words/batch-add`, data)

// Study — 今日学习
export const getStudyToday = (params?: { sourceType?: string }) => api.get('/study-today', { params })
export const openStudySession = (params?: { sourceType?: string }) => api.post('/study-today/session/open', null, { params })
export const getStudySessionQueue = (params?: { phase?: string; sourceType?: string }) =>
  api.get('/study-today/session/queue', { params })
export const submitStudyAnswer = (data: {
  wordId: number
  feedback: number
  phase: string
}, params?: { sourceType?: string }) => api.post('/study-today/answer', data, { params })
export const getStudySettings = () => api.get('/study-today/settings')
export const updateStudySettings = (data: { dailyNewLimit?: number; reviewUnlockHour?: number }) =>
  api.put('/study-today/settings', data)
export const getTodayCompleted = (params?: { sourceType?: string }) => api.get('/study-today/completed', { params })

// Study — 编辑/删除当前单词
export const updateStudyWord = (wordId: number, data: { wordProto?: string; partSpeechMean?: string; usPhonetic?: string; ukPhonetic?: string }) =>
  api.put(`/study-today/words/${wordId}`, data)
export const deleteStudyWord = (wordId: number) => api.delete(`/study-today/words/${wordId}`)

// Study — 背单词计划
export const getBrowseWords = (params?: { notebookId?: number; page?: number; pageSize?: number }) =>
  api.get('/study-plan/browse/words', { params })
export const submitBrowseAnswer = (data: { wordId: number; feedback: number }) =>
  api.post('/study-plan/browse/answer', data)
export const getStats = () => api.get('/study-plan/stats')

/** 学习时长埋点配置（空闲超时 / 最短入库秒数，不对用户展示） */
export const getStudyDurationConfig = (config?: SoftRequestConfig) =>
  api.get('/study-duration/config', { soft: true, ...config })

/** 学习时长：本地计时达标后一次性入库 */
export const commitStudyDuration = (data: {
  actionType?: string
  refTable?: string
  refId?: number
  startTime: string
  endTime: string
  durationSeconds: number
}, config?: SoftRequestConfig) => api.post('/study-duration/commit', data, { soft: true, ...config })

/** 学习时长统计面板 */
export const getStudyDurationStats = (range: 'today' | 'all' = 'today') =>
  api.get('/study-duration/stats', { params: { range } })

export const getPlanWords = (params?: { status?: 'pending' | 'learning' | 'mastered' | 'all' }) =>
  api.get('/study-plan/words', { params })

// Quotes（激励语）
export const getRandomQuote = () => api.get('/quotes/random')

export default api

// ========== 阅读器 ==========
export const getAiFeaturePreferences = (locale?: string) =>
  api.get('/books/ai-features/preferences', { params: { locale: locale || 'zh-CN' } })
export const saveAiFeaturePreferences = (data: { featureKeys: string[]; featureOrder?: string[] }) =>
  api.put('/books/ai-features/preferences', data)

export const uploadBook = async (
  file: File,
  onProgress?: (percent: number) => void
) => {
  console.log('[API] uploadBook 请求, 文件名:', file.name, '大小:', (file.size / 1024 / 1024).toFixed(2), 'MB')
  // 优先 COS 直传，避免大文件经 nginx→后端→COS 双跳 504
  try {
    const initRes = await api.post('/books/upload-init', {
      fileName: file.name,
      fileSize: file.size
    })
    const init = initRes.data?.data
    if (!init?.uploadUrl || !init?.cosKey) {
      throw new Error('upload-init missing fields')
    }
    await axios.put(init.uploadUrl, file, {
      headers: {
        'Content-Type': init.contentType || file.type || 'application/octet-stream'
      },
      timeout: 600000,
      onUploadProgress: (evt) => {
        if (!onProgress || !evt.total) return
        onProgress(Math.min(99, Math.round((evt.loaded / evt.total) * 100)))
      }
    })
    onProgress?.(99)
    const done = await api.post('/books/upload-complete', {
      cosKey: init.cosKey,
      originalName: file.name,
      fileSize: file.size
    })
    onProgress?.(100)
    console.log('[API] uploadBook 直传完成:', done.status)
    return done
  } catch (directErr: any) {
    const status = directErr?.response?.status
    const msg = String(directErr?.message || '')
    const corsOrNetwork =
      !directErr?.response &&
      (msg.includes('Network') || msg.includes('CORS') || msg.includes('Failed to fetch'))
    // 签名/鉴权/业务错误不要盲目兜底；CORS 或直传链路不可用时回退 multipart
    if (status === 401 || status === 403 || status === 400) {
      console.error('[API] uploadBook 直传业务失败:', status, directErr?.response?.data || msg)
      throw directErr
    }
    console.warn('[API] uploadBook 直传失败，回退经后端上传:', status || msg)
    // 大文件经 nginx→后端仍易 504，直传失败时不再兜底徒增等待
    if (file.size > 8 * 1024 * 1024) {
      const err = new Error('DIRECT_UPLOAD_FAILED')
      ;(err as any).response = directErr?.response
      ;(err as any).__directUploadHint = true
      throw err
    }
    const formData = new FormData()
    formData.append('file', file)
    try {
      const res = await api.post('/books/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 600000,
        onUploadProgress: (evt) => {
          if (!onProgress || !evt.total) return
          onProgress(Math.min(99, Math.round((evt.loaded / evt.total) * 100)))
        }
      })
      onProgress?.(100)
      return res
    } catch (fallbackErr: any) {
      console.error(
        '[API] uploadBook 失败:',
        fallbackErr?.response?.status,
        fallbackErr?.response?.data || fallbackErr?.message
      )
      if (corsOrNetwork) {
        ;(fallbackErr as any).__directUploadHint = true
      }
      throw fallbackErr
    }
  }
}
export const getBooks = () => api.get('/books')
export const getBookDetail = (bookId: number) => api.get(`/books/${bookId}`)
export const deleteBook = (bookId: number) => api.delete(`/books/${bookId}`)
export const createBookShareCode = (bookId: number) => api.post(`/books/${bookId}/share-code`)
export const importBookByShareCode = (shareCode: string) => api.post('/books/import-share', { shareCode })
export const updateProgress = (bookId: number, data: any, config?: SoftRequestConfig) =>
  api.post(`/books/${bookId}/progress`, data, config)
export const getBookFileUrl = (bookId: number) => `/api/books/${bookId}/file`
/** COS 预签名直链（大 PDF 直连，避免经后端中转超时） */
export const getBookPresignedFileUrl = (bookId: number) => api.get(`/books/${bookId}/file-url`)

// Annotations
export const getAnnotations = (bookId: number) => api.get(`/books/${bookId}/annotations`)
export const getChapterAnnotations = (bookId: number, chapterIndex: number) =>
  api.get(`/books/${bookId}/annotations/chapter/${chapterIndex}`)
export const addAnnotation = (bookId: number, data: any, config?: SoftRequestConfig) =>
  api.post(`/books/${bookId}/annotations`, data, config)
export const updateAnnotation = (bookId: number, id: number, data: any, config?: SoftRequestConfig) =>
  api.put(`/books/${bookId}/annotations/${id}`, data, config)
export const deleteAnnotation = (bookId: number, id: number, config?: SoftRequestConfig) =>
  api.delete(`/books/${bookId}/annotations/${id}`, config)

// PDF Canvas 批注（与 EPUB CFI 笔记分离）
export const getPdfMarkups = (bookId: number) => api.get(`/books/${bookId}/pdf-markups`)
export const getPdfMarkupsByPage = (bookId: number, pageNum: number) =>
  api.get(`/books/${bookId}/pdf-markups/page/${pageNum}`)
export const addPdfMarkup = (bookId: number, data: {
  pageNum: number
  markupType: string
  geometryJson: string
  color?: string
  noteText?: string
}, config?: SoftRequestConfig) => api.post(`/books/${bookId}/pdf-markups`, data, config)
export const updatePdfMarkup = (bookId: number, id: number, data: {
  geometryJson?: string
  color?: string
  noteText?: string
}, config?: SoftRequestConfig) => api.put(`/books/${bookId}/pdf-markups/${id}`, data, config)
export const deletePdfMarkup = (bookId: number, id: number, config?: SoftRequestConfig) =>
  api.delete(`/books/${bookId}/pdf-markups/${id}`, config)

// Reader Settings
export const getReaderSettings = () => api.get('/books/reader-settings')
export const saveReaderSettings = (data: any) => api.put('/books/reader-settings', data)

// AI Sessions
export const getAiSessions = (bookId?: number) =>
  api.get('/books/ai-sessions', { params: bookId != null ? { bookId } : {} })
export const createAiSession = (data: { bookId?: number }) => api.post('/books/ai-sessions', data)
export const renameAiSession = (sessionId: number, title: string) =>
  api.put(`/books/ai-sessions/${sessionId}`, { title })
export const getAiMessages = (sessionId: number) => api.get(`/books/ai-sessions/${sessionId}/messages`)
export const getAiMessagesPaged = (sessionId: number, offset: number, limit: number) =>
  api.get(`/books/ai-sessions/${sessionId}/messages/paged`, { params: { offset, limit } })
export const deleteAiSession = (sessionId: number) => api.delete(`/books/ai-sessions/${sessionId}`)
export const updateAiMessage = (messageId: number, content: string) =>
  api.put(`/books/ai-messages/${messageId}`, { content })

// Admin contact & issue reporting
export const reportIssue = (data: { username?: string; description: string; errorDetail?: string; subject?: string }) =>
  api.post('/books/report-issue', data)
export const reportIssuePublic = (data: { username: string; description: string; errorDetail?: string; subject?: string }) =>
  api.post('/user/report-issue-public', data)

/** 帮助中心：发送联系邮箱验证码（公开） */
export const sendAbuseNoticeMailCode = (email: string) =>
  api.post('/abuse-notices/send-mail-code', { email })

/** 投诉 / 建议（公开可匿名；须邮箱验证码；登录时带 Token 会绑定账号） */
export const submitAbuseNotice = (data: {
  noticeType: 'suggestion' | 'complaint'
  complainantName?: string
  complainantEmail?: string
  emailCode?: string
  complainantPhone?: string
  detail: string
  goodFaith?: boolean
}) => api.post('/abuse-notices', data)

export const listMyAbuseNotices = (params?: { page?: number; size?: number }) =>
  api.get('/abuse-notices/mine', { params })

export const getMyAbuseNotice = (id: number) =>
  api.get(`/abuse-notices/mine/${id}`)

export const claimMyAbuseNotice = (noticeNo: string) =>
  api.post('/abuse-notices/mine/claim', { noticeNo })

// Writing (IELTS)
export const listWritingEssays = () => api.get('/writing')
export const getWritingSamples = () => api.get('/writing/samples')
export const createWritingEssay = (data: {
  title?: string
  taskType: string
  promptText: string
  bodyText?: string
}) => api.post('/writing', data)
export const getWritingEssay = (id: number) => api.get(`/writing/${id}`)
export const saveWritingEssay = (
  id: number,
  data: { title?: string; taskType?: string; promptText?: string; bodyText?: string }
) => api.put(`/writing/${id}`, data)
export const deleteWritingEssay = (id: number) => api.delete(`/writing/${id}`)
export const gradeWritingEssay = (id: number) =>
  api.post(`/writing/${id}/grade`, null, { timeout: 180000 })
export const listWritingGrades = (id: number) => api.get(`/writing/${id}/grades`)
export const uploadWritingPromptImage = (id: number, file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return api.post(`/writing/${id}/prompt-image`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}
export const clearWritingPromptImage = (id: number) =>
  api.delete(`/writing/${id}/prompt-image`)

// Shadowing (ASR transcript, independent of reading)
export const listShadowingClips = () => api.get('/shadowing')
export const getShadowingClip = (id: number) => api.get(`/shadowing/${id}`)
export const uploadShadowingClip = (file: File, title?: string) => {
  const formData = new FormData()
  formData.append('file', file)
  if (title?.trim()) formData.append('title', title.trim())
  return api.post('/shadowing', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}
export const saveShadowingClip = (
  id: number,
  data: {
    title?: string
    segments?: Array<{ text: string; startMs: number; endMs: number }>
  }
) => api.put(`/shadowing/${id}`, data)
export const transcribeShadowingClip = (id: number) =>
  api.post(`/shadowing/${id}/transcribe`, null, { timeout: 360000 })
/**
 * 带鉴权拉取音频 Blob（播放）。
 * 优先读 Cache API 本地缓存；带 Authorization 的请求几乎不会被浏览器 HTTP 缓存命中。
 * @param fileSize 用于缓存键，避免同 id 文件变更后读到旧 blob（一般用详情里的 fileSize）
 */
export async function fetchShadowingAudioBlob(
  id: number,
  fileSize?: number | null
): Promise<Blob> {
  const {
    readShadowingAudioCache,
    writeShadowingAudioCache,
    invalidateShadowingAudioCache
  } = await import('../utils/shadowingAudioCache')

  const expected = fileSize != null && fileSize > 0 ? Number(fileSize) : 0

  const normalizeAudioBlob = (blob: Blob): Blob => {
    if (blob.type && blob.type.startsWith('audio/')) return blob
    return new Blob([blob], { type: 'audio/mpeg' })
  }

  const isPlausible = (blob: Blob) => {
    if (!blob || blob.size < 256) return false
    // 已知体积时：缓存/响应明显过小视为损坏（半截下载或把错误页当音频）
    if (expected > 8 * 1024 && blob.size < expected * 0.5) return false
    return true
  }

  const cached = await readShadowingAudioCache(id, fileSize)
  if (cached && isPlausible(cached)) {
    return normalizeAudioBlob(cached)
  }
  if (cached) {
    await invalidateShadowingAudioCache(id)
  }

  const token = localStorage.getItem('token')
  const res = await fetch(`/api/shadowing/${id}/audio`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `audio HTTP ${res.status}`)
  }
  const blob = normalizeAudioBlob(await res.blob())
  if (!isPlausible(blob)) {
    throw new Error('audio blob too small / incomplete')
  }
  void writeShadowingAudioCache(id, blob, expected > 0 ? expected : blob.size)
  return blob
}
export const deleteShadowingClip = async (id: number) => {
  const res = await api.delete(`/shadowing/${id}`)
  try {
    const { invalidateShadowingAudioCache } = await import('../utils/shadowingAudioCache')
    await invalidateShadowingAudioCache(id)
  } catch {
    // ignore cache cleanup failure
  }
  return res
}

// Translation practice (learned words)
export const getTranslationPool = (poolMode: 'learned' | 'mastered' = 'learned') =>
  api.get('/translation/pool', { params: { poolMode } })
export const listTranslationPractices = () => api.get('/translation')
export type TranslationWordPick = { wordId: number; pos: string }
export const createTranslationPractice = (data: {
  title?: string
  direction: string
  poolMode: 'learned' | 'mastered'
  pickMode?: 'random' | 'manual'
  wordCount?: number
  /** 自选：一词一词性（推荐） */
  picks?: TranslationWordPick[]
  /** @deprecated 用 picks */
  wordIds?: number[]
}) => api.post('/translation', data)
export const getTranslationPractice = (id: number) => api.get(`/translation/${id}`)
export const saveTranslationPractice = (
  id: number,
  data: { title?: string; bodyText?: string }
) => api.put(`/translation/${id}`, data)
export const regenerateTranslationPractice = (id: number, direction: string) =>
  api.post(`/translation/${id}/regenerate`, { direction }, { timeout: 180000 })
export const updateTranslationWords = (
  id: number,
  picks: TranslationWordPick[]
) => api.put(`/translation/${id}/words`, { picks })
export const deleteTranslationPractice = (id: number) => api.delete(`/translation/${id}`)
export const gradeTranslationPractice = (id: number) =>
  api.post(`/translation/${id}/grade`, null, { timeout: 180000 })

/** 流式批改：返回 SSE EventSource 兼容的 fetch 响应 */
export const gradeTranslationPracticeStream = (id: number) =>
  fetch(`/api/translation/${id}/grade-stream`, {
    method: 'POST',
    headers: {
      'Accept': 'text/event-stream',
      'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
    },
    credentials: 'include'
  })
export const listTranslationGrades = (id: number) => api.get(`/translation/${id}/grades`)
export const listTranslationGradeChatMessages = (practiceId: number, gradeId: number) =>
  api.get(`/translation/${practiceId}/grades/${gradeId}/chat-messages`)

// Spelling practice (wrong pos-stat words → write English from meaning; SRS like daily study)
export const getSpellingSession = () => api.get('/spelling/session')
export const getSpellingQueue = () => api.get('/spelling/session')
export const checkSpelling = (data: { posStatId: number; answer: string }) =>
  api.post('/spelling/check', data)

// Spelling word management (CRUD for wd_word_pos_stat)
export const listSpellingWords = () => api.get('/spelling/words')
export const addSpellingWord = (data: { wordProto: string; pos: string; meaning?: string }) =>
  api.post('/spelling/words', data)
export const updateSpellingWord = (id: number, data: { wordProto?: string; pos?: string; meaning?: string }) =>
  api.put(`/spelling/words/${id}`, data)
export const deleteSpellingWord = (id: number) => api.delete(`/spelling/words/${id}`)

export const getMyProficiency = () => api.get('/proficiency/me')

// Book ↔ Notebook links & collect
export const getBookNotebookLinks = (bookId: number) => api.get(`/books/${bookId}/notebook-links`)
export const collectBookWords = (bookId: number, data: any) =>
  api.post(`/books/${bookId}/words/collect`, data)
