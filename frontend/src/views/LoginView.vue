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
            ref="registerFormRef"
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

            <!-- 注册方式切换：手机号 / 邮箱 -->
            <t-form-item :label="$t('login.registerType')" required-mark>
              <t-radio-group v-model="registerType" variant="default-filled" @change="onRegisterTypeChange">
                <t-radio-button value="phone">{{ $t('login.phone') }}</t-radio-button>
                <t-radio-button value="email">{{ $t('login.email') }}</t-radio-button>
              </t-radio-group>
            </t-form-item>

            <!-- 手机号输入 -->
            <t-form-item v-if="registerType === 'phone'" :label="$t('login.phone')" name="phone" required-mark>
              <t-input v-model="registerForm.phone" :placeholder="$t('login.phonePlaceholder')" />
            </t-form-item>
            <!-- 邮箱输入 -->
            <t-form-item v-if="registerType === 'email'" :label="$t('login.email')" name="email" required-mark>
              <t-input v-model="registerForm.email" :placeholder="$t('login.emailPlaceholder')" />
            </t-form-item>

            <!-- 验证码 -->
            <t-form-item :label="$t('login.verifyCode')" name="code" required-mark>
              <div class="sms-row">
                <t-input v-model="registerForm.code" :placeholder="$t('login.verifyCodePlaceholder')" class="sms-input" maxlength="6" />
                <t-button
                  variant="outline"
                  class="sms-btn"
                  :disabled="codeCooldown > 0 || !canSendCode"
                  :loading="codeSending"
                  @click="handleSendCode"
                >
                  {{ codeCooldown > 0 ? $t('login.sendSmsRetry', { seconds: codeCooldown }) : $t('login.sendCode') }}
                </t-button>
              </div>
            </t-form-item>

            <t-form-item :label="$t('login.inviteCode')" name="inviteCode" required-mark :help="$t('login.inviteCodeHint')">
              <t-input v-model="registerForm.inviteCode" :placeholder="$t('login.inviteCodePlaceholder')" />
            </t-form-item>
            <t-form-item class="submit-item">
              <t-button theme="primary" block :loading="loading" @click="handleRegisterClick">{{ $t('login.registerBtn') }}</t-button>
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
      <!-- 报错时让用户可以直接联系管理员 -->
      <t-dialog v-model:visible="showReportDialog" header="问题反馈" width="520px" :confirm-btn="{ content: '提交反馈', disabled: !reportForm.description }" @confirm="submitReport">
        <div class="feedback-form">
          <t-form :data="reportForm" label-width="120px">
            <t-form-item label="联系邮箱">
              <t-input v-model="reportForm.reporterEmail" placeholder="填写邮箱方便管理员回复（选填）" />
            </t-form-item>
            <t-form-item label="反馈标题">
              <t-input v-model="reportForm.subject" placeholder="请简要描述问题" />
            </t-form-item>
            <t-form-item label="详细描述">
              <t-textarea v-model="reportForm.description" rows="6" placeholder="请详细描述您遇到的问题，便于我们快速定位和修复..." />
            </t-form-item>
          </t-form>
        </div>
      </t-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import type { FormInstanceFunctions } from 'tdesign-vue-next'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { reportIssue, reportIssuePublic } from '../api'
import { useUserStore } from '../stores/user'
import { checkPhone, checkEmail, checkInviteCode, sendSms, sendMailCode, sendResetSms, resetPassword } from '../api'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const registerFormRef = ref<FormInstanceFunctions>()
const tab = ref('login')
const loading = ref(false)
const codeSending = ref(false)
const codeCooldown = ref(0)
const forgotSmsSending = ref(false)
const forgotCooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null
let forgotCooldownTimer: ReturnType<typeof setInterval> | null = null

