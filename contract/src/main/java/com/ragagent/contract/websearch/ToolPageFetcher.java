package com.ragagent.contract.websearch;

/**
 * 工具网页抓取端口（R4b 契约化）：{@code web_fetch}/{@code web_search} 的页面读取用。
 *
 * <p>语义与实现同源：markdown 归一、2MB 上限、60s、浏览器兜底（与管线侧 15s/100KB 的
 * {@link PipelinePageFetcher} 区分）。失败以运行时异常表达（实现侧抛 {@link FetchException}）。</p>
 */
@FunctionalInterface
public interface ToolPageFetcher {

    /** 抓取 URL 并归一为可读文本（失败抛运行时异常）。 */
    String fetch(String url);
}
