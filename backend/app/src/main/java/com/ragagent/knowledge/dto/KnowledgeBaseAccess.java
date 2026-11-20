package com.ragagent.knowledge.dto;

/** 知识库访问投影：控制器只读归属/创建者，不持有实体。 */
public record KnowledgeBaseAccess(String id, Long tenantId, String creatorId) {
}
