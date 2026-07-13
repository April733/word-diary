<template>
  <div class="login-container">
    <div class="login-card">
      <div class="locale-bar">
        <LocaleSwitcher />
      </div>
      <h1 class="app-title">{{ $t('common.appName') }}</h1>
      <p class="app-desc">{{ $t('login.subtitle') }}</p>

      <t-tabs v-model="tab" class="login-tabs">
        <t-tab-panel value="login" :label="$t('login.loginBtn')">
          <t-form :data="loginForm" @submit="handleLogin">
            <t-form-item :label="$t('login.username')" name="username">
              <t-input v-model="loginForm.username" :placeholder="$t('login.usernamePlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.password')" name="password">
              <t-input v-model="loginForm.password" type="password" :placeholder="$t('login.passwordPlaceholder')" />
            </t-form-item>
            <t-form-item class="submit-item">
              <t-button theme="primary" type="submit" block :loading="loading">{{ $t('login.loginBtn') }}</t-button>
            </t-form-item>
          </t-form>
        </t-tab-panel>

        <t-tab-panel value="register" :label="$t('login.registerBtn')">
          <t-form :data="registerForm" @submit="handleRegister">
            <t-form-item :label="$t('login.username')" name="username">
              <t-input v-model="registerForm.username" :placeholder="$t('login.usernamePlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.nickname')" name="nickname">
              <t-input v-model="registerForm.nickname" :placeholder="$t('login.nicknamePlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.password')" name="password">
              <t-input v-model="registerForm.password" type="password" :placeholder="$t('login.passwordPlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.email')" name="email">
              <t-input v-model="registerForm.email" :placeholder="$t('login.emailPlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.isMember')" name="isMember">
              <t-switch v-model="registerForm.isMember" :label="[$t('common.yes'), $t('common.no')]" />
            </t-form-item>
            <t-form-item class="submit-item">
              <t-button theme="primary" type="submit" block :loading="loading">{{ $t('login.registerBtn') }}</t-button>
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
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { useUserStore } from '../stores/user'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const tab = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', nickname: '', password: '', email: '', isMember: false })

async function handleLogin() {
  if (!loginForm.username || !loginForm.password) {
    MessagePlugin.warning(t('login.error'))
    return
  }
  loading.value = true
  try {
    await userStore.loginAction(loginForm.username, loginForm.password)
    MessagePlugin.success(t('login.loginSuccess'))
    router.push('/')
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || t('login.error'))
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerForm.username || !registerForm.password) {
    MessagePlugin.warning(t('login.error'))
    return
  }
  loading.value = true
  try {
    await userStore.registerAction({ ...registerForm, isMember: registerForm.isMember ? 1 : 0 })
    MessagePlugin.success(t('login.registerSuccess'))
    tab.value = 'login'
    loginForm.username = registerForm.username
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || t('login.error'))
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
  background: linear-gradient(135deg, #6a82fb 0%, #8458B3 50%, #5b3fa8 100%);
  background-attachment: fixed;
}

.login-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 40px 36px;
  width: 420px;
  box-shadow:
    0 24px 48px -12px rgba(50, 30, 100, 0.25),
    0 12px 24px -6px rgba(80, 50, 150, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.6) inset;
  backdrop-filter: blur(8px);
}

.locale-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.app-title {
  text-align: center;
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #1a1a2e;
  letter-spacing: 0.5px;
}

.app-desc {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 28px;
}

.login-tabs {
  width: 100%;
}

.login-tabs :deep(.t-tab-panel) {
  padding-top: 12px;
}

.login-tabs :deep(.t-form__item) {
  margin-bottom: 20px;
}

.submit-item {
  margin-top: 12px !important;
  margin-bottom: 0 !important;
}

.login-tabs :deep(.t-form__label) {
  margin-bottom: 4px;
}

.login-tabs :deep(.t-input) {
  border-radius: 8px;
}

.login-tabs :deep(.t-button--theme-primary) {
  border-radius: 8px;
  font-weight: 500;
}
</style>
