package com.ragagent.contract.vectorstore;

/**
 * 向量店连通性探测端口：按引擎类型做一次连通性/认证探测，成功返回探测到的版本（可空）。
 *
 * <p> 契约化：原先这 9 个探测分支（elasticsearch / postgres / qdrant / milvus /
 * tencent_vectordb / weaviate / doris / opensearch / sqlite）连同各自的探测体都住在业务域的
 * 配置服务里，而其中六个要直调 {@code retrieval-engine} 的厂商实现——那是**引擎知识**，
 * 已整块沉到实现侧；业务域只保留配置校验（必填 + SSRF）与对外错误包装。</p>
 *
 * <p><b>错误契约</b>：失败抛 {@link com.ragagent.contract.error.BizException}
 * （code 1000；各引擎的文案逐字固定，是 golden 用例钉住的对外契约），调用方按自己的流程
 * 再包一层（如创建路径的 {@code "connection test failed: ..."}）。</p>
 */
public interface VectorStoreConnectivity {

    /**
     * 探测（{@code engineType} 未知或未支持时抛 {@link com.ragagent.contract.error.BizException}）。
     *
     * <p>注意：本方法只做探测，不做配置必填校验与地址 SSRF 校验——那两步由调用方先走
     * （顺序固定：必填 → SSRF → 探测）。</p>
     */
    String probe(String engineType, ConnectionConfig config);
}
