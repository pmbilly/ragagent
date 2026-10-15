package com.ragagent.contract.websearch;

/**
 * 联网搜索 provider 读取端口（L1）：会话侧解析"默认 provider"所需的最小读面。
 *
 * <p><b>为什么是端口</b>：调用方只判断"该租户有没有默认 provider、id 是什么"，
 * 不持有 provider 实体；实现（含租户过滤的清单查询）在 websearch 域。</p>
 */
public interface WebSearchProviderPort {

    /**
     * 返回该租户 provider 清单里首个「默认且 id 非空」的 provider id；
     * 无 ⇒ 空串（调用方按"未配置默认"处理）。查询失败的上抛口径与域实现一致，
     * 由调用方捕获降级。
     */
    String findDefaultProviderId(long tenantId);
}
