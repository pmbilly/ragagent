package com.ragagent.knowledge.service;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.common.security.TenantAPIKeyScope;
import com.ragagent.common.error.AppError;
import com.ragagent.common.error.BizException;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.domain.KnowledgeTag;
import com.ragagent.knowledge.mapper.KnowledgeBaseMapper;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import com.ragagent.knowledge.mapper.KnowledgeTagMapper;
import org.springframework.stereotype.Component;

/**
 * 知识域访问面收敛点（M2 解环：原 KnowledgeService 门面 helper 下沉）。
 *
 * <p>requireKb / findKb / getKnowledge / getKnowledgeBatch 等是全子服务共用的
 * 访问原语，原先经 {@code @Lazy} 回注门面复用——那是 knowledge 域循环依赖环的根。
 * 现在下沉为本类，子服务直接依赖这里，不再回注门面
 * （R6 禁 {@code @Lazy} 的配套拆法；数据访问契约与门面时代逐字一致）。</p>
 */
@Component
public class KnowledgeAccessHelper {

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeTagMapper tagMapper;

    public KnowledgeAccessHelper(KnowledgeMapper knowledgeMapper,
                                 KnowledgeBaseMapper kbMapper,
                                 KnowledgeTagMapper tagMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.kbMapper = kbMapper;
        this.tagMapper = tagMapper;
    }

    /** 按 id 查 KB（软删不可见）。 */
    public KnowledgeBase findKb(String kbId) {
        return kbMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .isNull(KnowledgeBase::getDeletedAt)
                .last("LIMIT 1"));
    }

    public KnowledgeBase requireKb(String kbId) {
        // KB 受限的 API Key 不能触碰白名单外的库。
        TenantAPIKeyScope.authorizeKnowledgeBases(
                kbId == null ? List.of() : List.of(kbId));
        KnowledgeBase kb = kbMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getTenantId, KnowledgeService.tenantId())
                .isNull(KnowledgeBase::getDeletedAt)
                .last("LIMIT 1"));
        if (kb == null) {
            throw new BizException(AppError.notFound("knowledge base not found"));
        }
        return kb;
    }

    public Knowledge getKnowledge(String id) {
        Knowledge k = knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .eq(Knowledge::getTenantId, KnowledgeService.tenantId())
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
        if (k == null) {
            throw new BizException(AppError.notFound("Knowledge not found"));
        }
        // 按 knowledgeId 操作的端点，
        // 用其所属 KB 做 scope 校验（KB 受限的 Key 不得越界）。
        TenantAPIKeyScope.authorizeKnowledgeBases(
                List.of(k.getKnowledgeBaseId()));
        // 回填 tags（knowledge_tag_relations 连接查；
        // 无关系 → 保持 null，与 契约样例 "tags":null 一致）
        attachTags(k);
        return k;
    }

    /** 调用者空间内的可空读取。 */
    public Knowledge getKnowledgeInTenant(long tenantId, String id) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .eq(Knowledge::getTenantId, tenantId)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    /**
     * 按 (tenant, ids) 批量取，<b>不回填 tags</b>
     */
    public List<Knowledge> getKnowledgeBatch(long tenantId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getTenantId, tenantId)
                .in(Knowledge::getId, ids)
                .isNull(Knowledge::getDeletedAt));
    }

    /**
     * 本仓未实现，共享路径的"补捞"只对同租户行有效，而租户内行
     */
    public List<Knowledge> getKnowledgeBatchWithSharedAccess(long tenantId, List<String> ids) {
        return getKnowledgeBatch(tenantId, ids);
    }

    /** 有关系才回填（无关系保持 null）。 */
    private void attachTags(Knowledge k) {
        if (k == null) {
            return;
        }
        List<KnowledgeTag> rows = tagMapper.selectTagsWithKnowledgeId(List.of(k.getId()));
        if (!rows.isEmpty()) {
            k.setTags(new ArrayList<>(rows.stream().map(KnowledgeService::tagView).toList()));
        }
    }
}
