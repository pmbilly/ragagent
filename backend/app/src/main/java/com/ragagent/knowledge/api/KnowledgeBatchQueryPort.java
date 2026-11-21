package com.ragagent.knowledge.api;

import com.ragagent.retrieval.api.KnowledgeDocumentFacts;
import java.util.List;

/** 知识批量取。 */
public interface KnowledgeBatchQueryPort {

    List<KnowledgeDocumentFacts> getKnowledgeBatch(long tenantId, List<String> ids);
}
