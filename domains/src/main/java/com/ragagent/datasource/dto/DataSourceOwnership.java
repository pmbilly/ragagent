package com.ragagent.datasource.dto;

/** 凭据子资源所需的归属投影：只暴露 id 与所属知识库。 */
public record DataSourceOwnership(String id, String knowledgeBaseId) {
}
