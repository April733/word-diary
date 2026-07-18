<template>
  <div class="word-card" :class="{ revealed: showAnswer }" @click="onCardClick">
    <div class="card-front" v-if="!showAnswer">
      <div class="word-main-row">
        <div class="word-main">{{ word.wordProto }}</div>
        <button
          type="button"
          class="speak-btn speak-btn--lg"
          title="US"
          @click.stop="speak(word.wordProto, 'us')"
        >
          <t-icon name="sound" size="20px" />
        </button>
      </div>
      <div class="word-hint" v-if="word.usPhonetic">{{ word.usPhonetic }}</div>
      <p class="tap-hint">点击查看释义</p>
    </div>
    <div class="card-back" v-else>
      <div class="word-main-row">
        <div class="word-main">{{ word.wordProto }}</div>
      </div>
      <div class="phonetic-row" v-if="word.usPhonetic || word.ukPhonetic || word.wordProto">
        <span class="ph-item">
          <span class="ph-label">US</span>
          <span v-if="word.usPhonetic" class="ph-ipa">{{ word.usPhonetic }}</span>
          <button
            type="button"
            class="speak-btn"
            title="US"
            @click.stop="speak(word.wordProto, 'us')"
          >
            <t-icon name="sound" size="16px" />
          </button>
        </span>
        <span class="ph-item">
          <span class="ph-label">UK</span>
          <span v-if="word.ukPhonetic" class="ph-ipa">{{ word.ukPhonetic }}</span>
          <button
            type="button"
            class="speak-btn"
            title="UK"
            @click.stop="speak(word.wordProto, 'uk')"
          >
            <t-icon name="sound" size="16px" />
          </button>
        </span>
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
import { MessagePlugin } from 'tdesign-vue-next'

const props = defineProps<{
  word: any
  showAnswer: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

function onCardClick() {
  // 只揭示释义，不再来回切换隐藏
  if (props.showAnswer) return
  emit('toggle')
}

function speak(text: string, accent: 'us' | 'uk' = 'us') {
  if (!text?.trim()) return
  if (!window.speechSynthesis) {
    MessagePlugin.warning('当前浏览器不支持语音播放')
    return
  }
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text.trim())
  utterance.lang = accent === 'us' ? 'en-US' : 'en-GB'
  utterance.rate = 0.85
  const voices = window.speechSynthesis.getVoices()
  if (voices.length > 0) {
    const langPrefix = accent === 'us' ? 'en-US' : 'en-GB'
    let best = voices.find(v => v.lang === langPrefix && v.localService)
    if (!best) best = voices.find(v => v.lang.startsWith(langPrefix))
    if (!best) best = voices.find(v => v.lang.startsWith('en-'))
    if (best) utterance.voice = best
  }
  window.speechSynthesis.speak(utterance)
}
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

.word-card.revealed {
  cursor: default;
}

.word-card:not(.revealed):hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(0,0,0,0.12);
}

.word-main-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.word-main {
  font-size: 36px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 0;
}

.word-hint {
  margin-top: 12px;
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
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
  font-size: 14px;
  color: #9ca3af;
  margin: 12px 0 16px;
}

.ph-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ph-label {
  font-size: 11px;
  font-weight: 600;
  opacity: 0.75;
}

.ph-ipa {
  font-size: 14px;
}

.speak-btn {
  border: none;
  background: transparent;
  color: #315C34;
  cursor: pointer;
  padding: 2px;
  line-height: 1;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.speak-btn:hover {
  background: rgba(49, 92, 52, 0.1);
}

.speak-btn--lg {
  margin-top: 4px;
}

.meaning {
  font-size: 22px;
  color: #315C34;
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
