import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as subjectApi from '@/api/subject'
import type { ChapterUpsertDTO, ChapterVO, SubjectUpsertDTO, SubjectVO } from '@/api/types'

/** 把后端返回的扁平章节列表组装成树(后端不返回树结构) */
export function buildChapterTree(list: ChapterVO[]): ChapterVO[] {
  const map = new Map<number, ChapterVO>()
  for (const item of list) {
    map.set(item.id, { ...item, children: [] })
  }
  const roots: ChapterVO[] = []
  for (const item of list) {
    const node = map.get(item.id)
    if (!node) continue
    if (item.parentId != null && map.has(item.parentId)) {
      map.get(item.parentId)?.children?.push(node)
    } else {
      roots.push(node)
    }
  }
  const sortRecursive = (nodes: ChapterVO[]) => {
    nodes.sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    for (const node of nodes) {
      if (node.children && node.children.length > 0) sortRecursive(node.children)
    }
  }
  sortRecursive(roots)
  return roots
}

function sortSubjects(list: SubjectVO[]): SubjectVO[] {
  return [...list].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
}

export const useSubjectStore = defineStore('subject', () => {
  const subjects = ref<SubjectVO[]>([])
  const chaptersBySubject = ref<Record<number, ChapterVO[]>>({})
  const currentSubjectId = ref<number | null>(null)
  const loadingSubjects = ref(false)
  const loadingChapters = ref(false)
  const loaded = ref(false)

  /** id → 科目 */
  const subjectMap = computed(() => {
    const map = new Map<number, SubjectVO>()
    for (const subject of subjects.value) map.set(subject.id, subject)
    return map
  })

  /** id → 章节(所有已加载科目) */
  const chapterMap = computed(() => {
    const map = new Map<number, ChapterVO>()
    for (const list of Object.values(chaptersBySubject.value)) {
      for (const chapter of list) map.set(chapter.id, chapter)
    }
    return map
  })

  const enabledSubjects = computed(() => subjects.value.filter((s) => s.enabled))

  const currentChapters = computed<ChapterVO[]>(() => {
    if (currentSubjectId.value == null) return []
    return chaptersBySubject.value[currentSubjectId.value] ?? []
  })

  const currentChapterTree = computed<ChapterVO[]>(() => buildChapterTree(currentChapters.value))

  const currentSubject = computed<SubjectVO | null>(() => {
    if (currentSubjectId.value == null) return null
    return subjectMap.value.get(currentSubjectId.value) ?? null
  })

  async function fetchSubjects(force = false): Promise<SubjectVO[]> {
    if (loaded.value && !force) return subjects.value
    loadingSubjects.value = true
    try {
      const list = await subjectApi.getSubjects()
      subjects.value = sortSubjects(list)
      loaded.value = true
      return subjects.value
    } finally {
      loadingSubjects.value = false
    }
  }

  async function fetchChapters(subjectId: number): Promise<ChapterVO[]> {
    loadingChapters.value = true
    try {
      const list = await subjectApi.getChapters(subjectId)
      chaptersBySubject.value = { ...chaptersBySubject.value, [subjectId]: list }
      return list
    } finally {
      loadingChapters.value = false
    }
  }

  function selectSubject(subjectId: number | null) {
    currentSubjectId.value = subjectId
    if (subjectId != null && !chaptersBySubject.value[subjectId]) {
      void fetchChapters(subjectId)
    }
  }

  async function createSubject(body: SubjectUpsertDTO): Promise<SubjectVO> {
    const created = await subjectApi.createSubject(body)
    subjects.value = sortSubjects([...subjects.value, created])
    return created
  }

  async function updateSubject(id: number, body: SubjectUpsertDTO): Promise<SubjectVO> {
    const updated = await subjectApi.updateSubject(id, body)
    subjects.value = sortSubjects(subjects.value.map((s) => (s.id === id ? updated : s)))
    return updated
  }

  async function removeSubject(id: number): Promise<void> {
    await subjectApi.deleteSubject(id)
    subjects.value = subjects.value.filter((s) => s.id !== id)
    if (currentSubjectId.value === id) currentSubjectId.value = null
    const next = { ...chaptersBySubject.value }
    delete next[id]
    chaptersBySubject.value = next
  }

  async function createChapter(body: ChapterUpsertDTO): Promise<ChapterVO> {
    const created = await subjectApi.createChapter(body)
    await fetchChapters(body.subjectId)
    return created
  }

  async function updateChapter(id: number, body: ChapterUpsertDTO): Promise<ChapterVO> {
    const updated = await subjectApi.updateChapter(id, body)
    await fetchChapters(body.subjectId)
    return updated
  }

  async function removeChapter(id: number, subjectId: number): Promise<void> {
    await subjectApi.deleteChapter(id)
    await fetchChapters(subjectId)
  }

  return {
    subjects,
    chaptersBySubject,
    currentSubjectId,
    loadingSubjects,
    loadingChapters,
    loaded,
    subjectMap,
    chapterMap,
    enabledSubjects,
    currentChapters,
    currentChapterTree,
    currentSubject,
    fetchSubjects,
    fetchChapters,
    selectSubject,
    createSubject,
    updateSubject,
    removeSubject,
    createChapter,
    updateChapter,
    removeChapter
  }
})