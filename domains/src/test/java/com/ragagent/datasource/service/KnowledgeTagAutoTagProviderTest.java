package com.ragagent.datasource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ragagent.contract.error.BizException;
import com.ragagent.common.knowledge.KnowledgeCatalogPort;
import com.ragagent.contract.knowledge.KnowledgeTagView;
import com.ragagent.common.knowledge.KnowledgeBaseRowView;

/**
 * 自动标签生产实现（按名称查找或创建标签）：
 * 空参 400 文案、KB 缺失 404、同名直取不建、缺名走 createTag。
 */
class KnowledgeTagAutoTagProviderTest {

    private KnowledgeCatalogPort catalog;
    private KnowledgeTagAutoTagProvider provider;

    @BeforeEach
    void setUp() {
        catalog = mock(KnowledgeCatalogPort.class);
        provider = new KnowledgeTagAutoTagProvider(catalog);
    }

    @Test
    void blankArgsThrowBadRequestWithGoText() {
        assertThatThrownBy(() -> provider.findOrCreateTagId("kb-1", "  "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("知识库ID和标签名称不能为空");
        assertThatThrownBy(() -> provider.findOrCreateTagId("", "tag"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void missingKbThrowsNotFound() {
        when(catalog.getAllTenantById("kb-1")).thenReturn(null);
        assertThatThrownBy(() -> provider.findOrCreateTagId("kb-1", "tag"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("knowledge base not found");
    }

    @Test
    void existingTagIsReusedWithoutCreate() {
        KnowledgeBaseRowView kb = new KnowledgeBaseRowView();
        kb.setId("kb-1");
        kb.setTenantId(10002L);
        when(catalog.getAllTenantById("kb-1")).thenReturn(kb);
        KnowledgeTagView existing = new KnowledgeTagView("tag-1", "kb-1", 10002L, "tag", null, 0);
        when(catalog.findKnowledgeTagByName(10002L, "kb-1", "tag")).thenReturn(existing);

        assertThat(provider.findOrCreateTagId("kb-1", " tag ")).isEqualTo("tag-1");
        verify(catalog, never()).createKnowledgeTag(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void missingTagIsCreatedViaCreateTag() {
        KnowledgeBaseRowView kb = new KnowledgeBaseRowView();
        kb.setId("kb-1");
        kb.setTenantId(10002L);
        when(catalog.getAllTenantById("kb-1")).thenReturn(kb);
        when(catalog.findKnowledgeTagByName(10002L, "kb-1", "tag")).thenReturn(null);
        KnowledgeTagView created = new KnowledgeTagView("tag-new", "kb-1", 10002L, "tag", null, 0);
        when(catalog.createKnowledgeTag("kb-1", "tag", null, 0)).thenReturn(created);

        assertThat(provider.findOrCreateTagId("kb-1", "tag")).isEqualTo("tag-new");
    }
}
