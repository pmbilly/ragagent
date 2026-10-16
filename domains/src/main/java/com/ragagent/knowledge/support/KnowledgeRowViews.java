package com.ragagent.knowledge.support;

import java.util.ArrayList;
import java.util.List;

import com.ragagent.contract.knowledge.KnowledgePageView;
import com.ragagent.contract.knowledge.KnowledgeRowView;
import com.ragagent.knowledge.domain.Knowledge;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragagent.platform.web.JsonMappers;

/**
 * 知识行实体 → 行视图的**域内单一映射**（供端口适配器、投影器调用点共用）。
 *
 * <p>实体已归位 knowledge/domain；L1 的 KnowledgeCatalogPort 知识行面与
 * KnowledgeProjectionAdapter 都改吃视图 ⇒ 域内"喂实体"的点统一经本类
 * （避免映射逻辑多处漂移，同 ChunkRowViews / KnowledgeBaseRowViews 先例）。</p>
 */
public final class KnowledgeRowViews {

    private KnowledgeRowViews() {
    }

    /** 实体 → 行视图（逐字段搬运；null 透传）。 */
    public static KnowledgeRowView rowView(Knowledge e) {
        if (e == null) {
            return null;
        }
        KnowledgeRowView v = new KnowledgeRowView();
        v.setId(e.getId());
        v.setTenantId(e.getTenantId());
        v.setKnowledgeBaseId(e.getKnowledgeBaseId());
        v.setTags(JsonMappers.toJavaList(e.getTags()));
        v.setType(e.getType());
        v.setTitle(e.getTitle());
        v.setDescription(e.getDescription());
        v.setSource(e.getSource());
        v.setChannel(e.getChannel());
        v.setParseStatus(e.getParseStatus());
        v.setPendingSubtasksCount(e.getPendingSubtasksCount());
        v.setSummaryStatus(e.getSummaryStatus());
        v.setEnableStatus(e.getEnableStatus());
        v.setEmbeddingModelId(e.getEmbeddingModelId());
        v.setFileName(e.getFileName());
        v.setFolderPath(e.getFolderPath());
        v.setFileType(e.getFileType());
        v.setFileSize(e.getFileSize());
        v.setFileHash(e.getFileHash());
        v.setFilePath(e.getFilePath());
        v.setStorageSize(e.getStorageSize());
        v.setMetadata(JsonMappers.toJavaValue(e.getMetadata()));
        v.setCustomMetadata(JsonMappers.toJavaValue(e.getCustomMetadata()));
        v.setLastFaqImportResult(JsonMappers.toJavaValue(e.getLastFaqImportResult()));
        v.setCreatedAt(e.getCreatedAt());
        v.setUpdatedAt(e.getUpdatedAt());
        v.setProcessedAt(e.getProcessedAt());
        v.setErrorMessage(e.getErrorMessage());
        v.setDeletedAt(e.getDeletedAt());
        v.setKnowledgeBaseName(e.getKnowledgeBaseName());
        return v;
    }

    /** 批量（null 安全）。 */
    public static List<KnowledgeRowView> rowViews(List<Knowledge> rows) {
        if (rows == null) {
            return List.of();
        }
        List<KnowledgeRowView> out = new ArrayList<>(rows.size());
        for (Knowledge e : rows) {
            out.add(rowView(e));
        }
        return out;
    }

    /** 行视图 → 实体（写面用；经实体 setter ⇒ 保留归一语义；null 透传）。 */
    public static Knowledge toEntity(KnowledgeRowView v) {
        if (v == null) {
            return null;
        }
        Knowledge e = new Knowledge();
        e.setId(v.getId());
        e.setTenantId(v.getTenantId());
        e.setKnowledgeBaseId(v.getKnowledgeBaseId());
        e.setTags(JsonMappers.toJsonList(v.getTags()));
        e.setType(v.getType());
        e.setTitle(v.getTitle());
        e.setDescription(v.getDescription());
        e.setSource(v.getSource());
        e.setChannel(v.getChannel());
        e.setParseStatus(v.getParseStatus());
        e.setPendingSubtasksCount(v.getPendingSubtasksCount());
        e.setSummaryStatus(v.getSummaryStatus());
        e.setEnableStatus(v.getEnableStatus());
        e.setEmbeddingModelId(v.getEmbeddingModelId());
        e.setFileName(v.getFileName());
        e.setFolderPath(v.getFolderPath());
        e.setFileType(v.getFileType());
        e.setFileSize(v.getFileSize());
        e.setFileHash(v.getFileHash());
        e.setFilePath(v.getFilePath());
        e.setStorageSize(v.getStorageSize());
        e.setMetadata(JsonMappers.toJsonNode(v.getMetadata()));
        e.setCustomMetadata(JsonMappers.toJsonNode(v.getCustomMetadata()));
        e.setLastFaqImportResult(JsonMappers.toJsonNode(v.getLastFaqImportResult()));
        e.setCreatedAt(v.getCreatedAt());
        e.setUpdatedAt(v.getUpdatedAt());
        e.setProcessedAt(v.getProcessedAt());
        e.setErrorMessage(v.getErrorMessage());
        e.setDeletedAt(v.getDeletedAt());
        e.setKnowledgeBaseName(v.getKnowledgeBaseName());
        return e;
    }

    /** 把落库后的实体字段回填到传入视图（保持"同对象被填充"的旧语义；null 安全）。 */
    public static void copyInto(KnowledgeRowView v, Knowledge e) {
        if (v == null || e == null) {
            return;
        }
        KnowledgeRowView fresh = rowView(e);
        v.setId(fresh.getId());
        v.setTenantId(fresh.getTenantId());
        v.setKnowledgeBaseId(fresh.getKnowledgeBaseId());
        v.setTags(fresh.getTags());
        v.setType(fresh.getType());
        v.setTitle(fresh.getTitle());
        v.setDescription(fresh.getDescription());
        v.setSource(fresh.getSource());
        v.setChannel(fresh.getChannel());
        v.setParseStatus(fresh.getParseStatus());
        v.setPendingSubtasksCount(fresh.getPendingSubtasksCount());
        v.setSummaryStatus(fresh.getSummaryStatus());
        v.setEnableStatus(fresh.getEnableStatus());
        v.setEmbeddingModelId(fresh.getEmbeddingModelId());
        v.setFileName(fresh.getFileName());
        v.setFolderPath(fresh.getFolderPath());
        v.setFileType(fresh.getFileType());
        v.setFileSize(fresh.getFileSize());
        v.setFileHash(fresh.getFileHash());
        v.setFilePath(fresh.getFilePath());
        v.setStorageSize(fresh.getStorageSize());
        v.setMetadata(fresh.getMetadata());
        v.setCustomMetadata(fresh.getCustomMetadata());
        v.setLastFaqImportResult(fresh.getLastFaqImportResult());
        v.setCreatedAt(fresh.getCreatedAt());
        v.setUpdatedAt(fresh.getUpdatedAt());
        v.setProcessedAt(fresh.getProcessedAt());
        v.setErrorMessage(fresh.getErrorMessage());
        v.setDeletedAt(fresh.getDeletedAt());
        v.setKnowledgeBaseName(fresh.getKnowledgeBaseName());
    }

    /** MyBatis-Plus 分页（实体页 → 契约分页视图；null 透传）。 */
    public static KnowledgePageView pageView(
            Page<Knowledge> p) {
        if (p == null) {
            return null;
        }
        return new KnowledgePageView(rowViews(p.getRecords()), p.getTotal());
    }
}
