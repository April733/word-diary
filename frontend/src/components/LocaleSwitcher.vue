<template>
  <t-dropdown
    :options="localeOptions"
    :min-column-width="140"
    @click="handleChange"
  >
    <t-button
      variant="text"
      size="small"
      class="locale-btn"
      :icon="renderIcon"
    >
      <span class="locale-label">{{ currentLocaleLabel }}</span>
    </t-button>
  </t-dropdown>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { useI18n } from 'vue-i18n'
import { TranslateIcon } from 'tdesign-icons-vue-next'
import { LOCALE_OPTIONS, setStoredLocale, type SupportedLocale } from '../i18n'
import { useUserStore } from '../stores/user'

const { locale } = useI18n()
const userStore = useUserStore()

const currentLocale = computed(() => locale.value as SupportedLocale)

const currentLocaleLabel = computed(() => {
  const found = LOCALE_OPTIONS.find(o => o.value === currentLocale.value)
  return found?.label || currentLocale.value
})

const localeOptions = LOCALE_OPTIONS.map(o => ({
  content: o.label,
  value: o.value,
})) as { content: string; value: string }[]

function renderIcon() {
  return h(TranslateIcon)
}

function handleChange(dropdownItem: { content: string; value: string }) {
  const value = dropdownItem.value as SupportedLocale
  locale.value = value
  setStoredLocale(value)
  document.documentElement.lang = value
  if (userStore.user) {
    userStore.updateLocale(value)
  }
}
</script>

<style scoped>
.locale-btn {
  color: var(--td-text-color-secondary);
  font-weight: 500;
  transition: color 0.2s;
}
.locale-btn:hover {
  color: var(--td-text-color-primary);
  background: transparent;
}
.locale-label {
  margin-left: 4px;
}
</style>