const loginForm = reactive({ username: '', password: '' })
const registerType = ref('phone') // 'phone' | 'email'
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  email: '',
  code: '',
  inviteCode: ''
})
// report dialog state
const showReportDialog = ref(false)
const reportForm = reactive({
  reporterEmail: '',
  subject: '',
  description: ''
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
// 简单的手机号格式校验
const phonePattern = /^\+?\d{6,20}$/
// 邮箱格式校验
const emailPattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/

// 是否可以发送验证码
const canSendCode = computed(() => {
  if (registerType.value === 'phone') {
    return phonePattern.test(registerForm.phone)
  }
  return emailPattern.test(registerForm.email)
})

/** 切换注册方式时清空对应字段 */
function onRegisterTypeChange() {
  registerForm.phone = ''
  registerForm.email = ''
  registerForm.code = ''
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  codeCooldown.value = 0
}

/** 手机号异步校验是否已注册 */
async function phoneExistsValidator(val: string) {
  if (!val || !phonePattern.test(val)) return { result: true as const }
  try {
    const res = await checkPhone(val)
    if (res.data?.data === true) {
      return { result: false as const, message: t('login.errorPhoneExists') }
    }
    return { result: true as const }
  } catch (e) {
    const status = (e as any)?.response?.status
    if (status === 401) {
      return { result: false as const, message: t('login.errorPhoneExists') }
    }
    // 网络异常时不阻断提交，由后端兜底
    return { result: true as const }
  }
}

/** 邮箱异步校验是否已注册 */
async function emailExistsValidator(val: string) {
  if (!val || !emailPattern.test(val)) return { result: true as const }
  try {
    const res = await checkEmail(val)
    if (res.data?.data === true) {
      return { result: false as const, message: t('login.errorEmailExists') }
    }
    return { result: true as const }
  } catch (e) {
    const status = (e as any)?.response?.status
    if (status === 401) {
      return { result: false as const, message: t('login.errorEmailExists') }
    }
    return { result: true as const }
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
    // Network error → don't block registration, let backend handle it
    return { result: true }
  }
}

const registerRules = computed(() => {
  const submitTriggers = ['blur', 'submit'] as const
  const rules: any = {
    username: [
      { required: true, message: t('login.errorUsernameRequired'), trigger: submitTriggers },
      { pattern: usernamePattern, message: t('login.errorUsernameFormat'), trigger: submitTriggers }
    ],
    password: [
      { required: true, message: t('login.errorPasswordRequired'), trigger: submitTriggers },
      { pattern: passwordPattern, message: t('login.errorPasswordFormat'), trigger: submitTriggers }
    ],
    confirmPassword: [
      { required: true, message: t('login.errorConfirmPasswordRequired'), trigger: submitTriggers },
      { validator: (val: string) => val === registerForm.password, message: t('login.errorPasswordMismatch'), trigger: submitTriggers }
    ],
    code: [
      { required: true, message: t('login.errorVerifyCodeRequired'), trigger: submitTriggers },
      { pattern: /^\d{6}$/, message: t('login.errorVerifyCodeFormat'), trigger: submitTriggers }
    ],
    inviteCode: [
      { required: true, message: t('login.errorInviteCodeRequired'), trigger: 'blur' },
      { validator: inviteCodeValidator, message: t('login.errorInviteCodeFormat'), trigger: 'blur' },
      { validator: inviteCodeValidator, message: t('login.errorInviteCodeFormat'), trigger: 'change' },
      { validator: inviteCodeValidator, message: t('login.errorInviteCodeFormat'), trigger: 'submit' }
    ]
  }
  if (registerType.value === 'phone') {
    rules.phone = [
      { required: true, message: t('login.errorPhoneRequired'), trigger: 'blur' },
      { pattern: phonePattern, message: t('login.errorPhoneFormat'), trigger: 'blur' },
      { validator: phoneExistsValidator, message: t('login.errorPhoneExists'), trigger: 'blur' },
      { validator: phoneExistsValidator, message: t('login.errorPhoneExists'), trigger: 'change' },
      { validator: phoneExistsValidator, message: t('login.errorPhoneExists'), trigger: 'submit' }
    ]
  } else {
    rules.email = [
      { required: true, message: t('login.errorEmailRequired'), trigger: 'blur' },
      { pattern: emailPattern, message: t('login.errorEmailFormat'), trigger: 'blur' },
      { validator: emailExistsValidator, message: t('login.errorEmailExists'), trigger: 'blur' },
      { validator: emailExistsValidator, message: t('login.errorEmailExists'), trigger: 'change' },
      { validator: emailExistsValidator, message: t('login.errorEmailExists'), trigger: 'submit' }
    ]
  }
  return rules
})

/** 发送验证码（短信或邮箱） */
async function handleSendCode() {
  if (registerType.value === 'phone') {
    if (!phonePattern.test(registerForm.phone)) {
      MessagePlugin.warning(t('login.errorPhoneFormat'))
      return
    }
  } else {
    if (!emailPattern.test(registerForm.email)) {
      MessagePlugin.warning(t('login.errorEmailFormat'))
      return
    }
  }
  codeSending.value = true
  try {
    if (registerType.value === 'phone') {
      await sendSms(registerForm.phone)
    } else {
      await sendMailCode(registerForm.email)
    }
    MessagePlugin.success(t('login.sendCodeSuccess'))
    codeCooldown.value = 60
    cooldownTimer = setInterval(() => {
      codeCooldown.value--
      if (codeCooldown.value <= 0) {
        if (cooldownTimer) {
          clearInterval(cooldownTimer)
          cooldownTimer = null
        }
      }
    }, 1000)
  } catch (e: any) {
    const status = e?.response?.status
    const msg = e?.response?.data?.message || e.message || t('login.sendCodeFailed')
    if (status === 503) {
      reportForm.subject = '邮件服务未配置 - 无法发送验证码'
      reportForm.description = `我在注册页面尝试给邮箱 ${registerForm.email} 发送验证码时遇到错误：\n\n${msg}\n\n请管理员检查邮件服务配置。`
      reportForm.reporterEmail = registerForm.email || ''
      showReportDialog.value = true
    } else {
      MessagePlugin.error(msg)
    }
  } finally {
    codeSending.value = false
  }
}

/** 提交反馈到后台 */
async function submitReport() {
  try {
    const payload = {
      username: userStore.user?.username || (reportForm.reporterEmail || '匿名用户'),
      description: reportForm.description,
      errorDetail: `用户邮箱: ${reportForm.reporterEmail || ''}`,
      subject: reportForm.subject
    }
    if (userStore.user && userStore.user.username) {
      await reportIssue(payload)
    } else {
      await reportIssuePublic(payload)
    }
    MessagePlugin.success('反馈已提交，感谢您的建议')
    showReportDialog.value = false
    reportForm.subject = ''
    reportForm.description = ''
    reportForm.reporterEmail = ''
  } catch (err: any) {
    MessagePlugin.error(err.response?.data?.message || '提交失败')
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

/** 点击注册：先校验表单，通过后再提交 */
async function handleRegisterClick() {
  const form = registerFormRef.value
  if (!form) {
    await handleRegister()
    return
  }
  const result = await form.validate()
  if (result !== true) {
    MessagePlugin.warning(t('login.errorFormInvalid'))
    return
  }
  await handleRegister()
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
    const msg = e.response?.data?.message || e.message || t('login.error')
    MessagePlugin.error(msg)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  // 二次前端兜底校验
  const missingFields = []
  if (!registerForm.username) missingFields.push(t('login.username'))
  if (!registerForm.password) missingFields.push(t('login.password'))
  if (!registerForm.confirmPassword) missingFields.push(t('login.confirmPassword'))
  if (registerType.value === 'phone' && !registerForm.phone) missingFields.push(t('login.phone'))
  if (registerType.value === 'email' && !registerForm.email) missingFields.push(t('login.email'))
  if (!registerForm.code) missingFields.push(t('login.verifyCode'))
  if (!registerForm.inviteCode) missingFields.push(t('login.inviteCode'))

  if (missingFields.length > 0) {
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
  if (registerType.value === 'phone' && !phonePattern.test(registerForm.phone)) {
    MessagePlugin.warning(t('login.errorPhoneFormat'))
    return
  }
  if (registerType.value === 'email' && !emailPattern.test(registerForm.email)) {
    MessagePlugin.warning(t('login.errorEmailFormat'))
    return
  }
  if (!/^\d{6}$/.test(registerForm.code)) {
    MessagePlugin.warning(t('login.errorVerifyCodeFormat'))
    return
  }

  loading.value = true
  try {
    await userStore.registerAction({
      username: registerForm.username,
      password: registerForm.password,
      phone: registerType.value === 'phone' ? registerForm.phone : null,
      email: registerType.value === 'email' ? registerForm.email : null,
      code: registerForm.code,
      inviteCode: registerForm.inviteCode,
      registerType: registerType.value
    })
    MessagePlugin.success(t('login.registerSuccess'))
    // 注册成功后自动切到登录 tab，并预填用户名方便用户登录
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
  min-width: 96px;
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

.feedback-form {
  padding: 8px 4px 0 4px;
}

.feedback-form :deep(.t-form__item) {
  margin-bottom: 20px;
}

.feedback-form :deep(.t-form__label) {
  font-weight: 500;
  color: #374151;
}
</style>
