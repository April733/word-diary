<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>学习</h1>
      </div>
    </header>

    <div class="page-body">
      <t-tabs v-model="tab">
        <t-tab-panel value="new" :label="`新单词 (${newWords.length})`">
          <div v-if="newWords.length > 0" class="study-area">
            <WordCard v-if="currentNew" :word="currentNew" :show-answer="showAnswer" @toggle="showAnswer = !showAnswer" />

            <div class="study-actions" v-if="showAnswer">
              <t-button theme="danger" size="large" @click="handleFirstLearn(0)">没记住</t-button>
              <t-button theme="primary" size="large" @click="handleFirstLearn(1)">记住了</t-button>
            </div>

            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: newProgress + '%' }"></div>
            </div>
            <p class="progress-text">{{ newIndex + 1 }} / {{ newWords.length }}</p>
          </div>
          <t-empty v-else description="没有待学习的新单词" />
        </t-tab-panel>

        <t-tab-panel value="review" :label="`复习 (${reviewWords.length})`">
          <div v-if="reviewWords.length > 0" class="study-area">
            <WordCard v-if="currentReview" :word="currentReview" :show-answer="showReviewAnswer" @toggle="showReviewAnswer = !showReviewAnswer" />

            <div class="study-actions" v-if="showReviewAnswer">
              <t-button theme="danger" size="large" @click="handleReview(0)">忘记了</t-button>
              <t-button theme="primary" size="large" @click="handleReview(1)">记住了</t-button>
            </div>

            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: reviewProgress + '%' }"></div>
            </div>
            <p class="progress-text">{{ reviewIndex + 1 }} / {{ reviewWords.length }}</p>
          </div>
          <t-empty v-else description="今天没有需要复习的单词，太棒了！" />
        </t-tab-panel>

        <t-tab-panel value="completed" :label="`已完成 (${completedWords.length})`">
          <div v-if="completedWords.length > 0" class="completed-list">
            <div v-for="word in completedWords" :key="word.planId" class="completed-item">
              <div class="completed-word">{{ word.wordProto }}</div>
              <div class="completed-mean">{{ word.partSpeechMean }}</div>
              <t-tag :theme="word.learnStatus === 3 ? 'success' : 'default'" size="small">
                {{ word.learnStatus === 3 ? '已熟记' : word.learnStatus === 4 ? '已遗忘' : '已学习' }}
              </t-tag>
            </div>
          </div>
          <t-empty v-else description="今天还没有完成学习" />
        </t-tab-panel>
      </t-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { getNewWords, getTodayReview, getTodayCompleted, firstLearn, review } from '../api'
import WordCard from '../components/WordCard.vue'

const router = useRouter()
const tab = ref('new')
const newWords = ref<any[]>([])
const reviewWords = ref<any[]>([])
const completedWords = ref<any[]>([])
const newIndex = ref(0)
const reviewIndex = ref(0)
const showAnswer = ref(false)
const showReviewAnswer = ref(false)

const currentNew = computed(() => newWords.value[newIndex.value] || null)
const currentReview = computed(() => reviewWords.value[reviewIndex.value] || null)
const newProgress = computed(() => newWords.value.length ? (newIndex.value / newWords.value.length) * 100 : 0)
const reviewProgress = computed(() => reviewWords.value.length ? (reviewIndex.value / reviewWords.value.length) * 100 : 0)

onMounted(async () => {
  await loadData()
})

async function loadData() {
  try {
    const [nw, rw, cp] = await Promise.all([getNewWords(), getTodayReview(), getTodayCompleted()])
    newWords.value = nw.data.data
    reviewWords.value = rw.data.data
    completedWords.value = cp.data.data
    // Prioritize review if there are any
    if (reviewWords.value.length > 0) tab.value = 'review'
  } catch (e) {
    MessagePlugin.error('加载学习数据失败')
  }
}

async function handleFirstLearn(result: number) {
  const word = currentNew.value
  if (!word) return
  const currentIdx = newIndex.value
  try {
    await firstLearn(word.planId, result)
  } catch (e) {
    MessagePlugin.error('操作失败')
    return
  }
  showAnswer.value = false
  if (result === 1) {
    // Remembered: remove from local list immediately
    newWords.value.splice(currentIdx, 1)
  }
  // Also refresh from backend
  try {
    const [nw, cp] = await Promise.all([getNewWords(), getTodayCompleted()])
    newWords.value = nw.data.data
    completedWords.value = cp.data.data
  } catch (e) { /* fallback */ }
  if (newWords.value.length === 0) {
    newIndex.value = 0
    MessagePlugin.success('新单词学习完毕！')
  } else {
    newIndex.value = Math.min(currentIdx, newWords.value.length - 1)
  }
}

async function handleReview(result: number) {
  const word = currentReview.value
  if (!word) return
  const currentIdx = reviewIndex.value
  try {
    await review(word.planId, result)
  } catch (e) {
    MessagePlugin.error('操作失败')
    return
  }
  showReviewAnswer.value = false
  // Remove current word from local list immediately
  reviewWords.value.splice(currentIdx, 1)
  // Also refresh from backend to keep data consistent
  try {
    const [rw, cp] = await Promise.all([getTodayReview(), getTodayCompleted()])
    reviewWords.value = rw.data.data
    completedWords.value = cp.data.data
  } catch (e) { /* fallback */ }
  if (reviewWords.value.length === 0) {
    reviewIndex.value = 0
    MessagePlugin.success('复习完成！')
  } else {
    // If we removed locally, adjust index (stay at same position or go to next)
    reviewIndex.value = Math.min(currentIdx, reviewWords.value.length - 1)
  }
}

function moveToCompleted(word: any) {
  // No longer manually push — completed list is refreshed from backend
}

function nextNewWord() {
  showAnswer.value = false
  if (newIndex.value < newWords.value.length - 1) {
    newIndex.value++
  } else {
    newWords.value = []
    newIndex.value = 0
    MessagePlugin.success('新单词学习完毕！')
  }
}

function nextReviewWord() {
  showReviewAnswer.value = false
  if (reviewIndex.value < reviewWords.value.length - 1) {
    reviewIndex.value++
  } else {
    reviewWords.value = []
    reviewIndex.value = 0
    MessagePlugin.success('复习完成！')
  }
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
  max-width: 640px;
  margin: 24px auto;
  padding: 0 24px;
}

.study-area {
  padding: 32px 0;
}

.study-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 32px;
}

.study-actions .t-button {
  min-width: 140px;
}

.progress-bar {
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  margin-top: 32px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #4f46e5;
  border-radius: 3px;
  transition: width 0.3s;
}

.progress-text {
  text-align: center;
  color: #6b7280;
  font-size: 13px;
  margin-top: 8px;
}

.completed-list {
  padding: 16px 0;
}

.completed-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 8px;
}

.completed-word {
  font-weight: 600;
  font-size: 16px;
}

.completed-mean {
  color: #6b7280;
  font-size: 14px;
  flex: 1;
  margin: 0 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
