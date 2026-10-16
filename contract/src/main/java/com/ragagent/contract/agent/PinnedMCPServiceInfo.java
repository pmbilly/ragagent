package com.ragagent.contract.agent;

import java.util.List;

/** 本轮 @ 指定的 MCP 服务。 */
public record PinnedMCPServiceInfo(
        boolean discoverable,
        String id,
        String name,
        String description,
        List<String> toolNames) {
}
