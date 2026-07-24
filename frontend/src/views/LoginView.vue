<template>
  <div class="login-page">
    <div class="page-bg" aria-hidden="true">
      <div class="bg-wash" />
    </div>

    <div class="login-shell" :class="{ 'login-shell--wide': tab !== 'login' }">
      <div class="login-card">
        <div class="card-top">
          <div class="card-brand">
            <img class="card-logo" src="/brand/logo-circle-64.png" width="48" height="48" alt="" decoding="async" />
            <div class="card-brand-text">
              <h1 class="product-title">{{ $t('login.productTitle') }}</h1>
              <p class="product-tagline">{{ $t('login.brandTagline') }}</p>
            </div>
          </div>
          <LocaleSwitcher class="locale-switch" />
        </div>

      <template v-if="tab === 'login'">
        <t-form
          class="login-form"
          label-align="right"
          label-width="80px"
          :data="loginForm"
          @submit="handleLogin"
        >
          <t-form-item :label="$t('login.username')" name="username">
            <t-input v-model="loginForm.username" :placeholder="$t('login.usernamePlaceholder')" size="large" />
          </t-form-item>
          <t-form-item :label="$t('login.password')" name="password">
            <t-input v-model="loginForm.password" type="password" :placeholder="$t('login.passwordPlaceholder')" size="large" />
          </t-form-item>
          <div class="form-extra">
            <button type="button" class="link-btn" @click="tab = 'forgot'">{{ $t('login.forgotPassword') }}</button>
          </div>
          <t-button class="primary-btn" theme="primary" type="submit" block size="large" :loading="loading">
            {{ $t('login.loginBtn') }}
          </t-button>
        </t-form>
        <p class="card-foot">
          {{ $t('login.noAccount') }}
          <button type="button" class="link-btn go-register" @click="tab = 'register'">{{ $t('login.goRegister') }}</button>
          <span class="foot-sep">·</span>
          <router-link class="link-btn terms-link" to="/terms">{{ $t('login.termsLink') }}</router-link>
          <span class="foot-sep">·</span>
          <router-link class="link-btn terms-link" to="/report">{{ $t('login.reportLink') }}</router-link>
        </p>
      </template>

      <template v-else-if="tab === 'register'">
        <t-form
          ref="registerFormRef"
          class="login-form login-form--register"
          label-align="right"
          label-width="96px"
          :data="registerForm"
          :rules="registerRules"
          @submit="handleRegister"
        >
          <t-form-item :label="$t('login.username')" name="username" required-mark :help="$t('login.usernameHint')">
            <t-input v-model="registerForm.username" :placeholder="$t('login.usernamePlaceholder')" size="large" />
          </t-form-item>
          <t-form-item :label="$t('login.password')" name="password" required-mark :help="$t('login.passwordHint')">
            <t-input v-model="registerForm.password" type="password" :placeholder="$t('login.passwordPlaceholder')" size="large" />
          </t-form-item>
          <t-form-item :label="$t('login.confirmPassword')" name="confirmPassword" required-mark>
            <t-input v-model="registerForm.confirmPassword" type="password" :placeholder="$t('login.confirmPasswordPlaceholder')" size="large" />
          </t-form-item>
          <t-form-item :label="$t('login.registerType')" required-mark>
            <t-radio-group v-model="registerType" variant="default-filled" @change="onRegisterTypeChange">
              <t-radio-button value="phone" :disabled="!phoneRegisterAllowed">{{ $t('login.phone') }}</t-radio-button>
              <t-radio-button value="email">{{ $t('login.email') }}</t-radio-button>
            </t-radio-group>
          </t-form-item>
          <t-form-item v-if="registerType === 'phone'" :label="$t('login.phone')" name="phone" required-mark>
            <t-input v-model="registerForm.phone" :placeholder="$t('login.phonePlaceholder')" size="large" />
          </t-form-item>
          <t-form-item v-if="registerType === 'email'" :label="$t('login.email')" name="email" required-mark>
            <t-input v-model="registerForm.email" :placeholder="$t('login.emailPlaceholder')" size="large" />
          </t-form-item>
          <t-form-item :label="$t('login.verifyCode')" name="code" required-mark>
            <div class="sms-row">
              <t-input v-model="registerForm.code" :placeholder="$t('login.verifyCodePlaceholder')" class="sms-input" maxlength="6" size="large" />
              <t-button variant="outline" class="sms-btn" :disabled="codeCooldown > 0 || !canSendCode" :loading="codeSending" @click="handleSendCode">
                {{ codeCooldown > 0 ? $t('login.sendSmsRetry', { seconds: codeCooldown }) : $t('login.sendCode') }}
              </t-button>
            </div>
          </t-form-item>
          <label class="agree-row">
            <t-checkbox v-model="agreeTerms" />
            <span class="agree-text">
              {{ $t('login.agreeTermsPrefix') }}
              <router-link class="terms-link" to="/terms" target="_blank">{{ $t('login.termsLink') }}</router-link>
            </span>
          </label>
          <t-button class="primary-btn" theme="primary" block size="large" :loading="loading" @click="handleRegisterClick">
            {{ $t('login.registerBtn') }}
          </t-button>
        </t-form>
        <p class="card-foot">
          <button type="button" class="link-btn" @click="tab = 'login'">{{ $t('login.backToLogin') }}</button>
        </p>
      </template>

      <template v-else>
        <p class="panel-hint">{{ $t('login.forgotPasswordDesc') }}</p>
        <t-form
          class="login-form login-form--register"
          label-align="right"
          label-width="96px"
          :data="forgotForm"
          @submit="handleForgotPassword"
        >
          <t-form-item :label="$t('login.resetType')">
            <t-radio-group v-model="forgotType" variant="default-filled" @change="onForgotTypeChange">
              <t-radio-button value="phone">{{ $t('login.phone') }}</t-radio-button>
              <t-radio-button value="email">{{ $t('login.email') }}</t-radio-button>
            </t-radio-group>
          </t-form-item>
          <t-form-item v-if="forgotType === 'phone'" :label="$t('login.phone')" name="phone">
            <t-input v-model="forgotForm.phone" :placeholder="$t('login.phonePlaceholder')" size="large" />
          </t-form-item>
          <t-form-item v-else :label="$t('login.email')" name="email">
            <t-input v-model="forgotForm.email" :placeholder="$t('login.emailPlaceholder')" size="large" />
          </t-form-item>
          <t-form-item :label="$t('login.verifyCode')" name="code">
            <div class="sms-row">
              <t-input v-model="forgotForm.code" :placeholder="$t('login.verifyCodePlaceholder')" class="sms-input" maxlength="6" size="large" />
              <t-button
                variant="outline"
                class="sms-btn"
                :disabled="forgotCooldown > 0 || !canSendForgotCode"
                :loading="forgotCodeSending"
                @click="handleSendForgotCode"
              >
                {{ forgotCooldown > 0 ? $t('login.sendSmsRetry', { seconds: forgotCooldown }) : $t('login.sendCode') }}
              </t-button>
            </div>
          </t-form-item>
          <t-form-item :label="$t('login.newPassword')" name="newPassword" :help="$t('login.passwordHint')">
            <t-input v-model="forgotForm.newPassword" type="password" :placeholder="$t('login.newPasswordPlaceholder')" size="large" />
          </t-form-item>
          <t-button class="primary-btn" theme="primary" type="submit" block size="large" :loading="loading">
            {{ $t('login.forgotPasswordBtn') }}
          </t-button>
        </t-form>
        <p class="card-foot">
          <button type="button" class="link-btn" @click="tab = 'login'">{{ $t('login.backToLogin') }}</button>
        </p>
      </template>

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
  </div>
