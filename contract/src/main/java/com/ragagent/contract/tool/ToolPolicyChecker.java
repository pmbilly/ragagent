package com.ragagent.contract.tool;

/**
 * 逐工具的策略查询口（闸门的数据源）：注册与执行两个路径都会问它
 * "这个工具需要人工批准吗 / 还启用着吗"。
 *
 * <p>实现方通常由 mcp 侧的策略适配器承接；查询失败用异常表达
 * （{@link ToolApprovalGate#needsApproval} 按 fail-close / fail-open 处理，
 * {@link ToolApprovalGate#isEnabled} 直接向上抛）。</p>
 */
public interface ToolPolicyChecker extends ToolEnabledChecker {

    boolean isRequired(Cancellation ctx, long tenantId, String serviceId, String toolName);
}
