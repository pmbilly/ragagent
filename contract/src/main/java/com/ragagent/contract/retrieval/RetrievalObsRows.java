package com.ragagent.contract.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索观测行的纯函数族（契约面）：码点截断、命中预览、分数汇总、段落预览。
 *
 * <p>R3b 拆类：原 {@code retrieval-engine} 的 {@code telemetry.RetrievalObs} 把「纯计算结果行」
 * 与「引擎内部形状（{@code EngineTypes.*}）的汇总」混在一个类里——业务域只需要前者
 * （如 chunk 预览），后者留实现模块（{@code RetrievalObs}）。拆开后域侧不再穿透实现模块，
 * 纯函数族也回到无依赖的契约面。</p>
 *
 * <p>产出只进 span/trace（Java 侧恒 no-op），行为契约由既有测试钉住。</p>
 */
public final class RetrievalObsRows {

    /** 命中预览缺省上限。 */
    public static final int DEFAULT_HIT_PREVIEW_LIMIT = 25;

    private RetrievalObsRows() {}

    /** 截到 maxRunes 个码点，截断时追加 "..."。 */
    public static String truncateRunes(String s, int maxRunes) {
        if (maxRunes <= 0) {
            return "";
        }
        if (s == null) {
            return "";
        }
        int len = s.codePointCount(0, s.length());
        if (len <= maxRunes) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxRunes)) + "...";
    }

    /**
     * 重排后的紧凑预览（分数降序，ID 决序；
     * metadata 里的 base_score/model_score/faq_* 透出）。
     */
    public static Map<String, Object> summarizeSearchResults(List<SearchResult> results, int limit) {
        int effective = limit <= 0 ? DEFAULT_HIT_PREVIEW_LIMIT : limit;
        Map<String, Object> out = new LinkedHashMap<>();
        int count = results == null ? 0 : results.size();
        out.put("count", count);
        out.put("top_hits", new ArrayList<Map<String, Object>>());
        if (count == 0) {
            return out;
        }

        List<SearchResult> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> {
            if (a.getScore() != b.getScore()) {
                return Double.compare(b.getScore(), a.getScore());
            }
            return a.getId().compareTo(b.getId());
        });

        List<Map<String, Object>> hits = new ArrayList<>(Math.min(effective, sorted.size()));
        for (int i = 0; i < sorted.size() && i < effective; i++) {
            SearchResult sr = sorted.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", i + 1);
            item.put("chunk_id", sr.getId());
            item.put("knowledge_id", sr.getKnowledgeId());
            item.put("knowledge_title", sr.getKnowledgeTitle());
            item.put("composite_score", formatScore4(sr.getScore()));
            item.put("match_type", sr.getMatchType());
            item.put("chunk_type", sr.getChunkType());
            item.put("preview", truncateRunes(sr.getContent(), 160));
            if (sr.getMetadata() != null) {
                String base = sr.getMetadata().get("base_score");
                if (base != null) {
                    item.put("retrieval_score", base);
                }
                String model = sr.getMetadata().get("model_score");
                if (model != null) {
                    item.put("model_score", model);
                }
                String boosted = sr.getMetadata().get("faq_boosted");
                if (boosted != null) {
                    item.put("faq_boosted", boosted);
                }
                String orig = sr.getMetadata().get("faq_original_score");
                if (orig != null) {
                    item.put("faq_original_score", orig);
                }
            }
            hits.add(item);
        }
        out.put("top_hits", hits);
        if (sorted.size() > effective) {
            out.put("truncated", sorted.size() - effective);
        }
        return out;
    }

    /** 分数汇总：超过 limit 时截前 limit 行。 */
    public static List<Map<String, Object>> summarizeRankScores(List<Map<String, Object>> results, int limit) {
        int effective = limit <= 0 ? DEFAULT_HIT_PREVIEW_LIMIT : limit;
        if (results == null || results.size() <= effective) {
            return results;
        }
        return new ArrayList<>(results.subList(0, effective));
    }

    /** 与候选对齐的段落预览行。 */
    public static List<Map<String, Object>> summarizePassagePreviews(
            List<SearchResult> candidates, List<String> passages, int limit) {
        int effective = limit <= 0 ? DEFAULT_HIT_PREVIEW_LIMIT : limit;
        int n = candidates == null ? 0 : candidates.size();
        if (passages != null && passages.size() < n) {
            n = passages.size();
        }
        if (effective < n) {
            n = effective;
        }
        List<Map<String, Object>> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            SearchResult sr = candidates.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i);
            row.put("chunk_id", sr.getId());
            row.put("knowledge_id", sr.getKnowledgeId());
            row.put("knowledge_title", sr.getKnowledgeTitle());
            row.put("retrieval_score", formatScore4(sr.getScore()));
            row.put("match_type", sr.getMatchType());
            row.put("preview", truncateRunes(passages.get(i), 160));
            out.add(row);
        }
        return out;
    }

    /** 四舍五入到 4 位小数（toFixed 语义）。 */
    public static String formatScore4(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
