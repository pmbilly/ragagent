import type { GrepChunkResult, GrepKnowledgeResult } from '@/types/tool-results'

export type GrepGroupedRow = {
  key: string
  knowledgeId: string
  knowledgeBaseId: string
  title: string
  isFaq: boolean
  chunkHitCount: number
  titleMatch: boolean
  matchSnippet: string
  chunks: { content: string; chunkId: string; knowledgeId: string }[]
}

/** Collapse per-chunk grep hits into one row per document; FAQ entries stay separate. */
export function groupGrepChunkResults(chunkRows: GrepChunkResult[]): GrepGroupedRow[] {
  const map = new Map<string, GrepGroupedRow>()
  const order: string[] = []

  for (const result of chunkRows) {
    const isFAQ = !!result.faqId || result.chunkType === 'faq'
    const key = isFAQ ? (result.faqId || result.chunkId) : result.knowledgeId
    if (!key) continue

    const snippet = String(result.matchSnippet ?? '').trim()
    if (!map.has(key)) {
      map.set(key, {
        key,
        knowledgeId: result.knowledgeId,
        knowledgeBaseId: result.knowledgeBaseId,
        title: result.faqQuestion || result.knowledgeTitle || '',
        isFaq: isFAQ,
        chunkHitCount: 0,
        titleMatch: false,
        matchSnippet: snippet,
        chunks: [],
      })
      order.push(key)
    }

    const group = map.get(key)!
    group.chunkHitCount += 1
    if (result.titleMatch) group.titleMatch = true
    if (!group.matchSnippet && snippet) group.matchSnippet = snippet
    if (snippet) {
      group.chunks.push({
        content: snippet,
        chunkId: result.faqId || result.chunkId,
        knowledgeId: result.knowledgeId,
      })
    }
  }

  return order.map((key) => map.get(key)!)
}

export function countGrepDocuments(toolData: {
  documentCount?: number
  knowledgeResults?: GrepKnowledgeResult[]
  chunkResults?: GrepChunkResult[]
} | null | undefined): number {
  if (!toolData) return 0
  if (typeof toolData.documentCount === 'number' && toolData.documentCount >= 0) {
    return toolData.documentCount
  }
  if (toolData.knowledgeResults?.length) {
    return toolData.knowledgeResults.length
  }
  if (toolData.chunkResults?.length) {
    return groupGrepChunkResults(toolData.chunkResults).length
  }
  return 0
}
