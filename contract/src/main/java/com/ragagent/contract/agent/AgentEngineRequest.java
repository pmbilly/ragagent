package com.ragagent.contract.agent;

import java.util.List;

import com.ragagent.contract.tool.AgentTool;

/**
 * 创建 agent 引擎所需的全部输入（R5c：由域侧准备、agent-engine 侧消费）。
 *
 * <p>域侧保留"知道什么"（会话参数、检索到的 KB/文档元数据、外部工具清单、MCP 选择、技能开关），
 * agent-engine 保留"怎么装配"（工具注册表、内置工具、技能装载、提示词模板、引擎构造）。</p>
 *
 * @param spec              引擎消费字段的读面（域侧的 QaAgentConfig 实现之）
 * @param sessionId         会话 id
 * @param assistantMessageId 本轮助手消息 id（网页快照存储用）
 * @param knowledgeBases    KB 元数据（{@code contract.agent.KnowledgeBaseInfo}）
 * @param selectedDocuments 显式选中的文档
 * @param systemPromptTemplate 自定义系统提示词模板（空串 = 用内置）
 * @param allowedToolNames  域侧裁剪后的允许工具名（KB/wiki/沙箱门控在此之上已生效）
 * @param skillsEnabled     指令型技能开关
 * @param allowedSkills     技能白名单（null/空 = 不限）
 * @param tenantId          租户（技能目录读取用；null 兜 0）
 * @param mcpSelectionMode  MCP 选择模式（all/selected/none）
 * @param mcpServices       选中的 MCP 服务 id
 * @param mcpAuthWaitTimeoutSeconds MCP 授权等待秒数（审批门）
 * @param pinnedMcpServiceIds @ 引用的 MCP 服务 id
 * @param pinnedSkillNames  @ 引用的技能名
 * @param imageDescriber    工具图片的 VLM 描述器（null = 不可用）
 * @param tools             域侧构建的**外部工具**（检索/wiki/web 等；内置工具由工厂注册）
 */
public record AgentEngineRequest(
        AgentRunSpec spec,
        String sessionId,
        String assistantMessageId,
        List<KnowledgeBaseInfo> knowledgeBases,
        List<SelectedDocumentInfo> selectedDocuments,
        String systemPromptTemplate,
        List<String> allowedToolNames,
        boolean skillsEnabled,
        List<String> allowedSkills,
        Long tenantId,
        String mcpSelectionMode,
        List<String> mcpServices,
        int mcpAuthWaitTimeoutSeconds,
        List<String> pinnedMcpServiceIds,
        List<String> pinnedSkillNames,
        ImageDescriberFunc imageDescriber,
        List<AgentTool> tools) {
}
