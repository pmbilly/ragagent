package com.ragagent.agent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ragagent.agent.prompt.AgentPromptTemplates;
import com.ragagent.agent.skills.DbSkillSource;
import com.ragagent.agent.skills.Manager;
import com.ragagent.agent.skills.Skill;
import com.ragagent.agent.tool.SequentialThinkingTool;
import com.ragagent.agent.tool.SkillReadFileTool;
import com.ragagent.agent.tool.TodoWriteTool;
import com.ragagent.agent.tool.ToolRegistry;
import com.ragagent.contract.agent.AgentEngineFactory;
import com.ragagent.contract.agent.AgentEngineHandle;
import com.ragagent.contract.agent.AgentEngineRequest;
import com.ragagent.contract.agent.PinnedMCPServiceInfo;
import com.ragagent.contract.agent.PinnedSkillInfo;
import com.ragagent.contract.agent.SkillCatalogPort;
import com.ragagent.contract.event.EventBusFactory;
import com.ragagent.contract.event.EventBusInterface;
import com.ragagent.contract.model.LlmChatClient;
import com.ragagent.contract.pipeline.McpToolingGateway;
import com.ragagent.contract.pipeline.McpToolRegistrationPort;
import com.ragagent.contract.tool.ToolDefinitions;

/**
 * agent 引擎工厂实现（R5c：装配权由域侧下沉到此）。
 *
 * <p>负责：工具注册表（含输出上限）、域侧传入的外部工具、内置工具（思考/待办/技能读取，
 * 按 {@code allowedToolNames} 门控）、MCP 编排端口与按需目录、技能目录装载
 * （{@code DbSkillSource} + {@code Manager}，失败降级不阻断本轮）、提示词模板装载、
 * pinned mentions 组装、引擎构造与图片描述器接线。</p>
 *
 * <p>语义与迁移前 {@code AgentEngineAssembler.createAgentEngine} 的对应段逐条一致
 * （含失败降级与日志口径）。</p>
 */
