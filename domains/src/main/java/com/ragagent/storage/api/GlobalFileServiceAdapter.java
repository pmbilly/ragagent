package com.ragagent.storage.api;

import com.ragagent.contract.storage.fileserve.GlobalFileServicePort;
import com.ragagent.contract.storage.fileserve.WritableFileContentService;
import com.ragagent.storage.fileserve.StorageFileResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link GlobalFileServicePort} 的存储域实现：委托 {@link StorageFileResolver#globalFileService(String)}，
 * 并保留调用方原有的<b>懒建</b>语义（首次取用才碰文件系统）。
 */
@Component
public class GlobalFileServiceAdapter implements GlobalFileServicePort {

    private final StorageFileResolver resolver;
    private final String localBaseDir;

    private volatile WritableFileContentService cached;

    public GlobalFileServiceAdapter(StorageFileResolver resolver,
                                    @Value("${weknora.storage.local-base-dir:${LOCAL_STORAGE_BASE_DIR:/data/files}}")
                                            String localBaseDir) {
        this.resolver = resolver;
        this.localBaseDir = localBaseDir;
    }

    @Override
    public synchronized WritableFileContentService globalService() {
        if (cached == null) {
            cached = resolver.globalFileService(localBaseDir);
        }
        return cached;
    }
}
