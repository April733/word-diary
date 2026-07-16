<template>
  <t-drawer
    v-model:visible="visibleModel"
    size="420px"
    :close-btn="true"
    :footer="false"
  >
    <template #header>
      <div class="drawer-header">
        <div class="drawer-title">{{ drawerTitle }}</div>
        <t-tabs
          v-if="viewMode === 'basket' && notebookId"
          v-model="basketTab"
          size="small"
          class="basket-tabs"
          @change="onBasketTabChange"
        >
          <t-tab-panel value="pending" :label="pendingTabLabel" />
          <t-tab-panel value="saved" :label="savedTabLabel" />
        </t-tabs>
      </div>
    </template>

    <div v-if="linkedNotebooks.length === 0" class="hint">
      <p>{{ $t('reader.collectNeedLink') }}</p>
      <t-button theme="primary" size="small" @click="emit('open-link-dialog')">
        {{ $t('reader.notebookLinkTitle') }}
      </t-button>
    </div>

    <template v-else>
      <!-- 本次识别：可勾选后加入某本的收录篮，或直接保存 -->
      <template v-if="viewMode === 'current'">
        <p v-if="!currentEntries.length" class="hint">{{ $t('reader.collectEmptyHint') }}</p>
        <div v-else class="entry-table-wrap">
          <table class="entry-table">
            <thead>
              <tr>
                <th style="width: 36px">
                  <t-checkbox :checked="allChecked" :indeterminate="indeterminate" @change="toggleAll" />
                </th>
                <th>{{ $t('reader.collectWord') }}</th>
                <th>{{ $t('reader.collectMeaning') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in currentEntries" :key="entry.basketKey">
                <td>
                  <t-checkbox
                    :checked="checkedKeys.has(entry.basketKey)"
                    @change="(v: boolean) => toggleOne(entry.basketKey, v)"
                  />
                </td>
                <td>
                  <div class="proto-row">
                    <t-tag size="small" variant="light" :theme="entry.entryType === 'phrase' ? 'warning' : 'primary'">
                      {{ entry.entryType === 'phrase' ? $t('reader.collectPhrase') : $t('reader.collectWordTag') }}
                    </t-tag>
                    <span class="proto">{{ entry.wordProto }}</span>
                    <t-button
                      variant="text"
                      size="small"
                      class="speak-btn"
                      :title="$t('reader.collectPlayUs')"
                      @click.stop="speak(entry.wordProto, 'us')"
                    >
                      <t-icon name="sound" size="16px" />
                    </t-button>
                  </div>
                </td>
                <td>{{ entry.partSpeechMean }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="currentEntries.length" class="collect-actions">
          <div class="target-label">{{ $t('reader.collectTargetNotebook') }}</div>
          <div class="notebook-pick">
            <t-radio-group v-model="notebookId" variant="default-filled" size="small">
              <t-radio-button v-for="nb in linkedNotebooks" :key="nb.notebookId" :value="nb.notebookId">
                {{ nb.notebookName }}
                <t-badge
                  v-if="(basketCounts[nb.notebookId] || 0) > 0"
                  :count="basketCounts[nb.notebookId]"
                  size="small"
                />
              </t-radio-button>
            </t-radio-group>
          </div>
          <div class="btn-row">
            <t-button
              variant="outline"
              :disabled="!notebookId || checkedEntries.length === 0"
              @click="addCheckedToBasket"
            >
              {{ $t('reader.collectAddToBasket') }}
            </t-button>
            <t-button
              theme="primary"
              :loading="saving"
              :disabled="!notebookId || checkedEntries.length === 0"
              @click="saveToNotebook"
            >
              {{ $t('reader.collectSave') }}
            </t-button>
          </div>
        </div>
      </template>

      <!-- 某单词本：未收录篮 / 已收录 -->
      <template v-else>
        <!-- 未收录（本地收录篮） -->
        <template v-if="basketTab === 'pending'">
          <p v-if="!basket.length" class="hint">{{ $t('reader.collectBasketEmpty') }}</p>
          <div v-else class="entry-table-wrap">
            <table class="entry-table">
              <thead>
                <tr>
                  <th style="width: 36px">
                    <t-checkbox :checked="allChecked" :indeterminate="indeterminate" @change="toggleAll" />
                  </th>
                  <th>{{ $t('reader.collectWord') }}</th>
                  <th>{{ $t('reader.collectMeaning') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="entry in basket" :key="entry.basketKey">
                  <td>
                    <t-checkbox
                      :checked="checkedKeys.has(entry.basketKey)"
                      @change="(v: boolean) => toggleOne(entry.basketKey, v)"
                    />
                  </td>
                  <td>
                    <div class="proto-row">
                      <t-tag size="small" variant="light" :theme="entry.entryType === 'phrase' ? 'warning' : 'primary'">
                        {{ entry.entryType === 'phrase' ? $t('reader.collectPhrase') : $t('reader.collectWordTag') }}
                      </t-tag>
                      <span class="proto">{{ entry.wordProto }}</span>
                      <t-button
                        variant="text"
                        size="small"
                        class="speak-btn"
                        :title="$t('reader.collectPlayUs')"
                        @click.stop="speak(entry.wordProto, 'us')"
                      >
                        <t-icon name="sound" size="16px" />
                      </t-button>
                    </div>
                  </td>
                  <td>{{ entry.partSpeechMean }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="basket.length" class="collect-actions">
            <t-button
              theme="primary"
              block
              :loading="saving"
              :disabled="!notebookId || checkedEntries.length === 0"
              @click="saveToNotebook"
            >
              {{ $t('reader.collectSave') }}
            </t-button>
            <t-button variant="text" theme="danger" @click="clearBasketLocal">
              {{ $t('reader.collectClearBasket') }}
            </t-button>
          </div>
        </template>

        <!-- 已收录（单词本内已有词） -->
        <template v-else>
          <div v-if="savedLoading" class="loading-row">
            <t-loading size="small" />
          </div>
          <p v-else-if="!savedWords.length" class="hint">{{ $t('reader.collectSavedEmpty') }}</p>
          <div v-else class="entry-table-wrap">
            <table class="entry-table">
              <thead>
                <tr>
                  <th>{{ $t('reader.collectWord') }}</th>
                  <th>{{ $t('reader.collectMeaning') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="word in savedWords" :key="word.id">
                  <td>
                    <div class="proto-row">
                      <t-tag
                        size="small"
                        variant="light"
                        :theme="word.entryType === 'phrase' ? 'warning' : 'primary'"
                      >
                        {{ word.entryType === 'phrase' ? $t('reader.collectPhrase') : $t('reader.collectWordTag') }}
                      </t-tag>
                      <span class="proto">{{ word.wordProto }}</span>
                      <t-button
                        variant="text"
                        size="small"
                        class="speak-btn"
                        :title="$t('reader.collectPlayUs')"
                        @click.stop="speak(word.wordProto, 'us')"
                      >
                        <t-icon name="sound" size="16px" />
                      </t-button>
                    </div>
                  </td>
                  <td>{{ word.partSpeechMean }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </template>
    </template>
  </t-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { useI18n } from 'vue-i18n'
import { collectBookWords, getWords } from '../api'
import type { CollectEntry } from '../utils/collectBasket'
import {
  clearBasket,
  loadBasket,
  loadBasketCounts,
  mergeIntoBasket,
  saveBasket
} from '../utils/collectBasket'

interface SavedWord {
  id: number
  wordProto: string
  partSpeechMean?: string
  entryType?: string
}

const props = defineProps<{
  visible: boolean
  bookId: number
  currentEntries: CollectEntry[]
  linkedNotebooks: { notebookId: number; notebookName: string }[]
  sourceSnippet?: string
  sourceLocation?: string
  /** current=本次识别；basket=某单词本收录篮 */
  preferredViewMode?: 'current' | 'basket'
  /** 打开某本收录篮时指定 */
  targetNotebookId?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
  'basket-changed': []
  'open-link-dialog': []
}>()

const { t } = useI18n()
const viewMode = ref<'current' | 'basket'>('current')
/** pending=未收录篮；saved=已写入单词本 */
const basketTab = ref<'pending' | 'saved'>('pending')
const checkedKeys = ref<Set<string>>(new Set())
const notebookId = ref<number | undefined>()
const saving = ref(false)
const basket = ref<CollectEntry[]>([])
const basketCounts = ref<Record<number, number>>({})
const savedWords = ref<SavedWord[]>([])
const savedLoading = ref(false)

const visibleModel = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

const activeNotebookName = computed(() => {
  const nb = props.linkedNotebooks.find(n => n.notebookId === notebookId.value)
  return nb?.notebookName || ''
})

const drawerTitle = computed(() => {
  if (viewMode.value === 'basket' && activeNotebookName.value) {
    return activeNotebookName.value
  }
  return t('reader.collectTitle')
})

const pendingTabLabel = computed(() =>
  t('reader.collectTabPending', { count: basket.value.length })
)

const savedTabLabel = computed(() =>
  t('reader.collectTabSaved', { count: savedWords.value.length })
)

const displayEntries = computed(() => (viewMode.value === 'basket' ? basket.value : props.currentEntries))

const checkedEntries = computed(() =>
  displayEntries.value.filter(e => checkedKeys.value.has(e.basketKey))
)

const allChecked = computed(() =>
  displayEntries.value.length > 0 && displayEntries.value.every(e => checkedKeys.value.has(e.basketKey))
)

const indeterminate = computed(() => {
  const total = displayEntries.value.length
  const checked = displayEntries.value.filter(e => checkedKeys.value.has(e.basketKey)).length
  return checked > 0 && checked < total
})

function refreshCounts() {
  const ids = props.linkedNotebooks.map(n => n.notebookId)
  basketCounts.value = loadBasketCounts(props.bookId, ids)
  emit('basket-changed')
}

function refreshBasketFor(nbId: number) {
  basket.value = loadBasket(props.bookId, nbId)
  refreshCounts()
}

async function loadSavedWords(nbId: number) {
  savedLoading.value = true
  try {
    const res = await getWords(nbId)
    const list = (res.data?.data || []) as SavedWord[]
    savedWords.value = list
  } catch {
    savedWords.value = []
    MessagePlugin.error(t('reader.collectSavedLoadFail'))
  } finally {
    savedLoading.value = false
  }
}

function onBasketTabChange(val: string | number) {
  if (val === 'saved' && notebookId.value) {
    void loadSavedWords(notebookId.value)
  }
}

function toggleOne(key: string, checked: boolean) {
  const next = new Set(checkedKeys.value)
  if (checked) next.add(key)
  else next.delete(key)
  checkedKeys.value = next
}

function toggleAll(checked: boolean) {
  if (checked) {
    checkedKeys.value = new Set(displayEntries.value.map(e => e.basketKey))
  } else {
    checkedKeys.value = new Set()
  }
}

function addCheckedToBasket() {
  if (!notebookId.value || checkedEntries.value.length === 0) return
  basket.value = mergeIntoBasket(props.bookId, notebookId.value, checkedEntries.value)
  refreshCounts()
  MessagePlugin.success(
    t('reader.collectBasketAdded', {
      count: checkedEntries.value.length,
      name: activeNotebookName.value
    })
  )
  viewMode.value = 'basket'
  basketTab.value = 'pending'
  checkedKeys.value = new Set(basket.value.map(e => e.basketKey))
}

function clearBasketLocal() {
  if (!notebookId.value) return
  clearBasket(props.bookId, notebookId.value)
  basket.value = []
  checkedKeys.value = new Set()
  refreshCounts()
}

async function saveToNotebook() {
  if (!notebookId.value || checkedEntries.value.length === 0) return
  if (props.linkedNotebooks.length === 0) {
    emit('open-link-dialog')
    return
  }
  saving.value = true
  try {
    const res = await collectBookWords(props.bookId, {
      notebookId: notebookId.value,
      sourceSnippet: props.sourceSnippet || '',
      sourceLocation: props.sourceLocation || '',
      items: checkedEntries.value.map(e => ({
        entryType: e.entryType,
        wordProto: e.wordProto,
        partSpeechMean: e.partSpeechMean,
        usPhonetic: e.usPhonetic,
        ukPhonetic: e.ukPhonetic,
        sentenceExample: e.sentenceExample || props.sourceSnippet || '',
        rootAffix: e.rootAffix,
        appendExample: true
      }))
    })
    const data = res.data.data
    MessagePlugin.success(
      t('reader.collectSaveResult', {
        added: data.added,
        skipped: data.skipped,
        updated: data.updated
      })
    )

    if (viewMode.value === 'basket' && notebookId.value) {
      const savedKeys = new Set(checkedEntries.value.map(e => e.basketKey))
      basket.value = basket.value.filter(e => !savedKeys.has(e.basketKey))
      saveBasket(props.bookId, notebookId.value, basket.value)
      refreshCounts()
    }
    checkedKeys.value = new Set()
    emit('saved')
    // 保存后切到已收录，方便立刻核对
    if (viewMode.value === 'basket' && notebookId.value) {
      basketTab.value = 'saved'
      await loadSavedWords(notebookId.value)
    }
  } catch (e: any) {
    MessagePlugin.error(e?.response?.data?.message || t('reader.collectSaveFail'))
  } finally {
    saving.value = false
  }
}

function speak(text: string, accent: 'us' | 'uk' = 'us') {
  if (!text?.trim()) return
  if (!window.speechSynthesis) {
    MessagePlugin.warning(t('reader.collectSpeakUnsupported'))
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

watch(() => props.visible, (open) => {
  if (open) {
    refreshCounts()
    viewMode.value = props.preferredViewMode || (props.currentEntries.length ? 'current' : 'basket')
    basketTab.value = 'pending'
    if (props.targetNotebookId) {
      notebookId.value = props.targetNotebookId
    } else if (!notebookId.value || !props.linkedNotebooks.some(n => n.notebookId === notebookId.value)) {
      notebookId.value = props.linkedNotebooks[0]?.notebookId
    }
    if (viewMode.value === 'basket' && notebookId.value) {
      refreshBasketFor(notebookId.value)
      checkedKeys.value = new Set(basket.value.map(e => e.basketKey))
    } else {
      checkedKeys.value = new Set(props.currentEntries.map(e => e.basketKey))
    }
  }
})

watch(() => props.currentEntries, (entries) => {
  if (entries.length > 0 && props.visible && viewMode.value === 'current') {
    checkedKeys.value = new Set(entries.map(e => e.basketKey))
  }
})

watch(displayEntries, (entries) => {
  const valid = new Set(entries.map(e => e.basketKey))
  checkedKeys.value = new Set([...checkedKeys.value].filter(k => valid.has(k)))
})

watch(notebookId, (id) => {
  if (!id || !props.visible || viewMode.value !== 'basket') return
  refreshBasketFor(id)
  if (basketTab.value === 'pending') {
    checkedKeys.value = new Set(basket.value.map(e => e.basketKey))
  } else {
    void loadSavedWords(id)
  }
})
</script>

<style scoped>
.drawer-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 28px;
  min-width: 0;
}
.drawer-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.basket-tabs {
  margin-top: 2px;
}
.basket-tabs :deep(.t-tabs__nav-container) {
  margin-bottom: 0;
}
.hint {
  color: var(--td-text-color-secondary);
  font-size: 13px;
  margin: 0 0 12px;
}
.loading-row {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}
.entry-table-wrap {
  max-height: 360px;
  overflow: auto;
  margin-bottom: 16px;
}
.entry-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.entry-table th,
.entry-table td {
  border-bottom: 1px solid var(--td-component-border);
  padding: 8px 6px;
  text-align: left;
  vertical-align: top;
}
.proto {
  margin-left: 6px;
  font-weight: 600;
}
.proto-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}
.speak-btn {
  color: var(--brand-ink, #315C34);
  padding: 0 2px !important;
  min-width: 24px !important;
}
.speak-btn:hover {
  color: var(--brand-ink-hover, #274a2a);
}
.collect-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.target-label {
  font-size: 13px;
  color: var(--td-text-color-secondary);
}
.notebook-pick :deep(.t-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.btn-row {
  display: flex;
  gap: 8px;
}
</style>
