package com.ragagent.session.dto;


/**
 * 知识引用数据。
 *
 * <p>{@code references} 恒输出——null 输出 {@code "references":null}。
 * 元素以不透明 JSON 承载（运行时是 {@code contract.retrieval.SearchResult} 的列表）。</p>
 */

public class AgentReferencesData {

    /** null 也输出 null */
    private Object references;

    private int iteration;

    public AgentReferencesData() {
    }

    public AgentReferencesData(Object references, int iteration) {
        this.references = references;
        this.iteration = iteration;
    }

    public Object getReferences() {
        return references;
    }

    public void setReferences(Object v) {
        this.references = v;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int v) {
        this.iteration = v;
    }
}
