package com.ragagent.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地存储的部署配置。
 *
 * <p>env 名保持原样：{@code LOCAL_STORAGE_BASE_DIR} → {@code local.storage-base-dir}
 * （Spring 松散绑定）。只承载原始串；缺省 {@code ${user.home}/ragagent-data/files}
 * 的回落规则统一在 {@code StoragePaths.localStorageBaseDir()} 与各 {@code @Value} 读点。</p>
 */
@ConfigurationProperties(prefix = "local")
public record LocalStorageEnvProperties(String storageBaseDir) {
}
