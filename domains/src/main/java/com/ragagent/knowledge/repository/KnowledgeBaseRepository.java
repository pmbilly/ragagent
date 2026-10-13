package com.ragagent.knowledge.repository;

import java.time.OffsetDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.mapper.KnowledgeBaseMapper;
import org.springframework.stereotype.Component;

/**
 * knowledge_base 行级读写（B263⑪：由 {@code service.KnowledgeBaseService} 的直连 kbMapper 抽出；
 * 对应方案 §8.8.2 的 {@code repository/KnowledgeBaseRepository}）。
 *
 * <p>契约同 {@link KnowledgeRepository}：只做数据访问，不抛业务异常、不依赖 service，
 * 方向恒为 repository ← service。</p>
 */
@Component
public class KnowledgeBaseRepository {

    private final KnowledgeBaseMapper kbMapper;

    public KnowledgeBaseRepository(KnowledgeBaseMapper kbMapper) {
        this.kbMapper = kbMapper;
    }

    /** 插入新行。 */
    /**
     * 同租户 + 未软删的全部 KB（**含临时库、不排序**）。
     *
     * <p>与 {@link #listActiveByTenant(long)} 的区别是后者额外排除临时库并按 created_at 倒序；
     * 检索面要的是"租户下所有库"（B263⑯ 由 {@code KnowledgeSearchService} 的直连搬入）。</p>
     */
    public List<KnowledgeBase> listByTenant(long tenantId) {
        return kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getTenantId, tenantId)
                .isNull(KnowledgeBase::getDeletedAt));
    }

    public void insert(KnowledgeBase kb) {
        kbMapper.insert(kb);
    }

    /** 租户内 + 未软删按 id 取行（找不到返回 {@code null}）。 */
    public KnowledgeBase findById(long tenantId, String id) {
        return kbMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getTenantId, tenantId)
                .isNull(KnowledgeBase::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** **无租户过滤**（move/copy/duplicate 的 403/404 分歧前提）：未软删按 id 取行。 */
    public KnowledgeBase findByIdOnly(String id) {
        return kbMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .isNull(KnowledgeBase::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 租户内非临时库，created_at DESC（两处列表查询同形，合并为一份）。 */
    public List<KnowledgeBase> listActiveByTenant(long tenantId) {
        return kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getTenantId, tenantId)
                .eq(KnowledgeBase::isIsTemporary, false)
                .isNull(KnowledgeBase::getDeletedAt)
                .orderByDesc(KnowledgeBase::getCreatedAt));
    }

    /** 移动目标列表：同租户 + 同 type + 同 embedding_model_id + 非临时 + 排除自身；created_at DESC。 */
    public List<KnowledgeBase> listMoveTargets(long tenantId, String type, String embeddingModelId,
                                                         String excludeId) {
        return kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getTenantId, tenantId)
                .eq(KnowledgeBase::getType, type)
                .eq(KnowledgeBase::getEmbeddingModelId, embeddingModelId == null ? "" : embeddingModelId)
                .eq(KnowledgeBase::isIsTemporary, false)
                .ne(KnowledgeBase::getId, excludeId)
                .isNull(KnowledgeBase::getDeletedAt)
                .orderByDesc(KnowledgeBase::getCreatedAt));
    }

    /** 按主键整行更新。 */
    public void updateById(KnowledgeBase kb) {
        kbMapper.updateById(kb);
    }

    /** 软删 KB 行（置 deleted_at）。 */
    public void softDelete(String id, OffsetDateTime now) {
        kbMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id).set(KnowledgeBase::getDeletedAt, now));
    }
}
