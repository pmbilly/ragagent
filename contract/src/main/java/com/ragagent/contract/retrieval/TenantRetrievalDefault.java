package com.ragagent.contract.retrieval;

/**
 * 租户级检索回落口径（无绑定 store 的 KB 用哪套引擎）。
 *
 * <p>{@code retrieverEngines} 是租户配置里的检索引擎表，**不透明 JSON**（契约面惯例：
 * 业务域不认识其内部结构，只有检索引擎能解释）；{@code driver} 是部署级驱动名
 * （见 {@code platform.config.RetrievalDriverProperties}）。两者合起来由
 * {@link IndexEngineResolver#resolveFor(long, String, TenantRetrievalDefault)} 解释成引擎句柄。</p>
 */
public record TenantRetrievalDefault(Object retrieverEngines, String driver) {
}
