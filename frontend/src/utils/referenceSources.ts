export type ReferenceItemKind = 'web' | 'document' | 'tool'

/**
 * 引用对象（两种来源、两种拼写，见 {@link normalizeKnowledgeReference}）：
 * 消息对象上的 `knowledgeReferences`（检索域 `SearchResult`，camelCase）与
 * `data.references` 直通载荷（库内键名 / 工具结果，snake）。两边都列出来，
 * 消费处用 `??` 取值或先过归一化函数。
 */
export type KnowledgeReferenceLike = {
  id?: string
  chunkIds?: string[]
  knowledgeId?: string
  knowledgeTitle?: string
  knowledgeFilename?: string
  knowledgeBaseId?: string
  chunkIndex?: number
  chunkType?: string
  content?: string
  metadata?: Record<string, string>
}

/**
 * 归一单条引用，供 UI 侧统一消费。
 *
 * SSE `references` 事件的引用载荷（`data.references` 与 `knowledge_references`）
 * 一律 camelCase（工具面 2026-10-08 换锚；不再保留 snake 兼容分支）。
 */
export function normalizeKnowledgeReference(raw: Record<string, any> | null | undefined): KnowledgeReferenceLike {
  if (!raw) return {}
  return {
    id: raw.id,
    chunkIds: raw.chunkIds,
    knowledgeId: raw.knowledgeId,
    knowledgeTitle: raw.knowledgeTitle,
    knowledgeFilename: raw.knowledgeFilename,
    knowledgeBaseId: raw.knowledgeBaseId,
    chunkIndex: raw.chunkIndex,
    chunkType: raw.chunkType,
    content: raw.content,
    metadata: raw.metadata,
  }
}

export type ReferenceListItem = {
  key: string
  kind: ReferenceItemKind
  index: number
  title: string
  url?: string
  domain?: string
  faviconUrl?: string
  snippet?: string
  chunkId?: string
  chunkIds?: string[]
  knowledgeId?: string
  knowledgeBaseId?: string
  content?: string
}

export type ReferenceDrawerSection = {
  id: 'web' | 'documents' | 'tools'
  items: ReferenceListItem[]
}

export function normalizeReferenceUrl(url: string): string {
  const raw = String(url || '').trim()
  if (!raw) return ''
  try {
    const parsed = new URL(raw)
    parsed.hash = ''
    let pathname = parsed.pathname
    if (pathname.length > 1 && pathname.endsWith('/')) {
      pathname = pathname.slice(0, -1)
    }
    parsed.pathname = pathname
    return parsed.toString()
  } catch {
    return raw.replace(/\/$/, '')
  }
}

export function getWebSearchUrl(item: KnowledgeReferenceLike): string {
  if (item.metadata?.url) return item.metadata.url
  if (item.id && (item.id.startsWith('http://') || item.id.startsWith('https://'))) {
    return item.id
  }
  return ''
}

export function getDomainFromUrl(url: string): string {
  if (!url) return ''
  try {
    return new URL(url).hostname.replace(/^www\./i, '')
  } catch {
    return url
  }
}

export function getFaviconUrl(urlOrDomain: string): string {
  const domain = urlOrDomain.includes('://')
    ? getDomainFromUrl(urlOrDomain)
    : urlOrDomain.replace(/^www\./i, '')
  if (!domain) return ''
  return `https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=32`
}

function truncateText(text: string, maxLen: number): string {
  const normalized = formatReferenceSnippet(text)
  if (normalized.length <= maxLen) return normalized
  return `${normalized.slice(0, maxLen)}…`
}

