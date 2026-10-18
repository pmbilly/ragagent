package com.ragagent.memory.api;

import com.ragagent.contract.memory.MemoryRecall;
import com.ragagent.contract.memory.MemorySearchHit;
import com.ragagent.contract.memory.MemorySearchHits;
import com.ragagent.memory.service.MemoryExtractionService;
import com.ragagent.memory.service.MemoryService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 记忆域对外访问面（会话族内直接依赖：session 装配注入本类，不经契约端口）。
 *
 * <p>逐字转发域内服务，不新增缓冲、不改失败口径。查找面把域内 {@code MemorySearchResult}
 * （条目为实体）映射为载荷 {@link MemorySearchHit}（四字段原样传递，空值不归一）；
 * 域返回 null ⇒ {@link MemorySearchHits#UNAVAILABLE}。</p>
 */
@Component
public class MemoryAccess {

    private final MemoryService memoryService;
    private final MemoryExtractionService memoryExtraction;

    public MemoryAccess(MemoryService memoryService,
                               MemoryExtractionService memoryExtraction) {
        this.memoryService = memoryService;
        this.memoryExtraction = memoryExtraction;
    }

        public boolean memoryAvailable() {
        return memoryService.memoryAvailable();
    }

        public MemoryRecall recall(String query) {
        return memoryService.recall(query);
    }

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

        public void scheduleExtraction(String sessionId, String messageId, String chatModelId) {
        memoryExtraction.scheduleExtraction(sessionId, messageId, chatModelId);
    }
}
