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
            <t-form-item :label="$t('login.username')" name="username" required-mark>
              <t-input v-model="loginForm.username" :placeholder="$t('login.usernamePlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.password')" name="password" required-mark>
              <t-input v-model="loginForm.password" type="password" :placeholder="$t('login.passwordPlaceholder')" />
            </t-form-item>
            <t-form-item class="submit-item">
              <t-button theme="primary" type="submit" block :loading="loading">{{ $t('login.loginBtn') }}</t-button>
            </t-form-item>
          </t-form>
        </t-tab-panel>

        <t-tab-panel value="register" :label="$t('login.registerBtn')">
          <t-form
            :data="registerForm"
            :rules="registerRules"
            @submit="handleRegister"
          >
            <t-form-item :label="$t('login.username')" name="username" required-mark :help="$t('login.usernameHint')">
              <t-input v-model="registerForm.username" :placeholder="$t('login.usernamePlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.password')" name="password" required-mark :help="$t('login.passwordHint')">
              <t-input
                v-model="registerForm.password"
                type="password"
                :placeholder="$t('login.passwordPlaceholder')"
              />
            </t-form-item>
            <t-form-item :label="$t('login.confirmPassword')" name="confirmPassword" required-mark>
              <t-input
                v-model="registerForm.confirmPassword"
                type="password"
                :placeholder="$t('login.confirmPasswordPlaceholder')"
              />
            </t-form-item>
            <t-form-item :label="$t('login.phone')" name="phone" required-mark>
              <t-input v-model="registerForm.phone" :placeholder="$t('login.phonePlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.inviteCode')" name="inviteCode" required-mark :help="$t('login.inviteCodeHint')">
              <t-input v-model="registerForm.inviteCode" :placeholder="$t('login.inviteCodePlaceholder')" />
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
import { checkPhone, checkInviteCode } from '../api'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const tab = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  inviteCode: ''
})

// 用户名：4-32位，字母开头，支持字母/数字/下划线
const usernamePattern = /^[a-zA-Z][a-zA-Z0-9_]{3,31}$/
// 密码：6-20位，必须包含大小写字母、数字和符号
const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).{6,20}$/
// 简单的手机号格式校验（不发验证码）
const phonePattern = /^\+?\d{6,20}$/

/** 手机号失焦时异步校验是否已注册 */
async function phoneExistsValidator(val: string) {
  if (!val || !phonePattern.test(val)) return { result: true }
  try {
    const res = await checkPhone(val)
    if (res.data?.data === true) {
      return { result: false, message: t('login.errorPhoneExists') }
    }
    return { result: true }
  } catch (e) {
    // 401 时 axios 拦截器已跳转 /login，这里返回 false 阻断提交
    const status = (e as any)?.response?.status
    if (status === 401) {
      return { result: false, message: t('login.errorPhoneExists') }
    }
    // 其它网络异常：抛出错误让 t-form 显示并阻断提交
    throw e
  }
}

/** 邀请码异步校验：调后端 check-invite-code 确认存在且未被核销 */
async function inviteCodeValidator(val: string) {
  if (!val) return { result: true } // required 规则会处理空值
  if (val.length < 4 || val.length > 32) {
    return { result: false, message: t('login.errorInviteCodeFormat') }
  }
  try {
    const res = await checkInviteCode(val)
    if (res.data?.data !== true) {
      return { result: false, message: t('login.errorInviteCodeInvalid') }
    }
    return { result: true }
  } catch (e) {
    const status = (e as any)?.response?.status
    if (status === 401) {
      return { result: false, message: t('login.errorInviteCodeInvalid') }
    }
    throw e
  }
}

const registerRules = {
  username: [
    { required: true, message: t('login.errorUsernameRequired'), trigger: 'blur' },
    {
      pattern: usernamePattern,
      message: t('login.errorUsernameFormat'),
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: t('login.errorPasswordRequired'), trigger: 'blur' },
    {
      pattern: passwordPattern,
      message: t('login.errorPasswordFormat'),
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: t('login.errorConfirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (val: string) => val === registerForm.password,
      message: t('login.errorPasswordMismatch'),
      trigger: 'blur'
    }
  ],
  phone: [
    { required: true, message: t('login.errorPhoneRequired'), trigger: 'blur' },
    {
      pattern: phonePattern,
      message: t('login.errorPhoneFormat'),
      trigger: 'blur'
    },
    {
      validator: phoneExistsValidator,
      message: t('login.errorPhoneExists'),
      trigger: 'blur'
    },
    // input/change 时也校验，提交时再次校验
    {
      validator: phoneExistsValidator,
      message: t('login.errorPhoneExists'),
      trigger: 'change'
    },
    {
      validator: phoneExistsValidator,
      message: t('login.errorPhoneExists'),
      trigger: 'submit'
    }
  ],
  inviteCode: [
    { required: true, message: t('login.errorInviteCodeRequired'), trigger: 'blur' },
    {
      validator: inviteCodeValidator,
      message: t('login.errorInviteCodeFormat'),
      trigger: 'blur'
    },
    {
      validator: inviteCodeValidator,
      message: t('login.errorInviteCodeFormat'),
      trigger: 'change'
    },
    {
      validator: inviteCodeValidator,
      message: t('login.errorInviteCodeFormat'),
      trigger: 'submit'
    }
  ]
}

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
  // 二次前端兜底校验
  if (!registerForm.username || !registerForm.password || !registerForm.confirmPassword || !registerForm.phone || !registerForm.inviteCode) {
    MessagePlugin.warning(t('login.errorAllRequired'))
    return
  }
  if (!usernamePattern.test(registerForm.username)) {
    MessagePlugin.warning(t('login.errorUsernameFormat'))
    return
  }
  if (!passwordPattern.test(registerForm.password)) {
    MessagePlugin.warning(t('login.errorPasswordFormat'))
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    MessagePlugin.warning(t('login.errorPasswordMismatch'))
    return
  }
  if (!phonePattern.test(registerForm.phone)) {
    MessagePlugin.warning(t('login.errorPhoneFormat'))
    return
  }

  loading.value = true
  try {
    // 1. 手机号查重
    const phoneCheck = await checkPhone(registerForm.phone)
    if (phoneCheck.data?.data === true) {
      MessagePlugin.error(t('login.errorPhoneExists'))
      return
    }

    // 2. 邀请码有效性
    const inviteCheck = await checkInviteCode(registerForm.inviteCode)
    if (inviteCheck.data?.data !== true) {
      MessagePlugin.error(t('login.errorInviteCodeInvalid'))
      return
    }

    await userStore.registerAction({
      username: registerForm.username,
      password: registerForm.password,
      phone: registerForm.phone,
      inviteCode: registerForm.inviteCode
    })
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

/* TDesign required-mark 自带红色 *，并保留一定字距 */
.login-tabs :deep(.t-form__label--required::before) {
  color: #e34d59;
  margin-right: 4px;
  font-weight: 600;
}

.login-tabs :deep(.t-input) {
  border-radius: 8px;
}

.login-tabs :deep(.t-button--theme-primary) {
  border-radius: 8px;
  font-weight: 500;
}
</style>
