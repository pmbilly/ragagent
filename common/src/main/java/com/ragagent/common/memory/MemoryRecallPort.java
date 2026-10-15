package com.ragagent.common.memory;

import com.ragagent.contract.memory.MemoryRecall;

/**
 * 记忆召回读取端口（L1）：会话侧 QA 的"记忆开着吗 + 召回一轮"最小读面。
 *
 * <p><b>为什么是端口</b>：调用方只判断可用性并把查询变提示词信封，
 * 不持有记忆域的服务与租户配置判定；实现（三层开关判定、向量召回）在 memory 域。
 * 召回载荷 {@link MemoryRecall} 本就是 contract 类型，端口直接搬运。</p>
 */
public interface MemoryRecallPort {

    /** 长期记忆对该请求是否可用（工作区/用户/当前 agent 三层任一可关）。 */
    boolean memoryAvailable();

    /** 按查询召回一轮记忆信封；无匹配 ⇒ 空信封口径与域实现一致。 */
    MemoryRecall recall(String query);
}
