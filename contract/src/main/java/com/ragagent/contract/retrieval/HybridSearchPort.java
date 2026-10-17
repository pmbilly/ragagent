package com.ragagent.contract.retrieval;

import java.util.List;
import java.util.Map;

/**
 * 混合检索读面端口（业务域 → 检索引擎）。
 *
 * <p> 契约化：业务域（知识控制器/FAQ 查询/会话 QA/agent 工具）原先直注
 * {@code retrieval-engine} 的 {@code HybridSearchService} 实现类；本端口只收域侧真调用的
 * 三个方法，载体全部是契约面既有形状（{@link SearchParams}/{@link SearchResult}）。
 *
 * <p>失败以运行时异常表达（实现侧原有语义：KB 不存在等）。</p>
 */
public interface HybridSearchPort {

    /** 单 KB 混合检索（向量 + BM25 + 融合）。{@code id} 为知识库 id。 */
    List<SearchResult> hybridSearch(String id, SearchParams params);

    /** 查询向量（按 KB 的嵌入模型配置计算；KB 不存在抛运行时异常）。 */
    float[] getQueryEmbedding(String kbId, String queryText);

    /** 批量解析 KB 的嵌入模型键（KB 不存在则跳过）。 */
    Map<String, String> resolveEmbeddingModelKeys(List<String> kbIds);
}
