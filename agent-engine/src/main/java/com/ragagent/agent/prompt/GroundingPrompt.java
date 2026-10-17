package com.ragagent.agent.prompt;

import com.ragagent.agent.AgentToolNames;
import java.util.ArrayList;
import java.util.List;

/**
 * 内容接地（grounding）指引。
 *
 * <p>适用于每一个 agent 模板，包括保存的与自定义的提示词。来源路由走当前注册表，
 * 而不是可能指着被过滤工具的配置开关。这是给模型的指引，不是工具执行闸门：
 * 仅凭一次文件写入判断不了是否需要研究。</p>
 */
public final class GroundingPrompt {

    private GroundingPrompt() {
    }

    /** formatGroundingGuidance：按本轮实际注册的工具名渲染。 */
    public static String formatGroundingGuidance(List<String> names) {
        StringBuilder b = new StringBuilder();
        b.append("\n\nContent grounding (answers and deliverables):\n");
        b.append("- Follow the user's current source restrictions and explicit selections. Otherwise "
                + "choose relevant bound knowledge bases or connected sources. A selected source does not exclude "
                + "complementary sources unless the user says so. Directory entries, titles, and summaries are "
                + "navigation hints, not proof of detailed claims.\n");

        List<String> safeNames = names == null ? List.of() : names;
        List<String> kbTools = new ArrayList<>();
        for (String name : new String[] {
                AgentToolNames.TOOL_KNOWLEDGE_SEARCH, AgentToolNames.TOOL_GREP_CHUNKS,
                AgentToolNames.TOOL_LIST_KNOWLEDGE_CHUNKS, AgentToolNames.TOOL_GET_DOCUMENT_INFO,
                AgentToolNames.TOOL_WIKI_SEARCH, AgentToolNames.TOOL_WIKI_READ_PAGE,
                AgentToolNames.TOOL_WIKI_READ_SOURCE_DOC, AgentToolNames.TOOL_QUERY_KNOWLEDGE_GRAPH,
                AgentToolNames.TOOL_DATA_SCHEMA, AgentToolNames.TOOL_DATA_ANALYSIS,
                AgentToolNames.TOOL_DATABASE_QUERY
        }) {
            if (safeNames.contains(name)) {
                kbTools.add(name);
            }
        }
        if (!kbTools.isEmpty()) {
            b.append("- Available knowledge tools: " + String.join(", ", kbTools)
                    + ". Consult the current runtime_context scope and capabilities. When no source was "
                    + "explicitly selected, use relevant bound knowledge bases for factual tasks. With an "
                    + "explicit source selection, KB retrieval is complementary, not a prerequisite. Directory "
                    + "entries are routing hints, not retrieved evidence; do not exhaust unrelated bases. "
                    + "Choose an available search or reader appropriate to the scope.\n");
        }
        if (safeNames.contains(AgentToolNames.TOOL_WEB_SEARCH)) {
            b.append("- web_search is available: use it when relevant local evidence is missing, "
                    + "insufficient, or needs external/current verification. Prefer authoritative sources and "
                    + "verify the requested version and prerequisites. Do not send private source content to "
                    + "external search.\n");
        }
        if (safeNames.contains(AgentToolNames.TOOL_WEB_FETCH)) {
            b.append("- web_fetch is available: read relevant supplied or discovered URLs when "
                    + "their content is needed to support claims; a search snippet alone may omit essential "
                    + "conditions.\n");
        }
        if (safeNames.contains(AgentToolNames.TOOL_DISCOVER_MCP_TOOLS)) {
            b.append("- Connected MCP services may provide relevant evidence or actions. Use the "
                    + "selected service when applicable; a service description or a discovered tool is not "
                    + "itself evidence that an action was performed.\n");
        }
        b.append("- Use only resources accessible through this turn's tools and supplied context. "
                + "If relevant sources are unavailable or searches leave gaps, state a limitation only when it "
                + "affects the answer and distinguish unverified background knowledge from supported claims. Do "
                + "not invent sources, claim a search you did not perform, or treat a failed/empty lookup as "
                + "verification. Ask for missing material only when needed to complete the task accurately.\n");
        return b.toString();
    }
}
