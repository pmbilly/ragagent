package com.ragagent.knowledge.api;

import com.ragagent.knowledge.api.kb.KnowledgeBaseView;
import com.ragagent.shared.retrieval.SearchParams;
import com.ragagent.shared.retrieval.SearchResult;
import java.util.List;
import java.util.Map;

/** 知识库查询面：按 ID 直取、混合检索与向量化的最小子集。 */
public interface KnowledgeBaseQueryPort {

    /** 按 ID 直取（无租户过滤）。 */
    KnowledgeBaseView getKnowledgeBaseByIdUnscoped(String id);

    /** 批量直取（无租户过滤；缺失 ID 跳过）。 */
    List<KnowledgeBaseView> getKnowledgeBasesByIdsUnscoped(List<String> ids);

    List<SearchResult> hybridSearch(String knowledgeBaseId, SearchParams params);

    float[] getQueryEmbedding(String kbId, String queryText);

    /** KB ID → "模型名|endpoint"（解析失败的 KB 缺键）。 */
    Map<String, String> resolveEmbeddingModelKeys(List<String> kbIds);
}
