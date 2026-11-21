package com.ragagent.knowledge.api;

import com.ragagent.shared.knowledge.KnowledgeDocumentFacts;
import java.util.List;

/** 知识文档查询面：按租户过滤的单条 / 批量读取。 */
public interface KnowledgeDocumentQueryPort {

    /** 按租户过滤取单条。 */
    KnowledgeDocumentFacts getKnowledgeById(String id);

    List<KnowledgeDocumentFacts> getKnowledgeBatch(long tenantId, List<String> ids);

    List<KnowledgeDocumentFacts> getKnowledgeBatchWithSharedAccess(
            long tenantId, List<String> ids);
}
