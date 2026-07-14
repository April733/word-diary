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
  res => res,
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
export const checkInviteCode = (code: string) => api.get('/user/check-invite-code', { params: { code } })

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

// Study
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

// Reader Settings
export const getReaderSettings = () => api.get('/books/reader-settings')
export const saveReaderSettings = (data: any) => api.put('/books/reader-settings', data)
