package com.ragagent.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ragagent.agent.skills.Manager;
import com.ragagent.agent.skills.Skill;
import com.ragagent.contract.tool.ToolDefinitions;
import com.ragagent.contract.model.chat.ChatMessage;
import com.ragagent.contract.wiki.WikiLanguageSupport;
import com.ragagent.agent.prompt.RecentDocInfo;
import com.ragagent.agent.prompt.SelectedDocumentInfo;
import com.ragagent.agent.prompt.PinnedMCPServiceInfo;
import com.ragagent.agent.prompt.PinnedSkillInfo;
import com.ragagent.agent.prompt.PinnedSkillInstructions;
import com.ragagent.agent.prompt.KnowledgeBaseInfo;
import com.ragagent.agent.prompt.SystemPromptSection;

/**
 * 会话装配协作者（原 {@code PromptAssembly}）：把"这次要发给模型的消息"组装出来——
 * runtime_context / must_use 注入块、用户轮内容渲染、历史消息与 KB 工具结果净化，
 * 以及系统提示词（**段选择委托 {@link AgentPrompts} 的段表**，本类只负责取出并拼接）。
 *
 * <p>持有 {@link AgentEngine} 回引以访问引擎绑定面（知识库/文档/MCP/技能）；
 * 本类不得独立实例化。</p>
 *
 * <p>更名理由：本类的主要职责是 messages 数组的装配，系统提示词文本的"选段"已经
 * 归段表（F5b-1），"怎么拼"归 L1 {@code PromptComposer}（F5a）。</p>
 */
final class ConversationAssembler {

    private static final Logger log = LoggerFactory.getLogger(ConversationAssembler.class);

    private final AgentEngine engine;

    /** pinned 技能正文的每轮缓存（引擎每轮新建 → 天然每轮失效）。 */
    private List<PinnedSkillInstructions> pinnedSkillInstructions;

    ConversationAssembler(AgentEngine engine) {
        this.engine = engine;
    }

    AgentPrompts.BuildSystemPromptOptions systemPromptOptions() {
        AgentPrompts.BuildSystemPromptOptions opts = new AgentPrompts.BuildSystemPromptOptions()
                .setLanguage(WikiLanguageSupport.languageNameFromContext())
                .setConfig(engine.appConfig)
                .setMemoryPrompt(engine.memoryPrompt)
                .setProtocolPrompt(engine.modelContext.protocolPrompt());
        List<Skill.SkillMetadata> allMetadata = engine.skillsManager != null && engine.skillsManager.isEnabled()
                ? engine.skillsManager.getAllMetadata() : null;
        if (engine.toolRegistry != null) {
            opts.setSelectedTools(engine.toolRegistry.listTools());
            opts.setSkillsMetadata(toAgentSkillMetadata(allMetadata));
        } else {
            opts.setSkillsMetadata(toAgentSkillMetadata(allMetadata));
        }
        opts.setPinnedSkillInstructions(pinnedSkillInstructions());
        return opts;
    }

    /**
     * 本轮点名技能的正文：读不到就不注入（该技能改走 read_file 按需读取），
     * 不让技能读取失败拖垮整轮对话。
     */
    private List<PinnedSkillInstructions> pinnedSkillInstructions() {
        if (pinnedSkillInstructions == null) {
            pinnedSkillInstructions = resolvePinnedSkillInstructions(engine.skillsManager, engine.pinnedSkills);
        }
        return pinnedSkillInstructions;
    }

    /**
     * pinned 技能正文解析（静态便于单测）：技能关着或读不到就不注入该条
     * （该技能改走 read_file 按需读取），不让技能读取失败拖垮整轮对话。
     */
    static List<PinnedSkillInstructions> resolvePinnedSkillInstructions(
            Manager manager, List<PinnedSkillInfo> pinned) {
        List<PinnedSkillInstructions> out = new ArrayList<>();
        if (manager == null || !manager.isEnabled() || pinned == null) {
            return out;
        }
        for (PinnedSkillInfo item : pinned) {
            if (item == null || item.name() == null || item.name().isEmpty()) {
                continue;
            }
            try {
                Skill skill = manager.loadSkill(item.name());
                out.add(new PinnedSkillInstructions(item.name(), skill.instructions));
            } catch (Exception e) {
                log.warn("[Agent][Prompt] pinned skill \"{}\" not injected: {}", item.name(), e.getMessage());
            }
        }
        return out;
    }

