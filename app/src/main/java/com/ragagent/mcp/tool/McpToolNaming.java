package com.ragagent.mcp.tool;

import com.ragagent.contract.tool.ToolDefinitions;
import com.ragagent.platform.security.Hashes;

/**
 * MCP 工具的引用/注册名/摘要：引用绑定定义哈希，注册名保留 ASCII 提示段并带身份后缀。
 *
 * <p>哈希原始 server/tool 身份而非消毒后的名字——Unicode 名与碰撞名必须可区分。</p>
 */
final class McpToolNaming {

    private McpToolNaming() {
    }

    /**
     * 对未修改的身份做哈希，而不是有损的 64 字符函数名：
     * Unicode 名与消毒后碰撞的名字保持可区分。引用同时绑定定义：schema 刷新后
     * 必须重新 describe，而不是用旧形状的参数执行。
     */
    static String mcpToolRef(McpToolWrapper tool) {
        String schema = tool.parametersJson();
        String joined = tool.service.getId() + "\0" + tool.mcpTool.getName() + "\0" + schema;
        return "mcpt_" + Hashes.sha256Hex(joined);
    }

    /** 描述截断：>200 码点截到 197 + "..."。 */
    static String shortMcpDescription(String s) {
        if (s == null) {
            return "";
        }
        if (s.codePointCount(0, s.length()) > 200) {
            return shortByRunes(s, 197) + "...";
        }
        return s;
    }

    static String shortByRunes(String s, int maxRunes) {
        if (s.length() == s.codePointCount(0, s.length())) {
            return s.substring(0, Math.min(maxRunes, s.length()));
        }
        int i = 0;
        int runes = 0;
        while (i < s.length() && runes < maxRunes) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            runes++;
        }
        return s.substring(0, i);
    }

    static McpCatalog.McpToolSummary summarizeMcpTool(McpToolWrapper tool) {
        McpCatalog.McpToolSummary s = new McpCatalog.McpToolSummary();
        if (tool.service != null) {
            s.serverName = tool.service.getName();
        }
        s.toolRef = mcpToolRef(tool);
        s.serverId = tool.service.getId();
        s.name = tool.mcpTool.getName();
        s.description = shortMcpDescription(tool.mcpTool.getDescription());
        return s;
    }

    /**
     * 注册名派生：独立于枚举顺序与 schema 修订。
     * 哈希原始 server/tool 身份——单独消毒 Unicode 或截断名会静默别名不同的工具。
     * 保留 ASCII 提示段方便模型阅读。
     */
    static String mcpRegisteredName(McpToolWrapper tool) {
        String sum = Hashes.sha256Hex(tool.service.getId() + "\0" + tool.mcpTool.getName());
        String hint = sanitizeName(tool.service.getName()) + "_" + sanitizeName(tool.mcpTool.getName());
        final int suffixChars = 16;
        final int maxHint = ToolDefinitions.MAX_FUNCTION_NAME_LENGTH
                - "mcp_".length() - 1 - suffixChars;
        if (hint.length() > maxHint) {
            hint = hint.substring(0, maxHint);
        }
        return "mcp_" + hint + "_" + sum.substring(0, suffixChars);
    }

    static String sanitizeName(String name) {
        return McpToolWrapper.sanitizeName(name);
    }
}
