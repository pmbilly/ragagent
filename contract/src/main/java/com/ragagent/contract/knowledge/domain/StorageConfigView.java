package com.ragagent.contract.knowledge.domain;


/** 知识库对象存储配置视图（jsonb 列 {@code storage_config} 的跨域投影；字段与领域形状逐一对应）。 */
public record StorageConfigView(
        String secretId,
        String secretKey,
        String region,
        String bucketName,
        String appId,
        String pathPrefix,
        String provider,
        String endpoint,
        boolean useSsl,
        boolean forcePathStyle) {
}
