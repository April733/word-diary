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

// Notebooks
export const createNotebook = (data: any) => api.post('/notebooks', data)
export const getNotebooks = () => api.get('/notebooks')
export const updateNotebook = (id: number, data: any) => api.put(`/notebooks/${id}`, data)
export const deleteNotebook = (id: number) => api.delete(`/notebooks/${id}`)

// Words
export const addWord = (notebookId: number, data: any) => api.post(`/notebooks/${notebookId}/words`, data)
export const getWords = (notebookId: number) => api.get(`/notebooks/${notebookId}/words`)
export const deleteWord = (notebookId: number, wordId: number) => api.delete(`/notebooks/${notebookId}/words/${wordId}`)

// Study
export const getNewWords = () => api.get('/study/new-words')
export const getTodayReview = () => api.get('/study/today-review')
export const firstLearn = (planId: number, result: number) => api.post(`/study/first-learn/${planId}`, { result })
export const review = (planId: number, result: number) => api.post(`/study/review/${planId}`, { result })
export const getRecords = () => api.get('/study/records')
export const getStats = () => api.get('/study/stats')

export default api
