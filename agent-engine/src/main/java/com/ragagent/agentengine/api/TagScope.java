package com.ragagent.agentengine.api;

import java.util.List;

/** 建议问题的标签作用域：按知识库 + 标签集合限定候选。 */
public record TagScope(String knowledgeBaseId, List<String> tagIds) {
}
