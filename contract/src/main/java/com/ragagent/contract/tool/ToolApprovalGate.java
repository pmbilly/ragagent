package com.ragagent.contract.tool;

import java.util.List;
import java.util.Map;

/**
 * 工具审批闸门（§8.1 `contract.tool`）：工具调用前的审批判定、等待与解析。
 *
 * <p><b>为什么是契约</b>：闸门实例此前以 approval 域的实现类 {@code Gate} 在各模块间传递
 * （session 装配直接注入 {@code Gate} 并把它送进 agent 引擎 ✗；mcp 侧以 {@code McpApproval} 这一
 * 域内接口承接 ✗）。本接口把"工具审批"提为契约面 SPI，实现唯一（{@code approval.Gate}），
 * 消费方（session 装配 / mcp 工具包装 / embed 渠道的审批解析）只依赖本接口。</p>
 *
 * <p><b>命名说明</b>：§8.1 在该面列的是 {@code ToolApproval}；本接口取更明确的名字
 * {@code ToolApprovalGate}（"审批闸门"，与实现的类名 {@code Gate} 对应），语义不变。</p>
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
