package com.ragagent.contract.vectorstore;

import java.util.function.Function;

/**
 * 控制面 SSRF 地址规则：{@code value} 从连接配置取地址，空串放行。
 *
 * <p>{@code label} 只用于诊断；对外错误文案沿用统一前缀。</p>
 */
public record VectorStoreAddressField(String label, Function<ConnectionConfig, String> value) {
}
