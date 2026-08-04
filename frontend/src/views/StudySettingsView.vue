<template>
  <div class="page">
    <PageHeader :title="$t('study.settingsTitle')" :back-to="backToStudy" />

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
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { getStudySettings, updateStudySettings } from '../api'
import PageHeader from '../components/PageHeader.vue'

const route = useRoute()
const { t } = useI18n()
const sourceType = computed(() => (route.query.sourceType as string) || undefined)
const tab = computed(() => (route.query.tab as string) || (sourceType.value === '听力' ? 'listen' : 'read'))
const backToStudy = computed(() => ({
  path: '/study',
  query: {
    ...(sourceType.value ? { sourceType: sourceType.value } : {}),
    ...(tab.value ? { tab: tab.value } : {})
  }
}))
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

/* 设置页面特有 header 样式 */
.page-header {
  background: #fff;
  border-bottom: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
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
