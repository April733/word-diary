<template>
  <t-drawer
    v-model:visible="visibleModel"
    size="720px"
    :close-btn="true"
    :footer="false"
  >
    <template #header>
      <div class="drawer-header">
        <div class="drawer-title">
          <template v-if="viewMode === 'notebook' && linkedNotebooks.length">
            <t-dropdown
              trigger="click"
              :options="notebookMenuOptions"
              :min-column-width="200"
              :max-column-width="360"
              @click="onNotebookMenuClick"
            >
              <button type="button" class="drawer-title-switch" :title="$t('reader.notebookLinkManage')">
                <span class="drawer-title-text">{{ drawerTitle }}</span>
                <t-icon name="chevron-down" size="16px" />
              </button>
            </t-dropdown>
            <span
              class="count-badge"
              :title="$t('reader.collectWordCount', { n: savedWords.length })"
            >{{ savedWords.length }}</span>
          </template>
          <template v-else>
            <span class="drawer-title-text">{{ drawerTitle }}</span>
          </template>
        </div>
        <div v-if="linkedNotebooks.length && viewMode === 'notebook'" class="drawer-header-actions">
          <t-button
            variant="text"
            size="small"
            :title="$t('reader.collectAddWord')"
            @click="openAddWord"
          >
            <t-icon name="add" size="18px" />
            {{ $t('reader.collectAddWord') }}
          </t-button>
        </div>
      </div>
    </template>

    <div v-if="linkedNotebooks.length === 0" class="hint">
      <p>{{ $t('reader.collectNeedLink') }}</p>
      <t-button theme="primary" size="small" @click="emit('open-link-dialog')">
        {{ $t('reader.notebookLinkTitle') }}
      </t-button>
    </div>

    <div v-else class="collect-drawer-body">
      <!-- 本次 AI 识别：勾选后直接保存 -->
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
                <th class="col-meaning">{{ $t('reader.collectMeaning') }}</th>
                <th>{{ $t('reader.collectRootAffix') }}</th>
                <th>{{ $t('reader.collectExample') }}</th>
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
                  <div class="proto-block">
                    <div class="proto-row">
                      <span class="proto">{{ entry.wordProto }}</span>
                    </div>
                    <div class="ph-pair">
                      <span class="ph-item">
                        <span class="ph-label">US</span>
                        <span class="ph-ipa">{{ entry.usPhonetic || '—' }}</span>
                        <button
                          type="button"
                          class="speak-btn"
                          :title="$t('reader.collectPlayUs')"
                          @click.stop="speak(entry.wordProto, 'us')"
                        >
                          <t-icon name="sound" size="14px" />
                        </button>
                      </span>
                      <span class="ph-item">
                        <span class="ph-label">UK</span>
                        <span class="ph-ipa">{{ entry.ukPhonetic || '—' }}</span>
                        <button
                          type="button"
                          class="speak-btn"
                          :title="$t('reader.collectPlayUk')"
                          @click.stop="speak(entry.wordProto, 'uk')"
                        >
                          <t-icon name="sound" size="14px" />
                        </button>
                      </span>
                    </div>
                  </div>
                </td>
                <td class="cell-meaning">{{ entry.partSpeechMean || '—' }}</td>
                <td class="cell-muted">{{ entry.rootAffix || '—' }}</td>
                <td class="cell-example">
                  <t-tooltip
                    v-if="exampleForEntry(entry)"
                    :content="exampleForEntry(entry)"
                    placement="top"
                    :show-arrow="true"
                  >
                    <span class="example-ellipsis">{{ ellipsisText(exampleForEntry(entry)) }}</span>
                  </t-tooltip>
                  <span v-else>—</span>
                </td>
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
              </t-radio-button>
            </t-radio-group>
          </div>
          <t-button
            theme="primary"
            block
            :loading="saving"
            :disabled="!notebookId || checkedEntries.length === 0"
            @click="saveToNotebook"
          >
            {{ $t('reader.collectSave') }}
          </t-button>
        </div>
      </template>

      <!-- 单词本词表 -->
      <template v-else-if="viewMode === 'notebook'">
        <div v-if="savedLoading" class="loading-row">
          <t-loading size="small" />
        </div>
        <div v-else-if="!savedWords.length" class="hint">
          <p>{{ $t('reader.collectSavedEmpty') }}</p>
        </div>
        <div v-else class="entry-table-wrap">
          <table class="entry-table">
            <thead>
              <tr>
                <th>{{ $t('reader.collectWord') }}</th>
                <th class="col-meaning">{{ $t('reader.collectMeaning') }}</th>
                <th>{{ $t('reader.collectRootAffix') }}</th>
                <th>{{ $t('reader.collectExample') }}</th>
                <th style="width: 64px"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="word in savedWords" :key="word.id">
                <td>
                  <div class="proto-block">
                    <div class="proto-row">
                      <span class="proto">{{ word.wordProto }}</span>
                    </div>
                    <div class="ph-pair">
                      <span class="ph-item">
                        <span class="ph-label">US</span>
                        <span class="ph-ipa">{{ word.usPhonetic || '—' }}</span>
                        <button
                          type="button"
                          class="speak-btn"
                          :title="$t('reader.collectPlayUs')"
                          @click.stop="speak(word.wordProto, 'us')"
                        >
                          <t-icon name="sound" size="14px" />
                        </button>
                      </span>
                      <span class="ph-item">
                        <span class="ph-label">UK</span>
                        <span class="ph-ipa">{{ word.ukPhonetic || '—' }}</span>
                        <button
                          type="button"
                          class="speak-btn"
                          :title="$t('reader.collectPlayUk')"
                          @click.stop="speak(word.wordProto, 'uk')"
                        >
                          <t-icon name="sound" size="14px" />
                        </button>
                      </span>
                    </div>
                  </div>
                </td>
                <td class="cell-meaning">{{ word.partSpeechMean || '—' }}</td>
                <td class="cell-muted">{{ word.rootAffix || '—' }}</td>
                <td class="cell-example">
                  <t-tooltip
                    v-if="word.sentenceExample"
                    :content="stripOriginalPrefix(word.sentenceExample)"
                    placement="top"
                    :show-arrow="true"
                  >
                    <span class="example-ellipsis">{{ ellipsisText(word.sentenceExample) }}</span>
                  </t-tooltip>
                  <span v-else>—</span>
                </td>
                <td class="row-actions">
                  <t-button variant="text" theme="primary" size="small" shape="square" @click="openEditWord(word)">
                    <template #icon><t-icon name="edit" size="16px" /></template>
                  </t-button>
                  <t-popconfirm
                    :content="$t('reader.collectRemoveConfirm')"
                    @confirm="removeWord(word.id)"
                  >
                    <t-button variant="text" theme="danger" size="small" shape="square">
                      <template #icon><t-icon name="delete" size="16px" /></template>
                    </t-button>
                  </t-popconfirm>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>

    <t-dialog
      v-model:visible="showWordDialog"
      :header="editingWordId == null ? $t('reader.collectAddWord') : $t('reader.collectEditWord')"
      :confirm-btn="{ content: $t('common.confirm'), loading: wordSaving }"
      @confirm="submitWordForm"
    >
      <t-form :data="wordForm" label-width="80px">
        <t-form-item :label="$t('reader.collectWord')" name="wordProto" :required-mark="true">
          <t-input v-model="wordForm.wordProto" :placeholder="$t('reader.collectWordPlaceholder')" />
        </t-form-item>
        <t-form-item :label="$t('reader.collectUsPhonetic')" name="usPhonetic">
          <t-input v-model="wordForm.usPhonetic" :placeholder="$t('reader.collectUsPhoneticPlaceholder')" />
        </t-form-item>
        <t-form-item :label="$t('reader.collectUkPhonetic')" name="ukPhonetic">
          <t-input v-model="wordForm.ukPhonetic" :placeholder="$t('reader.collectUkPhoneticPlaceholder')" />
        </t-form-item>
        <t-form-item :label="$t('reader.collectMeaning')" name="partSpeechMean" :required-mark="true">
          <t-input v-model="wordForm.partSpeechMean" :placeholder="$t('reader.collectMeaningPlaceholder')" />
        </t-form-item>
        <t-form-item :label="$t('reader.collectExample')" name="sentenceExample">
          <t-textarea
            v-model="wordForm.sentenceExample"
            :placeholder="$t('reader.collectExamplePlaceholder')"
            :autosize="{ minRows: 3, maxRows: 8 }"
          />
        </t-form-item>
        <t-form-item :label="$t('reader.collectRootAffix')" name="rootAffix">
          <t-input v-model="wordForm.rootAffix" :placeholder="$t('reader.collectRootAffixPlaceholder')" />
        </t-form-item>
      </t-form>
    </t-dialog>
  </t-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { DialogPlugin, MessagePlugin } from 'tdesign-vue-next'
