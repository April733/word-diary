import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '../api'
import { getStoredLocale, setStoredLocale, type SupportedLocale } from '../i18n'
import { getStoredAiRegion, setStoredAiRegion, type AiRegionPreference } from '../utils/aiRegion'

interface UserInfo {
  id: number
  username: string
  nickname: string
  token: string
  preferredLocale?: string
  preferredAiRegion?: string
  phone?: string
  email?: string
}

export const useUserStore = defineStore('user', () => {
  const user = ref<UserInfo | null>(null)

  function loadFromStorage() {
    const stored = localStorage.getItem('user')
    if (stored) {
      user.value = JSON.parse(stored)
      // 用服务端保存的 locale 覆盖本地
      if (user.value?.preferredLocale) {
        setStoredLocale(user.value.preferredLocale as SupportedLocale)
      }
      if (user.value?.preferredAiRegion) {
        setStoredAiRegion(user.value.preferredAiRegion as AiRegionPreference)
      }
    }
  }

  async function loginAction(username: string, password: string) {
    const res = await userApi.login({ username, password })
    user.value = res.data.data
    localStorage.setItem('token', user.value!.token)
    localStorage.setItem('user', JSON.stringify(user.value))
    // 登录后用服务端保存的 locale 初始化 i18n
    if (user.value?.preferredLocale) {
      setStoredLocale(user.value.preferredLocale as SupportedLocale)
    }
    if (user.value?.preferredAiRegion) {
      setStoredAiRegion(user.value.preferredAiRegion as AiRegionPreference)
    } else {
      setStoredAiRegion(getStoredAiRegion())
    }
  }

  async function registerAction(data: any) {
    const res = await userApi.register(data)
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '注册失败')
    }
  }

  function logout() {
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  async function updateLocale(locale: string) {
    try {
      await userApi.updateUserLocale(locale)
      if (user.value) {
        user.value.preferredLocale = locale
        localStorage.setItem('user', JSON.stringify(user.value))
      }
    } catch {
      // 静默失败，本地已经生效
    }
  }

  async function updateAiRegion(region: AiRegionPreference) {
    try {
      await userApi.updateUserAiRegion(region)
      if (user.value) {
        user.value.preferredAiRegion = region
        localStorage.setItem('user', JSON.stringify(user.value))
      }
    } catch {
      // 静默失败，本地已经生效
    }
  }

  return { user, loadFromStorage, loginAction, registerAction, logout, updateLocale, updateAiRegion }
})
