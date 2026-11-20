package com.ragagent.platform.event;

/**
 * 事件类型常量。
 *
 * <p>取值为 String 常量集中定义（做法与 {@code com.ragagent.contract.model.ResponseType}
 * 同源）。流式子集与 ResponseType 大体同名（camelCase）但<b>不逐字相等</b>：
 * 思考事件取值 {@code thought}，而 ResponseType 的 wire 值是 {@code thinking}；其余
 * （toolCall / toolResult / reflection / references / error / toolApprovalRequired / ...）
 * 由接线层 {@code AgentStreamBridge} 负责两边映射，不要假设取值逐字相同。</p>
 *
 * <p>事件类型同时是 {@link EventBus} 的订阅键
 * 与 {@code Event#getType()} 的取值。不要在别处散写字面量——这里是唯一权威。</p>
 */
public final class EventType {

    // === 查询处理（Query processing events） ===
    /** 用户查询到达 */
    public static final String EVENT_QUERY_RECEIVED = "queryReceived";
    /** 查询验证完成 */
    public static final String EVENT_QUERY_VALIDATED = "queryValidated";
    /** 查询预处理 */
    public static final String EVENT_QUERY_PREPROCESS = "queryPreprocess";
    /** 查询改写 */
    public static final String EVENT_QUERY_REWRITE = "queryRewrite";
    /** 查询改写完成 */
    public static final String EVENT_QUERY_REWRITTEN = "queryRewritten";

    // === 检索（Retrieval events） ===
    /** 检索开始 */
    public static final String EVENT_RETRIEVAL_START = "retrievalStart";
    /** 向量检索 */
    public static final String EVENT_RETRIEVAL_VECTOR = "retrievalVector";
    /** 关键词检索 */
    public static final String EVENT_RETRIEVAL_KEYWORD = "retrievalKeyword";
    /** 实体检索 */
    public static final String EVENT_RETRIEVAL_ENTITY = "retrievalEntity";
    /** 检索完成 */
    public static final String EVENT_RETRIEVAL_COMPLETE = "retrievalComplete";

    // === 排序（Rerank events） ===
    /** 排序开始 */
    public static final String EVENT_RERANK_START = "rerankStart";
    /** 排序完成 */
    public static final String EVENT_RERANK_COMPLETE = "rerankComplete";

    // === 合并（Merge events） ===
    /** 合并开始 */
    public static final String EVENT_MERGE_START = "mergeStart";
    /** 合并完成 */
    public static final String EVENT_MERGE_COMPLETE = "mergeComplete";

    // === 聊天生成（Chat completion events） ===
    /** 聊天生成开始 */
    public static final String EVENT_CHAT_START = "chatStart";
    /** 聊天生成完成 */
    public static final String EVENT_CHAT_COMPLETE = "chatComplete";
    /** 聊天流式输出 */
    public static final String EVENT_CHAT_STREAM = "chatStream";

    // === Agent（Agent events） ===
    /** Agent 查询开始 */
    public static final String EVENT_AGENT_QUERY = "agentQuery";
    /** Agent 计划生成 */
    public static final String EVENT_AGENT_PLAN = "agentPlan";
    /** Agent 步骤执行 */
    public static final String EVENT_AGENT_STEP = "agentStep";
    /** Agent 工具调用 */
    public static final String EVENT_AGENT_TOOL = "agentTool";
    /** Agent 完成 */
    public static final String EVENT_AGENT_COMPLETE = "agentComplete";

    // === Agent 流式事件（实时反馈，AgentStreamBridge 订阅的就是这一组） ===
    /** Agent 思考过程 */
    public static final String EVENT_AGENT_THOUGHT = "thought";
    /** 工具调用通知 */
    public static final String EVENT_AGENT_TOOL_CALL = "toolCall";
    /** 工具结果 */
    public static final String EVENT_AGENT_TOOL_RESULT = "toolResult";
    /** Agent 反思 */
    public static final String EVENT_AGENT_REFLECTION = "reflection";
    /** 知识引用 */
    public static final String EVENT_AGENT_REFERENCES = "references";
    /** 最终答案 */
    public static final String EVENT_AGENT_FINAL_ANSWER = "finalAnswer";

    // === MCP 工具人工审批（issue #1173） ===
    public static final String EVENT_TOOL_APPROVAL_REQUIRED = "toolApprovalRequired";
    public static final String EVENT_TOOL_APPROVAL_RESOLVED = "toolApprovalResolved";

    // === MCP OAuth 会话内授权提示：调用带 OAuth 的 MCP 服务但用户尚未授权时发出，
    // agent 暂停等待用户授权（或超时 / 取消） ===
    public static final String EVENT_MCP_OAUTH_REQUIRED = "mcpOauthRequired";
    public static final String EVENT_MCP_OAUTH_RESOLVED = "mcpOauthResolved";

    // === 错误 ===
    /** 错误事件 */
    public static final String EVENT_ERROR = "error";

    /** 本轮召回的长期记忆；在答案流出前发一次，UI 可展示答案看到了哪些记忆 */
    public static final String EVENT_MEMORY_RECALLED = "memoryRecalled";

    /** 旧对话被摘要压缩以适配上下文窗口时发出 */
    public static final String EVENT_CONTEXT_COMPACTED = "contextCompacted";

    /** 用户在运行中追加的消息被并入本轮时发出（见 agent drainSteerMessages） */
    public static final String EVENT_USER_MESSAGE_INJECTED = "userMessageInjected";

    /** 会话标题更新 */
    public static final String EVENT_SESSION_TITLE = "sessionTitle";

    /** 停止对话生成 */
    public static final String EVENT_STOP = "stop";

    private EventType() {
    }
}
