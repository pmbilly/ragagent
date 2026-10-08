package com.ragagent.knowledge.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.common.knowledge.KnowledgeBaseLookup;
import com.ragagent.common.knowledge.KnowledgeBaseView;
import com.ragagent.common.knowledge.KnowledgeView;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.mapper.KnowledgeBaseMapper;
import com.ragagent.knowledge.mapper.KnowledgeMapper;

/**
 * {@link KnowledgeBaseLookup} 的 knowledge 侧实现（B98/C2）。
 *
 * <p>过滤条件与迁移前的 wiki 侧直查<b>逐字一致</b>（软删过滤、{@code LIMIT 1}、
 * 解析状态判定、仓储缺位保守返回），保证 C2 只是"换依赖方向"而非改行为。</p>
 */
@Component
public class KnowledgeBaseLookupAdapter implements KnowledgeBaseLookup {

    private final KnowledgeBaseMapper kbMapper;
    private final ObjectProvider<KnowledgeMapper> knowledgeMapper;

    public KnowledgeBaseLookupAdapter(KnowledgeBaseMapper kbMapper,
                                      ObjectProvider<KnowledgeMapper> knowledgeMapper) {
        this.kbMapper = kbMapper;
        this.knowledgeMapper = knowledgeMapper;
    }

    @Override
    public KnowledgeBaseView knowledgeBaseById(String kbId) {
        KnowledgeBase kb = kbMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .isNull(KnowledgeBase::getDeletedAt)
                .last("LIMIT 1"));
        return view(kb);
    }

    @Override
    public KnowledgeBaseView knowledgeBaseByIdIncludingDeleted(String kbId) {
        KnowledgeBase kb = kbMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .last("LIMIT 1"));
        return view(kb);
    }

    @Override
    public KnowledgeView knowledgeById(String knowledgeId) {
        KnowledgeMapper mapper = knowledgeMapper.getIfAvailable();
        if (mapper == null || knowledgeId == null || knowledgeId.isEmpty()) {
            return null;
        }
        Knowledge kn = mapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, knowledgeId)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
        return kn == null ? null : new KnowledgeView(kn.getId(), kn.getTitle());
    }

    @Override
    public boolean knowledgeGone(String knowledgeId) {
        KnowledgeMapper mapper = knowledgeMapper.getIfAvailable();
        if (mapper == null) {
            // 无知识仓储可查（装配裁剪）—— 无法判定，保守地当作"还在"（与迁移前一致）。
            return false;
        }
        Knowledge kn;
        try {
            kn = mapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                    .eq(Knowledge::getId, knowledgeId)
                    .isNull(Knowledge::getDeletedAt));
        } catch (Exception e) {
            return true;
        }
        if (kn == null) {
            return true;
        }
        return Knowledge.PARSE_DELETING.equals(kn.getParseStatus())
                || Knowledge.PARSE_CANCELLED.equals(kn.getParseStatus());
    }

    @Override
    public boolean knowledgeExists(String knowledgeId) {
        KnowledgeMapper mapper = knowledgeMapper.getIfAvailable();
        if (mapper == null) {
            return false;
        }
        Knowledge k = mapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, knowledgeId)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
        return k != null;
    }

    /**
     * 实体 → L1 视图的**域内公用投影**（B114 起转 public：装配层
     * {@code session/service/QaWiring} 需要把 KB 交给 chat 管线，
     * 而实体不许越层 ⇒ 复用同一份投影，避免映射逻辑两处漂移）。
     */
    public static KnowledgeBaseView view(KnowledgeBase kb) {
        if (kb == null) {
            return null;
        }
        KnowledgeBaseView v = new KnowledgeBaseView();
        v.setId(kb.getId());
        v.setTenantId(kb.getTenantId() == null ? 0L : kb.getTenantId());
        v.setName(kb.getName());
        v.setType(kb.getType());
        v.setDescription(kb.getDescription());
        v.setCreatorId(kb.getCreatorId());
        v.setSummaryModelId(kb.getSummaryModelId());
        v.setEmbeddingModelId(kb.getEmbeddingModelId());
        v.setWikiEnabled(kb.getIndexingStrategy() != null && kb.getIndexingStrategy().isWikiEnabled());
        v.setWikiConfig(kb.getWikiConfig());
        v.setVectorEnabled(kb.getIndexingStrategy() != null && kb.getIndexingStrategy().isVectorEnabled());
        v.setKeywordEnabled(kb.getIndexingStrategy() != null && kb.getIndexingStrategy().isKeywordEnabled());
        v.setExtractConfig(kb.getExtractConfig());
        v.setCreatedAt(kb.getCreatedAt());
        v.setUpdatedAt(kb.getUpdatedAt());
        return v;
    }
}
