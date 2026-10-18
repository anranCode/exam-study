import type { RouteRecordRaw } from 'vue-router'
import TodayView from '@/views/TodayView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题,用于 document.title 与布局标题 */
    title?: string
    /** 底部 Tab 标识:today / question-list / wrong-book / stats */
    tab?: string
    /** 布局偏好:auto(默认自适应) / single(单列居中) */
    layout?: 'auto' | 'single'
  }
}

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'today',
    component: TodayView,
    meta: { title: '今日', tab: 'today', layout: 'auto' }
  },
  {
    // 必须排在 /practice/:mode 之前:静态段优先,否则 custom 会被当成一种练习模式
    path: '/practice/custom',
    name: 'practice-custom',
    component: () => import('@/views/CustomPracticeView.vue'),
    meta: { title: '自由练习', layout: 'auto' }
  },
  {
    path: '/practice/:mode',
    name: 'practice',
    component: () => import('@/views/PracticeView.vue'),
    meta: { title: '刷题', layout: 'single' }
  },
  {
    path: '/practice/:mode/result/:sessionId',
    name: 'practice-result',
    component: () => import('@/views/ResultView.vue'),
    meta: { title: '本轮小结', layout: 'single' }
  },
  {
    path: '/questions',
    name: 'question-list',
    component: () => import('@/views/QuestionListView.vue'),
    meta: { title: '题库', tab: 'question-list', layout: 'auto' }
  },
  {
    path: '/questions/new',
    name: 'question-new',
    component: () => import('@/views/QuestionEditView.vue'),
    meta: { title: '新建题目', layout: 'auto' }
  },
  {
    path: '/questions/import',
    name: 'question-import',
    component: () => import('@/views/QuestionImportView.vue'),
    meta: { title: '批量导入', layout: 'auto' }
  },
  {
    path: '/questions/:id/edit',
    name: 'question-edit',
    component: () => import('@/views/QuestionEditView.vue'),
    meta: { title: '编辑题目', layout: 'auto' }
  },
  {
    path: '/wrong',
    name: 'wrong-book',
    component: () => import('@/views/WrongBookView.vue'),
    meta: { title: '错题', tab: 'wrong-book', layout: 'auto' }
  },
  {
    path: '/stats',
    name: 'stats',
    component: () => import('@/views/StatsView.vue'),
    meta: { title: '统计', tab: 'stats', layout: 'auto' }
  },
  {
    path: '/digest',
    name: 'digest',
    component: () => import('@/views/DigestView.vue'),
    meta: { title: '考前串讲', layout: 'auto' }
  },
  {
    path: '/subjects',
    name: 'subject-list',
    component: () => import('@/views/SubjectListView.vue'),
    meta: { title: '科目与章节', layout: 'auto' }
  },
  {
    path: '/settings',
    name: 'setting',
    component: () => import('@/views/SettingView.vue'),
    meta: { title: '设置', layout: 'auto' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  }
]