import { useI18n } from 'vue-i18n'
import { addWord, collectBookWords, deleteWord, getWords, updateWord } from '../api'
import type { CollectEntry } from '../utils/collectBasket'
import { ellipsisText, pickSentenceForLemma, stripOriginalPrefix } from '../utils/selectedText'

interface SavedWord {
  id: number
  wordProto: string
  partSpeechMean?: string
  entryType?: string
  rootAffix?: string
  sentenceExample?: string
  usPhonetic?: string
  ukPhonetic?: string
}

const props = defineProps<{
  visible: boolean
  bookId: number
  currentEntries: CollectEntry[]
  linkedNotebooks: { notebookId: number; notebookName: string }[]
  sourceSnippet?: string
  sourceLocation?: string
  /** current=本次识别；notebook=单词本词表 */
  preferredViewMode?: 'current' | 'notebook'
  targetNotebookId?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
  'open-link-dialog': []
  'select-notebook': [notebookId: number]
}>()

const { t } = useI18n()
const viewMode = ref<'current' | 'notebook'>('current')
const checkedKeys = ref<Set<string>>(new Set())
const notebookId = ref<number | undefined>()
const saving = ref(false)
const savedWords = ref<SavedWord[]>([])
const savedLoading = ref(false)
const showWordDialog = ref(false)
const editingWordId = ref<number | null>(null)
const wordSaving = ref(false)
const wordForm = reactive({
  wordProto: '',
  usPhonetic: '',
  ukPhonetic: '',
  partSpeechMean: '',
  sentenceExample: '',
  rootAffix: '',
})

