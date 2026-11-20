package com.ragagent.contract.knowledge;

import java.util.List;
import java.util.Map;

/** 知识内容写面：建行、落文件、更新元数据（评测/IM/数据源共用）。 */
public interface KnowledgeContentWritePort {

    KnowledgeRowView createFromPassageSync(String kbId, List<String> passages, String channel);

    KnowledgeRowView createFromFile(String kbId, byte[] fileContent, String fileName,
            String displayName, Object customMetadata, String channel);

    byte[] readKnowledgeFile(long tenantId, String filePath);

    void insertKnowledge(KnowledgeRowView knowledge);

    String saveKnowledgeFile(long tenantId, String knowledgeId, String fileName, byte[] content);

    void deleteKnowledgeFile(long tenantId, String knowledgeId, String filePath);

    void updateKnowledgeMetadataRow(KnowledgeRowView knowledge, Map<String, String> metadata);
}
