package com.ragagent.storage.api;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ragagent.contract.storage.ResourceCatalogPort;
import com.ragagent.storage.domain.StoredResource;
import com.ragagent.storage.service.ResourceCatalogService;

/**
 * {@link ResourceCatalogPort} 的存储域实现：把目录服务的解析与绑定映射成最小视图。
 *
 * <p>解析口径与调用方原先直连目录服务时一致：引用不可解析 ⇒ {@link Optional#empty()}；
 * 绑定动作逐字转发（服务内部"资源不存在则忽略"的语义保持不变）。</p>
 */
@Component
public class ResourceCatalogAdapter implements ResourceCatalogPort {

    private final ResourceCatalogService catalog;

    public ResourceCatalogAdapter(ResourceCatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<ResourceView> resolve(String reference) {
        return catalog.resolve(reference)
                .map(ResourceCatalogAdapter::toView);
    }

    @Override
    public void bind(String reference, String ownerType, String ownerId, String relation) {
        catalog.bind(reference, ownerType, ownerId, relation);
    }

    @Override
    public String register(long tenantId, String physicalPath, ResourceMeta meta) {
        return catalog.register(tenantId, physicalPath,
                new ResourceCatalogService.ResourceRegistration(
                        meta == null ? null : meta.kind(),
                        meta == null ? null : meta.mimeType(),
                        meta == null ? null : meta.originalName(),
                        meta == null ? 0 : meta.size(),
                        meta == null ? null : meta.contentHash(),
                        meta != null && meta.temporary()));
    }

    @Override
    public void markDeletedByLocation(long tenantId, String physicalPath) {
        catalog.markDeletedByLocation(tenantId, physicalPath);
    }

    private static ResourceView toView(StoredResource resource) {
        return new ResourceView(resource.getId(), resource.getTenantId());
    }
}
