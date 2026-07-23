<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/notebooks')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ notebookName }}</h1>
      </div>
      <div class="header-actions">
        <!-- 暂时隐藏批量生成入口 -->
        <!-- <t-button @click="openBatchImport">批量生成</t-button> -->
        <t-button theme="primary" @click="openAddDialog">添加单词</t-button>
      </div>
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
              <span v-if="word.partSpeechMean" class="word-meaning-inline">{{ word.partSpeechMean }}</span>
            </template>
            <template #description>
              <div class="word-detail">
                <div v-if="word.usPhonetic || word.ukPhonetic" class="phonetic">
                  <span class="label">US</span> {{ word.usPhonetic || '-' }}
                  <t-button variant="text" size="small" class="speaker-btn" @click="speak(word.wordProto, 'us')">
                    <t-icon name="sound" />
                  </t-button>
                  <span class="label">UK</span> {{ word.ukPhonetic || '-' }}
                  <t-button variant="text" size="small" class="speaker-btn" @click="speak(word.wordProto, 'uk')">
                    <t-icon name="sound" />
                  </t-button>
                </div>
                <div v-if="word.sentenceExample" class="example">
                  <span class="label">例句</span>
                  <span class="example-content">{{ word.sentenceExample }}</span>
                </div>
                <div v-if="word.rootAffix" class="root">
                  <span class="label">词根</span> {{ displayRootAffix(word.rootAffix) }}
                </div>
              </div>
            </template>
          </t-list-item-meta>
          <template #action>
            <t-button variant="text" theme="primary" @click="openEditDialog(word)">修改</t-button>
            <t-popconfirm content="确定删除该单词？删除后不可恢复。" @confirm="handleDelete(word.id)">
              <t-button variant="text" theme="danger">移除</t-button>
            </t-popconfirm>
          </template>
        </t-list-item>
      </t-list>
      <t-empty v-else description="还没有单词，点击右上角添加" />
    </div>

    <!-- 添加/编辑弹窗 -->
    <t-dialog
      v-model:visible="showDialog"
      :header="dialogTitle"
      :confirm-btn="{ content: '确认', loading: saving }"
      @confirm="handleSubmit"
    >
      <t-form :data="form" label-width="80px">
        <t-form-item label="单词" name="wordProto" :required-mark="true">
          <t-input v-model="form.wordProto" placeholder="请输入单词原型" />
        </t-form-item>
        <t-form-item label="美式音标" name="usPhonetic">
          <t-input v-model="form.usPhonetic" placeholder="如 /ˈeksəmpəl/（选填）" />
        </t-form-item>
        <t-form-item label="英式音标" name="ukPhonetic">
          <t-input v-model="form.ukPhonetic" placeholder="如 /ɪɡˈzɑːmpəl/（选填）" />
        </t-form-item>
        <t-form-item label="释义" name="partSpeechMean" :required-mark="true">
          <t-input v-model="form.partSpeechMean" placeholder="如 n. 例子，实例" />
        </t-form-item>
        <t-form-item label="例句" name="sentenceExample">
          <t-textarea
            v-model="form.sentenceExample"
            placeholder="请输入例句（选填）"
            :autosize="{ minRows: 5, maxRows: 12 }"
          />
        </t-form-item>
        <t-form-item label="词根词缀" name="rootAffix">
          <t-input v-model="form.rootAffix" placeholder="请输入词根词缀解析（选填）" />
        </t-form-item>
      </t-form>
    </t-dialog>

    <!-- 批量生成弹窗 -->
    <t-dialog
      v-model:visible="showBatchDialog"
      header="批量生成单词"
      width="900px"
      :footer="false"
      :close-btn="!batchParsing"
      :close-on-esc-keydown="!batchParsing"
      :close-on-overlay-click="false"
    >
      <!-- 步骤 1：输入 -->
      <div v-if="batchStep === 1">
        <p class="batch-hint">粘贴单词/短语列表，每行一个，AI 将自动生成音标与释义（单次最多 20 个）：</p>
        <t-textarea
          v-model="batchInput"
          placeholder="unload&#10;set up&#10;strategy&#10;strategic"
          :autosize="{ minRows: 8, maxRows: 16 }"
        />
        <div class="batch-actions">
          <t-button :disabled="batchParsing" @click="closeBatchDialog">取消</t-button>
          <t-button theme="primary" :loading="batchParsing" @click="handleAiParse">开始生成</t-button>
        </div>
      </div>

      <!-- 步骤 2：预览+编辑（流式时直接进入此步骤，边生成边填充表格） -->
      <div v-if="batchStep === 2">
        <div v-if="batchParsing" class="stream-status">
          <t-loading size="small" />
          <span>AI 正在生成中... 已生成 {{ batchResults.length }} 条</span>
        </div>
        <t-table
          :data="batchResults"
          :columns="batchColumns"
          row-key="wordProto"
          max-height="400"
          stripe
        >
          <template #wordProto="{ row, rowIndex }">
            <t-input v-model="batchResults[rowIndex].wordProto" size="small" :disabled="batchParsing" />
          </template>
          <template #usPhonetic="{ row, rowIndex }">
            <t-input v-model="batchResults[rowIndex].usPhonetic" size="small" :disabled="batchParsing" />
          </template>
          <template #ukPhonetic="{ row, rowIndex }">
            <t-input v-model="batchResults[rowIndex].ukPhonetic" size="small" :disabled="batchParsing" />
          </template>
          <template #partSpeechMean="{ row, rowIndex }">
            <t-input v-model="batchResults[rowIndex].partSpeechMean" size="small" :disabled="batchParsing" />
          </template>
          <template #sentenceExample="{ row, rowIndex }">
            <t-tooltip :content="row.sentenceExample" placement="top" :show-arrow="true">
              <t-input v-model="batchResults[rowIndex].sentenceExample" size="small" :disabled="batchParsing" />
            </t-tooltip>
          </template>
          <template #rootAffix="{ row, rowIndex }">
            <t-tooltip :content="row.rootAffix" placement="top" :show-arrow="true">
              <t-input v-model="batchResults[rowIndex].rootAffix" size="small" :disabled="batchParsing" />
            </t-tooltip>
          </template>
          <template #op="{ rowIndex }">
            <t-button variant="text" theme="danger" size="small" :disabled="batchParsing" @click="batchResults.splice(rowIndex, 1)">删除</t-button>
          </template>
        </t-table>
        <div class="batch-actions">
          <t-button :disabled="batchParsing" @click="resetBatch">返回修改</t-button>
          <span class="batch-count">共 {{ batchResults.length }} 条</span>
          <t-button theme="primary" :loading="batchSaving" :disabled="batchParsing" @click="handleBatchSave">确认导入</t-button>
        </div>
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, reactive, computed } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { getWords, addWord, updateWord, deleteWord, aiParseWords, aiParseWordsStream, batchAddWords } from '../api'

