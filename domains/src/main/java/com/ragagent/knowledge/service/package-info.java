/**
 * 知识库用例层。按能力的职责地图（队列/客户端/存储/守卫已各自分包）：
 * <ul>
 *   <li><b>处理管线</b>：KnowledgeService（门面与共享工具）+ KnowledgeParseService /
 *       KnowledgeSummaryService / KnowledgeFileService（解析生命周期、摘要、文件与手工内容）；</li>
 *       （命令、查询、导入）+ FaqChunkCodec / FaqIndexWriter / FaqIndexRows（编解码与向量写路径）；</li>
 *   <li><b>chunk 编辑与生成</b>：ChunkEditService（乐观锁编辑/回滚/软删）+ ChunkReadService +
 *       ChunkQuestionService（LLM 生成问题）+ ChunkVectorIndexer（图抽取与向量索引）；</li>
 *   <li><b>知识库与检索</b>：KnowledgeBaseService / KnowledgeTagService / KnowledgeSearchService /
 *       KnowledgeBatchOpsService / KnowledgeCloneService / KnowledgeMoveService /
 *       KnowledgeFolderService；</li>
 *   <li><b>支撑</b>：HousekeepingService（定时清理）+ KnowledgeVectorWrites（向量写路由网关）。
 *       <b>已迁出</b>（B263㉜）：SpanTracker（处理进度 span 树）→ {@code domain.span}
 *       —— 它被 {@code task} 侧 worker 使用，留在本包会形成 {@code task → service} 环。</li>
 * </ul>
 *
 * <p><b>子面</b>：{@code faq/}（B263㉞ 从本包整簇切出——导入编排 / 条目读写 / 索引形态）。</p>
 */
package com.ragagent.knowledge.service;
