package com.ragagent.agent.prompt;

/** 用户 @ 选中的文档摘要信息。 */
public record SelectedDocumentInfo(
        String knowledgeId,
        String knowledgeBaseId,
        String title,
        String fileName,
        String fileType) {
}
