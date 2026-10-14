package com.ragagent.contract.tool;

/**
 * 审批闸门的装配面：把"策略查询源 + 超时秒"装成一个 {@link ToolApprovalGate}。
 *
 * <p>调用方（mcp 接线）只提供策略源与超时，Redis 广播等实现细节由实现方内部取
 * ——装配层不接触 approval 实现类。</p>
 */
public interface ToolApprovalGateFactory {

    /**
     * 装配闸门。
     *
     * @param checker        策略查询源；null 表示关闭审批门（恒放行）
     * @param timeoutSeconds 审批等待秒数；null 或 ≤0 用默认值（10 分钟）
     */
    ToolApprovalGate create(ToolPolicyChecker checker, Integer timeoutSeconds);
}
