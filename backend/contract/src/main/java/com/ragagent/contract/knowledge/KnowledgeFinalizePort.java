package com.ragagent.contract.knowledge;

import java.time.OffsetDateTime;

/**
 * 知识库 · 入库收尾计数端口（wiki 子任务释放槽位）。
 *
 * <p><b>为什么是端口</b>：finalizing 计数归 knowledge 所有，而 wiki 的批次 worker 在 ingest
 * op 到达终态（映射成功或进死信）时必须递减该计数并尝试晋升终态。此前 wiki 直接注入
 * knowledge 域的具体适配器，构成 {@code wiki → knowledge} 的实现类直连。</p>
 *
 * <p>实现必须使用<b>脱钩的执行路径</b>：调用方可能是正在关闭的批次 worker，或上下文已被取消。</p>
 */
public interface KnowledgeFinalizePort {

    /**
     * 递减该文档的 finalizing 槽，并无条件尝试晋升终态。
     *
     * <p>对已完成或计数已为 0 的行调用是安全的 no-op（两个条件都带 WHERE 守卫）。</p>
     *
     * @param knowledgeId 知识文档 id
     * @param now         本次调用的 UTC 时间（由调用方传入，便于测试与重放）
     */
    FinalizeResult finalizeSubtask(String knowledgeId, OffsetDateTime now);
}
