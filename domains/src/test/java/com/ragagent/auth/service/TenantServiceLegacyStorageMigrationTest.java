package com.ragagent.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.auth.domain.Tenant;
import com.ragagent.auth.repository.TenantMemberRepository;
import com.ragagent.auth.repository.TenantRepository;
import com.ragagent.contract.storage.StorageBackendProvisioner;
import com.ragagent.platform.web.JsonMappers;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 旧 KV 存储配置的启动迁移：幂等、失败隔离。 */
class TenantServiceLegacyStorageMigrationTest {

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final StorageBackendProvisioner provisioner = mock(StorageBackendProvisioner.class);
    private final TenantService service =
            new TenantService(tenants, mock(TenantMemberRepository.class), provisioner);

    private static Tenant legacyTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setName("legacy");
        tenant.setStorageEngineConfig(JsonMappers.toJsonNode(
                java.util.Map.of("defaultProvider", "minio")));
        return tenant;
    }

    @Test
    void migratesTenantWithoutDefaultBackend() {
        Tenant tenant = legacyTenant();
        when(tenants.listLive()).thenReturn(List.of(tenant));
        when(provisioner.provisionFromLegacy(eq(7L), any())).thenReturn("backend-1");

        int migrated = service.migrateLegacyStorageBackends();

        assertThat(migrated).isEqualTo(1);
        assertThat(tenant.getDefaultStorageBackendId()).isEqualTo("backend-1");
        verify(tenants).updateById(tenant);
    }

    @Test
    void skipsTenantThatAlreadyHasDefaultBackend() {
        Tenant tenant = legacyTenant();
        tenant.setDefaultStorageBackendId("existing");
        when(tenants.listLive()).thenReturn(List.of(tenant));

        assertThat(service.migrateLegacyStorageBackends()).isZero();
        verify(provisioner, times(0)).provisionFromLegacy(any(Long.class), any());
    }

    @Test
    void isolatesFailureToTheTenant() {
        Tenant tenant = legacyTenant();
        when(tenants.listLive()).thenReturn(List.of(tenant));
        when(provisioner.provisionFromLegacy(eq(7L), any()))
                .thenThrow(new IllegalStateException("boom"));

        assertThat(service.migrateLegacyStorageBackends()).isZero();
        assertThat(tenant.getDefaultStorageBackendId()).isNull();
    }
}
