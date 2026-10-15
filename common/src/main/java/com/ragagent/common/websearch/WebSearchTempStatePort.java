package com.ragagent.common.websearch;

/**
 * 联网搜索临时库状态清理端口（L1）：会话删除时的伴生清理面。
 *
 * <p><b>为什么是端口</b>：会话域删除会话时要顺带清掉该会话的临时搜索状态
 * （存于 websearch 域的 Redis 结构），但不持有其服务与状态键约定。</p>
 */
public interface WebSearchTempStatePort {

    /**
     * 清理该会话的临时搜索状态；无状态时静默返回。
     * 仅最后的删除失败才上抛（文案与域实现一致），调用方吞掉并 warn。
     */
    void deleteTempKbState(String sessionId);
}
