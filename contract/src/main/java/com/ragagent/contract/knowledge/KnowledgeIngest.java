package com.ragagent.contract.knowledge;

/**
 * 知识**摄取面**（契约端口）：跨域方触发"入库前的必要能力"与"处理任务投递"。
 *
 * <p><b>为什么是端口</b>：内容指纹（幂等锚）、URL→文件名推导、任务号生成、处理队列投递，
 * 都是知识域的内部约定——四件实现（`KnowledgeHashes` · `KnowledgePaths` ·
 * `KnowledgeTaskIdCodec` · `KnowledgeProcessingQueue`）已于 2026-10-15 按方案 §8.9
 * 归位回 `knowledge/{support,task}`；数据源与评估域只应表达"要幂等锚 / 要摄取"，
 * 不直连实现件。</p>
 *
 * <p><b>与方案 §8.9 的偏差（登记见 HANDOFF §12.5）</b>：方案规划的完整形态是
 * `KnowledgeIngest` + `KnowledgeIngestCommand` + `KnowledgeIngestResult` + `service.ingest`
 * 用例（"知识来源验证 / 幂等 / 摄取"，**K 级设计项**）。本端口是其**能力面首版**：
 * 只收敛现有跨域调用点所需的四个能力，语义与域内实现**逐字一致**；
 * Command/Result/Policy 落地时以本端口为起点演进。</p>
 */
public interface KnowledgeIngest {

    /** 内容指纹（幂等锚）：与知识域 md5Hex 口径逐字一致。 */
    String contentHash(byte[] content);

    /** 从 URL 推导落库文件名（去 query 串、取最后一段；空 ⇒ {@code "download"}）。 */
    String fileNameFromUrl(String url);

    /** 新任务号（{@code <type>_<tenantId>_<millis>_<8hex>_<biz>} 口径，与域内一致）。 */
    String newTaskId(String taskType, long tenantId, String businessId);

    /** 投递知识处理任务（异步；口径同域内队列 {@code enqueue}）。 */
    void enqueueProcessing(String knowledgeId);
}
