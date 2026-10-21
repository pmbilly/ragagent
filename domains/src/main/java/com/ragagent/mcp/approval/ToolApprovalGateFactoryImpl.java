package com.ragagent.mcp.approval;

import com.ragagent.contract.tool.ToolApprovalGate;
import com.ragagent.contract.tool.ToolApprovalGateFactory;
import com.ragagent.contract.tool.ToolPolicyChecker;
import com.ragagent.platform.redis.RedisPubSub;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 审批闸门的装配面实现：把"策略查询源 + 超时秒"装成 {@link ToolApprovalGate}。
 *
 * <p>Redis 广播由本域内部取（缺省即单实例模式，审批结果不跨实例广播）——
 * 调用方不接触实现类与平台细节。</p>
 */
@Component
public class ToolApprovalGateFactoryImpl implements ToolApprovalGateFactory {

    private final ObjectProvider<RedisPubSub> redis;

    public ToolApprovalGateFactoryImpl(ObjectProvider<RedisPubSub> redis) {
        this.redis = redis;
    }

    @Override
    public ToolApprovalGate create(ToolPolicyChecker checker, Integer timeoutSeconds) {
        return new Gate(GateOptions.fromConfig(timeoutSeconds), checker, redis.getIfAvailable());
    }
}