const visibleModel = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

const notebookMenuOptions = computed(() => {
  const items = props.linkedNotebooks.map((nb) => ({
    content: nb.notebookName,
    value: String(nb.notebookId),
    active: nb.notebookId === notebookId.value,
  }))
  items.push({
    content: t('reader.notebookLinkCreate'),
    value: '__create__',
    active: false,
  })
  return items
})

function onNotebookMenuClick(dropdownItem: { value?: string | number }) {
  const val = dropdownItem?.value
  if (val == null) return
  if (val === '__create__') {
    emit('open-link-dialog')
    return
  }
  const id = Number(val)
  if (!Number.isFinite(id) || id === notebookId.value) return
  notebookId.value = id
  emit('select-notebook', id)
}
/** 本次收录：每词只展示包含该词的那一句原文 */
function exampleForEntry(entry: CollectEntry): string {
  const snippet = props.sourceSnippet || entry.sourceSnippet || entry.sentenceExample || ''
  return pickSentenceForLemma(snippet, entry.wordProto)
}

const activeNotebookName = computed(() => {
  const nb = props.linkedNotebooks.find(n => n.notebookId === notebookId.value)
  return nb?.notebookName || ''
})

const drawerTitle = computed(() => {
  if (viewMode.value === 'notebook' && activeNotebookName.value) {
    return activeNotebookName.value
  }
  return t('reader.collectTitle')
})

const checkedEntries = computed(() =>
  props.currentEntries.filter(e => checkedKeys.value.has(e.basketKey))
)

const allChecked = computed(() =>
  props.currentEntries.length > 0 && props.currentEntries.every(e => checkedKeys.value.has(e.basketKey))
)

const indeterminate = computed(() => {
  const total = props.currentEntries.length
  const checked = checkedEntries.value.length
  return checked > 0 && checked < total
})

async function loadSavedWords(nbId: number) {
  savedLoading.value = true
  try {
    const res = await getWords(nbId)
    savedWords.value = (res.data?.data || []) as SavedWord[]
  } catch {
    savedWords.value = []
    MessagePlugin.error(t('reader.collectSavedLoadFail'))
  } finally {
    savedLoading.value = false
  }
}

async function removeWord(wordId: number) {
  if (!notebookId.value) return
  try {
    await deleteWord(notebookId.value, wordId)
    MessagePlugin.success(t('reader.collectRemoved'))
    savedWords.value = savedWords.value.filter(w => w.id !== wordId)
  } catch (e: any) {
    MessagePlugin.error(e?.response?.data?.message || t('reader.collectRemoveFail'))
  }
}

function resetWordForm() {
  wordForm.wordProto = ''
  wordForm.usPhonetic = ''
  wordForm.ukPhonetic = ''
  wordForm.partSpeechMean = ''
  wordForm.sentenceExample = ''
  wordForm.rootAffix = ''
}

function openAddWord() {
  if (!notebookId.value) return
  editingWordId.value = null
  resetWordForm()
  showWordDialog.value = true
}