    private static List<SkillMetadata> toAgentSkillMetadata(List<Skill.SkillMetadata> in) {
        if (in == null) {
            return null;
        }
        List<SkillMetadata> out = new ArrayList<>(in.size());
        for (Skill.SkillMetadata m : in) {
            out.add(new SkillMetadata(m.name(), m.description(), m.basePath()));
        }
        return out;
    }

    String buildSystemPrompt() {
        List<SystemPromptSection> sections = AgentPrompts.buildSystemPromptSections(
                engine.knowledgeBasesInfo, engine.config != null && engine.config.isWebSearchEnabled(),
                systemPromptOptions(), LocalDate.now(), engine.systemPromptTemplate);
        for (SystemPromptSection section : sections) {
            log.debug("[Agent][Prompt] section={} bytes={}", section.name(),
                    section.content() == null ? 0 : section.content().length());
        }
        return AgentPrompts.renderSystemPromptSections(sections);
    }

    static String indentLines(String s, String indent) {
        if (s.isEmpty()) {
            return "";
        }
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            if (!lines[i].isEmpty()) {
                sb.append(indent).append(lines[i]);
            } else {
                sb.append(lines[i]);
            }
        }
        return sb.toString();
    }

    static String escapeXMLAttr(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 当前轮的元数据块：current_time + session + 本轮生效的检索范围。
     * 注入当前 user 消息、不落历史——回放的用户轮
     * 保持裸 Content，过期的范围快照不会误导追问。
     */
    static String buildRuntimeContextBlock(String sessionId, List<KnowledgeBaseInfo> kbs,
            List<SelectedDocumentInfo> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<runtimeContext scope=\"this_turn\">\n");
        sb.append("  <currentTime>").append(LocalDate.now().toString()).append("</currentTime>\n");
        sb.append("  <session>").append(escapeXMLAttr(sessionId)).append("</session>\n");

        if (kbs != null && !kbs.isEmpty()) {
            // 完整绑定 KB 详情（能力 + 近期文档），模型在一处完成检索路由。
            sb.append("  <boundKnowledgeBases>\n");
            sb.append(indentLines(AgentPrompts.formatKnowledgeBaseList(kbs), "    "));
            sb.append("\n  </boundKnowledgeBases>\n");
        }

        if (docs != null && !docs.isEmpty()) {
            sb.append("  <pinnedDocuments scope=\"authoritative_for_this_turn\">\n");
            for (SelectedDocumentInfo d : docs) {
                if (d == null) {
                    continue;
                }
                String title = d.title();
                if (title.isEmpty()) {
                    title = d.fileName();
                }
                if (title.isEmpty()) {
                    title = d.knowledgeId();
                }
                if (!d.fileType().isEmpty()) {
                    sb.append("    <document knowledgeId=\"").append(escapeXMLAttr(d.knowledgeId()))
                            .append("\" title=\"").append(escapeXMLAttr(title))
                            .append("\" fileType=\"").append(escapeXMLAttr(d.fileType()))
                            .append("\" />\n");
                } else {
                    sb.append("    <document knowledgeId=\"").append(escapeXMLAttr(d.knowledgeId()))
                            .append("\" title=\"").append(escapeXMLAttr(title)).append("\" />\n");
                }
            }
            sb.append("  </pinnedDocuments>\n");
        }

        sb.append("</runtimeContext>");
        return sb.toString();
    }

    /** @mention 的短提示（工具名已在 schema 里，此处不列）。 */
    static String buildMustUseBlock(List<PinnedMCPServiceInfo> mcpServices,
            List<PinnedSkillInfo> skills, List<String> injectedSkillNames) {
        List<String> lines = new ArrayList<>();
        if (mcpServices != null) {
            for (PinnedMCPServiceInfo svc : mcpServices) {
                if (svc == null) {
                    continue;
                }
                if (svc.discoverable() && svc.toolNames() != null && !svc.toolNames().isEmpty()) {
                    lines.add("Use relevant available MCP functions for service @"
                            + sanitizeMustUseField(svc.name()) + " (serverId=\""
                            + sanitizeMustUseField(svc.id()) + "\") before answering. Their descriptions "
                            + "identify the service and original tool names; use "
                            + "discover_mcp_tools if the service needs reconnection or authentication.");
                    continue;
                }
                if (svc.discoverable()) {
                    lines.add("Use discover_mcp_tools(mode=\"list_tools\", serverId=\""
                            + sanitizeMustUseField(svc.id()) + "\") for the selected MCP service @"
                            + sanitizeMustUseField(svc.name()) + ". Describe the required tools, then use "
                            + "the offered functions or call_mcp_tool as available before answering; report "
                            + "connection or authentication failures if the service is unavailable.");
                    continue;
                }
                String prefix = mcpToolNamePrefix(svc);
                if (prefix.isEmpty()) {
                    continue;
                }
                String display = sanitizeMustUseField(svc.name());
                if (display.isEmpty()) {
                    display = sanitizeMustUseField(svc.id());
                }
                lines.add("Must use MCP tools whose names start with " + prefix + " (@" + display
                        + ") to answer the question below.");
            }
        }
        if (skills != null) {
            for (PinnedSkillInfo skill : skills) {
                if (skill == null || skill.name().isEmpty()) {
                    continue;
                }
                String name = sanitizeMustUseField(skill.name());
                if (injectedSkillNames != null && injectedSkillNames.contains(skill.name())) {
                    // 正文已随系统提示词注入 → 直接照做即可，不必再去读一遍
                    lines.add("Apply the instructions of @Skill \"" + name
                            + "\" (provided in <skillInstructions>) to the task below.");
                } else {
                    // 正文未能注入（如技能在提问后被删/改名）→ 保留按需读取作为回退
                    lines.add("Must call read_file(path=\"skill://" + name + "/SKILL.md\") for @Skill \""
                            + name + "\" before answering.");
                }
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return "<mustUse>\n" + String.join("\n", lines)
                + "\nThese selections do not replace research into the task's factual content or exclude other "
                + "relevant available sources unless the user explicitly restricts them. Apply selections to the "
                + "relevant parts of the task; an @mention does not authorize unrelated actions. Follow the "
                + "user's current explicit restrictions if they narrow or cancel a selection.\n</mustUse>";
    }

    /** 去换行与尖括号，名字越不出 must_use 块。 */
    static String sanitizeMustUseField(String s) {
        return s.replace("\n", " ").replace("\r", " ").replace("<", " ").replace(">", " ").trim();
    }

    /**
     * MCP 服务注册工具的公共前缀：工具名是
     * mcp_{service}_{tool}，service slug 自己可能带下划线——取最长公共前缀再缩回最后
     * 一个段边界，而不是在第一个下划线处傻切。
     */
    static String mcpToolNamePrefix(PinnedMCPServiceInfo svc) {
        if (svc == null || svc.toolNames() == null || svc.toolNames().isEmpty()) {
            return "";
        }
        String head = "mcp_";
        List<String> mcpNames = new ArrayList<>();
        for (String toolName : svc.toolNames()) {
            if (toolName.startsWith(head)) {
                mcpNames.add(toolName);
            }
        }
        if (mcpNames.isEmpty()) {
            return "";
        }
        String prefix = mcpNames.get(0);
        for (String name : mcpNames.subList(1, mcpNames.size())) {
            prefix = commonStringPrefix(prefix, name);
        }
        int idx = prefix.lastIndexOf('_');
        if (idx >= head.length() - 1) {
            prefix = prefix.substring(0, idx + 1);
        }
        if (prefix.length() <= head.length()) {
            return "";
        }
        return prefix;
    }

    static String commonStringPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return a.substring(0, i);
    }

    /**
     * 当前 LLM 调用的 user-turn 载荷。
     * 只被执行入口与 finalize 路径使用；不进 rendered_content / 历史。
     */
    String renderUserTurnContent(String sessionId, String query) {
        registerRuntimeReferences();
        String runtimeCtx = buildRuntimeContextBlock(sessionId, engine.knowledgeBasesInfo, engine.selectedDocs);
        runtimeCtx = engine.modelContext.compactKnownText(runtimeCtx);
        String mustUse = buildMustUseBlock(engine.pinnedMCPServices, engine.pinnedSkills,
                pinnedSkillInstructions().stream().map(PinnedSkillInstructions::name).toList());
        return composeUserTurnContent(List.of(runtimeCtx, mustUse, query));
    }

    /** 绑定 KB / 钉住文档 / 近期 chunk 注册成请求内句柄。 */
    void registerRuntimeReferences() {
        if (engine.knowledgeBasesInfo != null) {
            for (KnowledgeBaseInfo kb : engine.knowledgeBasesInfo) {
                if (kb == null) {
                    continue;
                }
                engine.modelContext.registerKnowledgeBase(kb.id());
                if (kb.recentDocs() == null) {
                    continue;
                }
                for (RecentDocInfo doc : kb.recentDocs()) {
                    engine.modelContext.registerDocument(doc.knowledgeId());
                    if (doc.chunkId() != null && !doc.chunkId().isEmpty()) {
                        String title = doc.title();
                        if (title == null || title.isEmpty()) {
                            title = doc.fileName();
                        }
                        engine.modelContext.registerContextChunk(doc.chunkId(), doc.knowledgeId(),
                                firstNonEmptyAgent(doc.knowledgeBaseId(), kb.id()), title, 0, doc.type());
                    }
                }
            }
        }
        if (engine.selectedDocs != null) {
            for (SelectedDocumentInfo doc : engine.selectedDocs) {
                if (doc == null) {
                    continue;
                }
                engine.modelContext.registerDocument(doc.knowledgeId());
                engine.modelContext.registerKnowledgeBase(doc.knowledgeBaseId());
            }
        }
    }

    static String firstNonEmptyAgent(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    static String composeUserTurnContent(List<String> parts) {
        List<String> nonEmpty = new ArrayList<>(parts.size());
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                nonEmpty.add(part);
            }
        }
        return String.join("\n\n", nonEmpty);
    }


    /** 结果含 KB 内容、跨轮会过期的工具。 */
    private static final Map<String, Boolean> KB_TOOL_NAMES = buildKbToolNames();

    private static Map<String, Boolean> buildKbToolNames() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put(ToolDefinitions.TOOL_KNOWLEDGE_SEARCH, true);
        m.put(ToolDefinitions.TOOL_GREP_CHUNKS, true);
        m.put(ToolDefinitions.TOOL_LIST_KNOWLEDGE_CHUNKS, true);
        m.put(ToolDefinitions.TOOL_QUERY_KNOWLEDGE_GRAPH, true);
        m.put(ToolDefinitions.TOOL_GET_DOCUMENT_INFO, true);
        m.put(ToolDefinitions.TOOL_WIKI_SEARCH, true);
        m.put(ToolDefinitions.TOOL_WIKI_READ_PAGE, true);
        m.put(ToolDefinitions.TOOL_WIKI_READ_SOURCE_DOC, true);
        return m;
    }

    /** 历史 KB 工具结果替换成短标记，防 LLM 复用过期检索数据。 */
    static List<ChatMessage> redactHistoryKBResults(List<ChatMessage> llmContext) {
        List<ChatMessage> redacted = new ArrayList<>(llmContext.size());
        for (ChatMessage msg : llmContext) {
            if ("tool".equals(msg.getRole()) && Boolean.TRUE.equals(KB_TOOL_NAMES.get(msg.getName()))) {
                redacted.add(ChatMessage.tool(msg.getToolCallId(), msg.getName(),
                        "[Previous retrieval result omitted — knowledge base may have changed. Please perform a fresh search.]"));
            } else {
                redacted.add(msg);
            }
        }
        return redacted;
    }

    /** 消息数组 + LLM 上下文。 */
    List<ChatMessage> buildMessagesWithLLMContext(String systemPrompt, String currentQuery,
            String sessionId, List<ChatMessage> llmContext, List<String> imageURLs) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));

        if (llmContext != null && !llmContext.isEmpty()) {
            List<ChatMessage> sanitized;
            if (engine.config.isRetainRetrievalHistory()) {
                sanitized = llmContext;
                log.info("Retaining full retrieval history in context (RetainRetrievalHistory=true)");
            } else {
                // KB 被改过/切过时防止 LLM 复用过期检索数据。
                sanitized = redactHistoryKBResults(llmContext);
                log.info("Added {} history messages to context (KB tool results redacted)", llmContext.size());
            }
            for (ChatMessage msg : sanitized) {
                if ("system".equals(msg.getRole())) {
                    continue;
                }
                if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole())
                        || "tool".equals(msg.getRole())) {
                    messages.add(msg);
                }
            }
        }

        // 当前用户消息走 finalize 同款注册路径——直接调 buildRuntimeContextBlock 会把
        // 持久 KB/文档 ID 塞进首个请求，而请求内 source registry 还没见过它们。
        ChatMessage userMsg = new ChatMessage("user", renderUserTurnContent(sessionId, currentQuery));
        userMsg.setImages(imageURLs);
        messages.add(userMsg);

        return messages;
    }
}
