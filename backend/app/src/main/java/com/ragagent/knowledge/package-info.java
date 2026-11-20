/**
 * 知识库域：知识的存储、处理管线与检索面。主链路：文件上传 / URL / 手工 →
 * {@code KnowledgeProcessWorker} 状态机 → 分块 → 向量化 → 富化 fan-out（摘要/问题/wiki/图谱）
 * → 检索消费方。FAQ 是独立子链路。
 *
 * <p><b>种类</b>：产品能力<br>
 * <b>入口</b>：{@code KnowledgeBaseController}、{@code KnowledgeController}、{@code ChunkController}<br>
 * <b>对外契约面</b>：{@code KnowledgeCatalogPort}、{@code KnowledgeBaseCatalogPort}、
 * {@code KnowledgeMaintenancePort}、{@code KnowledgeSpanPort}、{@code KnowledgeBaseGateway} 等<br>
 * <b>依赖</b>：{@code contract}、{@code platform}；检索与模型经契约端口。</p>
 */
package com.ragagent.knowledge;
