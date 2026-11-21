package com.ragagent.websearch.api;

import com.ragagent.auth.api.WebSearchConfig;
import com.ragagent.retrieval.api.WebSearchResult;
import java.util.List;

/** web 搜索执行面：providerId + 执行面配置 + 查询。 */
public interface WebSearchRunPort {

    List<WebSearchResult> search(String providerId, WebSearchConfig config, String query);
}