@Component
public class AgentEngineFactoryImpl implements AgentEngineFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentEngineFactoryImpl.class);

    private final SkillCatalogPort skillCatalog;
    private final McpToolRegistrationPort mcpTools;
    private final EventBusFactory eventBusFactory;

    public AgentEngineFactoryImpl(SkillCatalogPort skillCatalog, McpToolRegistrationPort mcpTools,
            EventBusFactory eventBusFactory) {
        this.skillCatalog = skillCatalog;
        this.mcpTools = mcpTools;
        this.eventBusFactory = eventBusFactory;
    }

    @Override
    public AgentEngineHandle create(AgentEngineRequest request, LlmChatClient chatModel,
                                    EventBusInterface eventBus) {
        // 调用方未带总线时经工厂端口补一个（每轮独立；引擎内部的 new 兜底仍保留为防御默认）
        if (eventBus == null) {
            eventBus = eventBusFactory.create();
        }
        // 1) 工具注册表 + 输出上限 + 域侧外部工具
        ToolRegistry toolRegistry = new ToolRegistry();
        if (request.spec().getMaxToolOutputChars() > 0) {
            toolRegistry.setMaxToolOutputSize(request.spec().getMaxToolOutputChars());
        }
        if (request.tools() != null) {
            for (var tool : request.tools()) {
                toolRegistry.registerTool(tool);
            }
        }

        // 2) 内置工具（按域侧裁剪后的允许名；名字与注册一致）
        List<String> allowed = request.allowedToolNames() == null ? List.of() : request.allowedToolNames();
        if (allowed.contains(ToolDefinitions.TOOL_THINKING)) {
            toolRegistry.registerTool(new SequentialThinkingTool());
        }
        if (allowed.contains(ToolDefinitions.TOOL_TODO_WRITE)) {
            toolRegistry.registerTool(new TodoWriteTool());
        }

        // 3) MCP：编排端口 + 按需目录（模式裁剪与空清单短路口径同迁移前）
        McpToolingGateway mcpTooling = mcpTools.createTooling(toolRegistry.port());
        toolRegistry.setMcpTooling(mcpTooling);
        String mcpMode = request.mcpSelectionMode() == null || request.mcpSelectionMode().isEmpty()
                ? "all" : request.mcpSelectionMode();
        List<String> selected = request.mcpServices();
        if ("none".equals(mcpMode)) {
            log.info("MCP services disabled by agent config (mode: none)");
        } else if ("selected".equals(mcpMode) && (selected == null || selected.isEmpty())) {
            log.info("MCP services disabled by agent config (mode: selected, no services)");
        } else {
            long tenantId = request.tenantId() == null ? 0L : request.tenantId();
            mcpTools.registerOnDemandTools(tenantId, mcpMode, selected == null ? List.of() : selected,
                    toolRegistry.port(), mcpTooling, request.mcpAuthWaitTimeoutSeconds());
        }

        // 4) 指令型技能：Manager 只做三级注入；内容来自 skills 表（每轮新建 DbSkillSource）
        Manager skillsManager = null;
        if (request.skillsEnabled()) {
            try {
                DbSkillSource skillSource =
                        new DbSkillSource(skillCatalog.listVisible(request.tenantId()));
                skillsManager = new Manager(
                        new Manager.ManagerConfig(List.of(skillSource), request.allowedSkills(), true));
                skillsManager.initialize();
                log.info("Instructional skills enabled: {} skill(s) from DB catalog",
                        skillsManager.getAllMetadata() == null ? 0 : skillsManager.getAllMetadata().size());
            } catch (Exception e) {
                // 降级：技能读取失败不阻断本轮对话（与宿主目录时代「目录打错字不 500」同口径）
                skillsManager = null;
                log.error("skills disabled for this run: {}", e.getMessage());
            }
        }
        toolRegistry.prepareMcpTools();

        // 5) 构造引擎 + 提示词模板
        AgentEngine engine = new AgentEngine(request.spec(), chatModel, toolRegistry, eventBus,
                request.knowledgeBases(), request.selectedDocuments(), request.sessionId(),
                request.systemPromptTemplate());
        engine.setAppConfig(new AgentPromptTemplates.TemplatesConfig(
                AgentPromptTemplates.loadAgentSystemPromptTemplates()));

        // 6) pinned mentions（@ 引用的 MCP 服务与技能）
        List<PinnedMCPServiceInfo> pinnedMcp = new ArrayList<>();
        if (request.pinnedMcpServiceIds() != null) {
            for (String id : request.pinnedMcpServiceIds()) {
                if (id != null && !id.isEmpty()) {
                    pinnedMcp.add(new PinnedMCPServiceInfo(false, id, id, "", new ArrayList<>()));
                }
            }
        }
        List<PinnedSkillInfo> pinnedSkills = new ArrayList<>();
        if (request.pinnedSkillNames() != null) {
            java.util.Map<String, String> descByName = new java.util.HashMap<>();
            if (skillsManager != null && skillsManager.getAllMetadata() != null) {
                for (Skill.SkillMetadata m : skillsManager.getAllMetadata()) {
                    if (m != null && m.name() != null) {
                        descByName.put(m.name(), m.description() == null ? "" : m.description());
                    }
                }
            }
            for (String name : request.pinnedSkillNames()) {
                if (name != null && !name.isEmpty()) {
                    pinnedSkills.add(new PinnedSkillInfo(name, descByName.getOrDefault(name, "")));
                }
            }
        }
        engine.setPinnedMentions(pinnedMcp, pinnedSkills);

        // 7) 技能注入 + 读取通道工具
        if (skillsManager != null) {
            engine.setSkillsManager(skillsManager);
            toolRegistry.registerTool(new SkillReadFileTool(skillsManager));
        }

        // 8) 工具图片 VLM 描述器（域侧已建好；失败域侧只记警告）
        if (request.imageDescriber() != null) {
            engine.setImageDescriber(request.imageDescriber());
        }

        return new AgentEngineHandleAdapter(engine);
    }
}
