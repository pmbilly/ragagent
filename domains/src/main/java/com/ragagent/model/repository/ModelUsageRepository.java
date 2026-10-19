package com.ragagent.model.repository;

import com.ragagent.model.mapper.ModelUsageMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** 模型使用关系只读仓储（删除守卫的引用统计）。 */
@Repository
public class ModelUsageRepository {

    private final ModelUsageMapper mapper;

    public ModelUsageRepository(ModelUsageMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> listKnowledgeBaseRows(long tenantId) {
        return mapper.listKnowledgeBaseRows(tenantId);
    }

    public List<Map<String, Object>> listCustomAgentRows(long tenantId) {
        return mapper.listCustomAgentRows(tenantId);
    }
}