function openEditWord(word: SavedWord) {
  editingWordId.value = word.id
  wordForm.wordProto = word.wordProto ?? ''
  wordForm.usPhonetic = word.usPhonetic ?? ''
  wordForm.ukPhonetic = word.ukPhonetic ?? ''
  wordForm.partSpeechMean = word.partSpeechMean ?? ''
  wordForm.sentenceExample = word.sentenceExample ?? ''
  wordForm.rootAffix = word.rootAffix ?? ''
  showWordDialog.value = true
}

async function submitWordForm() {
  if (!notebookId.value) return false
  if (!wordForm.wordProto.trim()) {
    MessagePlugin.warning(t('reader.collectWordRequired'))
    return false
  }
  if (!wordForm.partSpeechMean.trim()) {
    MessagePlugin.warning(t('reader.collectMeaningRequired'))
    return false
  }
  wordSaving.value = true
  try {
    const data = {
      wordProto: wordForm.wordProto.trim(),
      usPhonetic: wordForm.usPhonetic.trim(),
      ukPhonetic: wordForm.ukPhonetic.trim(),
      partSpeechMean: wordForm.partSpeechMean.trim(),
      sentenceExample: wordForm.sentenceExample.trim(),
      rootAffix: wordForm.rootAffix.trim(),
    }
    if (editingWordId.value == null) {
      await addWord(notebookId.value, data)
      MessagePlugin.success(t('reader.collectAddWordSuccess'))
    } else {
      await updateWord(notebookId.value, editingWordId.value, data)
      MessagePlugin.success(t('reader.collectEditWordSuccess'))
    }
    showWordDialog.value = false
    resetWordForm()
    editingWordId.value = null
    await loadSavedWords(notebookId.value)
  } catch (e: any) {
    MessagePlugin.error(e?.response?.data?.message || e?.message || t('reader.collectSaveFail'))
    return false
  } finally {
    wordSaving.value = false
  }
}

function toggleOne(key: string, checked: boolean) {
  const next = new Set(checkedKeys.value)
  if (checked) next.add(key)
  else next.delete(key)
  checkedKeys.value = next
}

function toggleAll(checked: boolean) {
  checkedKeys.value = checked
    ? new Set(props.currentEntries.map(e => e.basketKey))
    : new Set()
}

