package com.ragagent.session.rag.plugin;

import com.ragagent.contract.retrieval.SearchResult;
import com.ragagent.contract.websearch.PipelinePageFetcher;
import com.ragagent.session.rag.PipelineEventType;
import com.ragagent.session.rag.PipelineLog;
import com.ragagent.session.rag.PipelineState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WEB_FETCH 阶段插件：rerank 之后对 web 结果
 * 抓取全文替换摘要，topN 默认 3；单页超 8000 字节截断追加 "\n...(truncated)"。
 * 抓取走 webfetch 的 pipeline fetcher（15s/100KB + SSRF 守卫）。
 */
public final class PluginWebFetch implements Plugin {

    /** 抓取端口（管线语义由实现侧提供：15s/100KB）。 */
    private final PipelinePageFetcher fetcher;

    public PluginWebFetch(PipelinePageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public PipelineEventType[] activationEvents() {
        return new PipelineEventType[] {PipelineEventType.WEB_FETCH};
    }

    @Override
    public PluginError onEvent(PipelineEventType eventType, PipelineState chatManage, Plugin.Chain next) {
        if (!chatManage.isWebFetchEnabled() || !chatManage.isWebSearchEnabled()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("reason", "disabled");
            PipelineLog.info("WebFetch", "skip", f);
            return next.next();
        }

        int topN = chatManage.getWebFetchTopN();
        if (topN <= 0) {
            topN = 3;
        }

        List<SearchResult> rerankResult = chatManage.getRerank().getResults();
        List<SearchResult> webResults = new ArrayList<>();
        if (rerankResult != null) {
            for (SearchResult r : rerankResult) {
                if ("web_search".equals(r.getKnowledgeSource().toLowerCase(java.util.Locale.ROOT))) {
                    webResults.add(r);
                    if (webResults.size() >= topN) {
                        break;
                    }
                }
            }
        }

        if (webResults.isEmpty()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("reason", "no_web_results");
            PipelineLog.info("WebFetch", "skip", f);
            return next.next();
        }

        // 并发抓取（经 pipeline fetcher）
        String[] contents = new String[webResults.size()];
        Throwable[] errors = new Throwable[webResults.size()];
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>(webResults.size());
            for (int i = 0; i < webResults.size(); i++) {
                final int idx = i;
                final String fetchUrl = webResults.get(i).getId(); // web 结果以 URL 为 ID
                futures.add(executor.submit(() -> {
                    if (fetchUrl == null || fetchUrl.isEmpty()) {
                        return null;
                    }
                    try {
                        contents[idx] = fetcher.fetch(fetchUrl);
                    } catch (RuntimeException e) {
                        errors[idx] = e;
                    }
                    return null;
                }));
            }
            for (var f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    throw new IllegalStateException("web fetch task failed", e);
                }
            }
        }

        int fetchedCount = 0;
        for (int i = 0; i < webResults.size(); i++) {
            if (errors[i] != null) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("url", webResults.get(i).getId());
                f.put("err", errors[i].getMessage());
                PipelineLog.warn("WebFetch", "fetch_failed", f);
                continue;
            }
            String content = contents[i];
            if (content == null || content.isEmpty()) {
                continue;
            }
            if (content.length() > 8000) {
                content = content.substring(0, 8000) + "\n...(truncated)";
            }
            webResults.get(i).setContent(content);
            fetchedCount++;
        }

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fetched", fetchedCount);
        f.put("total", webResults.size());
        PipelineLog.info("WebFetch", "complete", f);
        return next.next();
    }
}
