package com.ragagent.websearch.fetch;

import org.springframework.stereotype.Component;

import com.ragagent.contract.websearch.PipelinePageFetcher;

/** 管线抓取（15s/100KB）——{@link PipelinePageFetcher} 的唯一实现。 */
@Component
public class PipelinePageFetcherImpl implements PipelinePageFetcher {

    @Override
    public String fetch(String url) {
        return Fetcher.fetchUrlContent(url);
    }
}
