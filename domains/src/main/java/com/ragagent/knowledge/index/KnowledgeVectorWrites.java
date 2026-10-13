package com.ragagent.knowledge.index;

import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.retrieval.engine.CompositeRetrieveEngine;
import com.ragagent.retrieval.engine.RetrieveEngineFactories;
import com.ragagent.retrieval.engine.RetrieveEngineRegistry;
import com.ragagent.retrieval.engine.TenantStoreOwnership;
import org.springframework.stereotype.Component;

/**
 * 知识写链的向量店路由网关。
 * 所有知识写链（删除 / FAQ / 克隆移动等）都经
 * {@code retriever.CreateRetrieveEngineForKB} 路由——绑定外部 store 的 KB 写进自己的店。
 * 历史路径直连 pg JDBC（{@code VectorStoreService}），绑定店会写落错店。</p>
 * <p><b>改道策略（风险最小切分）</b>：绑定 store（{@code hasVectorStore()}）的 KB 走引擎口
 * （本网关解析）；未绑定的 KB 保持既有 pg 直连路径不变——契约样例锁定的错误形态
 * （kg-image 族、ChunkServiceTest 桩）全部在未绑定路径上，行为逐字节不变。
 * no-op（无引擎），向量行属残留数据，差异无观测面（备案）。</p>
 */
@Component
public class KnowledgeVectorWrites {

    private final RetrieveEngineRegistry retrieveEngineRegistry;
    private final TenantStoreOwnership storeOwnership;

    public KnowledgeVectorWrites(RetrieveEngineRegistry retrieveEngineRegistry,
                                 TenantStoreOwnership storeOwnership) {
        this.retrieveEngineRegistry = retrieveEngineRegistry;
        this.storeOwnership = storeOwnership;
    }

    /**
     * 绑定 store 的 KB → 复合引擎；未绑定 → {@code null}（调用方保持 pg 直连路径）。
     * 解析失败（跨租户 FORBIDDEN / 店不可用 UNAVAILABLE / 店未注册 NOT_FOUND）原样抛
     * {@link com.ragagent.retrieval.engine.RetrieveEngineException}——调用方按各自的可重试策略处理。
     *
     * <p><b>租户必须由调用方显式给出</b>：原实现在此读 {@code TenantContext} 的 ThreadLocal，
     * 但处理队列的 executor 线程不携带上下文 ⇒ null 兜成 0，非零租户的绑定 store 被误判
     * 跨租户 FORBIDDEN（预清理还会先删掉 chunks）。同步调用点传当前租户，异步 worker 传
     * 任务行的 {@code tenantId}；{@code null} 仍按 0 处理（无上下文调用的旧语义）。</p>
     */
    public CompositeRetrieveEngine boundEngine(KnowledgeBase kb, Long tenantId) {
        if (kb == null || !kb.hasVectorStore()) {
            return null;
        }
        long tid = tenantId == null ? 0L : tenantId;
        // storeId 非空时 createForKb 走 resolveBoundEngine（tenantEngines 不参与）
        return RetrieveEngineFactories.createForKb(retrieveEngineRegistry, storeOwnership,
                tid, kb.getVectorStoreId(), null);
    }
}
