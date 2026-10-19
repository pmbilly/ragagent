package com.ragagent.knowledge.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.contract.knowledge.KnowledgeBaseLookup;
import com.ragagent.contract.knowledge.KnowledgeBaseView;
import com.ragagent.contract.knowledge.KnowledgeView;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import com.ragagent.knowledge.repository.KnowledgeBaseRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * {@link KnowledgeBaseLookup} 的 knowledge 侧实现。
 *
 * <p><b>本类为何仍持有一个可选 mapper</b>：{@code ObjectProvider<KnowledgeMapper>} 是
 * **刻意的装配裁剪容忍**——某些装配/测试裁剪下知识表 mapper 不存在，此时保守返回（与迁移前一致）；
 * 而 {@code KnowledgeRepository} 是无条件 Bean、构造期就要求 mapper，换成它会让"缺 mapper"从
 * "保守返回"变成"上下文启动失败"。故保留该可选依赖（与 禁 {@code @Lazy} 规避环无关），
 * 其余行读取已改走 {@code KnowledgeBaseRepository}。</p>
 *
 * <p>过滤条件与迁移前的 wiki 侧直查<b>逐字一致</b>（软删过滤、{@code LIMIT 1}、
 * 解析状态判定、仓储缺位保守返回），保证这次端口化只是"换依赖方向"而非改行为。</p>
 */
@Component
public class KnowledgeBaseLookupAdapter implements KnowledgeBaseLookup {

    private final KnowledgeBaseRepository kbRepository;
    private final ObjectProvider<KnowledgeMapper> knowledgeMapper;

    public KnowledgeBaseLookupAdapter(KnowledgeBaseRepository kbRepository,
                                      ObjectProvider<KnowledgeMapper> knowledgeMapper) {
        this.kbRepository = kbRepository;
        this.knowledgeMapper = knowledgeMapper;
    }

    @Override
    public KnowledgeBaseView knowledgeBaseById(String kbId) {
        KnowledgeBase kb = kbRepository.findByIdOnly(kbId);
        return view(kb);
    }

    @Override
    public KnowledgeBaseView knowledgeBaseByIdIncludingDeleted(String kbId) {
        KnowledgeBase kb = kbRepository.findByIdAny(kbId);
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
     * 实体 → L1 视图的**域内公用投影**（开放为 {@code public}：装配层
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
