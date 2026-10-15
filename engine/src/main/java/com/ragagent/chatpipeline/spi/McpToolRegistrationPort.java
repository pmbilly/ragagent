package com.ragagent.chatpipeline.spi;

import java.util.List;

/**
 * MCP 按需目录注册端口（引擎侧接口）：从租户的启用 MCP 服务注册受限目录
 * （discover_mcp_tools / call_mcp_tool），不连上游、不广告完整 schema；
 * 具体工具定义在模型调用 discover 时按需列举。
 *
 * <p><b>为什么在 engine</b>：签名里的 {@link ToolRegistryPort} / {@link McpToolingGateway}
 * 是引擎 SPI，L1（{@code common}）看不到它们；消费方（会话域的引擎装配）需要的是
 * "按 agent 配置注册目录"，不是 MCP 域的服务清单/元数据/客户端管理器。</p>
 *
 * <p><b>实现</b>：MCP 域适配器持有那三件域内设施，注册编排（列表 → 启用过滤 →
 * 按需暴露）在域内完成；{@code hasToolExecContext=false}（装配发生在引擎准备阶段，
 * 没有 per-turn 的 ToolExecContext）；失败只记警告，不影响引擎创建。
 * 装配按类型注入。</p>
 */
public interface McpToolRegistrationPort {

    /** 为工具注册表造 MCP 编排件（目录逻辑在 MCP 域的 {@code McpTooling}）。 */
    McpToolingGateway createTooling(ToolRegistryPort registry);

    /**
     * 按选择模式（{@code all} / {@code selected}）从本租户的启用服务注册按需目录。
     * {@code selectedServiceIds} 仅 selected 模式使用（空清单由调用方先裁）；租户缺失 /
     * 列表失败 / 注册失败只记日志不抛。审批门（工具执行与 OAuth 等待）由实现自行持有。
     */
    void registerOnDemandTools(long tenantId, String selectionMode,
            List<String> selectedServiceIds, ToolRegistryPort registry,
            McpToolingGateway tooling, int authWaitTimeoutSeconds);
}
