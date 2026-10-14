package com.ragagent.mcp.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.common.mcp.McpApprovalOps;
import com.ragagent.mcp.controller.AgentToolApprovalController;
import com.ragagent.mcp.controller.McpOAuthController;
import com.ragagent.mcp.dto.ResolveToolApprovalRequest;
import com.ragagent.platform.web.JsonMappers;
import com.ragagent.contract.error.BizException;

/**
 * {@link McpApprovalOps} 的 mcp 侧实现：把 embed 渠道传来的原始 body 解析成既有 DTO，再调既有 controller。
 *
 * <p><b>零行为变化</b>：请求体解析（含解析失败的 {@code BizException.badRequest} 语义）由 embedchannel
 * 移到本类，其余（租户/主体校验、闸门调用、错误映射）仍走原 controller 路径 ✗⇒ 不新开旁路；
 * 返回值与 controller 的响应体一致（恒 200 + body）。</p>
 */
@Component
public class McpApprovalOpsAdapter implements McpApprovalOps {

    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    private final McpOAuthController oauth;
    private final AgentToolApprovalController toolApproval;

    public McpApprovalOpsAdapter(McpOAuthController oauth, AgentToolApprovalController toolApproval) {
        this.oauth = oauth;
        this.toolApproval = toolApproval;
    }

    @Override
    public Map<String, Object> authorizeUrl(String serviceId, String rawBodyJson) {
        McpOAuthController.AuthorizeRequest req =
                parse(rawBodyJson, McpOAuthController.AuthorizeRequest.class);
        return oauth.authorizeUrl(serviceId, req).getBody();
    }

    @Override
    public Object authorizationStatus(String serviceId, String attemptId) {
        ResponseEntity<?> res = oauth.status(serviceId, attemptId);
        return res == null ? null : res.getBody();
    }

    @Override
    public void resolveAuthorization(String pendingId, String rawBodyJson) {
        oauth.resolveMcpOAuth(pendingId, parse(rawBodyJson, McpOAuthController.ResolveRequest.class));
    }

    @Override
    public void cancelAuthorization(String pendingId) {
        oauth.cancelMcpOAuth(pendingId);
    }

    @Override
    public void resolveToolApproval(String pendingId, String rawBodyJson) {
        toolApproval.resolveToolApproval(pendingId, parse(rawBodyJson, ResolveToolApprovalRequest.class));
    }

    /** 与迁移前 embedchannel 内的解析口径一致：空 body ⇒ null；解析失败 ⇒ 400 且带原始原因。 */
    private static <T> T parse(String rawBodyJson, Class<T> type) {
        if (rawBodyJson == null || rawBodyJson.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(rawBodyJson, type);
        } catch (Exception e) {
            throw BizException.badRequest(e.getMessage() == null ? "" : e.getMessage());
        }
    }
}
