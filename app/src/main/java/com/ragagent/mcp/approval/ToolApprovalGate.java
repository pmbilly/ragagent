package com.ragagent.mcp.approval;

import java.util.List;
import java.util.Map;

/**
 * 工具审批闸门：工具调用前的审批判定、等待与解析。
 *
 * <p>mcp 域内接口，实现唯一（{@code Gate}）。消费方（session 装配 / mcp 工具包装 /
 * embed 渠道的审批解析）经装配层注入本接口，不直接依赖实现类。</p>
 */
public interface ToolApprovalGate {

    /** 该工具调用是否需要审批（策略查询错误已在实现内按 fail-close / fail-open 吞掉）。 */
    boolean needsApproval(Cancellation ctx, long tenantId, String serviceId, String toolName);

    /** 该工具当前是否启用；查询失败时抛运行时异常（与 {@code #needsApproval} 的吞错口径不同）。 */
    boolean isEnabled(Cancellation ctx, long tenantId, String serviceId, String toolName);

    /** 批量查启用态：key ＝ 工具名，value ＝ 是否启用。 */
    Map<String, Boolean> enabledTools(Cancellation ctx, long tenantId, String serviceId, List<String> names);

    /** 发起审批并等待用户决定（含超时/取消，结果都在 {@link Decision} 里）。 */
    Decision requestAndWait(Cancellation ctx, PendingRequest req);

    /** 发起 MCP OAuth 授权并等待结果。 */
    Decision requestOAuthAndWait(Cancellation ctx, OAuthPendingRequest req);

    /** 解析一个待决请求（用户在 UI 上点允许/拒绝）。 */
    void resolve(long tenantId, String userId, String pendingId, Decision decision);
}
