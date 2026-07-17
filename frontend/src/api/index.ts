import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  res => {
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
    }
    return Promise.reject(err)
  }
)

// User
export const register = (data: any) => api.post('/user/register', data)
export const login = (data: any) => api.post('/user/login', data)
export const updateUserLocale = (locale: string) => api.put('/user/locale', { locale })
export const checkPhone = (phone: string) => api.get('/user/check-phone', { params: { phone } })
export const checkEmail = (email: string) => api.get('/user/check-email', { params: { email } })
export const checkInviteCode = (code: string) => api.get('/user/check-invite-code', { params: { code } })
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
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data)
  })
}

export const batchAddWords = (notebookId: number, data: any) => api.post(`/notebooks/${notebookId}/words/batch-add`, data)
export const fillWordGaps = (notebookId: number, data?: { wordIds?: number[] }) =>
  api.post(`/notebooks/${notebookId}/words/ai-fill-gaps`, data || {}, { timeout: 120000 })

// Study
export const getStudyToday = () => api.get('/study/today')
export const openStudySession = () => api.post('/study/session/open')
export const submitStudyAnswer = (data: { planId: number; queueItemId: number; feedback: number; phase: string }) =>
  api.post('/study/answer', data)
export const getStudySettings = () => api.get('/study/settings')
export const updateStudySettings = (data: { dailyNewLimit?: number; reviewUnlockHour?: number; autoSpreadNew?: boolean }) =>
  api.put('/study/settings', data)
export const getBrowseWords = (params?: { notebookId?: number; page?: number; pageSize?: number }) =>
  api.get('/study/browse/words', { params })
export const submitBrowseAnswer = (data: { planId: number; feedback: number }) =>
  api.post('/study/browse/answer', data)
export const getNewWords = () => api.get('/study/new-words')
export const getTodayReview = () => api.get('/study/today-review')
export const getTodayCompleted = () => api.get('/study/today-completed')
export const firstLearn = (planId: number, result: number) => api.post(`/study/first-learn/${planId}`, { result })
export const review = (planId: number, result: number) => api.post(`/study/review/${planId}`, { result })
export const getRecords = () => api.get('/study/records')
export const getStats = () => api.get('/study/stats')

export default api

// ========== 阅读器 ==========
export const getAiFeatures = () => api.get('/books/ai-features')
export const getAiFeaturePreferences = () => api.get('/books/ai-features/preferences')
export const saveAiFeaturePreferences = (data: { featureKeys: string[]; featureOrder?: string[] }) =>
  api.put('/books/ai-features/preferences', data)

export const uploadBook = (file: File) => {
  console.log('[API] uploadBook 请求, 文件名:', file.name, '大小:', (file.size / 1024).toFixed(1), 'KB')
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/books/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
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
export const updateProgress = (bookId: number, data: any) => api.post(`/books/${bookId}/progress`, data)
export const getBookFileUrl = (bookId: number) => `/api/books/${bookId}/file`

// Annotations
export const getAnnotations = (bookId: number) => api.get(`/books/${bookId}/annotations`)
export const getChapterAnnotations = (bookId: number, chapterIndex: number) =>
  api.get(`/books/${bookId}/annotations/chapter/${chapterIndex}`)
export const addAnnotation = (bookId: number, data: any) => api.post(`/books/${bookId}/annotations`, data)
export const updateAnnotation = (bookId: number, id: number, data: any) =>
  api.put(`/books/${bookId}/annotations/${id}`, data)
export const deleteAnnotation = (bookId: number, id: number) => api.delete(`/books/${bookId}/annotations/${id}`)

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
}) => api.post(`/books/${bookId}/pdf-markups`, data)
export const updatePdfMarkup = (bookId: number, id: number, data: {
  geometryJson?: string
  color?: string
  noteText?: string
}) => api.put(`/books/${bookId}/pdf-markups/${id}`, data)
export const deletePdfMarkup = (bookId: number, id: number) =>
  api.delete(`/books/${bookId}/pdf-markups/${id}`)

// Reader Settings
export const getReaderSettings = () => api.get('/books/reader-settings')
export const saveReaderSettings = (data: any) => api.put('/books/reader-settings', data)

// AI Sessions
export const getAiSessions = (bookId?: number) =>
  api.get('/books/ai-sessions', { params: bookId != null ? { bookId } : {} })
export const createAiSession = (data: { bookId?: number }) => api.post('/books/ai-sessions', data)
export const getAiMessages = (sessionId: number) => api.get(`/books/ai-sessions/${sessionId}/messages`)
export const getAiMessagesPaged = (sessionId: number, offset: number, limit: number) =>
  api.get(`/books/ai-sessions/${sessionId}/messages/paged`, { params: { offset, limit } })
export const deleteAiSession = (sessionId: number) => api.delete(`/books/ai-sessions/${sessionId}`)

// Admin contact & issue reporting
export const reportIssue = (data: { username?: string; description: string; errorDetail?: string; subject?: string }) =>
  api.post('/books/report-issue', data)
export const reportIssuePublic = (data: { username: string; description: string; errorDetail?: string; subject?: string }) =>
  api.post('/user/report-issue-public', data)

// Book ↔ Notebook links & collect
export const getBookNotebookLinks = (bookId: number) => api.get(`/books/${bookId}/notebook-links`)
export const collectBookWords = (bookId: number, data: any) =>
  api.post(`/books/${bookId}/words/collect`, data)
