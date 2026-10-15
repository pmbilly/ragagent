package com.ragagent.common.memory;

/**
 * 记忆抽取触发端口（L1）：一轮落库后异步抽取记忆的触发面。
 *
 * <p><b>为什么是端口</b>：调用方（QA 轮收尾、知识库 QA 入口）只负责"这轮结束了、
 * 值得抽一把"，不持有抽取队列与去重状态。</p>
 */
public interface MemoryExtractionPort {

    /** 为该轮消息调度一次记忆抽取（幂等；队列与失败重试在域内）。 */
    void scheduleExtraction(String sessionId, String messageId, String chatModelId);
}
