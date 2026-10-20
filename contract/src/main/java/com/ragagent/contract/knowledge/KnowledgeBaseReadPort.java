package com.ragagent.contract.knowledge;

import java.util.List;

/** 知识库读面与访问闸门（初始化向导/配置面共用）。 */
public interface KnowledgeBaseReadPort {

    KnowledgeBaseRowView getKnowledgeBase(String id);

    List<KnowledgeBaseRowView> listKnowledgeBases(String creator);

    KnowledgeBaseRowView getAllTenantById(String id);

    KnowledgeBaseRowView requireKbAccess(String kbId);

    String retrieveDriver();

    boolean hasFiles(String kbId);
}
