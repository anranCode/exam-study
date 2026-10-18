<script setup lang="ts">
/**
 * 导入记录抽屉。
 *
 * 导入结果页只活到离开页面为止,而「哪几道没进去」恰恰是要事后补录的。
 * 这里列出每次导入留下的记录:失败条目的题干 + 原因、识别阶段的提醒。
 * 补录完成后删除记录即可(没有单独的「已处理」状态,单用户下删除就是最直接的处理方式)。
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteImportLog, getImportLogs } from '@/api/importLog'
import type { ImportLogStatus, ImportLogVO } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const PAGE_SIZE = 20

const logs = ref<ImportLogVO[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loaded = ref(false)
/** 展开的记录 id */
const opened = ref<string[]>([])
const busyId = ref<number | null>(null)

const STATUS_LABEL: Record<ImportLogStatus, string> = {
  IMPORTED: '全部入库',
  PARTIAL: '部分入库',
  DUPLICATED: '全部重复',
  FAILED: '未入库'
}

function formatTime(value: string): string {
  return value ? value.slice(0, 16) : ''
}

async function fetchPage(reset: boolean) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getImportLogs(page.value, PAGE_SIZE)
    logs.value = reset ? res.list : [...logs.value, ...res.list]
    total.value = res.total
    loaded.value = true
  } finally {
    loading.value = false
  }
}

async function refresh() {
  page.value = 1
  await fetchPage(true)
}

async function loadMore() {
  page.value += 1
  await fetchPage(false)
}

