/**
 * 查询面（从 {@code service} 切出，按 命名）：检索与 span 读取。
 *
 * <p>成员：{@code KnowledgeSearchService}（跨 KB 检索主查询的编排）、{@code KnowledgeSpanService}
 * （span 视图）、{@code KnowledgeSpanAdapter}（对 span 端口的适配）。</p>
 *
 * <p><b>切出依据</b>：三者在 {@code service} 顶层包内**代码级入度为零**（门面与其余子面只**被它们依赖**
 * 或经端口交互）⇒ 只产生 {@code service.query → service}/{@code → service.management} 单向边，SCC 不扩大。
 * {@code SpanTracker} 例外地在 {@code domain.span}（它要被 {@code task} 侧使用，
 * 留在 service 会造 {@code task ⇄ service} 环——此为本仓相对 的**有意偏离**）。</p>
 */
package com.ragagent.knowledge.service.query;