</template>


<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import type { FormInstanceFunctions } from 'tdesign-vue-next'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { reportIssue, reportIssuePublic } from '../api'
import { useUserStore } from '../stores/user'
import { checkPhone, checkEmail, sendSms, sendMailCode, sendResetSms, sendResetMail, resetPassword } from '../api'
import LocaleSwitcher from '../components/LocaleSwitcher.vue'
import { isPhoneRegisterAllowed } from '../utils/region'

const router = useRouter()
const userStore = useUserStore()
const { t, locale } = useI18n()
const registerFormRef = ref<FormInstanceFunctions>()
const tab = ref('login')
const agreeTerms = ref(false)
const loading = ref(false)
const codeSending = ref(false)
const codeCooldown = ref(0)
const forgotCodeSending = ref(false)
const forgotCooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null
let forgotCooldownTimer: ReturnType<typeof setInterval> | null = null

const loginForm = reactive({ username: '', password: '' })
/** 仅中文（中国）可用手机号注册；其它语言地区置灰并默认邮箱 */
const phoneRegisterAllowed = computed(() => isPhoneRegisterAllowed(locale.value))
const registerType = ref<'phone' | 'email'>(phoneRegisterAllowed.value ? 'phone' : 'email')
const forgotType = ref<'phone' | 'email'>('phone')
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  email: '',
  code: ''
})