async function saveToNotebook() {
  if (!notebookId.value || checkedEntries.value.length === 0) return
  if (props.linkedNotebooks.length === 0) {
    emit('open-link-dialog')
    return
  }

  // 保存前刷新词表，检测已存在词条
  await loadSavedWords(notebookId.value)
  const existingKeys = new Set(
    savedWords.value.map(w => (w.wordProto || '').trim().toLowerCase()).filter(Boolean)
  )
  const duplicates = checkedEntries.value.filter(e =>
    existingKeys.has(e.wordProto.trim().toLowerCase())
  )
  let overwriteExisting = false
  if (duplicates.length > 0) {
    const names = duplicates.map(e => e.wordProto).join(t('reader.collectWordListSep'))
    overwriteExisting = await askOverwriteExisting(names, duplicates.length)
  }

  saving.value = true
  try {
    const res = await collectBookWords(props.bookId, {
      notebookId: notebookId.value,
      sourceSnippet: props.sourceSnippet || '',
      sourceLocation: props.sourceLocation || '',
      overwriteExisting,
      items: checkedEntries.value.map(e => ({
        entryType: e.entryType,
        wordProto: e.wordProto,
        partSpeechMean: e.partSpeechMean,
        usPhonetic: e.usPhonetic,
        ukPhonetic: e.ukPhonetic,
        rootAffix: e.rootAffix,
        overwrite: overwriteExisting
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
    checkedKeys.value = new Set()
    emit('saved')
    visibleModel.value = false
  } catch (e: any) {
    MessagePlugin.error(e?.response?.data?.message || t('reader.collectSaveFail'))
  } finally {
    saving.value = false
  }
}

/**
 * 已存在时询问是否覆盖。
 * 注意：TDesign 点「确定」时可能先触发 onClose，不能在 onClose 里直接判为取消。
 */
function askOverwriteExisting(names: string, count: number): Promise<boolean> {
  return new Promise(resolve => {
    let settled = false
    let choseOverwrite = false
    const finish = (overwrite: boolean) => {
      if (settled) return
      settled = true
      resolve(overwrite)
    }
    const dialog = DialogPlugin.confirm({
      header: t('reader.collectOverwriteTitle'),
      body: t('reader.collectOverwriteBody', { words: names, n: count }),
      confirmBtn: { content: t('reader.collectOverwriteYes'), theme: 'primary' },
      cancelBtn: t('reader.collectOverwriteNo'),
      onConfirm: () => {
        choseOverwrite = true
        finish(true)
        dialog.destroy()
      },
      onCancel: () => {
        finish(false)
        dialog.destroy()
      },
      onClose: () => {
        // 仅在未点「覆盖」时视为取消/跳过
        if (!choseOverwrite) finish(false)
      }
    })
  })
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
  if (!open) return
  viewMode.value = props.preferredViewMode || (props.currentEntries.length ? 'current' : 'notebook')
  if (props.targetNotebookId) {
    notebookId.value = props.targetNotebookId
  } else if (!notebookId.value || !props.linkedNotebooks.some(n => n.notebookId === notebookId.value)) {
    notebookId.value = props.linkedNotebooks[0]?.notebookId
  }
  if (viewMode.value === 'notebook' && notebookId.value) {
    void loadSavedWords(notebookId.value)
  } else if (viewMode.value === 'current') {
    checkedKeys.value = new Set(props.currentEntries.map(e => e.basketKey))
  }
})

watch(
  () => props.targetNotebookId,
  (id) => {
    if (!props.visible || id == null) return
    if (id === notebookId.value) return
    if (!props.linkedNotebooks.some(n => n.notebookId === id)) return
    notebookId.value = id
  },
)

watch(
  () => props.linkedNotebooks.map(n => n.notebookId).join(','),
  () => {
    if (!props.visible || !props.linkedNotebooks.length) return
    if (notebookId.value && props.linkedNotebooks.some(n => n.notebookId === notebookId.value)) return
    const nextId = props.targetNotebookId && props.linkedNotebooks.some(n => n.notebookId === props.targetNotebookId)
      ? props.targetNotebookId
      : props.linkedNotebooks[0].notebookId
    notebookId.value = nextId
  },
)
watch(() => props.currentEntries, (entries) => {
  if (entries.length > 0 && props.visible && viewMode.value === 'current') {
    checkedKeys.value = new Set(entries.map(e => e.basketKey))
  }
})

watch(notebookId, (id) => {
  if (!id || !props.visible) return
  if (viewMode.value === 'notebook') {
    void loadSavedWords(id)
  }
})
</script>

<style scoped>
:deep(.t-drawer__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  box-sizing: border-box;
}

.collect-drawer-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-right: 28px;
  min-width: 0;
}
.drawer-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
  min-width: 0;
  flex: 1;
}
.drawer-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.drawer-title-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  color: inherit;
  text-align: left;
}
.drawer-title-switch:hover {
  color: #5e57b5;
}
.drawer-header-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}
.row-actions {
  white-space: nowrap;
  vertical-align: middle;
}
.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--td-error-color);
  color: #fff;
  font-size: 11px;
  line-height: 1;
  flex-shrink: 0;
  font-weight: 600;
}
.hint {
  color: var(--td-text-color-secondary);
  font-size: 13px;
  margin: 0 0 12px;
  flex-shrink: 0;
}
.hint p {
  margin: 0 0 12px;
}
.loading-row {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}
.entry-table-wrap {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  margin-bottom: 0;
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
  word-break: break-word;
}
.entry-table th {
  color: var(--td-text-color-secondary);
  font-weight: 500;
  position: sticky;
  top: 0;
  background: var(--td-bg-color-container);
  z-index: 1;
}
.col-meaning,
.cell-meaning {
  width: 22%;
  max-width: 140px;
}
.cell-meaning {
  line-height: 1.45;
}
.cell-muted {
  color: var(--td-text-color-secondary);
  font-size: 12px;
  line-height: 1.45;
  max-width: 180px;
  white-space: pre-wrap;
}
.cell-example {
  max-width: 160px;
  vertical-align: middle;
}
.example-ellipsis {
  display: inline-block;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--td-text-color-secondary);
  cursor: default;
  vertical-align: bottom;
}
.proto-block {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 0;
}
.proto-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 0;
}
.ph-pair {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  margin-left: 2px;
  min-width: 0;
}
.ph-item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--td-text-color-secondary);
  line-height: 1.2;
  white-space: nowrap;
}
.ph-label {
  flex: 0 0 auto;
  font-size: 10px;
  font-weight: 600;
  opacity: 0.75;
}
.ph-ipa {
  flex: 0 1 auto;
  max-width: 12em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: inherit;
  font-size: 12px;
  letter-spacing: -0.02em;
  font-variant-numeric: normal;
}
.proto {
  font-weight: 600;
  flex: 0 0 auto;
}
.speak-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  width: 18px;
  height: 18px;
  color: var(--td-text-color-secondary);
  line-height: 1;
}
.speak-btn:hover {
  color: #315c34;
}
.collect-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
  margin-top: 12px;
  padding-top: 4px;
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
</style>
