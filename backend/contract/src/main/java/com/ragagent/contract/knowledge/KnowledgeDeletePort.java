package com.ragagent.contract.knowledge;

import java.time.OffsetDateTime;
import java.util.List;

/** 知识删除面：软删/硬删与批量硬删。 */
public interface KnowledgeDeletePort {

    String deleteKnowledge(String id);

    void softDeleteKnowledgeFull(String knowledgeId, long tenantId, OffsetDateTime now);

    void hardDeleteKnowledge(String knowledgeId, long tenantId);

    void hardDeleteKnowledgeBatch(long tenantId, List<String> knowledgeIds);
}
