import { ref } from 'vue'
import { defineStore } from 'pinia'

/** 用户设置与提醒通道配置 —— 阶段 7 实现,当前为类型骨架 */
export const useSettingStore = defineStore('setting', () => {
  const dailyQuota = ref(10)
  const defaultSubjectId = ref<number | null>(null)

  return { dailyQuota, defaultSubjectId }
})