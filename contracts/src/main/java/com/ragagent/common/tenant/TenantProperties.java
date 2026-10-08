package com.ragagent.common.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tenant 配置段（WEKNORA_TENANT_* env / application.yml）。
 *
 * - enableRbac：可空布尔，null → 默认 true（未显式关闭即强制开启，
 *   "operator did not opt out"）。env: WEKNORA_TENANT_ENABLE_RBAC
 * - enableCrossTenantAccess：随空间分享裁撤（跨租户授予链已退役）。
 * - selfServiceCreationEnabled：指针语义，null → 默认 true。
 *   env: WEKNORA_TENANT_SELF_SERVICE_CREATION_ENABLED
 * - maxOwnedPerUser：自助创建的配额底座（0/null → 内置默认 10，由
 *   SystemSettingService 的三层解析在此之上叠 DB/env）。
 *   env: WEKNORA_TENANT_MAX_OWNED_PER_USER
 */
@ConfigurationProperties(prefix = "weknora.tenant")
public record TenantProperties(
        Boolean enableRbac,
        Boolean selfServiceCreationEnabled,
        Integer maxOwnedPerUser) {

    /**
     * 双构造器下必须显式钉住绑定构造器（否则 @ConfigurationPropertiesScan
     * 走默认 bean 实例化找无参构造，启动即 NoSuchMethodException）。
     */
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public TenantProperties(Boolean enableRbac,
                            Boolean selfServiceCreationEnabled, Integer maxOwnedPerUser) {
        this.enableRbac = enableRbac;
        this.selfServiceCreationEnabled = selfServiceCreationEnabled;
        this.maxOwnedPerUser = maxOwnedPerUser;
    }

    /** null 视为 true */
    public boolean isRbacEnforced() {
        return enableRbac == null || enableRbac;
    }

    /** null 视为 true */
    public boolean isSelfServiceCreationEnabled() {
        return selfServiceCreationEnabled == null || selfServiceCreationEnabled;
    }
}
