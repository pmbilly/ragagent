package com.ragagent.contract.knowledge;

import java.util.List;

/** 知识条目读面：按 id / 租户 / 分页取行。 */
public interface KnowledgeEntryReadPort {

    KnowledgeRowView getKnowledgeByIdOnly(String id);

    List<KnowledgeRowView> getKnowledgeBatch(long tenantId, List<String> ids);

    List<KnowledgeRowView> getKnowledgeBatchWithSharedAccess(long tenantId, List<String> ids);

    KnowledgeRowView getKnowledgeInTenant(long tenantId, String id);

    KnowledgePageView listKnowledge(String kbId, long page, long pageSize,
            String keyword, String parseStatus, String fileType,
            String folderPath, boolean folderPresent);
}
