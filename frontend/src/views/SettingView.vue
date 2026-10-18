<script setup lang="ts">
/**
 * 设置与提醒(阶段 7)。
 *
 * 三块:学习设置 / 提醒设置 / 通道配置,外加近 7 天提醒日志。
 *
 * 「发送测试提醒」会**先保存再发** —— 否则用户刚填完 Webhook 点测试,发的还是旧配置,
 * 会得到「显示已配置却收不到」的假象。这一块是提醒刚需的落地面,结果必须如实反馈。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getSettings, updateSettings } from '@/api/setting'
import { getNotifyLogs, testNotify } from '@/api/notify'
import type { NotifyChannel, NotifyLogVO } from '@/api/types'

const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const loadError = ref('')
const logs = ref<NotifyLogVO[]>([])

const form = reactive<{
  dailyQuestionCount: number
  notifyChannel: NotifyChannel
  notifyWebhook: string
  notifyKey: string
  reminderEnabled: boolean
  reminderTime: string
  eveningReminderEnabled: boolean
  eveningReminderTime: string
}>({
  dailyQuestionCount: 10,
  notifyChannel: 'WECOM',
  notifyWebhook: '',
  notifyKey: '',
  reminderEnabled: true,
  reminderTime: '07:00',
  eveningReminderEnabled: true,
  eveningReminderTime: '21:30'
})

const isWecom = computed(() => form.notifyChannel === 'WECOM')

/** 提醒时间统一成 HH:mm,后端返回可能是 HH:mm:ss */
function hhmm(value: string | null | undefined, fallback: string): string {
  return typeof value === 'string' && value.length >= 5 ? value.slice(0, 5) : fallback
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const s = await getSettings()
    form.dailyQuestionCount = s.dailyQuestionCount
    form.notifyChannel = s.notifyChannel
    form.notifyWebhook = s.notifyWebhook ?? ''
    form.notifyKey = s.notifyKey ?? ''
    form.reminderEnabled = s.reminderEnabled
    form.reminderTime = hhmm(s.reminderTime, '07:00')
    form.eveningReminderEnabled = s.eveningReminderEnabled
    form.eveningReminderTime = hhmm(s.eveningReminderTime, '21:30')
    logs.value = await getNotifyLogs(7)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载设置失败'
  } finally {
    loading.value = false
  }
}

function payload() {
  return { ...form }
}

async function save() {
  saving.value = true
  try {
    const updated = await updateSettings(payload())
    // 后端把刚存的值重新掩码回来,同步到输入框,避免显示的还是旧掩码
    form.notifyWebhook = updated.notifyWebhook ?? ''
    form.notifyKey = updated.notifyKey ?? ''
    ElMessage.success('设置已保存')
  } finally {
    saving.value = false
  }
}

