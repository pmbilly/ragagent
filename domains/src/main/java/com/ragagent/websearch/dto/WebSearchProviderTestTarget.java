package com.ragagent.websearch.dto;

import com.ragagent.websearch.config.WebSearchProviderParams;
import com.ragagent.websearch.domain.WebSearchProviderEntity;

/** 连通性测试所需的 provider 投影：控制器不再直接持有实体。 */
public record WebSearchProviderTestTarget(
        String providerType,
        WebSearchProviderParams parameters) {

    public static WebSearchProviderTestTarget from(WebSearchProviderEntity entity) {
        return new WebSearchProviderTestTarget(entity.getProvider(), entity.getParameters());
    }
}
