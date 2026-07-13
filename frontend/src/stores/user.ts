import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '../api'
import { getStoredLocale, setStoredLocale, type SupportedLocale } from '../i18n'

interface UserInfo {
  id: number
  username: string
  nickname: string
  token: string
  isMember: number
  preferredLocale?: string
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
  }

  async function registerAction(data: any) {
    await userApi.register(data)
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

  return { user, loadFromStorage, loginAction, registerAction, logout, updateLocale }
})
