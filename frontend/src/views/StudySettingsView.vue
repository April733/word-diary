<template>
  <div class="page">
    <header class="page-header">
      <div class="header-left">
        <t-button variant="text" @click="router.push('/study')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ $t('study.settingsTitle') }}</h1>
      </div>
      <div class="header-center">
        <span class="header-brand">{{ $t('common.appName') }}</span>
      </div>
      <div class="header-right" aria-hidden="true" />
    </header>

    <div class="page-body">
      <t-form label-align="top">
        <t-form-item :label="$t('study.dailyNewLimit')">
          <t-input-number v-model="form.dailyNewLimit" :min="1" :max="200" theme="column" />
        </t-form-item>
        <t-form-item :label="$t('study.reviewUnlockHour')">
          <t-input-number v-model="form.reviewUnlockHour" :min="0" :max="23" theme="column" />
          <p class="hint">{{ $t('study.reviewUnlockHourHint') }}</p>
        </t-form-item>
        <t-button theme="primary" block :loading="saving" @click="handleSave">{{ $t('common.save') }}</t-button>
      </t-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { getStudySettings, updateStudySettings } from '../api'

const router = useRouter()
const { t } = useI18n()
const saving = ref(false)
const form = ref({
  dailyNewLimit: 20,
  reviewUnlockHour: 7
})

onMounted(async () => {
  try {
    const res = await getStudySettings()
    const data = res.data.data
    form.value = {
      dailyNewLimit: data.dailyNewLimit,
      reviewUnlockHour: data.reviewUnlockHour
    }
  } catch {
    MessagePlugin.error(t('study.loadFailed'))
  }
})

async function handleSave() {
  saving.value = true
  try {
    await updateStudySettings(form.value)
    MessagePlugin.success(t('study.settingsSaved'))
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || t('study.saveFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: transparent;
}

.page-header {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 16px 10px 6px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
  max-width: min(42%, 160px);
  flex: 0 1 auto;
  z-index: 1;
}

.header-left :deep(.t-button) {
  width: 28px;
  height: 28px;
  min-width: 28px;
  padding: 0;
}

.header-left :deep(.t-icon) {
  font-size: 18px;
}

.header-center {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
  z-index: 0;
}

.header-brand {
  font-size: 18px;
  font-weight: 800;
  color: var(--brand-ink, #315c34);
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 2px;
  justify-content: flex-end;
  margin-left: auto;
  flex: 0 0 auto;
  max-width: 42%;
  z-index: 1;
}

.page-header h1 {
  font-size: 15px;
  margin: 0;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.page-body {
  max-width: 480px;
  margin: 24px auto;
  padding: 24px;
  background: #fff;
  border-radius: 12px;
}

.hint {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
</style>
