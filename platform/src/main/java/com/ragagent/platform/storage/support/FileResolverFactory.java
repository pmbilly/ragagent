package com.ragagent.platform.storage.support;

import com.ragagent.contract.storage.support.FileUrlPort;
import com.ragagent.contract.storage.support.Resolver;
import com.ragagent.contract.storage.support.StorageBackendResolver;
import com.ragagent.contract.tenant.TenantConfigLookup.TenantStorageView;

/**
 * 文件解析器（{@link Resolver}）的构造面（L1）：按"当前租户 + 进程级默认文件服务 + 后端解析器"
 * 造一个解析器。
 *
 * <p><b>为什么是接口</b>：具体的解析器实现（{@code storage.support.FileServiceResolver}）依赖
 * 存储域 provider 工厂，属存储域；而上层（session 的请求构造、检索响应重写）只按请求参数组装
 * ——把"组装"这一动作收成 L1 函数面，{@code Rewriter#forRequest} 便不再需要认识存储域实现。</p>
 */
@FunctionalInterface
public interface FileResolverFactory {

    /** 造一个按请求解析文件引用的解析器。 */
    Resolver create(TenantStorageView tenant, FileUrlPort defaultSvc,
                    StorageBackendResolver storageResolver);
}
