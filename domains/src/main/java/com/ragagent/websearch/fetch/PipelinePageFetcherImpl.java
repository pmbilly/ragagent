package com.ragagent.websearch.fetch;

import com.ragagent.contract.websearch.PipelinePageFetcher;
import org.springframework.stereotype.Component;

/** 管线抓取（15s/100KB）——{@link PipelinePageFetcher} 的唯一实现。 */
@Component
public class PipelinePageFetcherImpl implements PipelinePageFetcher {

    @Override
    public String fetch(String url) {
        return Fetcher.fetchUrlContent(url);
    }
}
