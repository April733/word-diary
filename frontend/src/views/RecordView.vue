<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ $t('record.title') }}</h1>
      </div>
    </header>

    <div class="page-body">
      <t-table
        v-if="records.length > 0"
        :data="records"
        :columns="columns"
        row-key="id"
        hover
        stripe
      />
      <t-empty v-else :description="$t('record.noRecords')" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { getRecords } from '../api'

const router = useRouter()
const { t } = useI18n()
const records = ref<any[]>([])

function studyTypeLabel(type: number) {
  if (type === 1) return t('record.typeFirst')
  if (type === 2) return t('record.typeReview')
  if (type === 3) return t('record.typeBrowse')
  return '-'
}

function feedbackLabel(row: any) {
  const fb = row.feedback ?? row.result
  if (fb === 1) return h('span', { style: { color: '#059669' } }, t('study.know'))
  if (fb === 2) return h('span', { style: { color: '#d97706' } }, t('study.fuzzy'))
  return h('span', { style: { color: '#dc2626' } }, t('study.forget'))
}

const columns = computed(() => [
  { colKey: 'wordProto', title: t('study.word'), width: 140 },
  {
    colKey: 'studyType', title: t('record.type'), width: 100,
    cell: (_h: any, { row }: any) => studyTypeLabel(row.studyType)
  },
  {
    colKey: 'feedback', title: t('record.feedback'), width: 100,
    cell: (_h: any, { row }: any) => feedbackLabel(row)
  },
  { colKey: 'notebookName', title: t('nav.wordbook'), width: 140 },
  {
    colKey: 'studyTime', title: t('record.date'), width: 180,
    cell: (_h: any, { row }: any) => new Date(row.studyTime).toLocaleString()
  }
])

onMounted(async () => {
  try {
    const res = await getRecords()
    records.value = res.data.data
  } catch {
    MessagePlugin.error(t('record.loadFailed'))
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: transparent;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
  max-width: 900px;
  margin: 24px auto;
  padding: 0 24px;
}
</style>
