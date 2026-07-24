<template>
  <div class="word-card" :class="{ revealed: showAnswer }" @click="onCardClick">
    <div class="card-front" v-if="!showAnswer">
      <div class="word-main-row">
        <div class="word-main">{{ word.wordProto }}</div>
        <button
          type="button"
          class="speak-btn speak-btn--lg"
          title="UK"
          @click.stop="speak(word.wordProto, 'uk')"
        >
          <t-icon name="sound" size="20px" />
        </button>
      </div>
      <div class="word-hint" v-if="word.ukPhonetic || word.usPhonetic">{{ word.ukPhonetic || word.usPhonetic }}</div>
      <p class="tap-hint">{{ $t('study.tapCardHint') }}</p>
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
      <div class="sentence" v-if="word.sentenceExample">{{ word.sentenceExample }}</div>
      <div class="root" v-if="word.rootAffix">{{ word.rootAffix }}</div>
      <div class="review-info" v-if="word.learnStatus > 0">
        {{ statusLabel(word.learnStatus) }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  word: any
  showAnswer: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

function statusLabel(status: number) {
  const map: Record<number, string> = {
    0: '未学习',
    1: '首次学完',
    2: '第一轮复习',
    3: '第二轮复习',
    4: '第三轮复习',
    5: '第四轮复习',
    6: '第五轮复习',
    7: '已熟记',
  }
  return map[status] || '未知'
}

function onCardClick() {
  if (props.showAnswer) return
  emit('toggle')
}

function speak(text: string, accent: 'us' | 'uk' = 'uk') {
  if (!text?.trim()) return
  if (!window.speechSynthesis) {
    MessagePlugin.warning(t('study.speechUnsupported'))
    return
  }
  window.speechSynthesis.cancel()
  const u = new SpeechSynthesisUtterance(text.trim())
  u.lang = accent === 'us' ? 'en-US' : 'en-GB'
  window.speechSynthesis.speak(u)
}

/** 进入卡片 / 换词时默认播一次（英音） */
watch(
  () => props.word?.wordId ?? props.word?.id ?? props.word?.wordProto,
  (key) => {
    if (!key || !props.word?.wordProto) return
    speak(props.word.wordProto, 'uk')
  },
  { immediate: true }
)
</script>

<style scoped>
.word-card {
  background: var(--brand-white);
  border-radius: 18px;
  padding: 40px 28px;
  text-align: center;
  border: 1px solid color-mix(in srgb, var(--brand-lizard) 42%, var(--brand-white));
  box-shadow: 0 10px 28px color-mix(in srgb, var(--brand-ink) 6%, transparent);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  min-height: 240px;
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
  border-color: color-mix(in srgb, var(--brand-lizard) 70%, var(--brand-ink));
  box-shadow: 0 14px 32px color-mix(in srgb, var(--brand-ink) 9%, transparent);
}

.word-main-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.word-main {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--brand-ink);
  line-height: 1.25;
}

.word-hint {
  margin-top: 12px;
  font-size: 15px;
  color: color-mix(in srgb, var(--brand-ink) 45%, var(--brand-lizard));
}

.tap-hint {
  margin-top: 28px;
  font-size: 13px;
  color: color-mix(in srgb, var(--brand-lizard) 35%, #999);
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
  color: color-mix(in srgb, var(--brand-ink) 50%, var(--brand-lizard));
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
  color: var(--brand-ink);
  cursor: pointer;
  padding: 2px;
  line-height: 1;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.speak-btn:hover {
  background: color-mix(in srgb, var(--brand-lizard) 35%, transparent);
}

.speak-btn--lg {
  margin-top: 4px;
}

.meaning {
  font-size: 20px;
  color: var(--brand-ink);
  font-weight: 600;
  margin-bottom: 14px;
  line-height: 1.45;
}

.sentence {
  font-size: 14px;
  color: color-mix(in srgb, var(--brand-ink) 62%, #666);
  margin-bottom: 8px;
  line-height: 1.65;
}

.root {
  font-size: 13px;
  color: color-mix(in srgb, var(--brand-ink) 55%, #888);
  margin-bottom: 8px;
}

.review-info {
  margin-top: 12px;
  font-size: 12px;
  color: color-mix(in srgb, var(--brand-lizard) 30%, #999);
}
</style>
