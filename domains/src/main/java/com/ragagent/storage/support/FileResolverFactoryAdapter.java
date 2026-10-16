package com.ragagent.storage.support;

import org.springframework.stereotype.Component;

import com.ragagent.platform.storage.support.FileResolverFactory;
import com.ragagent.contract.storage.support.FileUrlPort;
import com.ragagent.contract.storage.support.Resolver;
import com.ragagent.contract.storage.support.StorageBackendResolver;
import com.ragagent.contract.tenant.TenantConfigLookup.TenantStorageView;

/**
 * {@link FileResolverFactory} 的存储域实现：把 L1 的"按请求组装解析器"落到域内实现
 * {@link FileServiceResolver}（它依赖 provider 工厂，留在本域）。
 */
@Component
public class FileResolverFactoryAdapter implements FileResolverFactory {

    @Override
    public Resolver create(TenantStorageView tenant, FileUrlPort defaultSvc,
                           StorageBackendResolver storageResolver) {
        return new FileServiceResolver(tenant, defaultSvc, storageResolver);
    }
}
