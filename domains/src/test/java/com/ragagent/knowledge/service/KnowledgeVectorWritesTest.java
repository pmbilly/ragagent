package com.ragagent.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.retrieval.engine.CompositeRetrieveEngine;
import com.ragagent.retrieval.engine.RetrieveEngineException;
import com.ragagent.retrieval.engine.RetrieveEngineRegistry;
import com.ragagent.retrieval.engine.RetrieveEngineService;
import com.ragagent.retrieval.engine.TenantStoreOwnership;

/**
 * 知识写链的向量店路由（整改 K1）：租户必须由调用方显式传入。
 *
 * <p>场景 = 处理队列的<真实虚拟线程>：没有 TenantContext，而原实现在网关内读 ThreadLocal
 * 并把 null 兜成 0 ⇒ 非零租户的绑定 store 一律被判跨租户 FORBIDDEN（预清理还会先删 chunks）。</p>
 */
class KnowledgeVectorWritesTest {

    @Test
    @DisplayName("K1：虚拟线程（worker 场景）按显式租户校验 store 归属")
    void boundEngineResolvesOnWorkerThreadWithExplicitTenant() throws Exception {
        AtomicLong seenTenant = new AtomicLong(-1);
        TenantStoreOwnership ownership = (storeId, tenantId) -> {
            seenTenant.set(tenantId);
            return tenantId == 42L && "store-1".equals(storeId);
        };
        RetrieveEngineRegistry registry = mock(RetrieveEngineRegistry.class);
        RetrieveEngineService service = mock(RetrieveEngineService.class);
        when(registry.getOrLoadByStoreId(42L, "store-1")).thenReturn(service);
        KnowledgeVectorWrites writes = new KnowledgeVectorWrites(registry, ownership);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTenantId(42L);
        kb.setVectorStoreId("store-1");

        AtomicReference<CompositeRetrieveEngine> resolved = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                resolved.set(writes.boundEngine(kb, kb.getTenantId()));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        worker.join();

        assertThat(failure.get()).as("worker 线程不得再因缺 ThreadLocal 被判跨租户").isNull();
        assertThat(resolved.get()).isNotNull();
        assertThat(seenTenant.get()).as("归属校验用显式传入的租户").isEqualTo(42L);
    }

    @Test
    @DisplayName("显式传错租户：跨租户仍被拒（纵深防御不放松）")
    void boundEngineRejectsWrongTenant() {
        KnowledgeVectorWrites writes = new KnowledgeVectorWrites(
                mock(RetrieveEngineRegistry.class), (storeId, tenantId) -> tenantId == 42L);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setVectorStoreId("store-1");

        assertThatThrownBy(() -> writes.boundEngine(kb, 7L))
                .isSameAs(RetrieveEngineException.VECTOR_STORE_FORBIDDEN);
    }

    @Test
    @DisplayName("未绑定 store / null KB：返回 null 且不触碰注册表（回归）")
    void unboundOrNullReturnsNull() {
        RetrieveEngineRegistry registry = mock(RetrieveEngineRegistry.class);
        KnowledgeVectorWrites writes = new KnowledgeVectorWrites(registry, (storeId, tenantId) -> true);

        assertThat(writes.boundEngine(null, 42L)).isNull();
        assertThat(writes.boundEngine(new KnowledgeBase(), 42L)).isNull();
        verifyNoInteractions(registry);
    }
}
