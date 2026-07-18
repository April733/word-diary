<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/study')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ $t('study.browseTitle') }}</h1>
      </div>
    </header>

    <div class="page-body">
      <p class="browse-hint">{{ $t('study.browseHint') }}</p>

      <div v-if="currentWord" class="study-area">
        <WordCard :word="currentWord" :show-answer="showAnswer" @toggle="showAnswer = true" />

        <div v-if="showAnswer" class="study-actions">
          <t-button theme="danger" size="large" @click="handleAnswer(0)">{{ $t('study.forget') }}</t-button>
          <t-button theme="warning" size="large" @click="handleAnswer(2)">{{ $t('study.fuzzy') }}</t-button>
          <t-button theme="primary" size="large" @click="handleAnswer(1)">{{ $t('study.know') }}</t-button>
        </div>

        <div class="nav-actions">
          <t-button variant="outline" :disabled="index <= 0" @click="prevWord">{{ $t('study.prev') }}</t-button>
          <span class="nav-text">{{ index + 1 }} / {{ words.length }}</span>
          <t-button variant="outline" :disabled="index >= words.length - 1" @click="nextWord">{{ $t('study.next') }}</t-button>
        </div>
      </div>

      <t-empty v-else-if="!loading" :description="$t('study.browseEmpty')" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import { getBrowseWords, submitBrowseAnswer } from '../api'
import WordCard from '../components/WordCard.vue'

const router = useRouter()
const { t } = useI18n()
const loading = ref(true)
const showAnswer = ref(false)
const words = ref<any[]>([])
const index = ref(0)

const currentWord = computed(() => words.value[index.value] || null)

onMounted(loadWords)

async function loadWords() {
  loading.value = true
  try {
    const res = await getBrowseWords({ page: 1, pageSize: 100 })
    words.value = res.data.data.items || []
    index.value = 0
  } catch {
    MessagePlugin.error(t('study.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function handleAnswer(feedback: number) {
  const word = currentWord.value
  if (!word) return
  try {
    await submitBrowseAnswer({ planId: word.planId, feedback })
    showAnswer.value = false
    MessagePlugin.success(t('study.browseRecorded'))
    if (index.value < words.value.length - 1) {
      index.value++
    }
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.msg || t('study.actionFailed'))
  }
}

function prevWord() {
  if (index.value > 0) {
    index.value--
    showAnswer.value = false
  }
}

function nextWord() {
  if (index.value < words.value.length - 1) {
    index.value++
    showAnswer.value = false
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
  max-width: 640px;
  margin: 24px auto;
  padding: 0 24px;
}

.browse-hint {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 16px;
}

.study-area {
  padding: 24px 0;
}

.study-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 32px;
  flex-wrap: wrap;
}

.nav-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 32px;
}

.nav-text {
  color: #6b7280;
  font-size: 14px;
}
</style>
