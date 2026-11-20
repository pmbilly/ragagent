package com.ragagent.memory.adapter;

import com.ragagent.memory.service.MemoryService;
import com.ragagent.shared.memory.MemoryRecall;
import com.ragagent.shared.memory.MemoryRetrievalContext;
import com.ragagent.shared.pipeline.PipelinePorts;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link PipelinePorts.MemoryService}（管线面）的 memory 域实现：
 * 三个方法逐字转发 {@link MemoryService}（原会话 {@code QaWiring#qaPipelineMemoryService}
 * 匿名实现的搬运，签名本就与域服务对齐）。装配按类型注入。
 */
@Component
public class MemoryPipelineAdapter implements PipelinePorts.MemoryService {

    private final MemoryService memoryService;

    public MemoryPipelineAdapter(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public MemoryRecall recall(String query) {
        return memoryService.recall(query);
    }

    @Override
    public MemoryRetrievalContext retrievalContextFor() {
        return memoryService.retrievalContextFor();
    }

    @Override
    public Map<String, Integer> documentAffinity(List<String> knowledgeIds) {
        return memoryService.documentAffinity(knowledgeIds);
    }
}
