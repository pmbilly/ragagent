package com.ragagent.storage.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ragagent.platform.storage.fileserve.FileTransport;
import com.ragagent.platform.storage.fileserve.ProviderFileStorePort;
import com.ragagent.platform.config.TenantConfigLookup;
import com.ragagent.storage.fileserve.ProviderFileContentService;
import com.ragagent.storage.fileserve.StorageFileResolver;
import com.ragagent.storage.provider.FileService;

/**
 * {@link ProviderFileStorePort} 的存储域实现：把租户配置解析成 provider 服务，并暴露写/读/删操作。
 *
 * <p><b>代码来源</b>：解析口径自知识域 {@code TenantFileStorage#resolveProvider} 原样搬入
 * （租户视图取不到 ⇒ {@link Resolution#none()}＝"当作没有云配置"；解析失败 ⇒ error 非空）；操作面
 * 逐字复刻原写/读/删/直出路径（含 {@code ProviderFileContentService} 的流式打开与
 * {@link FileService.UploadFile} 的惰性开流）。</p>
 */
@Component
public class ProviderFileStoreAdapter implements ProviderFileStorePort {

    private final StorageFileResolver resolver;
    private final TenantConfigLookup tenantConfigLookup;
    private final String localBaseDir;

    public ProviderFileStoreAdapter(StorageFileResolver resolver,
                                    TenantConfigLookup tenantConfigLookup,
                                    @Value("${weknora.storage.local-base-dir:${LOCAL_STORAGE_BASE_DIR:/data/files}}")
                                            String localBaseDir) {
        this.resolver = resolver;
        this.tenantConfigLookup = tenantConfigLookup;
        this.localBaseDir = localBaseDir;
    }

    @Override
    public Resolution resolve(long tenantId, String provider) {
        var tenant = tenantId <= 0 ? null : tenantConfigLookup.storageView(tenantId);
        if (tenant == null) {
            // 取不到租户（无 tenantId / 库里无行）→ 当作没有云配置，走本地契约
            return Resolution.none();
        }
        StorageFileResolver.ProviderResolution pr =
                resolver.resolveProviderService(tenant, "", provider, localBaseDir);
        if (!pr.ok()) {
            return new Resolution(pr.provider(), pr.error(), null);
        }
        return new Resolution(pr.provider(), null,
                new ProviderOps(pr.service(), new ProviderFileContentService(pr.service())));
    }

    /** {@link Ops} 实现：直连 provider 服务（写面用原始服务，不经营销 catalog 装饰）。 */
    private record ProviderOps(FileService service, ProviderFileContentService contentService)
            implements Ops {

        @Override
        public String saveFile(byte[] content, String fileName, long tenantId, String knowledgeId) {
            return service.saveFile(
                    new FileService.UploadFile(fileName, content.length,
                            () -> new ByteArrayInputStream(content), ""),
                    tenantId, knowledgeId);
        }

        @Override
        public String saveBytes(byte[] data, long tenantId, String fileName, boolean temp) {
            return service.saveBytes(data, tenantId, fileName, temp);
        }

        @Override
        public String getFileURL(String filePath) {
            return service.getFileURL(filePath);
        }

        @Override
        public InputStream getFile(String filePath) {
            return service.getFile(filePath);
        }

        @Override
        public FileTransport.OpenedFile open(String filePath) throws IOException {
            return contentService.getFile(filePath);
        }

        @Override
        public void deleteFile(String filePath) {
            service.deleteFile(filePath);
        }
    }
}
