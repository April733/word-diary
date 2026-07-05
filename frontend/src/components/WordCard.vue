<template>
  <div class="word-card" @click="$emit('toggle')">
    <div class="card-front" v-if="!showAnswer">
      <div class="word-main">{{ word.wordProto }}</div>
      <div class="word-hint" v-if="word.usPhonetic">{{ word.usPhonetic }}</div>
      <p class="tap-hint">点击查看释义</p>
    </div>
    <div class="card-back" v-else>
      <div class="word-main">{{ word.wordProto }}</div>
      <div class="phonetic-row" v-if="word.usPhonetic || word.ukPhonetic">
        <span v-if="word.usPhonetic">US: {{ word.usPhonetic }}</span>
        <span v-if="word.ukPhonetic" style="margin-left: 12px">UK: {{ word.ukPhonetic }}</span>
      </div>
      <div class="meaning">{{ word.partSpeechMean }}</div>
      <div class="sentence" v-if="word.sentenceExample">📝 {{ word.sentenceExample }}</div>
      <div class="root" v-if="word.rootAffix">🔍 {{ word.rootAffix }}</div>
      <div class="review-info" v-if="word.reviewTimes > 0">
        已复习 {{ word.reviewTimes }} 次 · 当前第 {{ word.currentLevel }} 级
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  word: any
  showAnswer: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<style scoped>
.word-card {
  background: #fff;
  border-radius: 16px;
  padding: 48px 32px;
  text-align: center;
  box-shadow: 0 4px 24px rgba(0,0,0,0.08);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.word-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(0,0,0,0.12);
}

.word-main {
  font-size: 36px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 12px;
}

.word-hint {
  font-size: 16px;
  color: #9ca3af;
}

.tap-hint {
  margin-top: 24px;
  font-size: 13px;
  color: #c4c4c4;
}

.card-back {
  width: 100%;
}

.phonetic-row {
  font-size: 14px;
  color: #9ca3af;
  margin-bottom: 16px;
}

.meaning {
  font-size: 22px;
  color: #4f46e5;
  font-weight: 600;
  margin-bottom: 16px;
}

.sentence {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 8px;
  line-height: 1.6;
}

.root {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 8px;
}

.review-info {
  margin-top: 12px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
