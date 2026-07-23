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
export const getStudyToday = () => api.get('/study-today')
export const openStudySession = () => api.post('/study-today/session/open')
export const getStudySessionQueue = (params?: { phase?: string }) =>
  api.get('/study-today/session/queue', { params })
export const submitStudyAnswer = (data: {
  wordId: number
  feedback: number
  phase: string
}) => api.post('/study-today/answer', data)
export const getStudySettings = () => api.get('/study-today/settings')
export const updateStudySettings = (data: { dailyNewLimit?: number; reviewUnlockHour?: number }) =>
  api.put('/study-today/settings', data)
export const getTodayCompleted = () => api.get('/study-today/completed')

// Study — 背单词计划
export const getBrowseWords = (params?: { notebookId?: number; page?: number; pageSize?: number }) =>
  api.get('/study-plan/browse/words', { params })
export const submitBrowseAnswer = (data: { wordId: number; feedback: number }) =>
  api.post('/study-plan/browse/answer', data)
export const getStats = () => api.get('/study-plan/stats')
export const getPlanWords = (params?: { status?: 'pending' | 'learning' | 'mastered' | 'all' }) =>
  api.get('/study-plan/words', { params })

export default api

// ========== 阅读器 ==========
export const getAiFeatures = (locale?: string) =>
  api.get('/books/ai-features', { params: { locale: locale || 'zh-CN' } })
export const getAiFeaturePreferences = (locale?: string) =>
  api.get('/books/ai-features/preferences', { params: { locale: locale || 'zh-CN' } })
export const saveAiFeaturePreferences = (data: { featureKeys: string[]; featureOrder?: string[] }) =>
  api.put('/books/ai-features/preferences', data)

export const uploadBook = (file: File) => {
  console.log('[API] uploadBook 请求, 文件名:', file.name, '大小:', (file.size / 1024).toFixed(1), 'KB')
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/books/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 大 PDF：浏览器上传 + 服务端压缩 + COS，可能远超 1 分钟
    timeout: 300000
  }).then(res => {
    console.log('[API] uploadBook 响应:', res.status, res.data)
    return res
  }).catch(err => {
    console.error('[API] uploadBook 失败:', err.response?.status, err.response?.data || err.message)
    throw err
  })
}
export const getBooks = () => api.get('/books')
export const getBookDetail = (bookId: number) => api.get(`/books/${bookId}`)
export const deleteBook = (bookId: number) => api.delete(`/books/${bookId}`)
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
  noticeType: 'suggestion' | 'complaint' | 'copyright' | 'illegal'
  complainantName: string
  complainantEmail: string
  emailCode: string
  complainantPhone?: string
  organization?: string
  rightDescription?: string
  ownershipProof?: string
  targetUrl?: string
  targetBookId?: number
  targetDescription?: string
  detail: string
  goodFaith?: boolean
}) => api.post('/abuse-notices', data)

export const listMyAbuseNotices = (params?: { page?: number; size?: number }) =>
  api.get('/abuse-notices/mine', { params })

export const getMyAbuseNotice = (id: number) =>
  api.get(`/abuse-notices/mine/${id}`)

export const claimMyAbuseNotice = (noticeNo: string) =>
  api.post('/abuse-notices/mine/claim', { noticeNo })

// Book ↔ Notebook links & collect
export const getBookNotebookLinks = (bookId: number) => api.get(`/books/${bookId}/notebook-links`)
export const collectBookWords = (bookId: number, data: any) =>
  api.post(`/books/${bookId}/words/collect`, data)
