package com.ragagent.knowledge.api;

import com.ragagent.contract.knowledge.ChunkFacts;
import com.ragagent.contract.knowledge.ChunkRowView;
import com.ragagent.contract.knowledge.KnowledgeCatalogPort;
import com.ragagent.contract.knowledge.support.ChunkProjectionAdapter;
import com.ragagent.contract.pipeline.PipelinePorts;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link PipelinePorts.ChunkRepository}（管线面）的 knowledge 域实现：
 * 原会话 {@code QaWiring#qaPipelineChunkRepository} 匿名实现的搬运——
 * 投影经 L1 {@link ChunkProjectionAdapter}，父 id 批量保持"逐父循环取子"的原口径。
 */
@Component
public class KnowledgeChunkPipelineAdapter implements PipelinePorts.ChunkRepository {

    private final KnowledgeCatalogPort knowledgeCatalog;

    public KnowledgeChunkPipelineAdapter(KnowledgeCatalogPort knowledgeCatalog) {
        this.knowledgeCatalog = knowledgeCatalog;
    }

    @Override
    public List<ChunkFacts> listChunksById(long tenantId, List<String> ids) {
        return ChunkProjectionAdapter.factsAll(knowledgeCatalog.listChunksById(tenantId, ids));
    }

    @Override
    public List<ChunkFacts> listChunksByParentIds(long tenantId, List<String> parentIds) {
        List<ChunkRowView> out = new ArrayList<>();
        for (String parentId : parentIds) {
            out.addAll(knowledgeCatalog.listChunkByParentId(tenantId, parentId));
        }
        return ChunkProjectionAdapter.factsAll(out);
    }
}
