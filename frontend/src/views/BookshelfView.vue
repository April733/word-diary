<template>
  <div class="page">
    <header class="page-header">
      <div class="header-left">
        <t-button variant="text" @click="router.push('/')">
          <template #icon><t-icon name="chevron-left" /></template>
        </t-button>
        <h1>{{ $t('bookshelf.title') }}</h1>
      </div>
      <div class="header-center">
        <span class="header-brand">{{ $t('common.appName') }}</span>
      </div>
      <div class="header-actions">
        <t-upload
          class="header-upload"
          :auto-upload="false"
          accept=".epub,.pdf"
          :before-upload="beforeUpload"
          @change="handleFileChange"
          :show-upload-list="false"
          :disabled="uploading || atBookLimit"
        >
          <t-button variant="text" size="small" class="nav-link" :disabled="uploading || atBookLimit">
            {{ $t('bookshelf.import') }}
          </t-button>
        </t-upload>
      </div>
    </header>

    <div class="page-body">
      <!-- 格式提示 -->
      <div class="format-tip">
        <t-icon name="info-circle" />
        <span>{{ $t('bookshelf.formatHint') }}</span>
      </div>

      <div v-if="loading" class="loading-status">
        <t-loading size="medium" />
        <span>{{ $t('bookshelf.loading') }}</span>
      </div>

      <div v-if="uploading" class="upload-status">
        <t-loading size="small" />
        <span class="upload-name" :title="uploadingFileName">
          {{
            uploadPercent > 0
              ? $t('bookshelf.uploadingProgress', { name: uploadingFileName, percent: uploadPercent })
              : $t('bookshelf.uploading', { name: uploadingFileName })
          }}
        </span>
      </div>

      <div v-if="!loading && books.length > 0" class="bookshelf">
        <div
          v-for="book in books"
          :key="book.id"
          class="book-card"
          @click="openBook(book.id)"
        >
          <div class="book-cover">
            <div class="cover-placeholder">
              <span class="cover-title">{{ book.title }}</span>
              <span class="cover-author">{{ book.author || $t('bookshelf.unknownAuthor') }}</span>
            </div>
          </div>
          <div class="book-info">
            <div class="book-title">{{ book.title }}</div>
            <div class="book-meta">
              <t-tag size="small" variant="light">{{ book.fileType?.toUpperCase() }}</t-tag>
              <span class="progress" v-if="book.progressPercent > 0">
                {{ $t('bookshelf.progress', { percent: book.progressPercent }) }}
              </span>
            </div>
            <div class="book-actions">
              <t-button
                variant="text"
                size="small"
                theme="primary"
                shape="square"
                class="action-icon"
                :title="$t('bookshelf.read')"
                :aria-label="$t('bookshelf.read')"
                @click.stop="openBook(book.id)"
              >
                <template #icon><t-icon name="browse" size="16px" /></template>
              </t-button>
              <t-popconfirm :content="$t('bookshelf.deleteConfirm')" @confirm="handleDelete(book.id)">
                <t-button
                  variant="text"
                  size="small"
                  theme="danger"
                  shape="square"
                  class="action-delete"
                  :title="$t('common.delete')"
                  :aria-label="$t('common.delete')"
                  @click.stop
                >
                  <template #icon><t-icon name="delete" size="16px" /></template>
                </t-button>
              </t-popconfirm>
            </div>
          </div>
        </div>
      </div>

      <t-empty v-if="!loading && books.length === 0 && !uploading" :description="$t('bookshelf.empty')" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { MessagePlugin, DialogPlugin } from 'tdesign-vue-next'
import { getBooks, uploadBook, deleteBook } from '../api'

const router = useRouter()
const { t } = useI18n()
const books = ref<any[]>([])
const loading = ref(true)
const uploading = ref(false)
const uploadingFileName = ref('')
const uploadPercent = ref(0)

/** 与后端 BookServiceImpl.MAX_BOOKS_PER_USER 一致 */
const MAX_BOOKS_PER_USER = 10
const atBookLimit = computed(() => books.value.length >= MAX_BOOKS_PER_USER)

onMounted(() => loadBooks())

function beforeUpload(file: File): boolean {
  if (atBookLimit.value) {
    MessagePlugin.warning(t('bookshelf.limitReached', { n: MAX_BOOKS_PER_USER }))
    return false
  }
  const name = file.name.toLowerCase()
  if (!name.endsWith('.epub') && !name.endsWith('.pdf')) {
    MessagePlugin.warning(t('bookshelf.formatError'))
    return false
  }
  const maxSize = 100 * 1024 * 1024 // 100MB
  if (file.size > maxSize) {
    MessagePlugin.warning(t('bookshelf.fileTooBig', { size: (file.size / 1024 / 1024).toFixed(1) }))
    return false
  }
  return true
}

async function loadBooks() {
  try {
    console.log('[Bookshelf] 正在加载书架列表...')
    const res = await getBooks()
    books.value = res.data.data || []
    console.log('[Bookshelf] 书架列表加载完成, 共', books.value.length, '本:', books.value.map((b: any) => ({ id: b.id, title: b.title, filePath: b.filePath, fileType: b.fileType })))
  } catch (e: any) {
    console.error('[Bookshelf] 加载书架失败:', e)
    MessagePlugin.error(t('bookshelf.loadError'))
  } finally {
    loading.value = false
  }
}

