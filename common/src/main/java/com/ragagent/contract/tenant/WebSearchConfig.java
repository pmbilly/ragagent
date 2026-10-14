package com.ragagent.contract.tenant;

import java.util.List;


/**
 * 联网搜索配置段。
 *
 * <p>字段序 = JSON 键序；provider/api_key 与 rag 压缩四字段 + proxy_url 为
 * 空值省略，其余恒输出。{@code Filters} 字段不在本投影（服务端不消费）。</p>
 *
 * <p>字符串字段默认 ""，blacklist 默认 null（json 输出 {@code null}；
 * 经 Effective 归一化后才变 []）。</p>
 */

public class WebSearchConfig {

    /** max_results 的生效下限/缺省值。 */
    public static final int DEFAULT_MAX_RESULTS = 10;
    /** compression_method 的缺省值。 */
    public static final String DEFAULT_COMPRESSION_METHOD = "none";

    private String provider = "";

    private String apiKey = "";

    private int maxResults;

    private boolean includeDate;

    private String compressionMethod = "";

    /** 列表字段恒输出：null → "blacklist":null，[] → [] */
    private List<String> blacklist;

    private String embeddingModelId = "";

    /** 数值 0 省略键 */
    private int embeddingDimension;

    private String rerankModelId = "";

    private int documentFragments;

    private String proxyUrl = "";

    public String getProvider() { return provider; }
    public void setProvider(String v) { provider = v == null ? "" : v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { apiKey = v == null ? "" : v; }
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int v) { maxResults = v; }
    public boolean isIncludeDate() { return includeDate; }
    public void setIncludeDate(boolean v) { includeDate = v; }
    public String getCompressionMethod() { return compressionMethod; }
    public void setCompressionMethod(String v) { compressionMethod = v == null ? "" : v; }
    public List<String> getBlacklist() { return blacklist; }
    public void setBlacklist(List<String> v) { blacklist = v; }
    public String getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(String v) { embeddingModelId = v == null ? "" : v; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int v) { embeddingDimension = v; }
    public String getRerankModelId() { return rerankModelId; }
    public void setRerankModelId(String v) { rerankModelId = v == null ? "" : v; }
    public int getDocumentFragments() { return documentFragments; }
    public void setDocumentFragments(int v) { documentFragments = v; }
    public String getProxyUrl() { return proxyUrl; }
    public void setProxyUrl(String v) { proxyUrl = v == null ? "" : v; }

    /**
     * 归一化生效值：原地修改本对象
     * （max_results≤0→10、compression_method 空→"none"、blacklist null→[]）。
     */
    public void applyEffective() {
        if (maxResults <= 0) {
            maxResults = DEFAULT_MAX_RESULTS;
        }
        if (compressionMethod.isEmpty()) {
            compressionMethod = DEFAULT_COMPRESSION_METHOD;
        }
        if (blacklist == null) {
            blacklist = List.of();
        }
    }

    /**
     * 浅拷贝：调用方需要"临时改几个字段再传给执行面"时**必须**先 copy，
     * 否则会改到租户配置缓存里的同一个对象。
     */
    public WebSearchConfig copy() {
        WebSearchConfig c = new WebSearchConfig();
        c.provider = provider;
        c.apiKey = apiKey;
        c.maxResults = maxResults;
        c.includeDate = includeDate;
        c.compressionMethod = compressionMethod;
        c.blacklist = blacklist == null ? null : new java.util.ArrayList<>(blacklist);
        c.embeddingModelId = embeddingModelId;
        c.embeddingDimension = embeddingDimension;
        c.rerankModelId = rerankModelId;
        c.documentFragments = documentFragments;
        c.proxyUrl = proxyUrl;
        return c;
    }
}
