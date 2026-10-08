import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildReferenceSections,
  buildReferenceList,
  getDomainFromUrl,
  normalizeKnowledgeReference,
  normalizeReferenceUrl,
  resolveReferenceHighlightKey,
} from './referenceSources.ts'
import { resolveCitationChunkId } from './citationMarkdown.ts'

test('buildReferenceList separates web and document references', () => {
  const items = buildReferenceList([
    {
      id: 'https://example.com/a',
      chunkType: 'web_search',
      knowledgeTitle: 'Example A',
      metadata: { url: 'https://example.com/a', snippet: 'snippet a' },
      content: 'Example A\n\nsnippet a',
    },
    {
      id: 'chunk-1',
      knowledgeId: 'doc-1',
      knowledgeTitle: 'Policy',
      content: 'refund rules',
    },
  ])

  assert.equal(items.length, 2)
  assert.equal(items[0].kind, 'web')
  assert.equal(items[0].domain, 'example.com')
  assert.equal(items[1].kind, 'document')
})

test('buildReferenceList aggregates chunks from the same document', () => {
  const items = buildReferenceList([
    {
      id: 'chunk-1',
      knowledgeId: 'doc-1',
      knowledgeTitle: 'Policy',
      content: 'refund rules',
    },
    {
      id: 'chunk-2',
      knowledgeId: 'doc-1',
      knowledgeTitle: 'Policy',
      content: 'shipping rules',
    },
  ])

  assert.equal(items.length, 1)
  assert.equal(items[0].key, 'doc:doc-1')
  assert.deepEqual(items[0].chunkIds, ['chunk-1', 'chunk-2'])
  assert.match(items[0].content || '', /refund rules/)
  assert.match(items[0].content || '', /shipping rules/)
})

test('buildReferenceSections keeps tool results in their own section', () => {
  const sections = buildReferenceSections([
    {
      id: 'mcp-result-1',
      chunkType: 'toolResult',
      knowledgeTitle: 'MCP Search',
      content: 'tool output',
      metadata: { source: 'MCP service' },
    },
  ])

  assert.equal(sections.length, 1)
  assert.equal(sections[0].id, 'tools')
  assert.equal(sections[0].items[0].kind, 'tool')
  assert.equal(sections[0].items[0].content, 'tool output')
})

test('resolveReferenceHighlightKey matches web url', () => {
  const refs = [
    {
      id: 'https://news.example.com/post',
      chunkType: 'web_search',
      metadata: { url: 'https://news.example.com/post/' },
    },
  ]
  const key = resolveReferenceHighlightKey(refs, {
    url: 'https://news.example.com/post',
  })
  assert.equal(key, 'web:https://news.example.com/post')
})

test('resolveReferenceHighlightKey matches any chunk merged into a document item', () => {
  const refs = [
    {
      id: 'chunk-1',
      chunkIds: ['chunk-1', 'chunk-2'],
      knowledgeId: 'doc-1',
      knowledgeTitle: 'Policy',
    },
  ]

  assert.equal(
    resolveReferenceHighlightKey(refs, { chunkId: 'chunk-2' }),
    'doc:doc-1',
  )
})

test('resolveReferenceHighlightKey falls back to document title and knowledge base', () => {
  const refs = [
    {
      id: 'available-chunk',
      knowledgeId: 'doc-1',
      knowledgeTitle: 'Claude Sonnet 5.md',
      knowledgeBaseId: 'kb-1',
    },
  ]

  assert.equal(
    resolveReferenceHighlightKey(refs, {
      chunkId: 'cited-chunk-missing-from-legacy-replay',
      documentTitle: 'Claude Sonnet 5.md',
      knowledgeBaseId: 'kb-1',
    }),
    'doc:doc-1',
  )
})

test('normalizeReferenceUrl trims trailing slash', () => {
  assert.equal(
    normalizeReferenceUrl('https://example.com/path/'),
    'https://example.com/path',
  )
})

test('buildReferenceList uses domain instead of raw url title', () => {
  const items = buildReferenceList([
    {
      id: 'http://bj.bendibao.com/xiuxian/202671/384250.shtm',
      chunkType: 'web_search',
      knowledgeTitle: 'http://bj.bendibao.com/xiuxian/202671/384250.shtm',
      metadata: {
        url: 'http://bj.bendibao.com/xiuxian/202671/384250.shtm',
        snippet: '根据提供的网页内容...',
      },
      content: '根据提供的网页内容...',
    },
  ])

  assert.equal(items[0].title, 'bj.bendibao.com')
  assert.equal(items[0].domain, 'bj.bendibao.com')
})

test('buildReferenceList prefers metadata title for web references', () => {
  const items = buildReferenceList([
    {
      id: 'https://example.com/post',
      chunkType: 'web_search',
      knowledgeTitle: 'https://example.com/post',
      metadata: {
        url: 'https://example.com/post',
        title: 'Example headline',
        snippet: 'snippet text',
      },
    },
  ])

  assert.equal(items[0].title, 'Example headline')
})

test('formatReferenceSnippet strips markdown noise from preview text', async () => {
  const { formatReferenceSnippet } = await import('./referenceSources.ts')
  assert.equal(
    formatReferenceSnippet('... [Free Online Lectures](https://example.com) **Everything I Know**'),
    'Free Online Lectures Everything I Know',
  )
  assert.equal(
    formatReferenceSnippet('![Logo](local://image.jpg) Summary text'),
    'Summary text',
  )
})

test('getDomainFromUrl strips www prefix', () => {
  assert.equal(getDomainFromUrl('https://www.example.com/x'), 'example.com')
})

// ── SSE references 两种拼写归一（重建段 camelCase / 缓存直通与历史库存 snake） ──

const CAMEL_REF = {
  id: 'chunk-1',
  knowledgeId: 'doc-1',
  knowledgeTitle: '手册.pdf',
  knowledgeFilename: 'manual.pdf',
  knowledgeBaseId: 'kb-1',
  chunkIndex: 3,
  chunkType: 'text',
  content: '正文',
  metadata: { url: 'https://example.com/a' },
}

test('normalizeKnowledgeReference maps the camelCase payload', () => {
  assert.equal(normalizeKnowledgeReference(CAMEL_REF).knowledgeTitle, '手册.pdf')
  assert.equal(normalizeKnowledgeReference(CAMEL_REF).knowledgeBaseId, 'kb-1')
  assert.equal(normalizeKnowledgeReference(CAMEL_REF).chunkIndex, 3)
})

test('normalizeKnowledgeReference tolerates empty input', () => {
  assert.deepEqual(normalizeKnowledgeReference(null), {})
  assert.deepEqual(normalizeKnowledgeReference(undefined), {})
})

test('buildReferenceList yields one item per document', () => {
  const camel = buildReferenceList([CAMEL_REF])
  assert.equal(camel.length, 1)
  assert.equal(camel[0].knowledgeId, 'doc-1')
})

test('buildReferenceSections aggregates camelCase refs into one document section', () => {
  const sections = buildReferenceSections([CAMEL_REF, { id: 'chunk-2', chunkType: 'faq', knowledgeTitle: 'Q' }])
  assert.equal(sections.length, 1)
  assert.equal(sections[0].items.length, 2)
  assert.equal(sections[0].items[0].knowledgeId, 'doc-1')
})

test('resolveCitationChunkId resolves camelCase refs', () => {
  assert.equal(resolveCitationChunkId('DOC-1', { doc: '手册.pdf' }, [CAMEL_REF]), 'chunk-1')
})
