package com.ragagent.storage.provider;

import com.ragagent.contract.tenant.StorageEngineConfig;
import com.ragagent.platform.security.SsrfGuard;
import java.util.Locale;

/**
 * 按租户存储配置造 provider 专属 {@link FileService}。
 *
 * <p>provider 的空值回落与各厂商的构造细节都在 {@link StorageProviders} 的登记表里；
 * 本类只做入口归一（provider 为空时回落到 {@code default_provider}）。</p>
 */
public final class FileServiceFactory {

    private FileServiceFactory() {
    }

    /** 解析结果：服务 + 归一化后的 provider 名。 */
    public record Created(FileService service, String provider) {
    }

    /** 便捷入口：用默认 SSRF 闸（生产装配应注入 Spring 管理的单例）。 */
    public static Created fromStorageConfig(String provider, StorageEngineConfig sec,
                                            String localBaseDir) {
        return fromStorageConfig(provider, sec, localBaseDir, new SsrfGuard());
    }

    public static Created fromStorageConfig(String provider, StorageEngineConfig sec,
                                            String localBaseDir, SsrfGuard ssrfGuard) {
        String p = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty() && sec != null && sec.getDefaultProvider() != null) {
            p = sec.getDefaultProvider().trim().toLowerCase(Locale.ROOT);
        }
        if (p.isEmpty()) {
            throw new IllegalArgumentException("empty provider");
        }
        return new Created(StorageProviders.require(p).creator().create(sec, localBaseDir, ssrfGuard), p);
    }
}
