package com.ragagent.websearch.api;

import com.ragagent.shared.tenant.WebSearchConfig;
import com.ragagent.shared.websearch.WebSearchResult;
import java.util.List;

/** web 搜索执行面：providerId + 执行面配置 + 查询。 */
public interface WebSearchRunPort {

    List<WebSearchResult> search(String providerId, WebSearchConfig config, String query);
}
