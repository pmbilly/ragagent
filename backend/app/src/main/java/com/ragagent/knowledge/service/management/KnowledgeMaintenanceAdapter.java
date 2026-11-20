package com.ragagent.knowledge.service.management;

import com.ragagent.contract.knowledge.KnowledgeMaintenancePort;
import org.springframework.stereotype.Component;

/**
 * {@link KnowledgeMaintenancePort} 的 knowledge 侧实现：把"删知识条目 / 删知识库"
 * 两个动作转发给域内服务，删除语义（软删、索引清理）留在域内。
 */
@Component
public class KnowledgeMaintenanceAdapter implements KnowledgeMaintenancePort {

    private final KnowledgeService knowledgeService;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeMaintenanceAdapter(KnowledgeService knowledgeService,
                                       KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public void deleteKnowledge(String knowledgeId) {
        knowledgeService.deleteKnowledge(knowledgeId);
    }

    @Override
    public void deleteKnowledgeBase(String knowledgeBaseId) {
        knowledgeBaseService.deleteKnowledgeBase(knowledgeBaseId);
    }
}
