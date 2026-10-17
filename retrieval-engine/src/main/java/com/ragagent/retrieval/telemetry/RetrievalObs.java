package com.ragagent.retrieval.telemetry;

import com.ragagent.contract.retrieval.RetrievalObsRows;
import com.ragagent.retrieval.engine.EngineTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索观测：**引擎内部形状**（{@code EngineTypes.*}）的汇总两法。
 *
 * <p>R3b 拆类后本类只剩"引擎内部件"部分；纯函数族（截断/命中预览/分数汇总/段落预览/格式化）
 * 已上提 {@link RetrievalObsRows}（契约面，业务域只认它）。</p>
 *
 * <p>产出只进 span（Java 侧恒 no-op），行为契约由 rerank 的 span 输入测试钉住。</p>
 */
public final class RetrievalObs {

    private RetrievalObs() {}

    /**
     * 检索 span 的输出——
     * 多组命中汇总（total/vector/keyword + by_retriever 逐组计数）+ 前 25 条命中预览。
     *
     * <p>空输入返回全零（{@code by_retriever} 空数组、{@code top_hits} 为 null——
     * 空结果序列化为 null 的语义）。</p>
     */
    public static Map<String, Object> summarizeRetrieveOutput(
            List<EngineTypes.RetrieveResult> results) {
        int groupCount = results == null ? 0 : results.size();
        int totalHits = 0;
        int vectorHits = 0;
        int keywordHits = 0;
        List<Map<String, Object>> byRetriever = new ArrayList<>();
        List<EngineTypes.IndexWithScore> all = new ArrayList<>();
        if (results != null) {
            for (EngineTypes.RetrieveResult rr : results) {
                if (rr == null) {
                    continue;
                }
                int count = rr.results() == null ? 0 : rr.results().size();
                totalHits += count;
                if ("vector".equals(rr.retrieverType())) {
                    vectorHits += count;
                } else {
                    keywordHits += count;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("engine", rr.retrieverEngineType());
                row.put("retriever", rr.retrieverType());
                row.put("count", count);
                byRetriever.add(row);
                if (rr.results() != null) {
                    all.addAll(rr.results());
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total_hits", totalHits);
        out.put("vector_hits", vectorHits);
        out.put("keyword_hits", keywordHits);
        out.put("group_count", groupCount);
        out.put("by_retriever", byRetriever);
        out.put("top_hits", summarizeIndexHits(all, RetrievalObsRows.DEFAULT_HIT_PREVIEW_LIMIT));
        return out;
    }

    /** 分数降序、chunk_id 决序，截前 limit 条（空 → null）。 */
    public static List<Map<String, Object>> summarizeIndexHits(
            List<EngineTypes.IndexWithScore> hits, int limit) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        List<EngineTypes.IndexWithScore> sorted =
                new ArrayList<>(hits);
        sorted.sort((a, b) -> {
            if (a.score != b.score) {
                return Double.compare(b.score, a.score);
            }
            return a.chunkId.compareTo(b.chunkId);
        });
        int n = Math.min(limit, sorted.size());
        List<Map<String, Object>> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            EngineTypes.IndexWithScore hit = sorted.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", i + 1);
            row.put("chunk_id", hit.chunkId);
            row.put("knowledge_id", hit.knowledgeId);
            row.put("knowledge_base_id", hit.knowledgeBaseId);
            row.put("score", RetrievalObsRows.formatScore4(hit.score));
            row.put("match_type", hit.matchType);
            row.put("preview", RetrievalObsRows.truncateRunes(hit.content, 160));
            out.add(row);
        }
        return out;
    }
}
