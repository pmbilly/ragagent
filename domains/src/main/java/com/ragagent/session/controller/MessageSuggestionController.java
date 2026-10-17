package com.ragagent.session.controller;

import com.ragagent.platform.web.ApiResponse;
import com.ragagent.platform.web.ApiResult;
import com.ragagent.session.domain.message.MessageSuggestionSet;
import com.ragagent.session.service.suggestion.MessageSuggestionFacade;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 追问建议 HTTP 层。
 *
 * <h2>响应形态（裸对象，无 {"data","success"} 信封）</h2>
 * <ul>
 * <li>Ensure：裸 {@code MessageSuggestionSet}，集合进入 generating 态时是 <b>202</b>；</li>
 * <li>Get：同上恒 200；</li>
 * <li>RecordEvent：<b>200</b> + {@code ApiResponse} 外壳（无业务数据）。</li>
 * </ul>
 *
 * <p>用例本体（绑定、消毒、错误映射）在 {@link MessageSuggestionFacade} 的
 * 双面门面（{@code ensureFollowUpsBody}/{@code getFollowUpsSanitized}/{@code recordEventBody}）
 * ——HTTP 面与 embed 委托面共用；writeError 的子串映射顺序语义见该类。</p>
 */
@RestController
@ApiResult
@Tag(name = "Session")
public class MessageSuggestionController {

    private final MessageSuggestionFacade suggestionFacade;

    public MessageSuggestionController(MessageSuggestionFacade suggestionFacade) {
        this.suggestionFacade = suggestionFacade;
    }

    /**
     * ⚠️ 请求体<b>只在非空时解析</b>——空 body 合法
     * （regenerate=false），但畸形 JSON → 400 固定文案 "invalid request body"
     * （不是解析器原文，与其他端点不同）。
     */
    @PostMapping("/api/v1/sessions/{sessionId}/messages/{messageId}/suggestions")
    public ResponseEntity<MessageSuggestionSet> ensure(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("messageId") String messageId,
            @RequestBody(required = false) String rawBody) {
        MessageSuggestionSet set = suggestionFacade.ensureFollowUpsBody(sessionId, messageId, rawBody);
        return ResponseEntity.status(MessageSuggestionFacade.statusFor(set)).body(set);
    }

    /**
     * GET 同时注册在 :session_id 与 :id 两个通配下
     * （router 两种写法并存），Spring 用双 pattern 表达同一件事。
     */
    @GetMapping({"/api/v1/sessions/{sessionId}/messages/{messageId}/suggestions",
            "/api/v1/sessions/{id}/messages/{messageId}/suggestions"})
    public ResponseEntity<MessageSuggestionSet> get(
            @PathVariable(value = "sessionId", required = false) String sessionId,
            @PathVariable(value = "id", required = false) String idFallback,
            @PathVariable("messageId") String messageId) {
        String sid = sessionId == null || sessionId.isEmpty() ? idFallback : sessionId;
        MessageSuggestionSet set = suggestionFacade.getFollowUpsSanitized(sid, messageId);
        return ResponseEntity.status(MessageSuggestionFacade.statusFor(set)).body(set);
    }

    /**
     * 成功是 <b>200</b> + {@code ApiResponse} 外壳（无业务数据）；
     * 解析失败（含空 body、required 字段缺失）→ 400 固定文案 "invalid request body"。
     */
    @PostMapping("/api/v1/sessions/{sessionId}/suggestion-events")
    public ApiResponse<Void> recordEvent(
            @PathVariable("sessionId") String sessionId,
            @RequestBody(required = false) String rawBody) {
        suggestionFacade.recordEventBody(sessionId, rawBody);
        return ApiResponse.ok();   // 204 退役（空体与「外壳恒存在」冲突）
    }
}
