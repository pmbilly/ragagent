package com.ragagent.contract.knowledge;

import java.util.List;

/** 分块与 FAQ 读面：权限解析、工具/管线、FAQ 目录共用。 */
public interface ChunkFaqReadPort {

    ChunkRowView getChunkByIdOnly(String id);

    List<ChunkRowView> listChunksById(long tenantId, List<String> ids);

    List<ChunkRowView> listChunkByParentId(long tenantId, String parentId);

    List<ChunkRowView> listChunksByParentIDs(long tenantId, List<String> parentIds);

    ChunkPageView listPagedChunksByKnowledgeId(long tenantId, String knowledgeId, int offset,
            int limit, List<String> chunkTypes, List<String> tagIds,
            String keyword, String searchField, String sortOrder,
            String knowledgeType, Boolean isEnabled);

    FaqEntryPageView listFaqEntries(String kbId, int page, int pageSize,
            List<String> tagUuids, long legacyTagSeqId,
            String keyword, String searchField,
            String sortOrder, Boolean isEnabled);
}
