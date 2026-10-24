package com.ragagent.contract.vectorstore;

/**
 * 向量店引擎目录：控制面规则（必填 / SSRF 地址）+ 连通性探测。
 *
 * <p>引擎知识（每种引擎需要哪些字段、哪些地址要过 SSRF、怎么探测）只在
 * {@code retrieval-engine} 的引擎注册表登记一次；业务域通过本端口查询，不再维护
 * 自己的 switch。未知引擎的 {@link #rulesFor(String)} 返回 {@code null}，由调用方
 * 决定放行（必填校验）或 fail-closed（SSRF 校验）。</p>
 *
 * <p><b>错误契约</b>：{@link #probe(String, ConnectionConfig)} 失败抛
 * {@link com.ragagent.contract.error.BizException}（code 1000；各引擎文案逐字固定，
 * 由 golden 用例钉住），调用方按自己的流程再包一层。</p>
 */
public interface VectorStoreEngineCatalogPort {

    /**
     * 连通性探测（{@code engineType} 未知或未支持时抛
     * {@link com.ragagent.contract.error.BizException}）。
     *
     * <p>本方法只做探测，不做配置必填校验与地址 SSRF 校验——那两步由调用方先走
     * （顺序固定：必填 → SSRF → 探测）。</p>
     */
    String probe(String engineType, ConnectionConfig config);

    /** 单个引擎的控制面规则；未登记的引擎返回 {@code null}。 */
    VectorStoreEngineRules rulesFor(String engineType);
}
