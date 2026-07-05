<template>
  <div class="page">
    <header class="page-header">
      <div>
        <t-button variant="text" @click="router.push('/')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>单词本管理</h1>
      </div>
      <t-button theme="primary" @click="showAddDialog = true">新建单词本</t-button>
    </header>

    <div class="page-body">
      <t-list v-if="notebooks.length > 0" :split="true">
        <t-list-item v-for="book in notebooks" :key="book.id">
          <t-list-item-meta :title="book.bookName" :description="book.bookDesc || '暂无简介'" />
          <template #action>
            <t-space>
              <t-tag variant="light" theme="primary">{{ book.wordCount }} 词</t-tag>
              <t-button variant="text" @click="router.push(`/notebooks/${book.id}`)">查看</t-button>
              <t-button variant="text" @click="openEdit(book)">编辑</t-button>
              <t-popconfirm content="确认删除该单词本？" @confirm="handleDelete(book.id)">
                <t-button variant="text" theme="danger">删除</t-button>
              </t-popconfirm>
            </t-space>
          </template>
        </t-list-item>
      </t-list>
      <t-empty v-else description="还没有单词本，点击右上角新建" />
    </div>

    <t-dialog v-model:visible="showAddDialog" :header="editingBook ? '编辑单词本' : '新建单词本'"
      @confirm="handleSave" :confirm-btn="{ content: editingBook ? '确认编辑' : '确认创建', loading: saving }">
      <t-form>
        <t-form-item label="名称" name="bookName">
          <t-input v-model="form.bookName" placeholder="请输入单词本名称" />
        </t-form-item>
        <t-form-item label="简介" name="bookDesc">
          <t-textarea v-model="form.bookDesc" placeholder="请输入简介（选填）" />
        </t-form-item>
      </t-form>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { getNotebooks, createNotebook, updateNotebook, deleteNotebook } from '../api'

const router = useRouter()
const notebooks = ref<any[]>([])
const showAddDialog = ref(false)
const saving = ref(false)
const editingBook = ref<any>(null)
const form = reactive({ bookName: '', bookDesc: '' })

onMounted(() => loadNotebooks())

async function loadNotebooks() {
  try {
    const res = await getNotebooks()
    notebooks.value = res.data.data
  } catch (e: any) {
    MessagePlugin.error('加载失败')
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
    MessagePlugin.warning('请输入名称')
    return
  }
  saving.value = true
  try {
    if (editingBook.value) {
      await updateNotebook(editingBook.value.id, { ...form })
      MessagePlugin.success('更新成功')
    } else {
      await createNotebook({ ...form, sortNum: 0 })
      MessagePlugin.success('创建成功')
    }
    showAddDialog.value = false
    editingBook.value = null
    form.bookName = ''
    form.bookDesc = ''
    await loadNotebooks()
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteNotebook(id)
    MessagePlugin.success('已删除')
    await loadNotebooks()
  } catch (e: any) {
    MessagePlugin.error('删除失败')
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
  max-width: 800px;
  margin: 24px auto;
  padding: 0 24px;
}
</style>
