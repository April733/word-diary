import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '../api'

interface UserInfo {
  id: number
  username: string
  nickname: string
  token: string
  isMember: number
}

export const useUserStore = defineStore('user', () => {
  const user = ref<UserInfo | null>(null)

  function loadFromStorage() {
    const stored = localStorage.getItem('user')
    if (stored) {
      user.value = JSON.parse(stored)
    }
  }

  async function loginAction(username: string, password: string) {
    const res = await userApi.login({ username, password })
    user.value = res.data.data
    localStorage.setItem('token', user.value!.token)
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  async function registerAction(data: any) {
    await userApi.register(data)
  }

  function logout() {
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { user, loadFromStorage, loginAction, registerAction, logout }
})