async function remove(log: ImportLogVO) {
  try {
    await ElMessageBox.confirm(
      '手动补录完成后删除这条记录。删除只清理这份清单,不影响题库。',
      `删除「${log.paperName || log.subjectCode || '这条导入记录'}」?`,
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  busyId.value = log.id
  try {
    await deleteImportLog(log.id)
    logs.value = logs.value.filter((l) => l.id !== log.id)
    total.value = Math.max(0, total.value - 1)
    ElMessage.success('已删除记录')
  } finally {
    busyId.value = null
  }
}

async function copyStem(stem: string) {
  try {
    await navigator.clipboard.writeText(stem)
    ElMessage.success('题干已复制')
  } catch {
    ElMessage.warning('复制失败,请手动选中复制')
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) void refresh()
  }
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="导入记录"
    size="min(94vw, 640px)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="log-intro">
      每次导入没进去的题目都记在这里——题干和原因都留着,可以照着补录。补完点「删除记录」清理。
    </p>

    <div v-if="loading && !loaded" class="log-loading">
      <el-skeleton :rows="5" animated />
    </div>

    <EmptyState
      v-else-if="!logs.length"
      text="还没有导入记录。导入一次之后,没进去的题目会出现在这里。"
      icon="list"
    />

    <el-collapse v-else v-model="opened" class="log-list">
      <el-collapse-item v-for="log in logs" :key="log.id" :name="String(log.id)">
        <template #title>
          <div class="log-title">
            <div class="log-title__row">
              <span class="log-title__name wrap-anywhere">
                {{ log.paperName || log.subjectCode || '未命名导入' }}
              </span>
              <span :class="['log-status', `log-status--${log.status.toLowerCase()}`]">
                {{ STATUS_LABEL[log.status] }}
              </span>
            </div>
            <div class="log-title__meta">
              {{ log.source }} · <span class="mono">{{ log.subjectCode || '—' }}</span>
              · {{ formatTime(log.createdAt) }} · 共 {{ log.total }} 条,入库 {{ log.imported }}<template v-if="log.duplicated">,重复 {{ log.duplicated }}</template><template v-if="log.issues.length">,未入库 {{ log.issues.length }}</template>
            </div>
          </div>
        </template>

        <div class="log-body">
          <p v-if="!log.issues.length && !log.warnings.length" class="log-ok">
            没有漏掉的题目。
          </p>

          <div v-if="log.warnings.length" class="log-section">
            <h4 class="log-section__title log-section__title--warn">
              识别阶段的提醒({{ log.warnings.length }})
            </h4>
            <ul class="log-warnings">
              <li v-for="(w, i) in log.warnings" :key="i">{{ w }}</li>
            </ul>
          </div>

          <div v-if="log.issues.length" class="log-section">
            <h4 class="log-section__title">没入库的题目({{ log.issues.length }})</h4>
            <ul class="log-issues">
              <li v-for="(issue, i) in log.issues" :key="i" class="log-issue">
                <div class="log-issue__top">
                  <span class="log-issue__index">
                    {{ issue.index ? `第 ${issue.index} 条` : '整份' }}
                  </span>
                  <button
                    v-if="issue.stem"
                    type="button"
                    class="log-issue__copy"
                    @click="copyStem(issue.stem)"
                  >
                    复制题干
                  </button>
                </div>
                <p v-if="issue.stem" class="log-issue__stem wrap-anywhere">{{ issue.stem }}</p>
                <p class="log-issue__reason wrap-anywhere">{{ issue.reason }}</p>
              </li>
            </ul>
          </div>

          <div class="log-actions">
            <el-button
              type="danger"
              plain
              size="small"
              :disabled="busyId === log.id"
              @click="remove(log)"
            >
              删除记录
            </el-button>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>

    <div v-if="logs.length && logs.length < total" class="log-more">
      <el-button :loading="loading" @click="loadMore">
        加载更多(还有 {{ total - logs.length }} 条)
      </el-button>
    </div>
  </el-drawer>
</template>

<style scoped>
.log-intro {
  margin: 0 0 var(--space-md);
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.log-loading {
  padding: var(--space-md);
}

.log-list :deep(.el-collapse-item) {
  margin-bottom: var(--space-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  overflow: hidden;
}

.log-list :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 48px;
  padding: var(--space-sm) var(--space-md);
  background-color: var(--bg-card);
}

.log-list :deep(.el-collapse-item__wrap) {
  background-color: var(--bg-card);
  border-bottom: none;
}

.log-list :deep(.el-collapse-item__content) {
  padding: 0 var(--space-md) var(--space-md);
}

.log-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  text-align: start;
}

.log-title__row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.log-title__name {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--text-primary);
}

.log-title__meta {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.log-status {
  flex-shrink: 0;
  padding: 0 8px;
  border-radius: var(--radius-full);
  font-size: 12px;
  line-height: 20px;
}

.log-status--imported {
  background-color: var(--color-success-light);
  color: var(--color-success);
}

.log-status--partial {
  background-color: var(--color-warning-light);
  color: var(--color-warning);
}

.log-status--duplicated {
  background-color: var(--bg-page);
  color: var(--text-secondary);
}

.log-status--failed {
  background-color: var(--color-danger-light);
  color: var(--color-danger);
}

.log-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.log-ok {
  margin: 0;
  color: var(--color-success);
  font-size: 14px;
}

.log-section__title {
  margin: 0 0 var(--space-xs);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.log-section__title--warn {
  color: var(--color-warning);
}

.log-warnings {
  margin: 0;
  padding-left: 1.2em;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-warning);
}

.log-issues {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.log-issue {
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background-color: var(--bg-page);
}

.log-issue__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.log-issue__index {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.log-issue__copy {
  min-height: 32px;
  padding: 0 var(--space-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background-color: transparent;
  color: var(--color-primary);
  font-size: 12px;
}

.log-issue__copy:hover {
  border-color: var(--color-primary);
}

.log-issue__stem {
  margin: var(--space-xs) 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary);
}

.log-issue__reason {
  margin: 0;
  font-size: 13px;
  color: var(--color-danger);
}

.log-actions {
  display: flex;
  justify-content: flex-end;
}

.log-more {
  display: flex;
  justify-content: center;
  margin-top: var(--space-md);
}
</style>
