package com.ragagent.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.retrieval.HybridSearchService.RetrievalConfigView;
import com.ragagent.retrieval.engine.EngineTypes.IndexWithScore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * K9：RRF 的 rank 必须是**店内名次**——各 store 各自保序。
 *
 * <p>缺陷现场：`HybridSearchService` 把同类型的多个 store 命中先 `addAll` 成一个大列表，
 * RRF 只能按"拼接位置"算 rank ⇒ 后一组（第二个 KB / 跨租户组 / 另一个引擎）的本地第一名
 * 被系统性降级，且结果随 store 顺序漂移（交换顺序结果就变）。</p>
 */
class HybridFusionOpsStoreRankTest {

    /** 权重 1:1 便于构造同分（RRF 只决定名次，权重对齐后才可比）。 */
    private static final RetrievalConfigView RC = new RetrievalConfigView(60, 1.0, 1.0);

    @Test
    @DisplayName("每个 store 的本地第一名等权：第二组的 #1 不被拼接位置降级")
    void secondStoreTopHitKeepsRankOne() {
        List<IndexWithScore> storeA = List.of(hit("a-1", 1.0), hit("a-2", 0.9));
        List<IndexWithScore> storeB = List.of(hit("b-1", 1.0), hit("b-2", 0.9));
        List<IndexWithScore> keyword = List.of(hit("k-1", 1.0));

        List<IndexWithScore> fused = HybridFusionOps.fuseOrDeduplicate(
                List.of(storeA, storeB), List.of(keyword), RC);

        assertThat(scoreOf(fused, "b-1"))
                .as("b 组是第二个 store，它的本地第一名应拿到 rank=1 的同等贡献")
                .isEqualTo(scoreOf(fused, "a-1"));
        assertThat(scoreOf(fused, "a-1"))
                .as("同店内的名次仍要有区分")
                .isGreaterThan(scoreOf(fused, "a-2"));
    }

    @Test
    @DisplayName("交换 store 顺序结果稳定（含同分定序）")
    void storeOrderDoesNotChangeResult() {
        List<IndexWithScore> storeA = List.of(hit("a-1", 1.0), hit("a-2", 0.9));
        List<IndexWithScore> storeB = List.of(hit("b-1", 1.0), hit("b-2", 0.9));
        List<IndexWithScore> keyword = List.of(hit("k-1", 1.0));

        List<String> forward = ids(HybridFusionOps.fuseOrDeduplicate(
                List.of(storeA, storeB), List.of(keyword), RC));
        List<String> backward = ids(HybridFusionOps.fuseOrDeduplicate(
                List.of(storeB, storeA), List.of(keyword), RC));

        assertThat(backward).as("换 store 顺序不得改变输出顺序").isEqualTo(forward);
    }

    @Test
    @DisplayName("同分按 chunkId 定序：不靠插入顺序（交换 store 也稳）")
    void tiesBreakByChunkIdNotInsertionOrder() {
        // z-vec：向量店 rank1；a-kw：关键词店 rank1 ⇒ 同分（权重 1:1、k 相同）
        List<IndexWithScore> vector = List.of(hit("z-vec", 1.0));
        List<IndexWithScore> keyword = List.of(hit("a-kw", 1.0));

        List<String> ids = ids(HybridFusionOps.fuseOrDeduplicate(
                List.of(vector), List.of(keyword), RC));

        assertThat(ids).as("同分按 chunkId 升序，插入顺序（向量在前）不得决定输出")
                .containsExactly("a-kw", "z-vec");
    }

    @Test
    @DisplayName("单一来源（无关键词店）保持按分降序与来源顺序——既有语义不变")
    void singleSourceKeepsScoreOrder() {
        List<IndexWithScore> storeA = List.of(hit("a-1", 1.0), hit("a-2", 0.9));
        List<IndexWithScore> storeB = List.of(hit("b-1", 0.95));

        List<String> ids = ids(HybridFusionOps.fuseOrDeduplicate(
                List.of(storeA, storeB), List.of(), RC));

        assertThat(ids).containsExactly("a-1", "b-1", "a-2");
    }

    private static IndexWithScore hit(String chunkId, double score) {
        IndexWithScore h = new IndexWithScore();
        h.chunkId = chunkId;
        h.id = chunkId;
        h.knowledgeId = "doc-" + chunkId;
        h.score = score;
        return h;
    }

    private static double scoreOf(List<IndexWithScore> hits, String chunkId) {
        return hits.stream().filter(h -> chunkId.equals(h.chunkId)).findFirst()
                .orElseThrow(() -> new AssertionError("缺少命中: " + chunkId)).score;
    }

    private static List<String> ids(List<IndexWithScore> hits) {
        List<String> out = new ArrayList<>(hits.size());
        for (IndexWithScore h : hits) {
            out.add(h.chunkId);
        }
        return out;
    }
}
