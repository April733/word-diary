 <template>
  <div class="page">
    <PageHeader :title="$t('wordbook.titleManage')" back-to="/">
      <UserDropdown />
    </PageHeader>

    <div class="page-body">
      <PageTabs v-model="sourceType" :tabs="sourceTabs" />

      <div class="summary-row">
        <p class="nb-summary">
          {{ $t('wordbook.summary', { books: filteredNotebooks.length, words: filteredTotalWords }) }}
        </p>
        <div class="page-actions">
          <button type="button" class="btn btn-secondary btn-sm" @click="router.push('/plan')">
            <t-icon name="chart-bar" size="14px" />
            <span>{{ $t('wordbook.reciteProgress') }}</span>
          </button>
          <button type="button" class="btn btn-primary btn-sm" @click="showAddDialog = true">
            <t-icon name="add" size="14px" />
            <span>{{ $t('wordbook.createShort') }}</span>
          </button>
        </div>
      </div>
      <ul v-if="filteredNotebooks.length > 0" class="nb-list">
        <li v-for="book in filteredNotebooks" :key="book.id" class="nb-item">
          <div class="nb-main" @click="router.push(`/notebooks/${book.id}`)">
            <div class="nb-name">
              <span v-if="book.sourceType" class="nb-source-tag" :class="'nb-source-' + book.sourceType">{{ book.sourceType }}</span>
              {{ book.bookName }}
            </div>
            <div class="nb-desc" :title="book.bookDesc || $t('wordbook.noDesc')">
              {{ book.bookDesc || $t('wordbook.noDesc') }}
            </div>
          </div>
          <div class="nb-actions">
            <t-tag variant="light" theme="primary" class="nb-count">
              {{ book.wordCount }} {{ $t('wordbook.wordUnit') }}
            </t-tag>
            <t-switch
              :key="`${book.id}-${book.status}`"
              :value="isStudyOn(book)"
              :loading="togglingId === book.id"
              :disabled="isStudyToggleCooling(book.id)"
              :title="$t('wordbook.studyEnabled')"
              @change="(v: boolean) => handleToggleStudy(book.id, v)"
            />
            <t-button
              size="small"
              variant="text"
              shape="square"
              class="action-icon"
              :title="$t('wordbook.view')"
              :aria-label="$t('wordbook.view')"
              @click="router.push(`/notebooks/${book.id}`)"
            >
              <template #icon><t-icon name="browse" size="16px" /></template>
            </t-button>
            <t-button
              size="small"
              variant="text"
              shape="square"
              class="action-icon"
              :title="$t('wordbook.rename')"
              :aria-label="$t('wordbook.rename')"
              @click="openEdit(book)"
            >
              <template #icon><t-icon name="edit" size="16px" /></template>
            </t-button>
            <t-popconfirm :content="$t('wordbook.deleteConfirm')" @confirm="handleDelete(book.id)">
              <t-button
                size="small"
                variant="text"
                theme="danger"
                shape="square"
                class="action-delete"
                :title="$t('wordbook.delete')"
                :aria-label="$t('wordbook.delete')"
              >
                <template #icon><t-icon name="delete" size="16px" /></template>
              </t-button>
            </t-popconfirm>
          </div>
        </li>
      </ul>
      <t-empty v-else :description="sourceType ? $t('wordbook.noWordbooksForSource') : $t('wordbook.noWordbooks')" />
    </div>

    <t-dialog
      v-model:visible="showAddDialog"
      :header="editingBook ? $t('wordbook.rename') : $t('wordbook.create')"
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
        <t-form-item :label="$t('wordbook.sourceType')" name="sourceType">
          <t-select v-model="form.sourceType" :options="sourceOptions" />
        </t-form-item>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, reactive, watch } from 'vue'
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
import { useUserStore } from '../stores/user'
import UserDropdown from '../components/UserDropdown.vue'
import PageTabs from '../components/PageTabs.vue'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const userStore = useUserStore()
const notebooks = ref<any[]>([])
const totalWords = computed(() =>
  notebooks.value.reduce((sum, b) => sum + (Number(b.wordCount) || 0), 0)
)

