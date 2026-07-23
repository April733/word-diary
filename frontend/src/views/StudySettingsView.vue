<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/study')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ $t('study.settingsTitle') }}</h1>
      </div>
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
  padding: 16px 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.page-header > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-header h1 {
  font-size: 20px;
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
