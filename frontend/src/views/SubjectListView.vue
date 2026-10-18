<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useSubjectStore } from '@/stores/subject'
import { ApiError } from '@/api/request'
import { ERROR_CODE, EXAM_SLOT_OPTIONS } from '@/utils/constants'
import type { ChapterVO, SubjectVO } from '@/api/types'

const subjectStore = useSubjectStore()

/** 默认标识色取自 CSS 变量,避免在 TS 里写死颜色 */
function defaultColor(): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue('--color-primary')
  return value.trim()
}

onMounted(async () => {
  try {
    await subjectStore.fetchSubjects()
  } catch {
    // 错误提示已由 request.ts 统一弹出
  }
  if (subjectStore.currentSubjectId == null && subjectStore.subjects.length > 0) {
    subjectStore.selectSubject(subjectStore.subjects[0].id)
  }
})

/* ---------------- 科目 ---------------- */

const subjectDialogVisible = ref(false)
const subjectDialogMode = ref<'create' | 'edit'>('create')
const editingSubjectId = ref<number | null>(null)
const subjectSubmitting = ref(false)
const subjectFormRef = ref<FormInstance>()

const subjectForm = reactive({
  code: '',
  name: '',
  shortName: '',
  color: '',
  examDate: null as string | null,
  examSlot: null as string | null,
  sortOrder: 0,
  enabled: true
})

const subjectRules: FormRules = {
  code: [{ required: true, message: '请输入课程代码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入科目全称', trigger: 'blur' }],
  shortName: [{ required: true, message: '请输入科目简称', trigger: 'blur' }],
  color: [{ required: true, message: '请选择标识色', trigger: 'change' }]
}

const subjectDialogTitle = computed(() =>
  subjectDialogMode.value === 'create' ? '新建科目' : '编辑科目'
)

function nextSubjectSortOrder(): number {
  return subjectStore.subjects.reduce((max, s) => Math.max(max, s.sortOrder), 0) + 1
}

function openCreateSubject() {
  subjectDialogMode.value = 'create'
  editingSubjectId.value = null
  Object.assign(subjectForm, {
    code: '',
    name: '',
    shortName: '',
    color: defaultColor(),
    examDate: null,
    examSlot: null,
    sortOrder: nextSubjectSortOrder(),
    enabled: true
  })
  subjectDialogVisible.value = true
  subjectFormRef.value?.clearValidate()
}

function openEditSubject(subject: SubjectVO) {
  subjectDialogMode.value = 'edit'
  editingSubjectId.value = subject.id
  Object.assign(subjectForm, {
    code: subject.code,
    name: subject.name,
    shortName: subject.shortName,
    color: subject.color,
    examDate: subject.examDate,
    examSlot: subject.examSlot,
    sortOrder: subject.sortOrder,
    enabled: subject.enabled
  })
  subjectDialogVisible.value = true
  subjectFormRef.value?.clearValidate()
}

async function submitSubject() {
  const form = subjectFormRef.value
  if (!form) return
  const valid = await form.validate().catch(() => false)
  if (!valid) return

  subjectSubmitting.value = true
  try {
    const payload = {
      code: subjectForm.code.trim(),
      name: subjectForm.name.trim(),
      shortName: subjectForm.shortName.trim(),
      color: subjectForm.color,
      examDate: subjectForm.examDate || null,
      examSlot: subjectForm.examSlot || null,
      sortOrder: subjectForm.sortOrder ?? 0,
      enabled: subjectForm.enabled
    }
    if (subjectDialogMode.value === 'create') {
      const created = await subjectStore.createSubject(payload)
      subjectDialogVisible.value = false
      subjectStore.selectSubject(created.id)
      ElMessage.success('科目已创建')
    } else if (editingSubjectId.value != null) {
      await subjectStore.updateSubject(editingSubjectId.value, payload)
      subjectDialogVisible.value = false
      ElMessage.success('科目已更新')
    }
  } catch {
    // 错误提示已由 request.ts 统一弹出,此处保持弹窗打开让用户修正
  } finally {
    subjectSubmitting.value = false
  }
}