async function handleFileChange(files: any) {
  const file = files[0]?.raw || files[0]
  if (!file) return
  if (atBookLimit.value) {
    MessagePlugin.warning(t('bookshelf.limitReached', { n: MAX_BOOKS_PER_USER }))
    return
  }

  // 版权确认弹窗
  const confirmed = await new Promise<boolean>(resolve => {
    const dialog = DialogPlugin.confirm({
      header: t('bookshelf.copyrightTitle'),
      body: t('bookshelf.copyrightBody') + '\n\n' + t('bookshelf.formatHint'),
      confirmBtn: t('bookshelf.copyrightConfirm'),
      cancelBtn: t('common.cancel'),
      onConfirm: () => { dialog.destroy(); resolve(true) },
      onCancel: () => { dialog.destroy(); resolve(false) },
      onClose: () => { dialog.destroy(); resolve(false) },
    })
  })
  if (!confirmed) return

  uploadingFileName.value = file.name
  uploading.value = true
  uploadPercent.value = 0
  console.log('[Bookshelf] 开始上传:', file.name, '原始大小:', (file.size / 1024 / 1024).toFixed(2), 'MB')
  try {
    const res = await uploadBook(file, (p) => {
      uploadPercent.value = p
    })
    console.log('[Bookshelf] 上传响应:', res.data)
    const book = res.data?.data
    console.log('[Bookshelf] 返回的书籍数据:', book)
    if (book?.fileSize) {
      const savedSizeMB = (book.fileSize / 1024 / 1024).toFixed(2)
      const originalSizeMB = (file.size / 1024 / 1024).toFixed(2)
      console.log('[Bookshelf] 上传完成, 原始大小:', originalSizeMB, 'MB, 保存大小:', savedSizeMB, 'MB')
      MessagePlugin.success(t('bookshelf.importSuccess') + ` (${savedSizeMB}MB)`)
    } else {
      MessagePlugin.success(t('bookshelf.importSuccess'))
    }
    if (book?.id) {
      console.log('[Bookshelf] 跳转到阅读器 /reader/' + book.id)
      router.push(`/reader/${book.id}`)
    } else {
      console.warn('[Bookshelf] 书籍无 id，刷新书架列表')
      await loadBooks()
    }
  } catch (e: any) {
    console.error('[Bookshelf] 上传失败:', e)
    const status = e?.response?.status
    if (status === 504 || e?.__directUploadHint || e?.message === 'DIRECT_UPLOAD_FAILED') {
      MessagePlugin.error(t('bookshelf.importTimeout'))
    } else {
      MessagePlugin.error(e.response?.data?.message || e.response?.data?.msg || t('bookshelf.importError'))
    }
  } finally {
    uploading.value = false
    uploadingFileName.value = ''
    uploadPercent.value = 0
  }
}

function openBook(bookId: number) {
  router.push(`/reader/${bookId}`)
}

async function handleDelete(bookId: number) {
  try {
    await deleteBook(bookId)
    MessagePlugin.success(t('bookshelf.deleted'))
    await loadBooks()
  } catch (e: any) {
    MessagePlugin.error(e.response?.data?.message || t('bookshelf.deleteError'))
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: transparent;
  color: var(--brand-ink);
}

.page-header {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 16px 10px 6px;
  background: color-mix(in srgb, var(--brand-white) 72%, transparent);
  border-bottom: 1px solid color-mix(in srgb, var(--brand-lizard) 40%, transparent);
  backdrop-filter: blur(8px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
  max-width: min(42%, 160px);
  flex: 0 1 auto;
  z-index: 1;
}

.header-left :deep(.t-button) {
  width: 28px;
  height: 28px;
  min-width: 28px;
  padding: 0;
}

.header-left :deep(.t-icon) {
  font-size: 18px;
}

.header-center {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
  z-index: 0;
}

.header-brand {
  font-size: 18px;
  font-weight: 800;
  color: var(--brand-ink, #315c34);
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  justify-content: flex-end;
  margin-left: auto;
  flex: 0 1 auto;
  min-width: 0;
  max-width: min(42%, 220px);
  z-index: 1;
}

.header-upload {
  display: flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

/* 顶栏只保留「导入」按钮；选中文件名改由下方 upload-status 省略展示 */
.header-upload :deep(.t-upload__single-display-text),
.header-upload :deep(.t-upload__tips),
.header-upload :deep(.t-upload__file-list),
.header-upload :deep(.t-upload__single-name),
.header-upload :deep(.t-upload__display-text) {
  display: none !important;
}

.page-header h1 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.02em;
  color: var(--brand-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-link {
  color: var(--brand-ink) !important;
  max-width: 100%;
}

.page-body {
  max-width: 900px;
  margin: 24px auto;
  padding: 0 24px;
}

.format-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  font-size: 13px;
  color: #3b82f6;
}

.format-tip strong {
  font-weight: 600;
  color: #2563eb;
}

.loading-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 0;
  color: #6b7280;
  font-size: 14px;
}

.upload-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  color: #6b7280;
  font-size: 14px;
  min-width: 0;
}

.upload-status .upload-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bookshelf {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.book-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  border: 1px solid #eee;
}

.book-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.book-cover {
  flex-shrink: 0;
}

.cover-placeholder {
  width: 72px;
  height: 100px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px;
  text-align: center;
}

.cover-title {
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.cover-author {
  color: rgba(255, 255, 255, 0.7);
  font-size: 10px;
  margin-top: 4px;
}

.book-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.book-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0;
}

.progress {
  font-size: 12px;
  color: #6b7280;
}

.book-actions {
  display: flex;
  align-items: center;
  gap: 2px;
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
</style>
