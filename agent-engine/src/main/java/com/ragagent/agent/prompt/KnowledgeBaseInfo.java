package com.ragagent.agent.prompt;

import java.util.List;

/** agent 提示词用的知识库要点信息。 */
public record KnowledgeBaseInfo(
        String id,
        String name,
        String type,
        String description,
        int docCount,
        List<String> capabilities,
        List<RecentDocInfo> recentDocs) {

    public KnowledgeBaseInfo {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        type = type == null ? "" : type;
        description = description == null ? "" : description;
        capabilities = capabilities == null ? List.of() : capabilities;
        recentDocs = recentDocs == null ? List.of() : recentDocs;
    }

    /** 便捷最小构造（只有 ID，其余全空）。 */
    public static KnowledgeBaseInfo minimal(String id) {
        return new KnowledgeBaseInfo(id, "", "", "", 0, List.of(), List.of());
    }
}
