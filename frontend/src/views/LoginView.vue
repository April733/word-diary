<template>
  <div class="login-container">
    <div class="login-card">
      <h1 class="app-title">📖 Word Diary</h1>
      <p class="app-desc">基于艾宾浩斯记忆曲线的背单词系统</p>

      <t-tabs v-model="tab" class="login-tabs">
        <t-tab-panel value="login" label="登录">
          <t-form :data="loginForm" @submit="handleLogin">
            <t-form-item label="用户名" name="username">
              <t-input v-model="loginForm.username" placeholder="请输入用户名" />
            </t-form-item>
            <t-form-item label="密码" name="password">
              <t-input v-model="loginForm.password" type="password" placeholder="请输入密码" />
            </t-form-item>
            <t-form-item>
              <t-button theme="primary" type="submit" block :loading="loading">登录</t-button>
            </t-form-item>
          </t-form>
        </t-tab-panel>

        <t-tab-panel value="register" label="注册">
          <t-form :data="registerForm" @submit="handleRegister">
            <t-form-item label="用户名" name="username">
              <t-input v-model="registerForm.username" placeholder="请输入用户名" />
            </t-form-item>
            <t-form-item label="昵称" name="nickname">
              <t-input v-model="registerForm.nickname" placeholder="请输入昵称（选填）" />
            </t-form-item>
            <t-form-item label="密码" name="password">
              <t-input v-model="registerForm.password" type="password" placeholder="请输入密码" />
            </t-form-item>
            <t-form-item label="邮箱" name="email">
              <t-input v-model="registerForm.email" placeholder="请输入邮箱（选填）" />
            </t-form-item>
            <t-form-item>
              <t-button theme="primary" type="submit" block :loading="loading">注册</t-button>
            </t-form-item>
          </t-form>
        </t-tab-panel>
      </t-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const tab = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', nickname: '', password: '', email: '' })

async function handleLogin() {
  if (!loginForm.username || !loginForm.password) {
    MessagePlugin.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.loginAction(loginForm.username, loginForm.password)
    MessagePlugin.success('登录成功')
    router.push('/')
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerForm.username || !registerForm.password) {
    MessagePlugin.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.registerAction({ ...registerForm })
    MessagePlugin.success('注册成功，请登录')
    tab.value = 'login'
    loginForm.username = registerForm.username
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  background: #fff;
  border-radius: 16px;
  padding: 48px 40px;
  width: 420px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}

.app-title {
  text-align: center;
  font-size: 28px;
  margin-bottom: 8px;
  color: #1a1a2e;
}

.app-desc {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 32px;
}

.login-tabs {
  width: 100%;
}
</style>