const route = useRoute()
const router = useRouter()
const notebookId = Number(route.params.id)
const notebookName = ref('')
const words = ref<any[]>([])
const showDialog = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

// Batch import state
const showBatchDialog = ref(false)
const batchStep = ref(1)
const batchInput = ref('')
const batchParsing = ref(false)
const batchSaving = ref(false)
const batchResults = ref<any[]>([])
const batchStreamText = ref('')

function warnBatchBusy() {
  MessagePlugin.warning('AI 正在生成中，请稍候完成后再操作')
}

function closeBatchDialog() {
  if (batchParsing.value) {
    warnBatchBusy()
    return
  }
  showBatchDialog.value = false
}

function onBeforeUnload(e: BeforeUnloadEvent) {
  if (!batchParsing.value) return
  e.preventDefault()
  e.returnValue = ''
}

watch(showBatchDialog, (visible) => {
  if (!visible && batchParsing.value) {
    showBatchDialog.value = true
    warnBatchBusy()
  }
})

onBeforeRouteLeave(() => {
  if (batchParsing.value) {
    warnBatchBusy()
    return false
  }
})

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  loadWords()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
})

const batchColumns = [
  { colKey: 'wordProto', title: '单词', width: 120, cell: 'wordProto' },
  { colKey: 'usPhonetic', title: '美音', width: 130, cell: 'usPhonetic' },
  { colKey: 'ukPhonetic', title: '英音', width: 130, cell: 'ukPhonetic' },
  { colKey: 'partSpeechMean', title: '释义', width: 160, cell: 'partSpeechMean' },
  { colKey: 'sentenceExample', title: '例句', width: 200, cell: 'sentenceExample' },
  { colKey: 'rootAffix', title: '词根词缀', width: 160, cell: 'rootAffix' },
  { colKey: 'op', title: '操作', width: 70, cell: 'op' },
]

