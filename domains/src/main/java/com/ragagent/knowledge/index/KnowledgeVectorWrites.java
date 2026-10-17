package com.ragagent.knowledge.index;

import com.ragagent.contract.retrieval.IndexEngine;
import com.ragagent.contract.retrieval.IndexEngineResolver;
import com.ragagent.knowledge.domain.KnowledgeBase;
import org.springframework.stereotype.Component;

/**
 * 知识写链的向量店路由网关。
 *
 * <p>所有知识写链（删除 / FAQ / 克隆移动等）都经本网关路由——绑定外部 store 的 KB
 * 写进自己的店。历史路径直连 pg JDBC（{@code VectorIndexStore}），绑定店会写落错店。</p>
 *
 * <p><b>契约化</b>：绑定解析（registry + 工厂 + 归属校验 + 失败分类）是引擎知识，
 * 已沉到 retrieval-engine 的 {@code IndexEngineGateway}；本类只剩域判定
 * （{@code hasVectorStore()}）与转交，不再 import 任何检索实现类。</p>
 */
@Component
public class KnowledgeVectorWrites {

    private final IndexEngineResolver indexEngineResolver;

    public KnowledgeVectorWrites(IndexEngineResolver indexEngineResolver) {
        this.indexEngineResolver = indexEngineResolver;
    }

    /**
     * 绑定 store 的 KB → 索引引擎句柄；未绑定 → {@code null}（调用方保持 pg 直连路径）。
     * 解析失败（跨租户 FORBIDDEN / 店不可用 UNAVAILABLE / 店未注册 NOT_FOUND）原样抛
     * {@code RetrieveEngineException}——调用方按各自的可重试策略处理。
     *
     * <p><b>租户必须由调用方显式给出</b>：处理队列的 executor 线程不携带上下文 ⇒ null 兜成 0，
     * 非零租户的绑定 store 会被误判跨租户 FORBIDDEN（预清理还会先删掉 chunks）。同步调用点传当前租户，
     * 异步 worker 传任务行的 {@code tenantId}；{@code null} 仍按 0 处理（无上下文调用的旧语义）。</p>
     */
    public IndexEngine boundEngine(KnowledgeBase kb, Long tenantId) {
        if (kb == null || !kb.hasVectorStore()) {
            return null;
        }
        long tid = tenantId == null ? 0L : tenantId;
        return indexEngineResolver.resolveFor(tid, kb.getVectorStoreId());
    }
}
