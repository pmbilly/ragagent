package com.ragagent.contract.tool;

import java.util.List;
import java.util.Map;

/**
 * 目录枚举时的批量启用状态查询（可选实现："用一个查询代替 N 个查询"）。
 *
 * <p>调用方（{@link ToolEnabledChecks#enabledTools}）用 {@code instanceof} 探测是否支持批量：
 * 实现方必须**同时**实现 {@link ToolEnabledChecker} 与本接口，才会走批量分支；
 * 未覆盖的名字按"默认启用"处理。</p>
 */
public interface ToolBulkEnabledChecker extends ToolEnabledChecker {

    /**
     * 为每个请求的名字返回一个显式决定。
     * 返回值里缺少某个名字时，调用方按"默认启用"处理。
     */
    Map<String, Boolean> enabledTools(Cancellation ctx, long tenantId, String serviceId, List<String> names);
}
