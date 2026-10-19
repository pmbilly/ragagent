package com.ragagent.mcp;

import com.ragagent.contract.tool.Cancellation;
import com.ragagent.contract.tool.ToolApprovalGate;
import com.ragagent.contract.tool.ToolApprovalGateFactory;
import com.ragagent.contract.tool.ToolPolicyChecker;
import com.ragagent.mcp.protocol.McpClientManager;
import com.ragagent.mcp.protocol.McpOAuthSupport;
import com.ragagent.mcp.service.Adapter;
import com.ragagent.mcp.service.McpToolApprovalService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 模块的 bean 接线。
 *
 * 这里补上两个此前只有接口、没有实例的依赖：
 * - {@link McpClientManager}：MCP 连接池
 * - {@link ToolApprovalGate}：工具审批门
 *
 * 两者的消费方此前用 {@code Optional<...>} 注入，缺省时走"返回 500 / 业务失败"的兜底
 * 分支。这里提供实例后，那些分支不再触发。
 */
@Configuration
public class McpWiring {

    /**
     * MCP 连接池。OAuth 支持由 {@link com.ragagent.mcp.oauth.wiring.McpOAuthSupportImpl} 提供（协议层预留的注入点）。
     */
    @Bean(destroyMethod = "shutdown")
    public McpClientManager mcpClientManager(ObjectProvider<McpOAuthSupport> oauthSupport) {
        return new McpClientManager(oauthSupport.getIfAvailable());
    }

    /**
     * 工具审批门。
     *
     * <p>Checker 由 {@link McpToolApprovalService} 适配而来：用 {@link Adapter}
     * 包一层，保持"目录批量查询走单次查询"的优化路径。</p>
     *
     * <p>闸门实例由 approval 的装配工厂创建——Redis 广播等实现细节由工厂内部取，
     * 本模块不接触实现类。</p>
     */
    @Bean(destroyMethod = "close")
    public ToolApprovalGate toolApprovalGate(ToolApprovalGateFactory gateFactory,
                                 McpToolApprovalService toolApprovalService,
                                 @Value("${weknora.agent.tool-approval-timeout-seconds:0}") int timeoutSeconds) {
        ToolPolicyChecker checker = new Adapter(new ToolPolicyChecker() {
            @Override
            public boolean isRequired(Cancellation ctx, long tenantId, String serviceId, String toolName) {
                return toolApprovalService.isRequired(tenantId, serviceId, toolName);
            }

            @Override
            public boolean isEnabled(Cancellation ctx, long tenantId, String serviceId, String toolName) {
                return toolApprovalService.isEnabled(tenantId, serviceId, toolName);
            }
        });
        // timeoutSeconds<=0 → 闸门用默认值（10 分钟）
        return gateFactory.create(checker, timeoutSeconds > 0 ? timeoutSeconds : null);
    }
}