export function formatReferenceSnippet(text: string | undefined): string {
  let value = String(text || '').replace(/\s+/g, ' ').trim()
  if (!value) return ''
  value = value.replace(/^(\.{3}|…+)\s*/, '')
  value = value.replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
  value = value.replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
  value = value.replace(/`([^`]+)`/g, '$1')
  value = value.replace(/\*\*([^*]+)\*\*/g, '$1')
  value = value.replace(/\*([^*]+)\*/g, '$1')
  value = value.replace(/(^|\s)#+\s*/g, '$1')
  value = value.replace(/\s+/g, ' ').trim()
  return value
}

function isWebReference(item: KnowledgeReferenceLike): boolean {
  return item.chunkType === 'web_search'
}

function isToolReference(item: KnowledgeReferenceLike): boolean {
  return item.chunkType === 'tool_result'
}

function isLikelyUrl(text: string): boolean {
  const trimmed = String(text || '').trim()
  if (!trimmed) return false
  return /^https?:\/\//i.test(trimmed) || /^www\./i.test(trimmed)
}

function isSameReferenceUrl(a: string, b: string): boolean {
  const left = String(a || '').trim()
  const right = String(b || '').trim()
  if (!left || !right) return false
  if (left === right) return true
  return normalizeReferenceUrl(left) === normalizeReferenceUrl(right)
}

function resolveWebTitle(item: KnowledgeReferenceLike, url: string, domain: string): string {
  const metaTitle = item.metadata?.title?.trim()
  if (metaTitle && !isLikelyUrl(metaTitle)) return metaTitle

  const knowledgeTitle = item.knowledgeTitle?.trim()
  if (
    knowledgeTitle &&
    !isLikelyUrl(knowledgeTitle) &&
    !isSameReferenceUrl(knowledgeTitle, url) &&
    knowledgeTitle !== domain
  ) {
    return knowledgeTitle
  }

  return domain || 'Web page'
}

function buildWebItem(item: KnowledgeReferenceLike, index: number): ReferenceListItem | null {
  const url = getWebSearchUrl(item)
  if (!url) return null
  const domain = getDomainFromUrl(url)
  const title = resolveWebTitle(item, url, domain)
  const snippet =
    item.metadata?.snippet ||
    truncateText(item.content || '', 220)
  const normalizedUrl = normalizeReferenceUrl(url)
  return {
    key: `web:${normalizedUrl}`,
    kind: 'web',
    index,
    title,
    url,
    domain,
    faviconUrl: getFaviconUrl(url),
    snippet: snippet || undefined,
    chunkId: item.id,
    content: item.content,
  }
}

function buildDocumentItem(item: KnowledgeReferenceLike, index: number): ReferenceListItem {
  const chunkId = item.id || `${item.knowledgeId || 'doc'}-${item.chunkIndex ?? index}`
  const title = item.knowledgeTitle || item.knowledgeFilename || item.knowledgeId || 'Document'
  const documentKey =
    item.knowledgeId ||
    [item.knowledgeBaseId, item.knowledgeTitle || item.knowledgeFilename].filter(Boolean).join(':') ||
    chunkId
  return {
    key: `doc:${documentKey}`,
    kind: 'document',
    index,
    title,
    chunkId,
    chunkIds: item.chunkIds,
    knowledgeId: item.knowledgeId,
    knowledgeBaseId: item.knowledgeBaseId,
    snippet: truncateText(item.content || '', 220) || undefined,
    content: item.content,
  }
}

function buildToolItem(item: KnowledgeReferenceLike, index: number): ReferenceListItem {
  const id = item.id || `tool-${index}`
  return {
    key: `tool:${id}`,
    kind: 'tool',
    index,
    title: item.knowledgeTitle || item.metadata?.title || 'Tool result',
    domain: item.metadata?.source || item.metadata?.tool || undefined,
    snippet: truncateText(item.content || '', 220) || undefined,
    chunkId: id,
    content: item.content,
  }
}

function getDocumentGroupKey(item: KnowledgeReferenceLike, index: number): string {
  if (item.knowledgeId) return item.knowledgeId
  const title = item.knowledgeTitle || item.knowledgeFilename
  if (title) return [item.knowledgeBaseId, title].filter(Boolean).join(':')
  return (
    item.id ||
    `doc-${index}`
  )
}

function mergeDocumentReferences(refs: KnowledgeReferenceLike[]): KnowledgeReferenceLike[] {
  const groups = new Map<string, KnowledgeReferenceLike & { content_parts?: string[] }>()

  refs.forEach((item, index) => {
    const key = getDocumentGroupKey(item, index)
    const content = String(item.content || '').trim()
    const chunkIds = Array.from(new Set([...(item.chunkIds || []), ...(item.id ? [item.id] : [])]))
    const existing = groups.get(key)

    if (!existing) {
      groups.set(key, {
        ...item,
        id: item.id || key,
        content_parts: content ? [content] : [],
        chunkIds,
      })
      return
    }

    if (!existing.knowledgeId && item.knowledgeId) existing.knowledgeId = item.knowledgeId
    if (!existing.knowledgeTitle && item.knowledgeTitle) existing.knowledgeTitle = item.knowledgeTitle
    if (!existing.knowledgeFilename && item.knowledgeFilename) existing.knowledgeFilename = item.knowledgeFilename
    if (!existing.knowledgeBaseId && item.knowledgeBaseId) existing.knowledgeBaseId = item.knowledgeBaseId
    for (const chunkId of chunkIds) {
      if (!existing.chunkIds?.includes(chunkId)) {
        existing.chunkIds = [...(existing.chunkIds || []), chunkId]
      }
    }
    if (content && !existing.content_parts?.includes(content)) {
      existing.content_parts = [...(existing.content_parts || []), content]
    }
  })

  return Array.from(groups.values()).map((item) => {
    const { content_parts: contentParts, ...rest } = item
    return {
      ...rest,
      content: contentParts?.slice(0, 3).join('\n\n') || rest.content,
    }
  })
}

function mergeWebReferences(refs: KnowledgeReferenceLike[]): KnowledgeReferenceLike[] {
  const groups = new Map<string, KnowledgeReferenceLike>()

  refs.forEach((item, index) => {
    const url = getWebSearchUrl(item)
    const key = url ? normalizeReferenceUrl(url) : item.id || `web-${index}`
    if (!groups.has(key)) {
      groups.set(key, item)
    }
  })

  return Array.from(groups.values())
}

export function buildReferenceSections(
  refs: KnowledgeReferenceLike[] | null | undefined,
): ReferenceDrawerSection[] {
  // 归一：SSE 重建段与缓存直通载荷均为 camelCase（工具面 2026-10-08 换锚）
  const list = Array.isArray(refs)
    ? refs.map((r) => normalizeKnowledgeReference(r as Record<string, any>)).filter(Boolean)
    : []
  if (!list.length) return []

  const webReferences: KnowledgeReferenceLike[] = []
  const documentReferences: KnowledgeReferenceLike[] = []
  const toolReferences: KnowledgeReferenceLike[] = []
  const webItems: ReferenceListItem[] = []
  const docItems: ReferenceListItem[] = []
  const toolItems: ReferenceListItem[] = []
  let webIndex = 0
  let docIndex = 0
  let toolIndex = 0

  for (const item of list) {
    if (isWebReference(item)) {
      webReferences.push(item)
      continue
    }
    if (isToolReference(item)) {
      toolReferences.push(item)
      continue
    }
    documentReferences.push(item)
  }

  for (const item of mergeWebReferences(webReferences)) {
    const built = buildWebItem(item, ++webIndex)
    if (built) webItems.push(built)
  }

  for (const item of mergeDocumentReferences(documentReferences)) {
    docItems.push(buildDocumentItem(item, ++docIndex))
  }

  for (const item of toolReferences) {
    toolItems.push(buildToolItem(item, ++toolIndex))
  }

  const sections: ReferenceDrawerSection[] = []
  if (webItems.length) sections.push({ id: 'web', items: webItems })
  if (docItems.length) sections.push({ id: 'documents', items: docItems })
  if (toolItems.length) sections.push({ id: 'tools', items: toolItems })
  return sections
}

export function buildReferenceList(
  refs: KnowledgeReferenceLike[] | null | undefined,
): ReferenceListItem[] {
  return buildReferenceSections(refs).flatMap((section) => section.items)
}

export type ReferenceHighlightTarget = {
  url?: string
  chunkId?: string
  documentTitle?: string
  knowledgeBaseId?: string
  key?: string
}

export function resolveReferenceHighlightKey(
  refs: KnowledgeReferenceLike[] | null | undefined,
  target: ReferenceHighlightTarget | null | undefined,
): string | null {
  if (!target) return null
  if (target.key) return target.key

  const items = buildReferenceList(refs)
  if (!items.length) return null

  if (target.url) {
    const normalized = normalizeReferenceUrl(target.url)
    const hit = items.find(
      (item) => item.kind === 'web' && item.url && normalizeReferenceUrl(item.url) === normalized,
    )
    if (hit) return hit.key
  }

  if (target.chunkId) {
    const raw = String(target.chunkId).trim()
    const hit = items.find(
      (item) =>
        item.chunkId === raw ||
        item.chunkIds?.includes(raw) ||
        item.key === `doc:${raw}` ||
        (item.kind === 'web' && (item.chunkId === raw || item.url === raw)),
    )
    if (hit) return hit.key
  }

  // Defensive fallback for historical/partially streamed Agent messages whose
  // tool replay payload no longer contains the exact cited chunk. The public
  // citation still carries the full document title and KB id, which identify
  // the same document-level drawer card.
  if (target.documentTitle) {
    const title = target.documentTitle.trim().toLowerCase()
    const candidates = items.filter(
      (item) => item.kind === 'document' && item.title.trim().toLowerCase() === title,
    )
    const scoped = target.knowledgeBaseId
      ? candidates.find((item) => item.knowledgeBaseId === target.knowledgeBaseId)
      : undefined
    if (scoped) return scoped.key
    if (candidates.length === 1) return candidates[0].key
  }

  return null
}
