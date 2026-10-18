<script setup lang="ts">
/**
 * 自由练习:按科目 + 题型抽题,不受复习排期影响。
 *
 * 这页只负责「选题」;真正的练习、判分、排期全部复用 `/practice/random` ——
 * 组题参数通过 query 传过去,练习页照旧处理(科目 -> subjectId,题型 -> types)。
 *
 * 题型下拉只列**已发布**的题:待校对的题不进抽题池,列出来只会让人选了却抽不到。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getTypeCounts } from '@/api/question'
import type { TypeCountVO } from '@/api/types'
import { useSubjectStore } from '@/stores/subject'

const router = useRouter()
const subjectStore = useSubjectStore()

const subjectId = ref<number | null>(null)
const types = ref<string[]>([])
const count = ref(10)
const typeCounts = ref<TypeCountVO[]>([])
const loadingTypes = ref(false)
const loadError = ref('')

const totalAvailable = computed(() => typeCounts.value.reduce((a, t) => a + t.count, 0))
const selectedAvailable = computed(() =>
  types.value.length === 0
    ? totalAvailable.value
    : typeCounts.value
        .filter((t) => types.value.includes(t.type))
        .reduce((a, t) => a + t.count, 0)
)
/** 实际会练几题:请求题量与该范围下的存量取小 */
const actualCount = computed(() => Math.min(count.value, selectedAvailable.value))

async function loadTypes() {
  loadingTypes.value = true
  loadError.value = ''
  try {
    typeCounts.value = await getTypeCounts(subjectId.value ?? undefined)
    // 换了科目后,之前选的题型可能在新科目里一道题都没有
    const available = new Set(typeCounts.value.map((t) => t.type))
    types.value = types.value.filter((t) => available.has(t))
  } catch (e) {
    typeCounts.value = []
    loadError.value = e instanceof Error ? e.message : '加载题型失败'
  } finally {
    loadingTypes.value = false
  }
}

watch(subjectId, () => void loadTypes())

function start() {
  if (selectedAvailable.value === 0) return
  const query: Record<string, string> = { count: String(count.value) }
  if (subjectId.value != null) query.subjectId = String(subjectId.value)
  if (types.value.length) query.types = types.value.join(',')
  router.push({ path: '/practice/random', query })
}

onMounted(async () => {
  await subjectStore.fetchSubjects().catch(() => undefined)
  await loadTypes()
})
</script>

<template>
  <section class="custom">
    <header class="custom__head">
      <h1 class="custom__title">自由练习</h1>
      <p class="custom__hint">
        按科目和题型抽题,不受复习排期影响。只抽「已发布」的题;没做过的题也可能再抽到。
      </p>
    </header>

    <div v-if="loadError" class="banner" role="alert">
      <span class="banner__text wrap-anywhere">{{ loadError }}</span>
      <button type="button" class="banner__action" @click="loadTypes">重试</button>
    </div>

    <div class="card custom__form">
      <div class="field">
        <span class="field__label">科目</span>
        <el-select v-model="subjectId" placeholder="全部科目" clearable class="field__control">
          <el-option
            v-for="s in subjectStore.subjects"
            :key="s.id"
            :value="s.id"
            :label="`${s.code} · ${s.name}`"
          />
        </el-select>
      </div>

      <div class="field">
        <span class="field__label">题型</span>
        <el-select
          v-model="types"
          multiple
          collapse-tags
          collapse-tags-tooltip
          :placeholder="loadingTypes ? '加载中…' : '全部题型'"
          :loading="loadingTypes"
          :disabled="typeCounts.length === 0"
          class="field__control"
        >
          <el-option
            v-for="t in typeCounts"
            :key="t.type"
            :value="t.type"
            :label="`${t.typeLabel}(${t.count})`"
          />
        </el-select>
      </div>

      <div class="field">
        <span class="field__label">题量</span>
        <el-input-number v-model="count" :min="1" :max="100" class="field__control" />
      </div>

      <p class="custom__summary">
        <template v-if="selectedAvailable > 0">
          可抽 <strong class="mono">{{ selectedAvailable }}</strong> 题,本次将练
          <strong class="mono">{{ actualCount }}</strong> 题
        </template>
        <template v-else>这个范围下没有可练习的题</template>
      </p>

      <div class="custom__actions">
        <el-button type="primary" size="large" :disabled="selectedAvailable === 0" @click="start">
          <AppIcon name="target" :size="18" /> 开始练习
        </el-button>
      </div>
    </div>

    <div v-if="!loadingTypes && typeCounts.length === 0" class="card">
      <EmptyState
        text="这个范围下还没有「已发布」的题。待校对的题不进练习池,去题库把它们改成已发布就能练了。"
        action-text="去题库"
        icon="list"
        @action="router.push('/questions')"
      />
    </div>
  </section>
</template>

<style scoped>
.custom {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.custom__head {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.custom__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.custom__hint {
  font-size: var(--font-size-body);
  color: var(--text-secondary);
  line-height: 1.6;
}

.card {
  padding: var(--card-padding);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.custom__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.field__label {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.field__control {
  width: 100%;
}

.custom__summary {
  margin: 0;
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.custom__actions {
  display: flex;
}

.custom__actions .el-button {
  width: 100%;
  min-height: 48px;
}

.banner {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  background-color: var(--color-danger-light);
  color: var(--color-danger);
}

.banner__text {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-size-body);
}

.banner__action {
  flex-shrink: 0;
  min-height: var(--tap-target-min);
  padding: 0 var(--space-md);
  border: 1px solid currentColor;
  border-radius: var(--radius-full);
  background-color: transparent;
  color: inherit;
  font-size: var(--font-size-body);
}
</style>
