package com.ragagent.contract.pipeline;

import java.util.List;

import com.ragagent.contract.websearch.WebSearchRequest;
import com.ragagent.contract.websearch.WebSearchResult;

/**
 * 联网搜索执行端口（引擎侧接口）：按 provider + 工具面请求执行一次 web 搜索。
 *
 * <p><b>为什么在 engine</b>：出入参（{@link WebSearchRequest} / {@link WebSearchResult}）
 * 都是引擎模块词汇，L1（{@code common}）看不到它们；执行面配置（provider 密钥等
 * 域内细节）不进签名——请求到执行配置的转换留在 websearch 域。</p>
 *
 * <p><b>实现</b>：websearch 域适配器同时实现本口与管线的
 * {@code PipelinePorts.WebSearch}（后者入参是 L1 租户配置），两个工具/管线面同源；
 * 失败语义与域实现一致（工具侧折叠为 success=false）。装配按类型注入。</p>
 */
public interface WebSearchExecutor {

    /** 执行一次搜索（失败抛运行时异常，由工具层折叠）。 */
    List<WebSearchResult> search(long tenantId, String providerId,
            WebSearchRequest request, String query);
}
