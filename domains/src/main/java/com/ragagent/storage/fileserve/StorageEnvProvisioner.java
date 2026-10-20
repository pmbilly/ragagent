package com.ragagent.storage.fileserve;

import com.ragagent.platform.storage.config.StorageRuntimeEnv;
import com.ragagent.platform.web.JsonMappers;
import com.ragagent.storage.config.StorageProviderEnv;
import com.ragagent.storage.domain.StorageBackend;
import java.util.Map;

/**
 * 环境变量快照 → 进程级 System 存储行。
 *
 * <p>配置不由本类手写 switch 拼装，而是复用同一投影
 * （{@code StorageProviderEnv.*.writeConfig}，落库面 camel 词汇），随后照常经
 * {@link StorageConfigProjector} 派生出引擎面——同一份环境变量只有一条投影路径。</p>
 */
final class StorageEnvProvisioner {

    private final Map<String, StorageProviderEnv.ProviderEnvFamily> providerEnvs;

    StorageEnvProvisioner(Map<String, StorageProviderEnv.ProviderEnvFamily> providerEnvs) {
        this.providerEnvs = providerEnvs;
    }

    StorageBackend fromEnvironment(long tenantId) {
        String provider = StorageRuntimeEnv.storageType()
                .trim().toLowerCase(java.util.Locale.ROOT);
        if (provider.isEmpty()) {
            provider = "local";
        }
        StorageProviderEnv.ProviderEnvFamily family = providerEnvs.get(provider);
        if (family == null) {
            // 环境里没有这个 provider 的快照 / 未知 provider → null
            return null;
        }
        StorageBackend b = new StorageBackend();
        b.setTenantId(tenantId);
        b.setName("System " + provider.toUpperCase(java.util.Locale.ROOT));
        b.setProvider(provider);
        b.setSource("env");
        b.setStatus("active");
        b.setLegacyAlias(true);
        var cfg = JsonMappers.lenient().createObjectNode();
        family.writeConfig(cfg);
        b.setConfig(cfg);
        return b;
    }
}
