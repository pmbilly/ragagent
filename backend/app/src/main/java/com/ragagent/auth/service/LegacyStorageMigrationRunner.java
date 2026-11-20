package com.ragagent.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把旧 KV 存储配置迁成 {@code storage_backends} 行。
 *
 * <p>KV 写接口已停用（405），但旧行仍要能被运行期读到——迁移是幂等的，失败只记日志。</p>
 */
@Component
public final class LegacyStorageMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyStorageMigrationRunner.class);

    private final TenantService tenantService;

    public LegacyStorageMigrationRunner(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int migrated = tenantService.migrateLegacyStorageBackends();
            if (migrated > 0) {
                log.info("legacy storage migration: {} tenant(s) moved to storage_backends", migrated);
            }
        } catch (RuntimeException e) {
            // 启动迁移不能让应用起不来（测试环境可能还没建表；生产表缺失会由后续请求暴露）。
            log.info("legacy storage migration unavailable", e);
        }
    }
}
