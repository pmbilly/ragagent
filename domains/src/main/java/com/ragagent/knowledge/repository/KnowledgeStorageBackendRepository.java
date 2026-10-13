package com.ragagent.knowledge.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.storage.domain.StorageBackend;
import com.ragagent.knowledge.mapper.StorageBackendMapper;
import org.springframework.stereotype.Component;

/**
 * storage_backend 行级读写（B263⑫：由 {@code service.KnowledgeBaseService} 的直连
 * storageBackendMapper 抽出）。契约同上（只做数据访问）。
 *
 * <p>**命名注意**：{@code storage} 域已有一个同名 {@code StorageBackendRepository}
 * （{@code storage.mapper}，基于 JdbcClient）——同名会让 Spring Bean 名冲突、整个上下文起不来
 * （本步实测：boot 1211 条测试全挂）。故知识域这个加 {@code Knowledge} 前缀区分。</p>
 */
@Component
public class KnowledgeStorageBackendRepository {

    private final StorageBackendMapper storageBackendMapper;

    public KnowledgeStorageBackendRepository(StorageBackendMapper storageBackendMapper) {
        this.storageBackendMapper = storageBackendMapper;
    }

    /** 租户内 + 未软删按 id 取后端（找不到返回 {@code null}）。 */
    public StorageBackend findById(long tenantId, String id) {
        return storageBackendMapper.selectOne(new LambdaQueryWrapper<StorageBackend>()
                .eq(StorageBackend::getId, id)
                .eq(StorageBackend::getTenantId, tenantId)
                .isNull(StorageBackend::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 租户内按 provider 取 **legacy alias** 后端（未软删；找不到返回 {@code null}）。 */
    public StorageBackend findLegacyAliasByProvider(long tenantId, String provider) {
        return storageBackendMapper.selectOne(new LambdaQueryWrapper<StorageBackend>()
                .eq(StorageBackend::getTenantId, tenantId)
                .eq(StorageBackend::getProvider, provider)
                .eq(StorageBackend::isLegacyAlias, true)
                .isNull(StorageBackend::getDeletedAt)
                .last("LIMIT 1"));
    }
}
