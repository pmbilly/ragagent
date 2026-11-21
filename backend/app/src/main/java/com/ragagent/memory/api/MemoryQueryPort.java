package com.ragagent.memory.api;

import com.ragagent.shared.memory.MemoryRecall;
import com.ragagent.shared.memory.MemoryRetrievalContext;
import java.util.List;
import java.util.Map;

/** 记忆查询面：召回、检索上下文与文档亲和度。 */
public interface MemoryQueryPort {

    MemoryRecall recall(String query);

    MemoryRetrievalContext retrievalContextFor();

    Map<String, Integer> documentAffinity(List<String> knowledgeIds);
}
