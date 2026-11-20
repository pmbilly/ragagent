/**
 * 知识域**应用/门面面**（起）：对外的门面 {@code KnowledgeService}，以及它向下编排的
 * 用例服务（知识库/文件夹、文件与手工内容、解析生命周期、克隆/移动/标签/批处理）。
 *
 * <p><b>成员</b>：{@code KnowledgeService}（门面）、{@code KnowledgeBaseService}、
 * {@code KnowledgeFolderService}、{@code KnowledgeFileService}、{@code KnowledgeParseService}、
 * {@code KnowledgeTagService}、{@code KnowledgeBatchOpsService}、{@code KnowledgeCloneService}、
 * {@code KnowledgeMoveService}、{@code KnowledgeBaseLookupAdapter}。</p>
 *
 * <p><b>为什么能搬（/两次动前断言）</b>：门面的包内代码级 in-degree 已归零；
 * 的 4 类经实测——顶层残余（{@code KnowledgeAccessHelper} 等）**不引用**它们、它们对子面
 * 零依赖 ⇒ 搬迁只产生 {@code management → service}（4 类用顶层 {@code KnowledgeAccessHelper}）
 * 与外部（控制器 / 跨域消费方）→ {@code management} 的**单向边**，包级 SCC 不扩大。
 * **注意 {@code KnowledgeParseService} 必须与 {@code KnowledgeFileService}/
 * {@code KnowledgeFolderService} 同批搬**：它既引用后二者、又被门面/批处理引用，
 * 单搬会造 {@code management → TOP → management} 环。</p>
 */
package com.ragagent.knowledge.service.management;
