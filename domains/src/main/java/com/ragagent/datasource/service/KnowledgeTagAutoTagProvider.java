package com.ragagent.datasource.service;

import com.ragagent.contract.error.BizException;
import org.springframework.stereotype.Component;
import com.ragagent.contract.error.AppError;
import com.ragagent.platform.knowledge.KnowledgeCatalogPort;
import com.ragagent.contract.knowledge.KnowledgeTagView;
import com.ragagent.contract.knowledge.KnowledgeBaseRowView;

/**
 * 自动标签的生产实现：同名标签存在 → 直接用；否则走 {@code KnowledgeTagService#createTag}
 * 建（其内部承载 KB 404 / 写权限 403 / 重名 409 的校验链）。
 *
 * <p><b>标签失败不致命</b>：调用方（{@code resolveAutoTagIds}）catch 后
 * warn 并继续同步（条目只是没有自动标签）。</p>
 */
@Component
public class KnowledgeTagAutoTagProvider implements AutoTagProvider {

    private final KnowledgeCatalogPort knowledgeCatalog;

    public KnowledgeTagAutoTagProvider(KnowledgeCatalogPort knowledgeCatalog) {
        this.knowledgeCatalog = knowledgeCatalog;
    }

    @Override
    public String findOrCreateTagId(String kbId, String name) {
        String trimmed = name == null ? "" : name.strip();
        if (kbId == null || kbId.isEmpty() || trimmed.isEmpty()) {
            // 入参校验：kbId 与标签名都必填
            throw new BizException(AppError.badRequest(
                    "知识库ID和标签名称不能为空"));
        }
        // 先按 id 读 KB（无租户过滤）
        KnowledgeBaseRowView kb = knowledgeCatalog.getAllTenantById(kbId);
        if (kb == null) {
            throw BizException.notFound("knowledge base not found");
        }
        // 先查现有标签（tenant + kb + name）
        KnowledgeTagView existing = knowledgeCatalog.findKnowledgeTagByName(kb.getTenantId(), kbId, trimmed);
        if (existing != null) {
            return existing.id();
        }
        // 建标签；校验链（写权限/重名）在 createTag 内
        KnowledgeTagView created = knowledgeCatalog.createKnowledgeTag(kbId, trimmed, null, 0);
        return created.id();
    }
}
