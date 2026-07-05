<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>学习记录</h1>
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
      <t-empty v-else description="暂无学习记录" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { getRecords } from '../api'

const router = useRouter()
const records = ref<any[]>([])

const columns = [
  { colKey: 'wordProto', title: '单词', width: 140 },
  {
    colKey: 'studyType', title: '类型', width: 100,
    cell: (_h: any, { row }: any) => row.studyType === 1 ? '初次学习' : '复习'
  },
  {
    colKey: 'result', title: '结果', width: 100,
    cell: (_h: any, { row }: any) => row.result === 1
      ? h('span', { style: { color: '#059669' } }, '✓ 记住')
      : h('span', { style: { color: '#dc2626' } }, '✗ 未记住')
  },
  { colKey: 'notebookName', title: '单词本', width: 140 },
  {
    colKey: 'studyTime', title: '时间', width: 180,
    cell: (_h: any, { row }: any) => new Date(row.studyTime).toLocaleString('zh-CN')
  }
]

onMounted(async () => {
  try {
    const res = await getRecords()
    records.value = res.data.data
  } catch (e) {
    MessagePlugin.error('加载失败')
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f5f7fa;
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
