package com.ragagent.storage.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ragagent.contract.tenant.StorageEngineConfig;
import com.ragagent.platform.storage.config.StorageAllowList;
import com.ragagent.platform.storage.config.StorageAllowListProperties;
import org.junit.jupiter.api.Test;

/**
 * 单一登记点：展示名单与 platform 白名单同序、凭据族命名、旧 KV 段映射。
 */
class StorageProvidersTest {

    @Test
    void exposedKeysMatchPlatformAllowListOrder() {
        StorageAllowList allowList = new StorageAllowList(new StorageAllowListProperties(null));
        assertEquals(allowList.supported(), StorageProviders.exposedKeys());
    }

    @Test
    void schemeKeysKeepDummyForPathParsing() {
        assertEquals("dummy", StorageProviders.schemeKeys().get(StorageProviders.schemeKeys().size() - 1));
        assertTrue(StorageProviders.schemeKeys().containsAll(StorageProviders.exposedKeys()));
    }

    @Test
    void credentialFieldsFollowProviderFamily() {
        assertEquals("accessKeyId", StorageProviders.credentials("minio").accessKey());
        assertEquals("secretAccessKey", StorageProviders.credentials("minio").secretKey());
        assertEquals("secretId", StorageProviders.credentials("cos").accessKey());
        assertEquals("accessKey", StorageProviders.credentials("s3").accessKey());
        assertEquals("accessKey", StorageProviders.credentials("nope").accessKey());
    }

    @Test
    void legacySectionMapsProviderSections() {
        StorageEngineConfig config = new StorageEngineConfig();
        StorageEngineConfig.S3EngineConfig s3 = new StorageEngineConfig.S3EngineConfig();
        config.setS3(s3);
        assertEquals(s3, StorageProviders.legacySection("s3", config));
    }

    @Test
    void unknownProviderFailsWithLegacyMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> StorageProviders.require("ceph"));
        assertEquals("unsupported provider \"ceph\"", e.getMessage());
    }
}
