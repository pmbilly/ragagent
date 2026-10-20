package com.ragagent.contract.knowledge;

/** 知识库写面（建/删/改）与标签（建/查）。 */
public interface KnowledgeBaseWritePort {

    KnowledgeBaseRowView createKnowledgeBase(KnowledgeBaseRowView kb);

    void deleteKnowledgeBase(String id);

    void updateKnowledgeBase(KnowledgeBaseRowView kb);

    KnowledgeTagView createKnowledgeTag(String kbId, String name, String color, int sortOrder);

    KnowledgeTagView findKnowledgeTagByName(long tenantId, String kbId, String name);
}
