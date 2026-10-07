<template>
  <div class="grep-results">
    <div v-if="rows.length" class="results-list">
      <ResultRow v-for="(result, index) in rows" :key="result.key" :index="index + 1" :title="result.title"
        :meta="result.meta" :popup-key="result.key" :show-popup="result.chunks.length > 0 || !!result.snippet"
        :content="result.chunks.length === 1 ? result.snippet : undefined"
        :chunks="result.chunks.length > 1 ? result.chunks : undefined" :chunk-id="result.chunkId"
        :knowledge-id="result.knowledgeId" :highlight="searchPattern" :regex="true" />
    </div>

    <div v-else class="empty-state">
      {{ $t('chat.noMatchFound') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { cleanSnippet } from './contentClean';
import ResultRow from './ResultRow.vue';
import { groupGrepChunkResults } from '@/utils/grepResultsGroup';
import type { GrepKnowledgeResult, GrepResultsData } from '@/types/tool-results';

const props = defineProps<{
  data: GrepResultsData;
}>();

const { t } = useI18n();

const searchPattern = computed(() => props.data.query ?? props.data.patterns?.[0] ?? '');

type GrepRow = {
  key: string;
  title: string;
  meta: string;
  snippet: string;
  chunks: { content: string; chunkId: string; knowledgeId: string }[];
  chunkId?: string;
  knowledgeId?: string;
};

const formatKnowledgeMeta = (result: GrepKnowledgeResult): string => {
  const parts: string[] = [];
  const chunks = result.chunkHitCount ?? 0;
  if (chunks > 0) {
    parts.push(t('agentStream.grepResults.chunkHits', { count: chunks }));
  }
  const hits = result.totalPatternHits ?? 0;
  if (hits > 0 && hits !== chunks) {
    parts.push(t('agentStream.grepResults.keywordHits', { count: hits }));
  }
  if (result.titleMatch) {
    parts.push(t('agentStream.grepResults.titleMatch'));
  }
  return parts.join(' · ');
};

const rowFromGroupedChunk = (group: ReturnType<typeof groupGrepChunkResults>[number]): GrepRow => {
  const title = group.title || t('knowledge.untitledDocument');
  const meta = group.isFaq
    ? t('agentStream.grepResults.faqEntry')
    : formatKnowledgeMeta({
      knowledgeId: group.knowledgeId,
      knowledgeBaseId: '',
      knowledgeTitle: title,
      chunkHitCount: group.chunkHitCount,
      totalPatternHits: group.chunkHitCount,
      distinctPatterns: 1,
      patternCounts: {},
      titleMatch: group.titleMatch,
    });
  return {
    key: group.key,
    title,
    meta,
    snippet: cleanSnippet(group.matchSnippet),
    chunks: group.chunks.map((chunk) => ({
      content: cleanSnippet(chunk.content),
      chunkId: chunk.chunkId,
      knowledgeId: chunk.knowledgeId,
    })),
    chunkId: group.chunks.length === 1 ? group.chunks[0].chunkId : undefined,
    knowledgeId: group.knowledgeId,
  };
};

const rowFromKnowledge = (result: GrepKnowledgeResult): GrepRow => ({
  key: result.knowledgeId,
  title: result.faqQuestion || result.knowledgeTitle || t('knowledge.untitledDocument'),
  meta: formatKnowledgeMeta(result),
  snippet: cleanSnippet(result.matchSnippet ?? ''),
  chunks: result.matchSnippet
    ? [{
      content: cleanSnippet(result.matchSnippet ?? ''),
      chunkId: '',
      knowledgeId: result.knowledgeId,
    }]
    : [],
  knowledgeId: result.knowledgeId,
});

const rows = computed((): GrepRow[] => {
  const chunkRows = props.data.chunkResults;
  if (chunkRows?.length) {
    return groupGrepChunkResults(chunkRows).map(rowFromGroupedChunk);
  }
  return (props.data.knowledgeResults ?? []).map(rowFromKnowledge);
});
</script>

<style lang="less" scoped>
@import './tool-results.less';

.grep-results {
  display: flex;
  flex-direction: column;
  padding: 0 0 0 12px;
  gap: 3px;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-height: 200px;
  overflow-y: auto;
}
</style>
