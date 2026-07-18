<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ $t('wordbook.titleManage') }}</h1>
      </div>
      <t-button theme="primary" @click="showAddDialog = true">{{ $t('wordbook.create') }}</t-button>
    </header>

    <div class="page-body">
      <t-list v-if="notebooks.length > 0" :split="true">
        <t-list-item v-for="book in notebooks" :key="book.id">
          <t-list-item-meta :title="book.bookName" :description="book.bookDesc || $t('wordbook.noDesc')" />
          <template #action>
            <t-space align="center">
              <t-tag variant="light" theme="primary">{{ book.wordCount }} {{ $t('wordbook.wordUnit') }}</t-tag>
              <t-switch
                :key="`${book.id}-${book.status}`"
                :value="isStudyOn(book)"
                :loading="togglingId === book.id"
                :disabled="isStudyToggleCooling(book.id)"
                :title="$t('wordbook.studyEnabled')"
                @change="(v: boolean) => handleToggleStudy(book.id, v)"
              />
              <t-button variant="text" @click="router.push(`/notebooks/${book.id}`)">{{ $t('wordbook.view') }}</t-button>
              <t-button variant="text" @click="openEdit(book)">{{ $t('wordbook.edit') }}</t-button>
              <t-popconfirm :content="$t('wordbook.deleteConfirm')" @confirm="handleDelete(book.id)">
                <t-button variant="text" theme="danger">{{ $t('wordbook.delete') }}</t-button>
              </t-popconfirm>
            </t-space>
          </template>
        </t-list-item>
      </t-list>
      <t-empty v-else :description="$t('wordbook.noWordbooks')" />
    </div>

    <t-dialog
      v-model:visible="showAddDialog"
      :header="editingBook ? $t('wordbook.edit') : $t('wordbook.create')"
      @confirm="handleSave"
      :confirm-btn="{ content: editingBook ? $t('common.confirm') : $t('wordbook.create'), loading: saving }"
    >
      <t-form>
        <t-form-item :label="$t('wordbook.name')" name="bookName">
          <t-input v-model="form.bookName" :placeholder="$t('wordbook.namePlaceholder')" />
        </t-form-item>
        <t-form-item :label="$t('wordbook.desc')" name="bookDesc">
          <t-textarea v-model="form.bookDesc" :placeholder="$t('wordbook.descPlaceholder')" />
        </t-form-item>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin } from 'tdesign-vue-next'
import {
  getNotebooks,
  createNotebook,
  updateNotebook,
  deleteNotebook,
  setNotebookStudyEnabled
} from '../api'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const notebooks = ref<any[]>([])
const showAddDialog = ref(false)
const saving = ref(false)
const togglingId = ref<number | null>(null)
/** 每个单词本上次切换「加入背诵」的时间戳，5 秒内禁止再点 */
const studyToggleAt = ref<Record<number, number>>({})
const STUDY_TOGGLE_COOLDOWN_MS = 5000
const editingBook = ref<any>(null)
const form = reactive({ bookName: '', bookDesc: '' })

/** 用于驱动冷却期 UI 刷新 */
const cooldownTick = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

function ensureCooldownTicker() {
  if (cooldownTimer) return
  cooldownTimer = setInterval(() => {
    cooldownTick.value++
    const now = Date.now()
    const stillCooling = Object.values(studyToggleAt.value).some(
      t0 => now - t0 < STUDY_TOGGLE_COOLDOWN_MS
    )
    if (!stillCooling && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 500)
}

function isStudyToggleCooling(bookId: number) {
  void cooldownTick.value
  const t0 = studyToggleAt.value[bookId]
  if (!t0) return false
  return Date.now() - t0 < STUDY_TOGGLE_COOLDOWN_MS
}

onMounted(async () => {
  await loadNotebooks()
  if (route.query.create === '1') {
    editingBook.value = null
    form.bookName = ''
    form.bookDesc = ''
    showAddDialog.value = true
    router.replace({ path: '/notebooks', query: {} })
  }
})

onUnmounted(() => {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
})

async function loadNotebooks() {
  try {
    const res = await getNotebooks()
    notebooks.value = (res.data.data || []).map((b: any) => ({
      ...b,
      status: b.status === true || b.status === 1 ? 1 : 0
    }))
  } catch {
    MessagePlugin.error(t('wordbook.error'))
  }
}

function openEdit(book: any) {
  editingBook.value = book
  form.bookName = book.bookName
  form.bookDesc = book.bookDesc || ''
  showAddDialog.value = true
}

async function handleSave() {
  if (!form.bookName.trim()) {
    MessagePlugin.warning(t('wordbook.nameRequired'))
    return
  }
  saving.value = true
  try {
    if (editingBook.value) {
      await updateNotebook(editingBook.value.id, { ...form })
      MessagePlugin.success(t('wordbook.renameSuccess'))
    } else {
      await createNotebook({ ...form, sortNum: 0 })
      MessagePlugin.success(t('wordbook.createSuccess'))
    }
    showAddDialog.value = false
    editingBook.value = null
    form.bookName = ''
    form.bookDesc = ''
    await loadNotebooks()
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || t('wordbook.error'))
  } finally {
    saving.value = false
  }
}

function isStudyOn(book: { status?: number | boolean | null }) {
  return book.status === 1 || book.status === true
}

async function handleToggleStudy(bookId: number, enabled: boolean) {
  if (togglingId.value != null) return
  if (isStudyToggleCooling(bookId)) {
    MessagePlugin.warning(t('wordbook.studyToggleCooldown'))
    return
  }
  const idx = notebooks.value.findIndex(b => b.id === bookId)
  if (idx < 0) return
  const prev = notebooks.value[idx].status
  const on = !!enabled
  // 不可变更新，确保开关受控状态刷新
  notebooks.value[idx] = { ...notebooks.value[idx], status: on ? 1 : 0 }
  togglingId.value = bookId
  studyToggleAt.value = { ...studyToggleAt.value, [bookId]: Date.now() }
  ensureCooldownTicker()
  try {
    await setNotebookStudyEnabled(bookId, on)
    MessagePlugin.success(on ? t('wordbook.studyEnabledOn') : t('wordbook.studyEnabledOff'))
  } catch (e: any) {
    notebooks.value[idx] = { ...notebooks.value[idx], status: prev }
    // 失败允许立刻重试
    const next = { ...studyToggleAt.value }
    delete next[bookId]
    studyToggleAt.value = next
    MessagePlugin.error(e.response?.data?.msg || t('wordbook.error'))
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(id: number) {
  try {
    await deleteNotebook(id)
    MessagePlugin.success(t('wordbook.deleteSuccess'))
    await loadNotebooks()
  } catch {
    MessagePlugin.error(t('wordbook.error'))
  }
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
</style>
