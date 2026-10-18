package com.ragagent.knowledge.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ragagent.contract.retrieval.IndexEngine;
import com.ragagent.contract.retrieval.IndexEngineResolver;
import com.ragagent.knowledge.domain.KnowledgeBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 知识写链的向量店路由网关（R3b 后口径）：本类只剩**域判定 + 转交**——
 * 绑定解析（归属校验、失败分类、虚拟线程下的显式租户）已随行为沉到 retrieval-engine 的
 * {@code IndexEngineGateway}，断言随迁至该模块的 {@code IndexEngineGatewayTest}。
 */
class KnowledgeVectorWritesTest {

    private final IndexEngineResolver resolver = mock(IndexEngineResolver.class);

    @Test
    @DisplayName("未绑定 store：返回 null，不触解析端口（保持 pg 直连路径）")
    void unboundKbSkipsResolver() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId("kb-1");

        assertThat(new KnowledgeVectorWrites(resolver).boundEngine(kb, 42L)).isNull();
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("kb 为 null：返回 null，不触解析端口")
    void nullKbSkipsResolver() {
        assertThat(new KnowledgeVectorWrites(resolver).boundEngine(null, 42L)).isNull();
        verifyNoInteractions(resolver);
    }

    @Test
    @DisplayName("绑定 store：按（租户, storeId）转交解析端口")
    void boundKbDelegatesWithTenantAndStore() {
        IndexEngine engine = mock(IndexEngine.class);
        when(resolver.resolveFor(42L, "store-1")).thenReturn(engine);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTenantId(42L);
        kb.setVectorStoreId("store-1");

        assertThat(new KnowledgeVectorWrites(resolver).boundEngine(kb, 42L)).isSameAs(engine);
        verify(resolver).resolveFor(42L, "store-1");
    }

    @Test
    @DisplayName("租户为 null：按 0 转交（无上下文调用的旧语义）")
    void nullTenantFallsBackToZero() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setVectorStoreId("store-1");

        new KnowledgeVectorWrites(resolver).boundEngine(kb, null);

        verify(resolver).resolveFor(0L, "store-1");
    }
}
