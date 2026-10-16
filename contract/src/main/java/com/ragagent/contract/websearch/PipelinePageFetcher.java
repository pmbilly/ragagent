package com.ragagent.contract.websearch;

/**
 * 管线网页抓取端口（R4b 契约化）：{@code chatpipeline} 的网页抓取插件用。
 *
 * <p>语义与实现同源：15s 预算 / 100KB 上限的轻量抓取（与工具侧 web_fetch 的完整变体区分——
 * 那是另一个端口 {@link ToolPageFetcher}）。失败以运行时异常表达（实现侧抛
 * {@link FetchException}，可机读码见其 {@code Code}）。</p>
 */
@FunctionalInterface
public interface PipelinePageFetcher {

    /** 抓取 URL 的正文文本（失败抛运行时异常）。 */
    String fetch(String url);
}
