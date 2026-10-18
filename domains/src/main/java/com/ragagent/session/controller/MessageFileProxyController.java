package com.ragagent.session.controller;

import com.ragagent.contract.storage.fileserve.MessageFileFacts;
import com.ragagent.platform.storage.fileserve.FileProxyPort;
import com.ragagent.platform.storage.fileserve.FileServeHttp;
import com.ragagent.platform.web.ApiResult;
import com.ragagent.session.conversation.MessageService;
import com.ragagent.session.domain.message.Message;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息作用域的资源代理。
 *
 * <p>聊天渲染器加载 assistant 消息内嵌的资源用。消息服务先证明调用者拥有所在会话
 * （{@code MessageService.getMessage} 的 loadSessionForRead 可见性判定，含 Admin
 * 回退），持久化消息必须<b>逐字引用</b>该文件（content / artifacts[].url /
 * knowledge_references / images / agent_steps[].tool_calls[].result 五处持久化字段，
 * MessageReferencesFile 的整 token 匹配）。</p>
 *
 * <p>中间件链：APIKeyGate（chat+fullAccess 策略，见 APIKeyRoutePolicies）
 * → RBAC viewer 规则（RbacInterceptor）→ handler。</p>
 *
 * <p><b>已知收紧</b>：跨租户消息文件的 shared-agent / org-shared KB 两条授予路径
 * 未实现——owner ≠ caller 恒 403，方向偏保守。</p>
 *
 * <p><b>跨域口径</b>：直出与授权匹配经 L1 端口 {@link FileProxyPort}（消息事实由本类的
 * {@code lookup} 现取——存储侧不依赖会话实体）；响应小件经 {@link FileServeHttp}。</p>
 */
@RestController
@ApiResult
@Tag(name = "Session")
public class MessageFileProxyController {

    private final MessageService messageService;
    private final FileProxyPort fileProxy;

    public MessageFileProxyController(MessageService messageService, FileProxyPort fileProxy) {
        this.messageService = messageService;
        this.fileProxy = fileProxy;
    }

    /** 只有 GET 注册了真实处理：HEAD 落 NoRoute 响应（见文件代理面的类注释）。 */
    @RequestMapping(value = "/api/v1/sessions/{id}/messages/{messageId}/files",
            method = RequestMethod.HEAD)
    public void filesHead(@PathVariable("id") String id,
            @PathVariable("messageId") String messageId, HttpServletResponse response)
            throws IOException {
        FileServeHttp.writeGinNoRoute(response);
    }

    @GetMapping("/api/v1/sessions/{id}/messages/{messageId}/files")
    public void files(@PathVariable("id") String id,
            @PathVariable("messageId") String messageId, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        fileProxy.serveMessageFile(id, messageId, this::factsOf, request, response);
    }

    /**
     * 消息 → 授权事实：只取存储侧授权/匹配实际读取的字段
     * （见 {@link MessageFileFacts}）——避免存储依赖会话实体。
     */
    private MessageFileFacts factsOf(String sessionId, String messageId) {
        Message message = messageService.getMessage(sessionId, messageId);
        if (message == null) {
            return null;
        }
        List<String> artifactUrls = new ArrayList<>();
        if (message.getArtifacts() != null) {
            for (var artifact : message.getArtifacts()) {
                if (artifact != null) {
                    artifactUrls.add(artifact.getUrl());
                }
            }
        }
        List<Object> toolResults = new ArrayList<>();
        if (message.getAgentSteps() != null) {
            for (var step : message.getAgentSteps()) {
                if (step == null || step.getToolCalls() == null) {
                    continue;
                }
                for (var call : step.getToolCalls()) {
                    if (call != null && call.getResult() != null) {
                        toolResults.add(call.getResult());
                    }
                }
            }
        }
        return new MessageFileFacts(message.getContent(), artifactUrls,
                message.getKnowledgeReferences(), message.getImages(), toolResults,
                message.getAgentTenantId(), message.getRole());
    }
}
