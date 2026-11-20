package com.ragagent.contract.tool;

/**
 * 工具基类：name / description / schema 三件套与共享的输出格式化函数。
 *
 * <p>schema 必须是**纯 JDK JSON 值**（对象 → {@code Map}、数组 → {@code List}）；
 * 从 JSON 字符串构造时由 {@code platform.tool.ToolSchemas} 解析，本类不依赖 Jackson。</p>
 */
public abstract class BaseTool implements AgentTool {

    private final String name;
    private final String description;
    private final Object schema;

    protected BaseTool(String name, String description, Object schema) {
        this.name = name;
        this.description = description;
        this.schema = schema;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Object getParameters() {
        return schema;
    }

    /**
     * 相关度分级：≥0.8→High Relevance、≥0.6→Medium Relevance、
     * ≥0.4→Low Relevance，其余→Weak Relevance。
     */
    public static String getRelevanceLevel(double score) {
        if (score >= 0.8) {
            return "High Relevance";
        }
        if (score >= 0.6) {
            return "Medium Relevance";
        }
        if (score >= 0.4) {
            return "Low Relevance";
        }
        return "Weak Relevance";
    }

    /**
     * MatchType → 人读文案。MatchType 为 int 枚举：
     * 0..6 → Vector/Keyword/Adjacent Chunk/History/Parent Chunk/Relation Chunk/Graph
     * Match；其余 {@code Unknown Type(%d)}。
     */
    public static String formatMatchType(int matchType) {
        return switch (matchType) {
            case 0 -> "Vector Match";
            case 1 -> "Keyword Match";
            case 2 -> "Adjacent Chunk Match";
            case 3 -> "History Match";
            case 4 -> "Parent Chunk Match";
            case 5 -> "Relation Chunk Match";
            case 6 -> "Graph Match";
            default -> "Unknown Type(" + matchType + ")";
        };
    }
}
