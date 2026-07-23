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
          <button type="button" class="act act--pass" @click="handleAnswer(0)">
            {{ $t('study.forget') }}
          </button>
          <button type="button" class="act act--know" @click="handleAnswer(1)">
            {{ $t('study.know') }}
          </button>
        </div>

        <div class="nav-actions">
          <button type="button" class="nav-btn" :disabled="index <= 0" @click="prevWord">
            {{ $t('study.prev') }}
          </button>
          <span class="nav-text">{{ index + 1 }} / {{ words.length }}</span>
          <button type="button" class="nav-btn" :disabled="index >= words.length - 1" @click="nextWord">
            {{ $t('study.next') }}
          </button>
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
    await submitBrowseAnswer({ wordId: word.wordId, feedback })
    showAnswer.value = false
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
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-width: 520px;
  margin: 28px auto 0;
}

.act {
  appearance: none;
  border: none;
  cursor: pointer;
  height: 48px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.04em;
  transition: background 0.15s ease, transform 0.12s ease, border-color 0.15s ease;
}

.act:active {
  transform: scale(0.98);
}

.act--pass {
  background: var(--brand-white);
  color: var(--brand-ink);
  border: 1.5px solid color-mix(in srgb, var(--brand-lizard) 70%, var(--brand-ink));
}

.act--pass:hover {
  background: color-mix(in srgb, var(--brand-lizard) 28%, var(--brand-white));
}

.act--know {
  background: var(--brand-ink);
  color: var(--brand-white);
  border: 1.5px solid var(--brand-ink);
}

.act--know:hover {
  background: var(--brand-ink-hover);
  border-color: var(--brand-ink-hover);
}

.nav-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}

.nav-btn {
  appearance: none;
  border: none;
  background: transparent;
  padding: 8px 12px;
  font-size: 14px;
  color: color-mix(in srgb, var(--brand-ink) 70%, var(--brand-lizard));
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.15s ease, color 0.15s ease;
}

.nav-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--brand-lizard) 22%, transparent);
  color: var(--brand-ink);
}

.nav-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.nav-text {
  color: #6b7280;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  min-width: 4.5em;
  text-align: center;
}
</style>
