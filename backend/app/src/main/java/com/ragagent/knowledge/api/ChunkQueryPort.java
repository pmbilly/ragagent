package com.ragagent.knowledge.api;

import com.ragagent.retrieval.api.ChunkFacts;
import java.util.List;

/** chunk 查询面：按 ID / 按父 ID 批量取。 */
public interface ChunkQueryPort {

    List<ChunkFacts> listChunksById(long tenantId, List<String> ids);

    /** image_info 聚合用。 */
    List<ChunkFacts> listChunksByParentIds(long tenantId, List<String> parentIds);
}
