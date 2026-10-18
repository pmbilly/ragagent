/**
 * 检索引擎域（库式域，**无 HTTP 面**）：多引擎仓储（{@code engine/}：OpenSearch/Doris/Milvus/Qdrant/Tencent…，
 * 含 postgres 引擎的 embeddings 读写两侧）、混合检索（{@link com.ragagent.retrieval.HybridSearchService}）
 * 与图谱/VLM 辅助。
 *
 * <p><b>两层职责</b>：{@code engine/} 是**引擎适配**（把统一检索参数翻成各厂商协议，纯技术）；
 * {@link com.ragagent.retrieval.HybridFusionOps} 是**检索编排**（向量/关键词融合、租户过滤、
 * chunk 回查）——后者已带业务语义，这也是本域不能被当作纯技术模块的原因。</p>
 *
 * <p>消费方：{@code session}（7 处）、{@code knowledge}（2 处），一律经
 * {@link com.ragagent.contract.retrieval.HybridSearchPort} 注入；本域**不 import 任何业务域**，
 * 回查 chunk 走 {@link com.ragagent.contract.knowledge.ChunkSearchGateway}（实现是
 * {@code knowledge.repository.ChunkRepository}）。</p>
 */
package com.ragagent.retrieval;
