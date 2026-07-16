<template>
  <t-dialog
    v-model:visible="visibleModel"
    :header="$t('reader.notebookLinkTitle')"
    width="440px"
    :confirm-btn="null"
    :cancel-btn="{ content: $t('common.confirm') }"
  >
    <div v-if="loading" class="loading-row">
      <t-loading size="small" />
    </div>
    <template v-else>
      <div v-if="notebooks.length > 0" class="nb-list">
        <t-radio-group v-model="activeId" class="nb-radio-group" @change="onActiveChange">
          <div
            v-for="nb in notebooks"
            :key="nb.notebookId"
            class="nb-item"
            :class="{ 'nb-item--active': nb.notebookId === activeId }"
          >
            <t-radio :value="nb.notebookId" class="nb-radio">
              <span class="nb-name">{{ nb.notebookName }}</span>
              <t-tag v-if="nb.notebookId === activeId" size="small" theme="primary" variant="light">
                {{ $t('reader.notebookLinkCurrent') }}
              </t-tag>
            </t-radio>
          </div>
        </t-radio-group>
      </div>
      <t-empty v-else :description="$t('reader.notebookLinkEmpty')" />

      <div class="create-section">
        <div class="section-title">{{ $t('reader.notebookLinkCreate') }}</div>
        <t-form label-width="72px">
          <t-form-item :label="$t('reader.notebookLinkCreateName')">
            <t-input
              v-model="createForm.bookName"
              :placeholder="$t('reader.notebookLinkCreateNamePlaceholder')"
              maxlength="64"
            />
          </t-form-item>
          <t-form-item :label="$t('reader.notebookLinkCreateDesc')">
            <t-textarea
              v-model="createForm.bookDesc"
              :placeholder="$t('reader.notebookLinkCreateDescPlaceholder')"
              :autosize="{ minRows: 2, maxRows: 4 }"
            />
          </t-form-item>
        </t-form>
        <t-button
          theme="primary"
          block
          :loading="creating"
          :disabled="!createForm.bookName.trim()"
          @click="createForBook"
        >
          {{ $t('reader.notebookLinkCreateConfirm') }}
        </t-button>
      </div>
    </template>
  </t-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { useI18n } from 'vue-i18n'
import { getBookNotebookLinks, createNotebook } from '../api'

const props = defineProps<{
  visible: boolean
  bookId: number
  bookTitle?: string
  activeNotebookId?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  changed: []
  'update:activeNotebookId': [notebookId: number | undefined]
}>()

const { t } = useI18n()
const loading = ref(false)
const creating = ref(false)
const notebooks = ref<{ notebookId: number; notebookName: string }[]>([])
const activeId = ref<number | undefined>()
const createForm = reactive({ bookName: '', bookDesc: '' })

const visibleModel = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

function onActiveChange(val: number) {
  activeId.value = val
  emit('update:activeNotebookId', val)
}

function resetCreateForm() {
  createForm.bookName = props.bookTitle?.trim() || ''
  createForm.bookDesc = ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getBookNotebookLinks(props.bookId)
    notebooks.value = res.data.data || []
    const ids = notebooks.value.map(n => n.notebookId)
    if (props.activeNotebookId && ids.includes(props.activeNotebookId)) {
      activeId.value = props.activeNotebookId
    } else if (ids.length > 0) {
      activeId.value = ids[0]
      emit('update:activeNotebookId', activeId.value)
    } else {
      activeId.value = undefined
      emit('update:activeNotebookId', undefined)
      resetCreateForm()
    }
  } catch {
    MessagePlugin.error(t('reader.notebookLinkLoadError'))
  } finally {
    loading.value = false
  }
}

async function createForBook() {
  const name = createForm.bookName.trim()
  if (!name) {
    MessagePlugin.warning(t('reader.notebookLinkNameRequired'))
    return
  }
  creating.value = true
  try {
    const res = await createNotebook({
      bookName: name,
      bookDesc: createForm.bookDesc.trim() || undefined,
      sortNum: 0,
      bookId: props.bookId
    })
    const newId = res.data.data?.id
    if (newId) {
      activeId.value = newId
      emit('update:activeNotebookId', newId)
    }
    resetCreateForm()
    await loadData()
    emit('changed')
    MessagePlugin.success(t('reader.notebookLinkCreateSuccess'))
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || t('reader.notebookLinkCreateError'))
  } finally {
    creating.value = false
  }
}

watch(() => props.visible, (open) => {
  if (open) loadData()
})
</script>

<style scoped>
.loading-row {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}
.nb-list {
  margin-bottom: 16px;
}
.nb-radio-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.nb-item {
  padding: 8px 12px;
  border: 1px solid var(--td-component-border);
  border-radius: 6px;
}
.nb-item--active {
  border-color: var(--td-brand-color);
  background: var(--td-brand-color-light);
}
.nb-radio {
  width: 100%;
}
.nb-radio :deep(.t-radio__label) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
}
.nb-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.section-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--td-text-color-primary);
  margin-bottom: 8px;
}
.create-section {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
