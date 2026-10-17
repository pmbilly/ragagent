package com.ragagent.websearch.fetch;

import com.ragagent.contract.websearch.ToolPageFetcher;
import org.springframework.stereotype.Component;

/** 工具抓取（markdown/2MB/60s/浏览器兜底）——{@link ToolPageFetcher} 的唯一实现。 */
@Component
public class ToolPageFetcherImpl implements ToolPageFetcher {

    @Override
    public String fetch(String url) {
        return Fetcher.newFetcher().fetch(url);
    }
}
