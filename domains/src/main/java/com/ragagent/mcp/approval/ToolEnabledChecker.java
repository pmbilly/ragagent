package com.ragagent.mcp.approval;

/**
 * 单工具启用态查询面（策略源的最小契约）。
 *
 * <p>目录批量校验（{@link ToolEnabledChecks}）只要求这一个方法；
 * 查询失败用运行时异常表达，{@link ToolApprovalGate#isEnabled} 直接向上抛。</p>
 */
public interface ToolEnabledChecker {

    boolean isEnabled(Cancellation ctx, long tenantId, String serviceId, String toolName);
}
