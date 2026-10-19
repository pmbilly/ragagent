package com.ragagent.im.repository;

import com.ragagent.im.domain.ImChannelEntity;
import com.ragagent.im.mapper.ImChannelMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** IM 渠道仓储门面（方法为 {@link ImChannelMapper} 的薄转发）。 */
@Repository
public class ImChannelRepository {

    private final ImChannelMapper mapper;

    public ImChannelRepository(ImChannelMapper mapper) {
        this.mapper = mapper;
    }

    public ImChannelEntity getById(String id) {
        return mapper.getById(id);
    }

    public ImChannelEntity getByIdAndTenant(String id, long tenantId) {
        return mapper.getByIdAndTenant(id, tenantId);
    }

    public List<ImChannelEntity> listEnabled() {
        return mapper.listEnabled();
    }

    public List<ImChannelEntity> listByAgent(String agentId, long tenantId) {
        return mapper.listByAgent(agentId, tenantId);
    }

    public List<Map<String, Object>> listByTenantWithAgent(long tenantId, List<String> builtinIds) {
        return mapper.listByTenantWithAgent(tenantId, builtinIds);
    }

    public ImChannelEntity findByBotIdentity(String botIdentity, String excludeId) {
        return mapper.findByBotIdentity(botIdentity, excludeId);
    }

    public void insertChannel(ImChannelEntity channel) {
        mapper.insertChannel(channel);
    }

    public int saveChannel(ImChannelEntity channel) {
        return mapper.saveChannel(channel);
    }

    public int softDelete(String id, long tenantId, OffsetDateTime now) {
        return mapper.softDelete(id, tenantId, now);
    }

    public int deleteHardByAgent(String agentId, long tenantId) {
        return mapper.deleteHardByAgent(agentId, tenantId);
    }
}
