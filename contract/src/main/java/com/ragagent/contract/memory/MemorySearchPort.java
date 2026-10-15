package com.ragagent.contract.memory;

/**
 * 记忆查找端口（L1）：agent 的 search_memory 工具取数面。
 *
 * <p><b>为什么与 {@link MemoryRecallPort} 分开</b>：召回为某一轮产出提示词信封，
 * 查找为工具产出条目且必须能区分"被关掉"与"没存过"（两个载荷类型分立的原因
 * 见域内 {@code MemorySearchResult} 的说明）。</p>
 */
public interface MemorySearchPort {

    /** 按查询查找记忆条目；返回 {@link MemorySearchHits#UNAVAILABLE} 口径与域实现一致。 */
    MemorySearchHits search(String query, int limit);
}
