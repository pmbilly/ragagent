package com.ragagent.agenttools.api;

/** 待分析的知识文件。 */
public record KnowledgeData(String id, String knowledgeBaseId, long tenantId, String fileType,
                            String filePath) {
}
