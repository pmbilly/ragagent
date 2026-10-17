package com.ragagent.contract.pipeline;

import com.ragagent.contract.model.chat.ChatResponse;
import com.ragagent.contract.retrieval.SearchResult;
import java.util.List;

/**
 * RAG 预设跑完后的全链路产物（评估度量用）：检索命中 · 重排命中 · 生成响应。
 *
 * <p>三个都是契约面既有形状 ⇒ 评估侧无需认识管线运行时类型即可度量。</p>
 */
public record KnowledgeQaRunResult(List<SearchResult> searchResults,
                                   List<SearchResult> rerankResults,
                                   ChatResponse chatResponse) {
}
