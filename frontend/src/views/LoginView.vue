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
            <t-form-item :label="$t('login.smsCode')" name="smsCode" required-mark>
              <div class="sms-row">
                <t-input v-model="registerForm.smsCode" :placeholder="$t('login.smsCodePlaceholder')" class="sms-input" maxlength="6" />
                <t-button
                  variant="outline"
                  class="sms-btn"
                  :disabled="smsCooldown > 0 || !phonePattern.test(registerForm.phone)"
                  :loading="smsSending"
                  @click="handleSendSms"
                >
                  {{ smsCooldown > 0 ? $t('login.sendSmsRetry', { seconds: smsCooldown }) : $t('login.sendSms') }}
                </t-button>
              </div>
            </t-form-item>
            <t-form-item :label="$t('login.inviteCode')" name="inviteCode" required-mark :help="$t('login.inviteCodeHint')">
              <t-input v-model="registerForm.inviteCode" :placeholder="$t('login.inviteCodePlaceholder')" />
            </t-form-item>
            <t-form-item class="submit-item">
              <t-button theme="primary" type="submit" block :loading="loading">{{ $t('login.registerBtn') }}</t-button>
            </t-form-item>
          </t-form>
        </t-tab-panel>

        <t-tab-panel value="forgot" :label="$t('login.forgotPassword')">
          <p class="app-desc">{{ $t('login.forgotPasswordDesc') }}</p>
          <t-form :data="forgotForm" @submit="handleForgotPassword">
            <t-form-item :label="$t('login.phone')" name="phone" required-mark>
              <t-input v-model="forgotForm.phone" :placeholder="$t('login.phonePlaceholder')" />
            </t-form-item>
            <t-form-item :label="$t('login.smsCode')" name="smsCode" required-mark>
              <div class="sms-row">
                <t-input v-model="forgotForm.smsCode" :placeholder="$t('login.smsCodePlaceholder')" class="sms-input" maxlength="6" />
                <t-button
                  variant="outline"
                  class="sms-btn"
                  :disabled="forgotCooldown > 0 || !phonePattern.test(forgotForm.phone)"
                  :loading="forgotSmsSending"
                  @click="handleSendForgotSms"
                >
                  {{ forgotCooldown > 0 ? $t('login.sendSmsRetry', { seconds: forgotCooldown }) : $t('login.sendSms') }}
                </t-button>
              </div>
            </t-form-item>
            <t-form-item :label="$t('login.newPassword')" name="newPassword" required-mark :help="$t('login.passwordHint')">
              <t-input v-model="forgotForm.newPassword" type="password" :placeholder="$t('login.newPasswordPlaceholder')" />
            </t-form-item>
            <t-form-item class="submit-item">
              <t-button theme="primary" type="submit" block :loading="loading">{{ $t('login.forgotPasswordBtn') }}</t-button>
            </t-form-item>
          </t-form>
        </t-tab-panel>
      </t-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { useUserStore } from '../stores/user'