const dialogTitle = computed(() => (editingId.value == null ? '添加单词' : '编辑单词'))

const form = reactive({
  wordProto: '', usPhonetic: '', ukPhonetic: '', partSpeechMean: '',
  sentenceExample: '', rootAffix: ''
})

function resetForm() {
  Object.keys(form).forEach(k => (form as any)[k] = '')
}

function openAddDialog() {
  editingId.value = null
  resetForm()
  showDialog.value = true
}

function openEditDialog(word: any) {
  editingId.value = word.id
  form.wordProto = word.wordProto ?? ''
  form.usPhonetic = word.usPhonetic ?? ''
  form.ukPhonetic = word.ukPhonetic ?? ''
  form.partSpeechMean = word.partSpeechMean ?? ''
  form.sentenceExample = word.sentenceExample ?? ''
  form.rootAffix = word.rootAffix ?? ''
  showDialog.value = true
}

async function loadWords() {
  try {
    const res = await getWords(notebookId)
    words.value = res.data.data
  } catch (e) {
    MessagePlugin.error('加载失败')
  }
}

async function handleSubmit() {
  if (!form.wordProto.trim()) {
    MessagePlugin.warning('请输入单词')
    return false
  }
  if (!form.partSpeechMean.trim()) {
    MessagePlugin.warning('请输入释义')
    return false
  }
  saving.value = true
  try {
    const data = {
      wordProto: form.wordProto,
      usPhonetic: form.usPhonetic,
      ukPhonetic: form.ukPhonetic,
      partSpeechMean: form.partSpeechMean,
      sentenceExample: form.sentenceExample,
      rootAffix: form.rootAffix,
    }
    if (editingId.value == null) {
      await addWord(notebookId, data)
      MessagePlugin.success('添加成功')
    } else {
      await updateWord(notebookId, editingId.value, data)
      MessagePlugin.success('修改成功')
    }
    showDialog.value = false
    resetForm()
    editingId.value = null
    await loadWords()
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || '保存失败，请联系管理员'
    MessagePlugin.error(msg)
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

// Batch import functions
function openBatchImport() {
  batchInput.value = ''
  batchResults.value = []
  batchStreamText.value = ''
  batchStep.value = 1
  parsedOffset = 0
  showBatchDialog.value = true
}

async function handleAiParse() {
  const lines = batchInput.value
    .split('\n')
    .map(s => s.trim())
    .filter(s => s.length > 0)
  if (lines.length === 0) {
    MessagePlugin.warning('请输入单词')
    return
  }
  if (lines.length > 20) {
    MessagePlugin.warning('单次最多生成 20 个单词')
    return
  }
  // 校验：每行必须是英文（允许字母、空格、连字符、撇号、点）
  const invalidLines: string[] = []
  for (const line of lines) {
    if (!/^[A-Za-z\s\-'.]+$/.test(line)) {
      invalidLines.push(line)
    }
  }
  if (invalidLines.length > 0) {
    MessagePlugin.error(`包含非英文内容: ${invalidLines.slice(0, 3).join(', ')}${invalidLines.length > 3 ? '...' : ''}`)
    return
  }
  batchParsing.value = true
  batchResults.value = []
  batchStreamText.value = ''
  parsedOffset = 0
  // 直接跳到步骤 2 展示表格，流式填充
  batchStep.value = 2
  try {
    const response = await aiParseWordsStream(notebookId, { words: lines })
    if (!response.ok) {
      throw new Error('请求失败: ' + response.status)
    }
    const reader = response.body?.getReader()
    if (!reader) throw new Error('不支持流式读取')

    const decoder = new TextDecoder()
    let fullText = ''
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // SSE 事件以 \n\n 分隔，按事件块解析
      const events = buffer.split('\n\n')
      // 最后一段可能不完整，保留到下次
      buffer = events.pop() || ''

      for (const eventBlock of events) {
        if (!eventBlock.trim()) continue
        const lines2 = eventBlock.split('\n')
        let eventType = ''
        let dataLines: string[] = []

        for (const line of lines2) {
          // Spring SseEmitter 输出格式: "event:chunk\n" 和 "data:<content>\n"
          if (line.startsWith('event:')) {
            eventType = line.substring('event:'.length).trim()
          } else if (line.startsWith('data:')) {
            dataLines.push(line.substring('data:'.length))
          }
        }

        if (dataLines.length === 0) continue
        const data = dataLines.join('\n')

        // 诊断日志：第一个 chunk 时打印原始 event 块
        if (fullText.length === 0 && eventType === 'chunk') {
          console.log('[SSE] 第一个 chunk 事件块:', eventBlock.substring(0, 200))
          console.log('[SSE] data 长度:', data.length, '前 100 字符:', data.substring(0, 100))
        }

        if (eventType === 'chunk') {
          fullText += data
          tryUpdateBatchResults(fullText)
        } else if (eventType === 'done') {
          // 强制再解析一次（用 force=true 仅触发空结果错误），但不再分两种弹错
          tryUpdateBatchResults(fullText, true)
          // 诊断日志：打印 fullText 总长度与前 500 字符
          console.log('[SSE] done 事件触发，fullText 总长度:', fullText.length)
          console.log('[SSE] fullText 前 500 字符:', fullText.substring(0, 500))
          console.log('[SSE] batchResults 当前数量:', batchResults.value.length)
        } else if (eventType === 'error') {
          MessagePlugin.error(data || 'AI 解析失败')
          batchParsing.value = false
          return
        }
      }
    }

    if (batchResults.value.length === 0) {
      MessagePlugin.warning('AI 未能解析出单词信息')
      batchStep.value = 1
    }
  } catch (e: any) {
    MessagePlugin.error(e.message || 'AI 解析失败，请重试')
    batchStep.value = 1
  } finally {
    batchParsing.value = false
  }
}

// 用于记录已解析到文本的哪个位置，避免重复解析
let parsedOffset = 0

/** 兼容模型自创字段名（word/cn_definition 等）→ 前端表格字段 */
function normalizeBatchResult(raw: any) {
  const pick = (...keys: string[]) => {
    for (const k of keys) {
      const v = raw?.[k]
      if (v != null && String(v).trim() !== '') return String(v).trim()
    }
    return ''
  }
  const wordProto = pick('wordProto', 'word', 'lemma', 'text')
  const usPhonetic = pick('usPhonetic', 'phonetic', 'us_phonetic', 'ipa')
  const ukPhonetic = pick('ukPhonetic', 'uk_phonetic') || usPhonetic
  const pos = pick('pos', 'partOfSpeech')
  const cn = pick('partSpeechMean', 'cn_definition', 'definition', 'meaning')
  const en = pick('en_definition')
  let partSpeechMean = cn
  if (pos && cn && !cn.startsWith(pos)) {
    partSpeechMean = `${pos} ${cn}`
  } else if (!partSpeechMean && en) {
    partSpeechMean = pos ? `${pos} ${en}` : en
  }
  const exampleEn = pick('sentenceExample', 'example_en', 'example')
  const exampleCn = pick('example_cn')
  const sentenceExample = exampleEn && exampleCn && !exampleEn.includes(exampleCn)
    ? `${exampleEn} ${exampleCn}`
    : exampleEn || exampleCn
  const rootAffix = pick('rootAffix', 'root_affix', 'etymology') || '暂无'
  return { wordProto, usPhonetic, ukPhonetic, partSpeechMean, sentenceExample, rootAffix }
}

/** 从流式文本中逐步提取 JSON 对象并更新表格（基于大括号平衡） */
function tryUpdateBatchResults(text: string, force: boolean = false) {
  // 找到 JSON 数组起始位置
  const start = text.indexOf('[')
  if (start === -1) {
    if (force && batchResults.value.length === 0) {
      MessagePlugin.error('AI 返回数据格式异常，未找到 JSON 数组')
    }
    return
  }
  // 跳过 [ 之前的所有内容（包括 markdown 围栏）
  if (parsedOffset < start + 1) {
    parsedOffset = start + 1
  }

  // 从 parsedOffset 开始扫描，逐个提取完整的 {...} 对象
  while (parsedOffset < text.length) {
    // 跳过空白和逗号
    while (parsedOffset < text.length && /[\s,]/.test(text[parsedOffset])) {
      parsedOffset++
    }
    if (parsedOffset >= text.length) break
    // 流式 JSON 还没到下一个对象，先停
    if (text[parsedOffset] !== '{') break

    // 大括号平衡扫描，忽略字符串内的括号
    const objStart = parsedOffset
    let depth = 0
    let inString = false
    let escape = false
    let objEnd = -1
    for (let i = parsedOffset; i < text.length; i++) {
      const ch = text[i]
      if (inString) {
        if (escape) {
          escape = false
        } else if (ch === '\\') {
          escape = true
        } else if (ch === '"') {
          inString = false
        }
        continue
      }
      if (ch === '"') {
        inString = true
      } else if (ch === '{') {
        depth++
      } else if (ch === '}') {
        depth--
        if (depth === 0) {
          objEnd = i
          break
        }
      }
    }
    if (objEnd === -1) {
      // 还没到完整对象边界，等下一个 chunk
      break
    }
    const objStr = text.substring(objStart, objEnd + 1)
    try {
      const obj = normalizeBatchResult(JSON.parse(objStr))
      // 避免重复追加（防御性，正常 parsedOffset 已保证不重复）
      const exists = batchResults.value.some(
        (r: any) => r.wordProto && obj.wordProto && r.wordProto === obj.wordProto
      )
      if (!exists && obj.wordProto) {
        batchResults.value.push(obj)
      }
    } catch (e) {
      // 单个对象解析失败，跳过这一个继续（不致命）
      console.warn('解析单个 JSON 对象失败:', objStr.substring(0, 100), e)
    }
    parsedOffset = objEnd + 1
  }

  if (force && batchResults.value.length === 0) {
    MessagePlugin.error('AI 未能解析出单词信息')
  }
}

function resetBatch() {
  if (batchParsing.value) {
    warnBatchBusy()
    return
  }
  batchStep.value = 1
  batchResults.value = []
  batchStreamText.value = ''
  parsedOffset = 0
}

async function handleBatchSave() {
  if (batchResults.value.length === 0) {
    MessagePlugin.warning('没有可导入的单词')
    return
  }
  batchSaving.value = true
  try {
    await batchAddWords(notebookId, { words: batchResults.value })
    MessagePlugin.success(`成功导入 ${batchResults.value.length} 个单词`)
    showBatchDialog.value = false
    await loadWords()
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || '导入失败')
  } finally {
    batchSaving.value = false
  }
}

function statusTheme(status: number) {
  if (status === 0) return 'default'
  if (status === 7) return 'success'
  if (status >= 1 && status <= 6) return 'primary'
  return 'default'
}

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

function speak(text: string, accent: 'us' | 'uk') {
  if (!window.speechSynthesis) {
    MessagePlugin.warning('当前浏览器不支持语音播放')
    return
  }
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = accent === 'us' ? 'en-US' : 'en-GB'
  utterance.rate = 0.85

  // 优先选择对应口音的原生英文发音人
  const voices = window.speechSynthesis.getVoices()
  if (voices.length > 0) {
    const langPrefix = accent === 'us' ? 'en-US' : 'en-GB'
    // 优先匹配完全一致的语言 + 高分语音
    let best = voices.find(v => v.lang === langPrefix && v.localService)
    if (!best) best = voices.find(v => v.lang.startsWith(langPrefix))
    if (!best) best = voices.find(v => v.lang.startsWith('en-'))
    if (best) utterance.voice = best
  }
  window.speechSynthesis.speak(utterance)
}

function displayRootAffix(text: string) {
  if (!text) return ''
  // "短语，无词根词缀" 这种直接显示为 "无"
  if (text.includes('无词根词缀') || text.includes('无词根')) return '无'
  return text
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: transparent;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.page-header > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-header h1 {
  font-size: 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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

.word-meaning-inline {
  margin-left: 24px;
  font-size: 14px;
  font-weight: 400;
  color: #4b5563;
}

.word-detail {
  font-size: 14px;
  color: #374151;
  line-height: 1.8;
}

.word-detail .phonetic {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  color: #6b7280;
}

.speaker-btn {
  margin-right: 10px;
  color: #6b7280;
}

.word-detail .label {
  display: inline-block;
  min-width: 36px;
  padding: 0 6px;
  margin-right: 4px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  border-radius: 4px;
  text-align: center;
}

.word-detail .meaning {
  margin-top: 4px;
}

.word-detail .example,
.word-detail .root {
  color: #4b5563;
  display: flex;
  align-items: flex-start;
  gap: 4px;
}

.example-content {
  white-space: pre-line;
  flex: 1;
}

.batch-hint {
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 8px;
}

.batch-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  gap: 12px;
}

.batch-count {
  color: #6b7280;
  font-size: 13px;
}

.stream-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 4px;
}
</style>
