import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { StatsOverviewVO } from '@/api/types'

/** 统计数据 —— 阶段 6 实现,当前为类型骨架 */
export const useStatsStore = defineStore('stats', () => {
  const overview = ref<StatsOverviewVO | null>(null)
  const loading = ref(false)

  return { overview, loading }
})