async function confirmDeleteSubject(subject: SubjectVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除科目「${subject.name}」吗?此操作不可撤销。`,
      '删除科目',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return // 用户取消
  }

  try {
    await subjectStore.removeSubject(subject.id)
    ElMessage.success('科目已删除')
  } catch (error) {
    if (error instanceof ApiError && error.code === ERROR_CODE.SUBJECT_HAS_QUESTIONS) {
      // 科目下还有题目,后端已给出提示,这里不做额外处理
      return
    }
    // 其余错误(如 2001 不存在)同样已由拦截器提示
  }
}

/* ---------------- 章节 ---------------- */

const chapterDialogVisible = ref(false)
const chapterDialogMode = ref<'create' | 'edit'>('create')
const editingChapterId = ref<number | null>(null)
const chapterSubmitting = ref(false)
const chapterFormRef = ref<FormInstance>()

const chapterForm = reactive({
  name: '',
  parentId: null as number | null,
  sortOrder: 0
})

const chapterRules: FormRules = {
  name: [{ required: true, message: '请输入章节名称', trigger: 'blur' }]
}

const chapterDialogTitle = computed(() =>
  chapterDialogMode.value === 'create' ? '新建章节' : '编辑章节'
)

/** 父章节树形选择数据:编辑时排除自身及其子树,防止形成环 */
const parentTreeData = computed<ChapterVO[]>(() => {
  const tree = subjectStore.currentChapterTree
  if (chapterDialogMode.value !== 'edit' || editingChapterId.value == null) return tree
  return pruneChapter(tree, editingChapterId.value)
})

function pruneChapter(nodes: ChapterVO[], excludeId: number): ChapterVO[] {
  return nodes
    .filter((node) => node.id !== excludeId)
    .map((node) => ({
      ...node,
      children: node.children ? pruneChapter(node.children, excludeId) : []
    }))
}

function nextChapterSortOrder(parentId: number | null): number {
  const siblings = subjectStore.currentChapters.filter((c) => (c.parentId ?? null) === parentId)
  return siblings.reduce((max, c) => Math.max(max, c.sortOrder), 0) + 1
}

function openCreateChapter(parentId: number | null = null) {
  if (subjectStore.currentSubjectId == null) {
    ElMessage.warning('请先选择一个科目')
    return
  }
  chapterDialogMode.value = 'create'
  editingChapterId.value = null
  chapterForm.name = ''
  chapterForm.parentId = parentId
  chapterForm.sortOrder = nextChapterSortOrder(parentId)
  chapterDialogVisible.value = true
  chapterFormRef.value?.clearValidate()
}

function openEditChapter(chapter: ChapterVO) {
  chapterDialogMode.value = 'edit'
  editingChapterId.value = chapter.id
  chapterForm.name = chapter.name
  chapterForm.parentId = chapter.parentId ?? null
  chapterForm.sortOrder = chapter.sortOrder
  chapterDialogVisible.value = true
  chapterFormRef.value?.clearValidate()
}

async function submitChapter() {
  const subjectId = subjectStore.currentSubjectId
  if (subjectId == null) return
  const form = chapterFormRef.value
  if (!form) return
  const valid = await form.validate().catch(() => false)
  if (!valid) return

  chapterSubmitting.value = true
  try {
    const payload = {
      subjectId,
      name: chapterForm.name.trim(),
      parentId: chapterForm.parentId ?? null,
      sortOrder: chapterForm.sortOrder ?? 0
    }
    if (chapterDialogMode.value === 'create') {
      await subjectStore.createChapter(payload)
      ElMessage.success('章节已创建')
    } else if (editingChapterId.value != null) {
      await subjectStore.updateChapter(editingChapterId.value, payload)
      ElMessage.success('章节已更新')
    }
    chapterDialogVisible.value = false
  } catch {
    // 错误提示已由 request.ts 统一弹出
  } finally {
    chapterSubmitting.value = false
  }
}

async function confirmDeleteChapter(chapter: ChapterVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除章节「${chapter.name}」吗?此操作不可撤销。`,
      '删除章节',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }

  const subjectId = subjectStore.currentSubjectId
  if (subjectId == null) return

  try {
    await subjectStore.removeChapter(chapter.id, subjectId)
    ElMessage.success('章节已删除')
  } catch (error) {
    if (error instanceof ApiError && error.code === ERROR_CODE.CHAPTER_HAS_CONTENT) {
      // 章节下还有题目或子章节,后端已给出提示
      return
    }
  }
}
</script>

