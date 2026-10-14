package com.ragagent.common.audit;

/**
 * 审计写入端口：供**适配面**（{@code agenttools}/{@code agentmanagement}）写审计日志，
 * 而不直连 {@code audit.service.*}（规则）。
 *
 * <p>实现留在 audit 侧（{@code api/AuditWriteAdapter}）——它把本端口的 JDK 记录映射成
 * {@code audit.domain.AuditLog} 再调 {@code AuditLogService#logBestEffort}
 * （**尽力而为**：写失败只记日志，不影响业务响应；语义与迁移前控制器直调逐字一致）。</p>
 */
public interface AuditWritePort {

    /**
     * 一条审计记录（与迁移前调用方**实际设置过**的字段一一对应；其余列由实体默认值补空串）。
     *
     * @param tenantId     租户 id（调用方无上下文时传 0）
     * @param actorUserId  操作者用户 id（可空串）
     * @param actorRole    操作者角色（可空串）
     * @param action       {@code audit.domain.AuditAction} 的 String 常量（点分命名空间）
     * @param targetType   目标类型（如 {@code "skill"}）
     * @param targetId     目标 id（可空串）
     * @param targetUserId 目标用户 id（RBAC 动作用，可空串）
     * @param outcome      {@code audit.domain.AuditOutcome} 的 String 常量
     * @param detailsJson  详情 JSON（null/空 = 无详情）
     */
    record AuditRecord(long tenantId, String actorUserId, String actorRole, String action,
            String targetType, String targetId, String targetUserId, String outcome,
            String detailsJson) {
    }

    /** 尽力而为写一条审计（实现内吞异常，与迁移前 {@code logBestEffort} 同语义）。 */
    void logBestEffort(AuditRecord record);
}
