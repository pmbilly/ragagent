package com.ragagent.agenttools.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.agenttools.knowledge.KnowledgeSearchTool.ResultWithMeta;
import com.ragagent.agenttools.knowledge.KnowledgeSearchTool.SearchResultView;

/**
 * 去重语义：多键（id / parent / kb#index）+ 内容签名，且<b>同 ID 保留最高分</b>。
 *
 * <p>反例（旧实现的确定缺口，方案 A6）：同 id=x 输入 [(0.1), (0.9)] —— 第一遍
 * seen 在 0.1 时登记 x，0.9 被 continue 丢弃；第二遍"留最高分"永远看不到被丢弃的
 * 高分项 ⇒ 终值仍是 0.1。修复 = 同 ID 保最高分移到多键去重之前。</p>
 */
class KnowledgeSearchRankingDedupTest {

    @Test
    @DisplayName("同 ID 保最高分：低分先到也不能把高分挤掉")
    void sameIdKeepsHighestScore() {
        List<ResultWithMeta> out = KnowledgeSearchRanking.deduplicateResults(
                List.of(item("x", 0.1, "", "", -1, "low"),
                        item("x", 0.9, "", "", -1, "high")));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).sr.score).isEqualTo(0.9);
    }

    @Test
    @DisplayName("同 ID 保最高分：高分先到时同样只留一条")
    void sameIdKeepsHighestScoreHighFirst() {
        List<ResultWithMeta> out = KnowledgeSearchRanking.deduplicateResults(
                List.of(item("x", 0.9, "", "", -1, "high"),
                        item("x", 0.1, "", "", -1, "low")));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).sr.score).isEqualTo(0.9);
    }

    @Test
    @DisplayName("同 ID 同分：保留先到者（确定 tie-break）")
    void sameIdTieKeepsFirst() {
        List<ResultWithMeta> out = KnowledgeSearchRanking.deduplicateResults(
                List.of(item("x", 0.5, "", "", -1, "first"),
                        item("x", 0.5, "", "", -1, "second")));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).sr.content).isEqualTo("first");
    }

    @Test
    @DisplayName("同 ID 替换不改变输出位置：最高分版本占用该 ID 首次出现的位置")
    void sameIdReplacementKeepsFirstSeenPosition() {
        List<ResultWithMeta> out = KnowledgeSearchRanking.deduplicateResults(
                List.of(item("x", 0.1, "", "", -1, "low"),
                        item("y", 0.5, "", "", -1, "mid"),
                        item("x", 0.9, "", "", -1, "high")));
        assertThat(out).extracting(r -> r.sr.id).containsExactly("x", "y");
        assertThat(out.get(0).sr.score).isEqualTo(0.9);
    }

    @Test
    @DisplayName("多键去重语义不变：共享 parent / kb#index / 内容签名时保留先到者")
    void otherKeysStillKeepFirst() {
        List<ResultWithMeta> byParent = KnowledgeSearchRanking.deduplicateResults(
                List.of(item("a", 0.2, "p", "", -1, "alpha"),
                        item("b", 0.9, "p", "", -1, "beta")));
        assertThat(byParent).extracting(r -> r.sr.id).containsExactly("a");

        List<ResultWithMeta> byKbIndex = KnowledgeSearchRanking.deduplicateResults(
                List.of(item("a", 0.2, "", "k", 3, "alpha"),
                        item("b", 0.9, "", "k", 3, "beta")));
        assertThat(byKbIndex).extracting(r -> r.sr.id).containsExactly("a");

        List<ResultWithMeta> bySignature = KnowledgeSearchRanking.deduplicateResults(
                List.of(item("a", 0.2, "", "", -1, "Same   Content"),
                        item("b", 0.9, "", "", -1, "same content")));
        assertThat(bySignature).extracting(r -> r.sr.id).containsExactly("a");
    }

    private static ResultWithMeta item(String id, double score, String parentChunkId,
            String knowledgeId, int chunkIndex, String content) {
        SearchResultView sr = new SearchResultView();
        sr.id = id;
        sr.score = score;
        sr.parentChunkId = parentChunkId;
        sr.knowledgeId = knowledgeId;
        sr.chunkIndex = chunkIndex;
        sr.content = content;
        return new ResultWithMeta(sr, "q", "hybrid", "text");
    }
}