import { checkPhone, checkInviteCode, sendSms, sendResetSms, resetPassword } from '../api'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const tab = ref('login')
const loading = ref(false)
const smsSending = ref(false)
const smsCooldown = ref(0)
const forgotSmsSending = ref(false)
const forgotCooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null
let forgotCooldownTimer: ReturnType<typeof setInterval> | null = null

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  smsCode: '',
  inviteCode: ''
})
const forgotForm = reactive({
  phone: '',
  smsCode: '',
  newPassword: ''
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
  smsCode: [
    { required: true, message: t('login.errorSmsCodeRequired'), trigger: 'blur' },
    {
      pattern: /^\d{6}$/,
      message: t('login.errorSmsCodeFormat'),
      trigger: 'blur'
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

/** 发送短信验证码 */
async function handleSendSms() {
  if (!phonePattern.test(registerForm.phone)) {
    MessagePlugin.warning(t('login.errorPhoneFormat'))
    return
  }
  smsSending.value = true
  try {
    const res = await sendSms(registerForm.phone)
    console.log('sendSms 完整响应:', JSON.stringify(res.data, null, 2))
    MessagePlugin.success(t('login.sendSmsSuccess'))
    smsCooldown.value = 60
    cooldownTimer = setInterval(() => {
      smsCooldown.value--
      if (smsCooldown.value <= 0) {
        if (cooldownTimer) {
          clearInterval(cooldownTimer)
          cooldownTimer = null
        }
      }
    }, 1000)
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || t('login.sendSmsFailed'))
  } finally {
    smsSending.value = false
  }
}

onBeforeUnmount(() => {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  if (forgotCooldownTimer) {
    clearInterval(forgotCooldownTimer)
    forgotCooldownTimer = null
  }
})

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
    const msg = e.response?.data?.message || e.message || t('login.error')
    MessagePlugin.error(msg)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  // 二次前端兜底校验
  if (!registerForm.username || !registerForm.password || !registerForm.confirmPassword || !registerForm.phone || !registerForm.smsCode || !registerForm.inviteCode) {
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
  if (!/^\d{6}$/.test(registerForm.smsCode)) {
    MessagePlugin.warning(t('login.errorSmsCodeFormat'))
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
      smsCode: registerForm.smsCode,
      inviteCode: registerForm.inviteCode
    })
    MessagePlugin.success(t('login.registerSuccess'))
    tab.value = 'login'
    loginForm.username = registerForm.username
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('login.error')
    MessagePlugin.error(msg)
  } finally {
    loading.value = false
  }
}

/** 发送密码找回短信验证码 */
async function handleSendForgotSms() {
  if (!phonePattern.test(forgotForm.phone)) {
    MessagePlugin.warning(t('login.errorPhoneFormat'))
    return
  }
  forgotSmsSending.value = true
  try {
    await sendResetSms(forgotForm.phone)
    MessagePlugin.success(t('login.sendSmsSuccess'))
    forgotCooldown.value = 60
    forgotCooldownTimer = setInterval(() => {
      forgotCooldown.value--
      if (forgotCooldown.value <= 0) {
        if (forgotCooldownTimer) {
          clearInterval(forgotCooldownTimer)
          forgotCooldownTimer = null
        }
      }
    }, 1000)
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('login.sendSmsFailed')
    MessagePlugin.error(msg)
  } finally {
    forgotSmsSending.value = false
  }
}

/** 密码找回提交 */
async function handleForgotPassword() {
  if (!phonePattern.test(forgotForm.phone)) {
    MessagePlugin.warning(t('login.errorPhoneFormat'))
    return
  }
  if (!/^\d{6}$/.test(forgotForm.smsCode)) {
    MessagePlugin.warning(t('login.errorSmsCodeFormat'))
    return
  }
  if (!passwordPattern.test(forgotForm.newPassword)) {
    MessagePlugin.warning(t('login.errorPasswordFormat'))
    return
  }

  loading.value = true
  try {
    await resetPassword({
      phone: forgotForm.phone,
      smsCode: forgotForm.smsCode,
      newPassword: forgotForm.newPassword
    })
    MessagePlugin.success(t('login.resetPasswordSuccess'))
    tab.value = 'login'
    loginForm.username = ''
    loginForm.password = ''
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || t('login.error')
    MessagePlugin.error(msg)
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
  padding: 40px 36px 48px 36px;
  width: 460px;
  box-shadow:
    0 24px 48px -12px rgba(50, 30, 100, 0.25),
    0 12px 24px -6px rgba(80, 50, 150, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.6) inset;
  backdrop-filter: blur(8px);
  box-sizing: border-box;
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
  margin-bottom: 16px;
}

.login-tabs {
  width: 100%;
}

.login-tabs :deep(.t-tab-panel) {
  padding-top: 12px;
  padding-bottom: 0;
}

.login-tabs :deep(.t-form__item) {
  margin-bottom: 24px;
}

.submit-item {
  margin-top: 24px !important;
  margin-bottom: 4px !important;
}

.sms-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.sms-input {
  flex: 1;
  min-width: 0;
}

.sms-btn {
  flex-shrink: 0;
  white-space: nowrap;
  padding: 0 16px;
}

.login-tabs :deep(.t-form__label) {
  margin-bottom: 6px;
  font-weight: 500;
  color: #374151;
}

.login-tabs :deep(.t-form__help) {
  margin-top: 6px;
  line-height: 1.4;
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
