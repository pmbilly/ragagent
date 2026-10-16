package com.ragagent.retrieval.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.contract.retrieval.IndexEngine;
import com.ragagent.contract.retrieval.RetrieveEngineException;
import com.ragagent.contract.retrieval.TenantStoreOwnership;

/**
 * 索引引擎解析（R3b 契约化）：断言随行为从 domains 的 {@code KnowledgeVectorWritesTest} 迁来——
 * "租户必须由调用方显式给出"、跨租户拒绝、虚拟线程（worker）场景。
 */
class IndexEngineGatewayTest {

    @Test
    @DisplayName("K1：虚拟线程（worker 场景）按显式租户校验 store 归属")
    void resolvesOnWorkerThreadWithExplicitTenant() throws Exception {
        AtomicLong seenTenant = new AtomicLong(-1);
        TenantStoreOwnership ownership = (storeId, tenantId) -> {
            seenTenant.set(tenantId);
            return tenantId == 42L && "store-1".equals(storeId);
        };
        RetrieveEngineRegistry registry = mock(RetrieveEngineRegistry.class);
        RetrieveEngineService service = mock(RetrieveEngineService.class);
        when(registry.getOrLoadByStoreId(42L, "store-1")).thenReturn(service);
        IndexEngineGateway gateway = new IndexEngineGateway(registry, ownership);

        AtomicReference<IndexEngine> resolved = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                resolved.set(gateway.resolveFor(42L, "store-1"));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        worker.join();

        assertThat(failure.get()).as("worker 线程不得因缺 ThreadLocal 被判跨租户").isNull();
        assertThat(resolved.get()).isNotNull();
        assertThat(seenTenant.get()).as("归属校验用显式传入的租户").isEqualTo(42L);
    }

    @Test
    @DisplayName("显式传错租户：跨租户仍被拒（纵深防御不放松）")
    void rejectsWrongTenant() {
        IndexEngineGateway gateway = new IndexEngineGateway(
                mock(RetrieveEngineRegistry.class), (storeId, tenantId) -> tenantId == 42L);

        assertThatThrownBy(() -> gateway.resolveFor(7L, "store-1"))
                .isInstanceOf(RetrieveEngineException.class);
    }
}
