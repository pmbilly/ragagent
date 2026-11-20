package com.ragagent.contract.knowledge;

import java.util.List;

/** 摄取查重面：按外部 id / 元数据前缀 / 文件哈希 / 租户库范围取行。 */
public interface KnowledgeIngestLookupPort {

    KnowledgeBaseRowView findKnowledgeBaseRow(String kbId);

    KnowledgeRowView findKnowledgeByExternalId(long tenantId, String kbId,
            String dataSourceId, String externalId);

    List<KnowledgeRowView> listKnowledgeByMetadataPrefix(long tenantId, String kbId,
            String metadataKey, String rawPrefix);

    List<KnowledgeRowView> listKnowledgeLive(long tenantId, String kbId);

    KnowledgeRowView findKnowledgeByHash(String kbId, String fileHash, String fileType);
}
