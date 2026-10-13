package com.ragagent.knowledge.repository;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZoneOffset;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import org.springframework.stereotype.Component;

/**
 * knowledge 行级读写（B263⑧：由 {@code service.KnowledgeService} 的直连 mapper 抽出；对应方案
 * §8.8.2 的 {@code repository/KnowledgeRepository}）。
 *
 * <p>契约（避免复活已断的环）：本层**只做数据访问**——不抛业务异常（重复冲突由 service 侧判定并
 * 抛 {@code DuplicateKnowledgeException}）、不依赖任何 service 类型；方向恒为 repository ← service。</p>
 */
@Component
public class KnowledgeRepository {

    private final KnowledgeMapper knowledgeMapper;

    public KnowledgeRepository(KnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    /** 同 KB 内按文件哈希找未软删的重复行（找不到返回 {@code null}）。 */
    public Knowledge findDuplicateByHash(String kbId, String fileHash) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(Knowledge::getFileHash, fileHash)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 同 KB 内按来源 URL 找未软删的重复行（找不到返回 {@code null}）。 */
    public Knowledge findDuplicateBySource(String kbId, String source) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(Knowledge::getSource, source)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 按 id 取租户内未软删行（找不到返回 {@code null}）。 */
    public Knowledge findById(long tenantId, String id) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .eq(Knowledge::getTenantId, tenantId)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 摘要列写（B263⑩：由 {@code service.KnowledgeFileService} 归位；只写摘要自己那几列）。 */
    public void updateSummaryColumns(Knowledge k) {
        knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, k.getId())
                .set(Knowledge::getDescription, k.getDescription())
                .set(Knowledge::getSummaryStatus, k.getSummaryStatus())
                .set(Knowledge::getMetadata, k.getMetadata() == null
                                ? JsonNodeFactory.instance.objectNode()
                                : k.getMetadata(),
                        "typeHandler=com.ragagent.common.web.PgJsonTypeHandler")
                .set(Knowledge::getUpdatedAt, k.getUpdatedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : k.getUpdatedAt()));
    }

    /** 全列写（DeletedAt/PendingSubtasksCount 除外；零值也落库）——B263⑩ 由 KnowledgeFileService 归位。 */
    public void updateRow(Knowledge k, JsonNode metadata) {
        knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, k.getId())
                .set(Knowledge::getType, k.getType())
                .set(Knowledge::getTitle, k.getTitle())
                .set(Knowledge::getDescription, k.getDescription())
                .set(Knowledge::getSource, k.getSource())
                .set(Knowledge::getParseStatus, k.getParseStatus())
                .set(Knowledge::getSummaryStatus, k.getSummaryStatus())
                .set(Knowledge::getEnableStatus, k.getEnableStatus())
                .set(Knowledge::getEmbeddingModelId, k.getEmbeddingModelId())
                .set(Knowledge::getFileName, k.getFileName())
                .set(Knowledge::getFileType, k.getFileType())
                .set(Knowledge::getFileSize, k.getFileSize() == null ? 0L : k.getFileSize())
                .set(Knowledge::getFileHash, k.getFileHash())
                .set(Knowledge::getFilePath, k.getFilePath())
                .set(Knowledge::getMetadata, metadata,
                        "typeHandler=com.ragagent.common.web.PgJsonTypeHandler")
                .set(Knowledge::getUpdatedAt, k.getUpdatedAt())
                .set(Knowledge::getProcessedAt, k.getProcessedAt())
                .set(Knowledge::getErrorMessage, k.getErrorMessage()));
    }

    /** 只刷 file_hash + updated_at（图片信息更新后的对照哈希）——B263⑩ 归位。 */
    public void updateFileHash(String id, String fileHash, OffsetDateTime now) {
        knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .set(Knowledge::getFileHash, fileHash)
                .set(Knowledge::getUpdatedAt, now));
    }

    /** 插入新行。 */
    public void insert(Knowledge k) {
        knowledgeMapper.insert(k);
    }

    /** 分页查询（条件由调用方以 wrapper 传入：查询构建仍在用例侧，本层只执行）。 */
    public Page<Knowledge> selectPage(
            Page<Knowledge> page,
            LambdaQueryWrapper<Knowledge> qw) {
        return knowledgeMapper.selectPage(page, qw);
    }

    /** 仅按 id 取未软删行（无租户过滤；守卫/权限解析用）。 */
    public Knowledge findByIdOnly(String id) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 软删 knowledge 行（置 deleted_at）。 */
    public void softDelete(String id, OffsetDateTime now) {
        knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id).set(Knowledge::getDeletedAt, now));
    }

    /** 按主键整行更新。 */
    public void updateById(Knowledge k) {
        knowledgeMapper.updateById(k);
    }
}
