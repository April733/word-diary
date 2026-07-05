<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/notebooks')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ notebookName }}</h1>
      </div>
      <t-button theme="primary" @click="showAddDialog = true">添加单词</t-button>
    </header>

    <div class="page-body">
      <t-list v-if="words.length > 0" :split="true">
        <t-list-item v-for="word in words" :key="word.id">
          <t-list-item-meta>
            <template #title>
              <span class="word-proto">{{ word.wordProto }}</span>
              <t-tag size="small" variant="light" :theme="statusTheme(word.learnStatus)" style="margin-left: 8px">
                {{ statusLabel(word.learnStatus) }}
              </t-tag>
            </template>
            <template #description>
              <div v-if="word.usPhonetic" class="phonetic">US: {{ word.usPhonetic }}</div>
              <div class="meaning">{{ word.partSpeechMean }}</div>
            </template>
          </t-list-item-meta>
          <template #action>
            <t-popconfirm content="确认删除该单词？" @confirm="handleDelete(word.id)">
              <t-button variant="text" theme="danger">删除</t-button>
            </t-popconfirm>
          </template>
        </t-list-item>
      </t-list>
      <t-empty v-else description="还没有单词，点击右上角添加" />
    </div>

    <t-dialog v-model:visible="showAddDialog" header="添加单词" @confirm="handleAdd" :confirm-btn="{ content: '确认添加', loading: saving }">
      <t-form>
        <t-form-item label="单词" name="wordProto">
          <t-input v-model="form.wordProto" placeholder="请输入单词原型" />
        </t-form-item>
        <t-form-item label="美式音标" name="usPhonetic">
          <t-input v-model="form.usPhonetic" placeholder="如 /ˈeksəmpəl/" />
        </t-form-item>
        <t-form-item label="英式音标" name="ukPhonetic">
          <t-input v-model="form.ukPhonetic" placeholder="如 /ɪɡˈzɑːmpəl/" />
        </t-form-item>
        <t-form-item label="释义" name="partSpeechMean">
          <t-input v-model="form.partSpeechMean" placeholder="如 n. 例子，实例" />
        </t-form-item>
        <t-form-item label="例句" name="sentenceExample">
          <t-textarea v-model="form.sentenceExample" placeholder="请输入例句（选填）" />
        </t-form-item>
        <t-form-item label="词根词缀" name="rootAffix">
          <t-input v-model="form.rootAffix" placeholder="请输入词根词缀解析（选填）" />
        </t-form-item>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { getWords, addWord, deleteWord } from '../api'

const route = useRoute()
const router = useRouter()
const notebookId = Number(route.params.id)
const notebookName = ref('')
const words = ref<any[]>([])
const showAddDialog = ref(false)
const saving = ref(false)
const form = reactive({
  wordProto: '', usPhonetic: '', ukPhonetic: '', partSpeechMean: '',
  sentenceExample: '', rootAffix: ''
})

onMounted(() => loadWords())

async function loadWords() {
  try {
    const res = await getWords(notebookId)
    words.value = res.data.data
  } catch (e) {
    MessagePlugin.error('加载失败')
  }
}

async function handleAdd() {
  if (!form.wordProto.trim()) {
    MessagePlugin.warning('请输入单词')
    return false
  }
  saving.value = true
  try {
    await addWord(notebookId, { ...form })
    MessagePlugin.success('添加成功')
    showAddDialog.value = false
    Object.keys(form).forEach(k => (form as any)[k] = '')
    await loadWords()
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || '添加失败')
    return false
  } finally {
    saving.value = false
  }
}

async function handleDelete(wordId: number) {
  try {
    await deleteWord(notebookId, wordId)
    MessagePlugin.success('已删除')
    await loadWords()
  } catch (e) {
    MessagePlugin.error('删除失败')
  }
}

function statusTheme(status: number) {
  const map: Record<number, string> = { 0: 'default', 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }
  return map[status] || 'default'
}

function statusLabel(status: number) {
  const map: Record<number, string> = { 0: '未学习', 1: '已初学', 2: '复习中', 3: '已熟记', 4: '遗忘' }
  return map[status] || '未知'
}
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
  max-width: 800px;
  margin: 24px auto;
  padding: 0 24px;
}

.word-proto {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.phonetic {
  font-size: 13px;
  color: #9ca3af;
}

.meaning {
  font-size: 14px;
  color: #374151;
  margin-top: 4px;
}
</style>
