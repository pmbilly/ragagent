package com.ragagent.memory.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ragagent.common.memory.MemoryExtractionPort;
import com.ragagent.common.memory.MemoryRecallPort;
import com.ragagent.common.memory.MemorySearchHit;
import com.ragagent.common.memory.MemorySearchHits;
import com.ragagent.common.memory.MemorySearchPort;
import com.ragagent.contract.memory.MemoryRecall;
import com.ragagent.memory.service.MemoryExtractionService;
import com.ragagent.memory.service.MemoryService;

/**
 * 记忆三个 L1 端口的 memory 域实现：逐字转发域内服务，不新增缓冲、不改失败口径。
 *
 * <p>查找面把域内 {@code MemorySearchResult}（条目为实体）映射为 L1 载荷
 * {@link MemorySearchHit}（四字段原样传递，空值不归一）；域返回 null ⇒
 * {@link MemorySearchHits#UNAVAILABLE}（消费方视图结果等价）。</p>
 */
@Component
public class MemoryAccessAdapter implements MemoryRecallPort, MemorySearchPort, MemoryExtractionPort {

    private final MemoryService memoryService;
    private final MemoryExtractionService memoryExtraction;

    public MemoryAccessAdapter(MemoryService memoryService,
                               MemoryExtractionService memoryExtraction) {
        this.memoryService = memoryService;
        this.memoryExtraction = memoryExtraction;
    }

    @Override
    public boolean memoryAvailable() {
        return memoryService.memoryAvailable();
    }

    @Override
    public MemoryRecall recall(String query) {
        return memoryService.recall(query);
    }

    @Override
    public MemorySearchHits search(String query, int limit) {
        var r = memoryService.searchMemory(query, limit);
        if (r == null) {
            return MemorySearchHits.UNAVAILABLE;
        }
        if (r.items() == null) {
            return new MemorySearchHits(r.available(), null);
        }
        List<MemorySearchHit> items = new ArrayList<>(r.items().size());
        for (var it : r.items()) {
            items.add(new MemorySearchHit(it.getKind(), it.getTopic(), it.getContent(),
                    it.getValidFrom()));
        }
        return new MemorySearchHits(r.available(), items);
    }

    @Override
    public void scheduleExtraction(String sessionId, String messageId, String chatModelId) {
        memoryExtraction.scheduleExtraction(sessionId, messageId, chatModelId);
    }
}
