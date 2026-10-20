package com.ragagent.session.rag;

import com.ragagent.contract.retrieval.SearchResult;
import java.util.List;

/**
 * 状态段 · 重排结果：由 {@code CHUNK_RERANK} 写入，下游（WebFetch / Merge）只读。
 *
 * <p>单独成组后，重排段的写入点只有一个插件，读插件拿不到其它状态字段。</p>
 */
public final class RerankOutcome {

    private List<SearchResult> results;

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> v) {
        results = v;
    }
}
