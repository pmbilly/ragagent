package com.ragagent.websearch.api;

import com.ragagent.contract.websearch.WebSearchProviderPort;
import com.ragagent.contract.websearch.WebSearchTempStatePort;
import com.ragagent.websearch.mapper.WebSearchProviderRepository;
import com.ragagent.websearch.service.WebSearchTempKbStateService;
import org.springframework.stereotype.Component;

/**
 * {@link WebSearchProviderPort} / {@link WebSearchTempStatePort} 的 websearch 域实现：
 * 两个支撑面逐字转发域内实现，不新增缓冲、不改失败口径。
 */
@Component
public class WebSearchSupportAdapter implements WebSearchProviderPort, WebSearchTempStatePort {

    private final WebSearchProviderRepository providerRepository;
    private final WebSearchTempKbStateService tempKbStateService;

    public WebSearchSupportAdapter(WebSearchProviderRepository providerRepository,
                                   WebSearchTempKbStateService tempKbStateService) {
        this.providerRepository = providerRepository;
        this.tempKbStateService = tempKbStateService;
    }

    @Override
    public String findDefaultProviderId(long tenantId) {
        for (var provider : providerRepository.list(tenantId)) {
            if (provider != null && provider.isDefault() && provider.getId() != null
                    && !provider.getId().isEmpty()) {
                return provider.getId();
            }
        }
        return "";
    }

    @Override
    public void deleteTempKbState(String sessionId) {
        tempKbStateService.deleteTempKbState(sessionId);
    }
}