// 来源类型 Tab
const sourceType = ref('')
const sourceTabs = computed(() => [
  { label: t('wordbook.sourceAll'), value: '' },
  { label: t('wordbook.sourceReading'), value: '阅读' },
  { label: t('wordbook.sourceListening'), value: '听力' }
])

const filteredNotebooks = computed(() => {
  if (!sourceType.value) return notebooks.value
  return notebooks.value.filter(b => b.sourceType === sourceType.value)
})
const filteredTotalWords = computed(() =>
  filteredNotebooks.value.reduce((sum, b) => sum + (Number(b.wordCount) || 0), 0)
)
const showAddDialog = ref(false)
const saving = ref(false)
const togglingId = ref<number | null>(null)
/** 每个单词本上次切换「加入背诵」的时间戳，5 秒内禁止再点 */
const studyToggleAt = ref<Record<number, number>>({})
const STUDY_TOGGLE_COOLDOWN_MS = 5000
const editingBook = ref<any>(null)
const form = reactive({ bookName: '', bookDesc: '', sourceType: '阅读' })

const sourceOptions = computed(() => [
  { label: t('wordbook.sourceReading'), value: '阅读' },
  { label: t('wordbook.sourceListening'), value: '听力' }
])

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
  userStore.loadFromStorage()
  await loadNotebooks()
  if (route.query.create === '1') {
    editingBook.value = null
    form.bookName = ''
    form.bookDesc = ''
    form.sourceType = '阅读'
    showAddDialog.value = true
    router.replace({ path: '/notebooks', query: {} })
  }
})

watch(sourceType, () => {
  // Tab 切换时不需要重新请求，前端过滤即可
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
  form.sourceType = book.sourceType || '阅读'
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
    form.sourceType = '阅读'
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
  color: var(--brand-ink);
}

.page-body {
  max-width: 640px;
  margin: 4px auto;
  padding: 0 24px;
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.page-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.nb-summary {
  margin: 0;
  padding: 0 2px;
  font-size: 13px;
  line-height: 1.4;
  color: color-mix(in srgb, var(--brand-ink, #315c34) 62%, #888);
}

.nb-list {
  list-style: none;
  margin: 0;
  padding: 0;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.nb-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid color-mix(in srgb, var(--brand-lizard, #a8b59f) 28%, transparent);
  overflow: hidden;
}

.nb-item:last-child {
  border-bottom: none;
}

.nb-main {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  cursor: pointer;
}

.nb-name,
.nb-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nb-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--brand-ink, #315c34);
  line-height: 1.4;
  display: flex;
  align-items: center;
  gap: 6px;
}

.nb-source-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  line-height: 1.4;
  flex-shrink: 0;
}

.nb-source-阅读 {
  background: color-mix(in srgb, #4a90d9 15%, #fff);
  color: #3a7bc8;
}

.nb-source-听力 {
  background: color-mix(in srgb, #e8a835 15%, #fff);
  color: #c88a20;
}

.nb-desc {
  margin-top: 2px;
  font-size: 13px;
  color: color-mix(in srgb, var(--brand-ink, #315c34) 55%, #888);
  line-height: 1.4;
}

.nb-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 2px 4px;
  flex-shrink: 0;
  max-width: 100%;
}

.nb-count {
  flex-shrink: 0;
  margin-right: 4px;
}

.action-icon {
  border-radius: 6px !important;
  color: var(--brand-ink, #315c34) !important;
}

.action-icon:hover {
  background: color-mix(in srgb, var(--brand-lizard, #a8b59f) 22%, transparent) !important;
}

.action-delete {
  border-radius: 6px !important;
  color: color-mix(in srgb, #d54941 82%, #888) !important;
}

.action-delete:hover {
  background: color-mix(in srgb, #d54941 8%, transparent) !important;
  color: #d54941 !important;
}

@media (max-width: 560px) {
  .page-body {
    margin: 16px auto;
    padding: 0 16px;
  }

  .nb-item {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .nb-main {
    width: 100%;
    flex: 1 1 100%;
  }

  .nb-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