<template>
  <section class="subject-page">
    <header class="subject-page__head">
      <h1 class="subject-page__title">科目与章节</h1>
      <el-button type="primary" @click="openCreateSubject">
        <AppIcon name="plus" :size="16" />
        <span class="btn-label">新建科目</span>
      </el-button>
    </header>

    <div class="subject-page__grid">
      <!-- 左栏:科目列表 -->
      <aside class="panel">
        <div class="panel__head">
          <span class="panel__title">科目</span>
          <span class="panel__count mono">{{ subjectStore.subjects.length }}</span>
        </div>

        <div v-if="subjectStore.loadingSubjects && !subjectStore.loaded" class="loading">
          加载中…
        </div>

        <EmptyState
          v-else-if="subjectStore.subjects.length === 0"
          text="还没有科目,先新建一个开始吧。"
          action-text="新建科目"
          @action="openCreateSubject"
        />

        <ul v-else class="subject-list">
          <li v-for="subject in subjectStore.subjects" :key="subject.id" class="subject-list__row">
            <button
              type="button"
              class="subject-item"
              :class="{ 'subject-item--active': subject.id === subjectStore.currentSubjectId }"
              @click="subjectStore.selectSubject(subject.id)"
            >
              <span class="subject-item__bar" :style="{ backgroundColor: subject.color }" />
              <span class="subject-item__main">
                <span class="subject-item__name wrap-anywhere">{{ subject.shortName }}</span>
                <span class="subject-item__sub wrap-anywhere">{{ subject.name }}</span>
              </span>
              <span class="subject-item__count mono" :title="`共 ${subject.questionCount} 题`">
                {{ subject.questionCount }}
              </span>
            </button>
            <div class="subject-list__tools">
              <button
                type="button"
                class="icon-btn"
                title="编辑科目"
                aria-label="编辑科目"
                @click="openEditSubject(subject)"
              >
                <AppIcon name="edit" :size="16" />
              </button>
              <button
                type="button"
                class="icon-btn icon-btn--danger"
                title="删除科目"
                aria-label="删除科目"
                @click="confirmDeleteSubject(subject)"
              >
                <AppIcon name="trash" :size="16" />
              </button>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 右栏:章节树 -->
      <section class="panel">
        <div class="panel__head">
          <span class="panel__title">
            章节
            <span v-if="subjectStore.currentSubject" class="panel__subject">
              · {{ subjectStore.currentSubject.shortName }}
            </span>
          </span>
          <el-button
            type="primary"
            plain
            :disabled="subjectStore.currentSubjectId == null"
            @click="openCreateChapter(null)"
          >
            <AppIcon name="plus" :size="16" />
            <span class="btn-label">新建章节</span>
          </el-button>
        </div>

        <div
          v-if="subjectStore.currentSubjectId == null"
          class="empty-hint"
        >
          <EmptyState text="请先在左侧选择一个科目,再管理它的章节。" icon="book" />
        </div>

        <div v-else-if="subjectStore.loadingChapters" class="loading">加载中…</div>

        <div v-else-if="subjectStore.currentChapterTree.length === 0" class="empty-hint">
          <EmptyState
            text="这个科目还没有章节,先建一个吧。"
            action-text="新建章节"
            @action="openCreateChapter(null)"
          />
        </div>

        <el-tree
          v-else
          class="chapter-tree"
          :data="subjectStore.currentChapterTree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :indent="16"
        >
          <template #default="{ data }">
            <div class="chapter-node">
              <span class="chapter-node__name wrap-anywhere">{{ data.name }}</span>
              <span class="chapter-node__count mono">{{ data.questionCount }} 题</span>
              <div class="chapter-node__actions">
                <button
                  type="button"
                  class="icon-btn"
                  title="添加子章节"
                  aria-label="添加子章节"
                  @click.stop="openCreateChapter((data as ChapterVO).id)"
                >
                  <AppIcon name="plus" :size="16" />
                </button>
                <button
                  type="button"
                  class="icon-btn"
                  title="编辑章节"
                  aria-label="编辑章节"
                  @click.stop="openEditChapter(data as ChapterVO)"
                >
                  <AppIcon name="edit" :size="16" />
                </button>
                <button
                  type="button"
                  class="icon-btn icon-btn--danger"
                  title="删除章节"
                  aria-label="删除章节"
                  @click.stop="confirmDeleteChapter(data as ChapterVO)"
                >
                  <AppIcon name="trash" :size="16" />
                </button>
              </div>
            </div>
          </template>
        </el-tree>
      </section>
    </div>

    <!-- 科目表单 -->
    <el-dialog
      v-model="subjectDialogVisible"
      :title="subjectDialogTitle"
      width="min(92vw, 480px)"
      :close-on-click-modal="false"
    >
      <el-form
        ref="subjectFormRef"
        :model="subjectForm"
        :rules="subjectRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="课程代码" prop="code">
          <el-input v-model="subjectForm.code" placeholder="如 03709" maxlength="32" />
        </el-form-item>
        <el-form-item label="全称" prop="name">
          <el-input v-model="subjectForm.name" placeholder="如 马克思主义基本原理概论" />
        </el-form-item>
        <el-form-item label="简称" prop="shortName">
          <el-input v-model="subjectForm.shortName" placeholder="如 马原" maxlength="16" />
        </el-form-item>
        <el-form-item label="标识色" prop="color">
          <el-color-picker v-model="subjectForm.color" />
        </el-form-item>
        <el-form-item label="考试日期" prop="examDate">
          <el-date-picker
            v-model="subjectForm.examDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择考试日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="考试场次" prop="examSlot">
          <el-select v-model="subjectForm.examSlot" placeholder="选择考试场次" clearable style="width: 100%">
            <el-option
              v-for="option in EXAM_SLOT_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="subjectForm.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="subjectForm.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="subjectDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="subjectSubmitting" @click="submitSubject">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 章节表单 -->
    <el-dialog
      v-model="chapterDialogVisible"
      :title="chapterDialogTitle"
      width="min(92vw, 480px)"
      :close-on-click-modal="false"
    >
      <el-form
        ref="chapterFormRef"
        :model="chapterForm"
        :rules="chapterRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="chapterForm.name" placeholder="如 第一章 绪论" />
        </el-form-item>
        <el-form-item label="父章节(可选)" prop="parentId">
          <el-tree-select
            v-model="chapterForm.parentId"
            :data="parentTreeData"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="不选则为根章节"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="chapterForm.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="chapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="chapterSubmitting" @click="submitChapter">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.subject-page {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.subject-page__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.subject-page__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.btn-label {
  margin-left: var(--space-xs);
}

.subject-page__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--card-gap);
  align-items: start;
}

