package com.ragagent.embedchannel.repository;

import com.ragagent.embedchannel.domain.EmbedChannelEntity;
import com.ragagent.embedchannel.mapper.EmbedChannelMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 嵌入渠道仓储门面（方法为 {@link EmbedChannelMapper} 的薄转发）。 */
@Repository
public class EmbedChannelRepository {

    private final EmbedChannelMapper mapper;

    public EmbedChannelRepository(EmbedChannelMapper mapper) {
        this.mapper = mapper;
    }

    public void insertChannel(EmbedChannelEntity channel) {
        mapper.insertChannel(channel);
    }

    public List<EmbedChannelEntity> listByAgent(long tenantId, String agentId) {
        return mapper.listByAgent(tenantId, agentId);
    }

    public List<EmbedChannelEntity> listByTenant(long tenantId) {
        return mapper.listByTenant(tenantId);
    }

    /** 整行写回（含 updated_at）。 */
    public void saveChannel(EmbedChannelEntity channel) {
        mapper.saveChannel(channel);
    }

    public void softDelete(long tenantId, String id, OffsetDateTime now) {
        mapper.softDelete(tenantId, id, now);
    }

    /** 按 id 取（不含租户过滤；调用方自行校验归属）。 */
    public EmbedChannelEntity getById(String id) {
        return mapper.getById(id);
    }
}
