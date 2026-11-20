package com.ragagent.session.rag;

import com.ragagent.contract.retrieval.SearchResult;
import java.util.List;

/**
 * 状态段 · 合并结果：由 {@code CHUNK_MERGE} 写入，{@code DATA_ANALYSIS} 读写
 * （在合并结果上补数据分析片段）。
 *
 * <p>合并段是检索链的出口：下游只读或追加，不再回看原始 search / rerank 结果。</p>
 */
public final class MergedContext {

    private List<SearchResult> results;

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> v) {
        results = v;
    }
}
