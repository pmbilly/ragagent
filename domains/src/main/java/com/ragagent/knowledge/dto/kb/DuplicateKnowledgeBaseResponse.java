package com.ragagent.knowledge.dto.kb;
import com.ragagent.common.knowledge.dto.KnowledgeBaseResponse;


/** KB 复制（settings-only 同步）响应：源/目标 ID + 新 KB 视图。 */
public record DuplicateKnowledgeBaseResponse(
        String sourceId,
        String targetId,
        KnowledgeBaseResponse knowledgeBase) {
}
