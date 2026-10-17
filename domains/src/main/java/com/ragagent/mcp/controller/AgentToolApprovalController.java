package com.ragagent.mcp.controller;

import com.ragagent.mcp.dto.ResolveToolApprovalRequest;
import com.ragagent.mcp.service.AgentToolApprovalService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ragagent.platform.web.ApiResponse;
import com.ragagent.platform.web.ApiResult;

/**
 * 处理待审批的 MCP 工具调用。
 *
 * <p>路由注册在 {@code /agent} 组下（Viewer+，不开放给 API key——人工交互流程不做
 * API key 声明），故独立成一个控制器。用例本体（鉴权闸门前置、决议解析、错误映射）
 * 在 {@link AgentToolApprovalService}——HTTP 面与 embed 委托面共用。</p>
 *
 * <p><b>为什么是 Viewer+ 而不是 Admin+</b>：审批卡片出现在调用者自己发起的 agent 会话里，
 * 收紧到 Admin+ 会把唯一有上下文做判断的人挡在门外；门禁保持"租户内任意成员"，
 * 真正的越权防线是 gate 内的 tenant/user 校验。</p>
 */
@RestController
@ApiResult
@RequestMapping("/api/v1/agent")
public class AgentToolApprovalController {

    private final AgentToolApprovalService service;

    public AgentToolApprovalController(AgentToolApprovalService service) {
        this.service = service;
    }

    /** 权限 Viewer+。 */
    @PostMapping("/tool-approvals/{pendingId}")
    public ApiResponse<Void> resolveToolApproval(@PathVariable("pendingId") String pendingId,
                                                    @RequestBody(required = false) ResolveToolApprovalRequest body) {
        service.resolveToolApproval(pendingId, body);
        // 无业务数据的受理回执（不再返回 {"success":true}）
        return ApiResponse.ok();   // 204 退役 ⇒ 200 + 外壳
    }
}
