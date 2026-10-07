import type { ComposerTranslation } from 'vue-i18n'

import type { KnowledgeChunksListData } from '@/types/tool-results'

export function getKnowledgeChunksSummaryHtml(
  t: ComposerTranslation,
  toolData: KnowledgeChunksListData | null | undefined,
): string {
  if (!toolData || toolData.fetchedChunks === undefined) {
    return ''
  }

  const parts: string[] = [
    t('agentStream.knowledgeChunksList.chunkRange', {
      fetched: `<strong>${toolData.fetchedChunks ?? 0}</strong>`,
      total: `<strong>${toolData.totalChunks ?? '?'}</strong>`,
    }),
  ]

  const total = Number(toolData.totalChunks ?? 0)
  const pageSize = Number(toolData.pageSize ?? 0)
  if (total > pageSize && pageSize > 0) {
    parts.push(
      t('agentStream.knowledgeChunksList.page', {
        page: toolData.page ?? 1,
        pageSize,
      }),
    )
  }

  return parts.join(' · ')
}
