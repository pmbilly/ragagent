package com.ragagent.session.rag;

import com.ragagent.contract.graph.GraphData;
import com.ragagent.contract.retrieval.SearchResult;
import java.util.List;

/**
 * 状态段 · 检索产出：向量/关键词检索结果与实体图结果。
 *
 * <p>写入口是 {@code CHUNK_SEARCH} / {@code ENTITY_SEARCH}（含并发分支的合并）；
 * 重排、合并与生成段只读。并发分支各自持有独立实例，父轮次只接收合并后的结果。</p>
 */
public final class SearchOutcome {

    private List<SearchResult> results;
    private GraphData graph;

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> v) {
        results = v;
    }

    public GraphData getGraph() {
        return graph;
    }

    public void setGraph(GraphData v) {
        graph = v;
    }
}
