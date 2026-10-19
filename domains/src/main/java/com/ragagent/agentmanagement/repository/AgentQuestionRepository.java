package com.ragagent.agentmanagement.repository;

import com.ragagent.agentmanagement.mapper.AgentQuestionMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** Agent 推荐问题只读仓储（跨表随机取样与标签/知识库解析）。 */
@Repository
public class AgentQuestionRepository {

    private final AgentQuestionMapper mapper;

    public AgentQuestionRepository(AgentQuestionMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> listRecommendedFaqChunks(long tenantId, List<String> kbIds,
            boolean hasKb, List<String> knowledgeIds, boolean hasKnowledge,
            List<String> tagIds, boolean hasTag, int limit) {
        return mapper.listRecommendedFaqChunks(tenantId, kbIds, hasKb, knowledgeIds, hasKnowledge,
                tagIds, hasTag, limit);
    }

    public List<Map<String, Object>> listRecentDocumentChunksWithQuestions(long tenantId, List<String> kbIds,
            boolean hasKb, List<String> knowledgeIds, boolean hasKnowledge, int limit) {
        return mapper.listRecentDocumentChunksWithQuestions(tenantId, kbIds, hasKb, knowledgeIds, hasKnowledge,
                limit);
    }

    public List<Map<String, Object>> findKbs(List<String> ids) {
        return mapper.findKbs(ids);
    }

    public List<Map<String, Object>> findTags(long tenantId, List<String> ids) {
        return mapper.findTags(tenantId, ids);
    }

    public List<String> listKnowledgeIdsByTagIds(long tenantId, String kbId, List<String> tagIds) {
        return mapper.listKnowledgeIdsByTagIds(tenantId, kbId, tagIds);
    }
}
