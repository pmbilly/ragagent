package com.ragagent.mcp.repository;

import com.ragagent.mcp.domain.McpService;
import com.ragagent.mcp.mapper.McpServiceMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MCP 服务仓储门面（方法为 {@link McpServiceMapper} 的薄转发）。 */
@Repository
public class McpServiceRepository {

    private final McpServiceMapper mapper;

    public McpServiceRepository(McpServiceMapper mapper) {
        this.mapper = mapper;
    }

    public McpService getByIdForTenant(long tenantId, String id) {
        return mapper.getByIdForTenant(tenantId, id);
    }

    public List<McpService> listForTenant(long tenantId) {
        return mapper.listForTenant(tenantId);
    }

    public List<McpService> listEnabledForTenant(long tenantId) {
        return mapper.listEnabledForTenant(tenantId);
    }

    public List<McpService> listByIdsForTenant(long tenantId, List<String> ids) {
        return mapper.listByIdsForTenant(tenantId, ids);
    }

    public void insert(McpService service) {
        mapper.insert(service);
    }

    /** 只更新非空列（局部更新语义）。 */
    public int updatePartial(McpService service) {
        return mapper.updatePartial(service);
    }

    public int softDelete(long tenantId, String id, OffsetDateTime now) {
        return mapper.softDelete(tenantId, id, now);
    }
}