@media (min-width: 1025px) {
  .subject-page__grid {
    grid-template-columns: 320px minmax(0, 1fr);
  }
}

.panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  padding: var(--card-padding);
  border-bottom: 1px solid var(--border-color);
}

.panel__title {
  font-size: var(--font-size-card-title);
  font-weight: var(--font-weight-card-title);
  color: var(--text-primary);
}

.panel__subject {
  color: var(--text-secondary);
  font-weight: 400;
}

.panel__count {
  color: var(--text-secondary);
  font-size: var(--font-size-aux);
}

.loading {
  padding: var(--space-lg);
  text-align: center;
  color: var(--text-secondary);
  font-size: var(--font-size-body);
}

.empty-hint {
  padding: var(--space-md);
}

.subject-list {
  padding: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.subject-list__row {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.subject-item {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: var(--tap-target-min);
  padding: var(--space-sm);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background-color: transparent;
  text-align: left;
  color: var(--text-primary);
}

.subject-item:hover {
  background-color: var(--bg-page);
}

.subject-item--active {
  background-color: var(--color-primary-light);
  border-color: var(--color-primary);
}

.subject-item__bar {
  flex-shrink: 0;
  width: 4px;
  align-self: stretch;
  border-radius: var(--radius-full);
}

.subject-item__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1 1 auto;
}

.subject-item__name {
  font-size: var(--font-size-body);
  font-weight: 500;
}

.subject-item__sub {
  font-size: var(--font-size-aux);
  line-height: var(--line-height-aux);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subject-item__count {
  flex-shrink: 0;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.subject-list__tools {
  display: flex;
  flex-shrink: 0;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--tap-target-min);
  min-height: var(--tap-target-min);
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background-color: transparent;
  color: var(--text-secondary);
}

.icon-btn:hover {
  background-color: var(--bg-page);
  color: var(--text-primary);
}

.icon-btn--danger:hover {
  background-color: var(--color-danger-light);
  color: var(--color-danger);
}

.chapter-tree {
  padding: var(--space-sm);
  --el-tree-node-hover-bg-color: var(--bg-page);
  background-color: transparent;
}

.chapter-node {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  min-width: 0;
  padding-right: var(--space-xs);
}

.chapter-node__name {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-size-body);
  color: var(--text-primary);
}

.chapter-node__count {
  flex-shrink: 0;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.chapter-node__actions {
  display: flex;
  flex-shrink: 0;
}
</style>