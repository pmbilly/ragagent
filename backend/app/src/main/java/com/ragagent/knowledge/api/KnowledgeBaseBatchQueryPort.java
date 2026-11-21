package com.ragagent.knowledge.api;

import com.ragagent.knowledge.api.kb.KnowledgeBaseView;
import java.util.List;

/** 知识库批量取。 */
public interface KnowledgeBaseBatchQueryPort {

    List<KnowledgeBaseView> getKnowledgeBaseByIds(List<String> ids);
}