watch(phoneRegisterAllowed, (allowed) => {
  if (!allowed && registerType.value === 'phone') {
    registerType.value = 'email'
    registerForm.phone = ''
    registerForm.code = ''
    if (cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
    codeCooldown.value = 0
  }
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
  email: '',
  code: '',
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

const canSendForgotCode = computed(() => {
  if (forgotType.value === 'phone') {
    return phonePattern.test(forgotForm.phone)
  }
  return emailPattern.test(forgotForm.email)
})

/** 切换注册方式时清空对应字段 */
function onRegisterTypeChange() {
  if (!phoneRegisterAllowed.value && registerType.value === 'phone') {
    registerType.value = 'email'
    return
  }
  registerForm.phone = ''
  registerForm.email = ''
  registerForm.code = ''
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  codeCooldown.value = 0
}

/** 切换找回方式时清空对应字段 */
function onForgotTypeChange() {
  forgotForm.phone = ''
  forgotForm.email = ''
  forgotForm.code = ''
  if (forgotCooldownTimer) {
    clearInterval(forgotCooldownTimer)
    forgotCooldownTimer = null
  }
  forgotCooldown.value = 0
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

/** 邀请码已废弃 */

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
  if (!phoneRegisterAllowed.value && registerType.value === 'phone') {
    registerType.value = 'email'
    return
  }
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
  if (!agreeTerms.value) {
    MessagePlugin.warning(t('login.errorAgreeTerms'))
    return
  }
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
  if (!phoneRegisterAllowed.value) {
    registerType.value = 'email'
  }
  if (!agreeTerms.value) {
    MessagePlugin.warning(t('login.errorAgreeTerms'))
    return
  }
  // 二次前端兜底校验
  const missingFields = []
  if (!registerForm.username) missingFields.push(t('login.username'))
  if (!registerForm.password) missingFields.push(t('login.password'))
  if (!registerForm.confirmPassword) missingFields.push(t('login.confirmPassword'))
  if (registerType.value === 'phone' && !registerForm.phone) missingFields.push(t('login.phone'))
  if (registerType.value === 'email' && !registerForm.email) missingFields.push(t('login.email'))
  if (!registerForm.code) missingFields.push(t('login.verifyCode'))

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

/** 发送密码找回验证码（手机或邮箱） */
async function handleSendForgotCode() {
  if (forgotType.value === 'phone') {
    if (!phonePattern.test(forgotForm.phone)) {
      MessagePlugin.warning(t('login.errorPhoneFormat'))
      return
    }
  } else if (!emailPattern.test(forgotForm.email)) {
    MessagePlugin.warning(t('login.errorEmailFormat'))
    return
  }

  forgotCodeSending.value = true
  try {
    if (forgotType.value === 'phone') {
      await sendResetSms(forgotForm.phone)
    } else {
      await sendResetMail(forgotForm.email)
    }
    MessagePlugin.success(t('login.sendCodeSuccess'))
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
    const msg = e.response?.data?.message || e.message || t('login.sendCodeFailed')
    MessagePlugin.error(msg)
  } finally {
    forgotCodeSending.value = false
  }
}

/** 密码找回提交 */
async function handleForgotPassword() {
  if (forgotType.value === 'phone') {
    if (!phonePattern.test(forgotForm.phone)) {
      MessagePlugin.warning(t('login.errorPhoneFormat'))
      return
    }
  } else if (!emailPattern.test(forgotForm.email)) {
    MessagePlugin.warning(t('login.errorEmailFormat'))
    return
  }
  if (!/^\d{6}$/.test(forgotForm.code)) {
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
      resetType: forgotType.value,
      phone: forgotType.value === 'phone' ? forgotForm.phone : undefined,
      email: forgotType.value === 'email' ? forgotForm.email : undefined,
      code: forgotForm.code,
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
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  overflow: hidden;
  background: #F6F0E6;
}

.page-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-wash {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 55% at 50% -10%, rgba(168, 181, 159, 0.42), transparent 58%),
    radial-gradient(ellipse 50% 40% at 100% 100%, rgba(168, 181, 159, 0.18), transparent 55%);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  transition: max-width 0.2s ease;
}

.login-shell--wide {
  max-width: 520px;
}

.login-card {
  width: 100%;
  padding: 36px 36px 28px;
  border-radius: 20px;
  background: #FFFFFF;
  box-shadow: 0 8px 28px -12px rgba(49, 92, 52, 0.16);
}

.login-shell--wide .login-card {
  padding: 36px 40px 28px;
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 28px;
}

.card-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.card-logo {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 50%;
}

.card-brand-text {
  min-width: 0;
}

.product-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
  color: #315C34;
}

.product-tagline {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.4;
  color: color-mix(in srgb, #315C34 55%, #F6F0E6);
}

.locale-switch {
  flex-shrink: 0;
}

.login-form {
  --login-label-w: 80px;
}

.login-form--register {
  --login-label-w: 96px;
}

.login-form :deep(.t-form__item) {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  margin-bottom: 16px;
}

.login-form--register :deep(.t-form__item) {
  margin-bottom: 14px;
}

.login-form :deep(.t-form__label),
.login-form :deep(.t-form__label--top),
.login-form :deep(.t-form__label--left),
.login-form :deep(.t-form__label--right) {
  float: none !important;
  flex: 0 0 var(--login-label-w);
  width: var(--login-label-w) !important;
  max-width: var(--login-label-w);
  margin-bottom: 0 !important;
  padding-right: 10px;
  color: #315C34;
  font-weight: 500;
  text-align: right;
  line-height: 40px;
  white-space: nowrap;
  box-sizing: border-box;
}

.login-form :deep(.t-form__controls) {
  flex: 1;
  min-width: 0;
  margin-left: 0 !important;
}

.login-form :deep(.t-form__controls-content) {
  width: 100%;
}

.login-form :deep(.t-form__help) {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
}

.login-form :deep(.t-input),
.login-form :deep(.t-input__wrap) {
  border-radius: 10px;
  width: 100%;
}

.login-form :deep(.t-radio-group) {
  width: 100%;
  display: flex;
}

.login-form :deep(.t-radio-button) {
  flex: 1;
  justify-content: center;
}

.login-form .primary-btn {
  margin-top: 4px;
  width: 100%;
}

.form-extra {
  display: flex;
  justify-content: flex-end;
  margin: -6px 0 20px;
  padding-left: var(--login-label-w);
}

.link-btn {
  border: none;
  background: none;
  padding: 0;
  color: #315C34;
  font-size: 13px;
  cursor: pointer;
  opacity: 0.75;
  text-decoration: none;
}

.link-btn:hover {
  opacity: 1;
  text-decoration: underline;
}

.foot-sep {
  margin: 0 6px;
  opacity: 0.45;
}

.agree-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 4px 0 16px;
  cursor: pointer;
  user-select: none;
}

.agree-text {
  font-size: 13px;
  line-height: 1.5;
  color: color-mix(in srgb, #315C34 70%, #F6F0E6);
  padding-top: 2px;
}

.terms-link {
  color: #315C34;
  font-weight: 600;
  text-decoration: none;
}

.terms-link:hover {
  text-decoration: underline;
}

.primary-btn {
  --td-brand-color: #315C34;
  --td-brand-color-hover: #274a2a;
  --td-brand-color-active: #1f3c22;
  height: 46px !important;
  border-radius: 10px !important;
  font-weight: 600 !important;
}

.card-foot {
  margin: 20px 0 0;
  text-align: center;
  font-size: 13px;
  color: color-mix(in srgb, #315C34 50%, #F6F0E6);
}

.card-foot .go-register {
  font-size: 15px;
  font-weight: 600;
  opacity: 0.9;
}

.card-foot .terms-link {
  font-size: 12px;
  font-weight: 400;
}

.panel-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: #8a8a8a;
  line-height: 1.5;
}

.sms-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.sms-input {
  flex: 1;
  min-width: 0;
}

.sms-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

.feedback-form {
  padding: 8px 4px 0;
}

.feedback-form :deep(.t-form__label) {
  font-weight: 500;
  color: #315C34;
}

@media (max-width: 640px) {
  .login-shell,
  .login-shell--wide {
    max-width: 100%;
  }

  .login-card,
  .login-shell--wide .login-card {
    padding: 28px 22px 24px;
  }

  .product-title {
    font-size: 18px;
  }

  .login-form,
  .login-form--register {
    --login-label-w: 72px;
  }

  .login-form :deep(.t-form__label),
  .login-form :deep(.t-form__label--top),
  .login-form :deep(.t-form__label--left),
  .login-form :deep(.t-form__label--right) {
    padding-right: 8px;
    font-size: 13px;
  }
}
</style>
