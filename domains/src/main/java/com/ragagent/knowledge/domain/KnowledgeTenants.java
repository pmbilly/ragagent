package com.ragagent.knowledge.domain;

import com.ragagent.common.context.TenantContext;

/**
 * 知识域的租户取值助手：调用线程无租户时退化为 {@code 0}（与原 {@code service.KnowledgeService#tenantId}
 * 逐字同义）。
 *
 * <p><b>为什么单列</b>（B263㉝）：该方法原先只是 {@code KnowledgeService} 的**包内可见静态**，
 * 却被 17 处子服务当作工具复用 ⇒ 任何"把 {@code KnowledgeService} 挪到子面"的尝试都会立刻产生
 * {@code service → service.management} 反向边（B263① 实测的 SCC 扩大）。上移到中立面后，
 * 这些调用点不再依赖门面。</p>
 */
public final class KnowledgeTenants {

    private KnowledgeTenants() {}

    /** 当前租户 id；无租户上下文时为 {@code 0}。 */
    public static long tenantId() {
        Long tid = TenantContext.currentTenantId();
        return tid == null ? 0 : tid;
    }
}
