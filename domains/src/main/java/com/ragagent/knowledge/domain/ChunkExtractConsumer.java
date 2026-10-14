package com.ragagent.knowledge.domain;

/**
 * 分块抽取对"JSON 载荷消费者"的端口（B263㉛ 依赖反转）。
 *
 * <p><b>为什么要有它</b>：`RedisChunkExtractTaskQueue` / `InProcessChunkExtractTaskQueue` 与装配类 `KnowledgeRedisWiring` 原先**直接持有** {@code service.ChunkExtractService} ⇒ 形成
 * {@code task → service} 边，与既有的 {@code service → task}（{@code KnowledgeProcessingQueue} 等）合成
 * 一对**包级环**。反转后队列只认本端口，{@code ChunkExtractService} {@code implements} 之 ⇒ 方向固定为
 * {@code service → task}（单向），环随之消解；方法签名与原调用 {@code service.handleJson(body, finalAttempt)}
 * 逐字对齐，**实现方无需改动方法体**。</p>
 *
 * <p><b>为什么在 {@code domain} 而非 {@code task}</b>：首版端口放在 {@code task}，两个 service 因
 * {@code implements} 立刻被 A16（service 不得依赖边界层 task）判为新增违例 ✗——端口必须落在**两侧都
 * 能合法依赖**的中立面，{@code domain} 正是（payload 类型本就在此）。</p>
 *
 * <p><b>为什么拆成两个端口</b>：首版用**一个**共享端口，两个 service 同时实现它 ⇒ Spring 按类型注入
 * 直接报**歧义**（boot 的 1211 个测试全红 ✗）。每个队列家族一个端口 ⇒ 每个端口恰好一个实现者。</p>
 */
public interface ChunkExtractConsumer {

    /** 消费一条分块抽取载荷；{@code terminal} = 本次已是最后一次尝试（不再重试）。 */
    void handleJson(String payloadJson, boolean terminal);
}
