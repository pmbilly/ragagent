package com.ragagent.agenttools.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.contract.agent.SearchAuth;
import com.ragagent.contract.knowledge.ChunkFacts;
import com.ragagent.contract.retrieval.SearchTarget;
import com.ragagent.contract.tool.ToolRequest;
import com.ragagent.contract.tool.ToolResult;
import com.ragagent.platform.knowledge.DocChunkSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** startChunkIndex 是精确块起点：范围读取、单块点读、旧 offset 兼容。 */
class ListKnowledgeChunksRangeTest {

    private static final int TOTAL = 30;

    private static ChunkFacts chunk(int index) {
        return new ChunkFacts("c" + index, "doc-1", "kb-1", "content-" + index, "text", "ready", true,
                index, 0, 0, 0, "", "", "", null, null, "");
    }

    private static final DocChunkSupport.KnowledgeInfoReader READER =
            new DocChunkSupport.KnowledgeInfoReader() {
                @Override
                public DocChunkSupport.KnowledgeInfoRow byIdOnly(String id) {
                    return new DocChunkSupport.KnowledgeInfoRow(id, 10002, "kb-1", "Doc 1", "",
                            "file", "upload", "", "", 0, "parsed", Map.of());
                }

                @Override
                public Map<String, List<SearchAuth.TagView>> fetchTags(List<String> ids) {
                    return Map.of();
                }
            };

    private static DocChunkSupport.PagedChunks paged() {
        List<ChunkFacts> all = IntStream.range(0, TOTAL)
                .mapToObj(ListKnowledgeChunksRangeTest::chunk).toList();
        return new DocChunkSupport.PagedChunks() {
            @Override
            public DocChunkSupport.ChunkPage listPaged(long tenantId, String knowledgeId, int page, int pageSize) {
                return listRange(tenantId, knowledgeId,
                        Math.max(page - 1, 0) * Math.max(pageSize, 0), pageSize);
            }

            @Override
            public DocChunkSupport.ChunkPage listRange(long tenantId, String knowledgeId, int offset, int limit) {
                if (limit <= 0 || offset >= all.size()) {
                    return new DocChunkSupport.ChunkPage(List.of(), TOTAL);
                }
                int end = Math.min(offset + limit, all.size());
                return new DocChunkSupport.ChunkPage(new ArrayList<>(all.subList(offset, end)), TOTAL);
            }
        };
    }

    private static ToolResult run(Map<String, Object> args) {
        ListKnowledgeChunksTool tool = new ListKnowledgeChunksTool(
                READER, null, paged(), null,
                new SearchTarget.SearchTargets(List.of(SearchTarget.wholeKb("kb-1", 10002))));
        return tool.execute(ToolRequest.of(args));
    }

    @Test
    void startIndexAndLimitReadExactRange() {
        ToolResult result = run(Map.of("knowledgeId", "doc-1", "startChunkIndex", 12, "limit", 5));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData())
                .containsEntry("startChunkIndex", 12)
                .containsEntry("fetchedChunks", 5);
        assertThat(chunkIndexes(result)).containsExactly(12, 13, 14, 15, 16);
    }

    @Test
    void limitOneReadsOneBlock() {
        ToolResult result = run(Map.of("knowledgeId", "doc-1", "startChunkIndex", 2, "limit", 1));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("fetchedChunks", 1);
        assertThat(chunkIndexes(result)).containsExactly(2);
    }

    @Test
    void legacyOffsetArgumentStillWorks() {
        ToolResult result = run(Map.of("knowledgeId", "doc-1", "offset", 20, "limit", 3));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("startChunkIndex", 20);
        assertThat(chunkIndexes(result)).containsExactly(20, 21, 22);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> chunkIndexes(ToolResult result) {
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) result.getData().get("chunks");
        return chunks.stream().map(c -> c.get("chunkIndex")).toList();
    }
}