async function sendTest() {
  testing.value = true
  try {
    // 先落库再发:刚填的 Webhook 立刻生效,才不会出现「填了却测不到」
    const updated = await updateSettings(payload())
    form.notifyWebhook = updated.notifyWebhook ?? ''
    form.notifyKey = updated.notifyKey ?? ''
    const result = await testNotify()
    if (result.success) {
      ElMessage.success(result.message)
    } else {
      ElMessage.error(result.message)
    }
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="settings">
    <header class="settings__head">
      <h1 class="settings__title">设置</h1>
      <p class="settings__hint">提醒走企业微信群机器人或 Server酱,配置好可以点「发送测试提醒」验证。</p>
    </header>

    <div v-if="loadError" class="banner" role="alert">
      <span class="banner__text wrap-anywhere">{{ loadError }}</span>
      <button type="button" class="banner__action" @click="load">重试</button>
    </div>

    <div v-if="loading" class="card skeleton-card">
      <div class="skeleton skeleton--line skeleton--w60" />
      <div class="skeleton skeleton--block" />
    </div>

    <template v-else>
      <!-- 学习设置 -->
      <div class="card">
        <div class="card__head"><span class="card__title">学习设置</span></div>
        <div class="field-row">
          <div class="field">
            <span class="field__label">每日任务题量</span>
            <el-input-number v-model="form.dailyQuestionCount" :min="1" :max="200" />
          </div>
          <p class="field__hint">今日页「开始今日 N 题」用的就是这个数量。</p>
        </div>
      </div>

      <!-- 提醒设置 -->
      <div class="card">
        <div class="card__head"><span class="card__title">提醒设置</span></div>

        <div class="field-row">
          <el-switch v-model="form.reminderEnabled" />
          <span class="field__label">早间提醒</span>
          <el-time-picker
            v-model="form.reminderTime"
            format="HH:mm"
            value-format="HH:mm"
            :disabled="!form.reminderEnabled"
            placeholder="07:00"
            class="time-picker"
          />
        </div>
        <p class="field__hint field__hint--indent">有到期题时推送「今日 N 题待复习」;没有到期题不发。</p>

        <div class="field-row field-row--gap">
          <el-switch v-model="form.eveningReminderEnabled" />
          <span class="field__label">晚间未打卡提醒</span>
          <el-time-picker
            v-model="form.eveningReminderTime"
            format="HH:mm"
            value-format="HH:mm"
            :disabled="!form.eveningReminderEnabled"
            placeholder="21:30"
            class="time-picker"
          />
        </div>
        <p class="field__hint field__hint--indent">当天没做过题才提醒;已经做过就不打扰。</p>

        <div class="actions">
          <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
          <el-button :loading="testing" @click="sendTest">
            <AppIcon name="target" :size="14" /> 发送测试提醒
          </el-button>
        </div>
        <p class="field__hint">「发送测试提醒」会先保存当前配置再发,微信里收到即表示通道可用。</p>
      </div>

      <!-- 通道配置 -->
      <div class="card">
        <div class="card__head"><span class="card__title">通道配置</span></div>

        <div class="field-row">
          <span class="field__label">通道</span>
          <el-radio-group v-model="form.notifyChannel">
            <el-radio-button value="WECOM">企业微信群机器人</el-radio-button>
            <el-radio-button value="SERVERCHAN">Server酱</el-radio-button>
          </el-radio-group>
        </div>

        <template v-if="isWecom">
          <div class="field">
            <span class="field__label">Webhook 地址</span>
            <el-input
              v-model="form.notifyWebhook"
              placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=…"
              class="mono"
            />
          </div>
          <p class="field__hint">
            群聊 → 群设置 → 群机器人 → 添加机器人,复制 Webhook 地址。
            已保存的地址只显示末 6 位掩码;要改就整段替换,留空表示清除。
          </p>
        </template>

        <template v-else>
          <div class="field">
            <span class="field__label">SendKey</span>
            <el-input v-model="form.notifyKey" placeholder="SCT… 或 sctp… 开头的 SendKey" class="mono" />
          </div>
          <p class="field__hint">
            Server酱控制台 → SendKey。已保存的 Key 只显示末 6 位掩码;要改就整段替换,留空表示清除。
          </p>
        </template>
      </div>

      <!-- 提醒日志 -->
      <div class="card">
        <div class="card__head">
          <span class="card__title">近 7 天提醒</span>
          <span class="card__meta">{{ logs.length }} 条</span>
        </div>

        <div v-if="logs.length === 0" class="empty-slot">
          <EmptyState text="还没有提醒记录。到点发送后会出现在这里。" />
        </div>

        <ul v-else class="log-list">
          <li v-for="log in logs" :key="log.id" class="log-row">
            <span class="log-row__date mono">{{ log.sentAt }}</span>
            <span class="log-row__type">{{ log.typeLabel }}</span>
            <span :class="['log-row__status', log.success ? 'meta-ok' : 'meta-warn']">
              {{ log.success ? '已发送' : '发送失败' }}
            </span>
            <p v-if="!log.success && log.failReason" class="log-row__reason wrap-anywhere">
              {{ log.failReason }}
            </p>
          </li>
        </ul>
      </div>
    </template>
  </section>
</template>

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

.settings__head {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.settings__title {
  font-size: var(--font-size-page-title);
  font-weight: var(--font-weight-page-title);
  line-height: var(--line-height-page-title);
  color: var(--text-primary);
}

.settings__hint {
  font-size: var(--font-size-body);
  color: var(--text-secondary);
}

.card {
  padding: var(--card-padding);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.card__title {
  font-size: var(--font-size-card-title);
  font-weight: var(--font-weight-card-title);
  color: var(--text-primary);
}

.card__meta {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.field-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.field-row--gap {
  margin-top: var(--space-md);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.field__label {
  font-size: var(--font-size-body);
  color: var(--text-primary);
}

.field__hint {
  margin: var(--space-xs) 0 0;
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
  line-height: 1.6;
}

.field__hint--indent {
  padding-left: calc(40px + var(--space-sm));
}

.time-picker {
  width: 140px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
}

.empty-slot {
  padding: var(--space-sm) 0;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.log-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: var(--space-sm);
  align-items: baseline;
  padding: var(--space-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}

.log-row__date {
  font-size: var(--font-size-aux);
  color: var(--text-secondary);
}

.log-row__type {
  font-size: var(--font-size-body);
  color: var(--text-primary);
}

.log-row__status {
  font-size: var(--font-size-aux);
}

.meta-ok { color: var(--color-success); }
.meta-warn { color: var(--color-danger); }

.log-row__reason {
  grid-column: 1 / -1;
  margin: 0;
  font-size: var(--font-size-aux);
  color: var(--color-danger);
}

.skeleton-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.skeleton {
  border-radius: var(--radius-sm);
  background: linear-gradient(
    90deg,
    var(--border-color) 25%,
    var(--bg-page) 37%,
    var(--border-color) 63%
  );
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

.skeleton--line { height: 16px; }
.skeleton--block { height: 56px; border-radius: var(--radius-md); }
.skeleton--w60 { width: 60%; }

@keyframes skeleton-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
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
