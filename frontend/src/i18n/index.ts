import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.json'
import en from './locales/en.json'
import id from './locales/id.json'
import vi from './locales/vi.json'
import th from './locales/th.json'

export type SupportedLocale = 'zh-CN' | 'en' | 'id' | 'vi' | 'th'

export const LOCALE_OPTIONS: { value: SupportedLocale; label: string }[] = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en', label: 'English' },
  { value: 'id', label: 'Bahasa Indonesia' },
  { value: 'vi', label: 'Tiếng Việt' },
  { value: 'th', label: 'ไทย' },
]

const STORAGE_KEY = 'app-locale'

export function getStoredLocale(): SupportedLocale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored && LOCALE_OPTIONS.some(o => o.value === stored)) {
      return stored as SupportedLocale
    }
  } catch { /* ignore */ }
  // fallback to browser language
  const browserLang = navigator.language
  if (browserLang.startsWith('zh')) return 'zh-CN'
  if (browserLang.startsWith('id')) return 'id'
  if (browserLang.startsWith('vi')) return 'vi'
  if (browserLang.startsWith('th')) return 'th'
  return 'zh-CN'
}

export function setStoredLocale(locale: SupportedLocale) {
  localStorage.setItem(STORAGE_KEY, locale)
}

const i18n = createI18n({
  legacy: false,
  locale: getStoredLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    en,
    id,
    vi,
    th,
  },
})

export default i18n
