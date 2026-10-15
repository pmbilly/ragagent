package com.ragagent.storage.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ragagent.common.storage.StorageBackend;
import com.ragagent.common.storage.StorageBackendPort;
import com.ragagent.storage.mapper.StorageBackendRepository;
import com.ragagent.storage.service.StorageBackendService;

/**
 * {@link StorageBackendPort} 的存储域实现：探测与脱敏转发运行时探测服务，清单转发元数据仓储。
 *
 * <p>语义逐字转发，不新增缓冲/改写：探测异常原样上抛（调用方按异常消息分派文案）、脱敏规则
 * 与 {@code StorageBackendService.sanitizeConnectivity} 同一实现。</p>
 */
@Component
public class StorageBackendPortAdapter implements StorageBackendPort {

    private final StorageBackendService backendService;
    private final StorageBackendRepository backendRepository;

    public StorageBackendPortAdapter(StorageBackendService backendService,
                                     StorageBackendRepository backendRepository) {
        this.backendService = backendService;
        this.backendRepository = backendRepository;
    }

    @Override
    public void test(StorageBackend backend) {
        backendService.test(backend);
    }

    @Override
    public String sanitizeConnectivity(String message) {
        return StorageBackendService.sanitizeConnectivity(message);
    }

    @Override
    public List<StorageBackend> list(long tenantId) {
        return backendRepository.list(tenantId);
    }
}
