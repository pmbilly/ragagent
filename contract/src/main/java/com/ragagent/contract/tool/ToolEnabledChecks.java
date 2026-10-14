package com.ragagent.contract.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * 目录级启用状态查询（闸门与目录枚举共用的静态工具）。
 *
 * <p><b>缺行 = 启用</b>：逐个查先给每个名字填默认 true，批量路径再用策略表覆盖
 * ——策略表只存覆盖值，未覆盖即默认启用。</p>
 */
public final class ToolEnabledChecks {

    private ToolEnabledChecks() {
    }

    /**
     * 支持批量就一次查完，否则退化为逐个查（兼容只实现单工具契约的 checker）。
     * 取消信号与身份缺失在**查询之前**短路。
     */
    public static Map<String, Boolean> enabledTools(Cancellation ctx, ToolEnabledChecker checker,
            long tenantId, String serviceId, List<String> names) {
        if (ctx.isCancelled()) {
            throw new CancellationException("context canceled");
        }
        if (tenantId == 0 || serviceId == null || serviceId.isEmpty()) {
            throw ToolApprovalException.internal("MCP policy identity is required");
        }
        if (checker instanceof ToolBulkEnabledChecker bulk) {
            return bulk.enabledTools(ctx, tenantId, serviceId, names);
        }
        return individually(ctx, checker, tenantId, serviceId, names);
    }

    /**
     * 逐个查、每个名字前检查 ctx 取消。
     * checker 为 null 时全部默认启用。
     */
    public static Map<String, Boolean> individually(Cancellation ctx, ToolEnabledChecker checker,
            long tenantId, String serviceId, List<String> names) {
        Map<String, Boolean> result = new HashMap<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (ctx.isCancelled()) {
                throw new CancellationException("context canceled");
            }
            boolean enabled = true;
            if (checker != null) {
                enabled = checker.isEnabled(ctx, tenantId, serviceId, name);
            }
            result.put(name, enabled);
        }
        return result;
    }
}
