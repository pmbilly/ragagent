package com.ragagent.storage.dto;

import java.util.List;

/**
 * 存储引擎能力面（状态清单 + 连通性检测）。
 *
 * <p>原住 {@code system/dto}：存储探测的 HTTP 面归 storage 后随端口一起搬来。</p>
 */
public final class StorageEngineDtos {

    private StorageEngineDtos() {
    }

    /** 存储引擎状态项。 */
    public record StorageEngineStatusItem(
            String name,
            boolean allowed,
            boolean available,
            String description) {
    }

    /** 存储引擎状态清单。 */
    public record StorageEngineStatusResponse(
            List<StorageEngineStatusItem> engines,
            List<String> allowedProviders,
            boolean minioEnvAvailable) {
    }

    /** 存储连通性检测结果（bucketCreated 仅成功建桶时为 true）。 */
    public record StorageCheckResponse(
            boolean ok,
            String message,
            boolean bucketCreated) {
    }
}
