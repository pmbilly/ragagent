import assert from 'node:assert/strict'
import test from 'node:test'

import { countGrepDocuments, groupGrepChunkResults } from './grepResultsGroup.ts'

test('groupGrepChunkResults merges chunks from the same document', () => {
  const grouped = groupGrepChunkResults([
    {
      chunkId: 'chunk-a',
      knowledgeId: 'doc-1',
      knowledgeBaseId: 'kb-1',
      knowledgeTitle: 'sample-report.pdf',
      matchSnippet: 'hit one',
    },
    {
      chunkId: 'chunk-b',
      knowledgeId: 'doc-1',
      knowledgeBaseId: 'kb-1',
      knowledgeTitle: 'sample-report.pdf',
      matchSnippet: 'hit two',
    },
    {
      chunkId: 'chunk-c',
      knowledgeId: 'doc-2',
      knowledgeBaseId: 'kb-1',
      knowledgeTitle: 'other-report.pdf',
      matchSnippet: 'other hit',
    },
  ])

  assert.equal(grouped.length, 2)
  assert.equal(grouped[0].chunkHitCount, 2)
  assert.equal(grouped[0].chunks.length, 2)
  assert.equal(grouped[0].knowledgeBaseId, 'kb-1')
  assert.deepEqual(grouped[0].chunks.map((chunk) => chunk.chunkId), ['chunk-a', 'chunk-b'])
  assert.equal(grouped[1].chunkHitCount, 1)
})

test('groupGrepChunkResults keeps FAQ entries separate', () => {
  const grouped = groupGrepChunkResults([
    {
      chunkId: 'faq-1',
      faqId: 'faq-1',
      knowledgeId: 'doc-faq',
      knowledgeBaseId: 'kb-1',
      knowledgeTitle: 'FAQ doc',
      chunkType: 'faq',
      faqQuestion: 'Question A',
      matchSnippet: 'answer a',
    },
    {
      chunkId: 'faq-2',
      faqId: 'faq-2',
      knowledgeId: 'doc-faq',
      knowledgeBaseId: 'kb-1',
      knowledgeTitle: 'FAQ doc',
      chunkType: 'faq',
      faqQuestion: 'Question B',
      matchSnippet: 'answer b',
    },
  ])

  assert.equal(grouped.length, 2)
  assert.equal(grouped[0].title, 'Question A')
  assert.equal(grouped[1].title, 'Question B')
})

test('countGrepDocuments prefers documentCount from backend', () => {
  assert.equal(
    countGrepDocuments({
      documentCount: 1,
      knowledgeResults: [{ knowledgeId: 'doc-1' }, { knowledgeId: 'doc-2' }],
      chunkResults: [{ chunkId: 'c1', knowledgeId: 'doc-1', knowledgeBaseId: 'kb', knowledgeTitle: 'a' }],
    }),
    1,
  )
})

test('countGrepDocuments prefers knowledgeResults length when documentCount absent', () => {
  assert.equal(
    countGrepDocuments({
      knowledgeResults: [{ knowledgeId: 'doc-1' }, { knowledgeId: 'doc-2' }],
      chunkResults: [{ chunkId: 'c1', knowledgeId: 'doc-1', knowledgeBaseId: 'kb', knowledgeTitle: 'a' }],
    }),
    2,
  )
})
