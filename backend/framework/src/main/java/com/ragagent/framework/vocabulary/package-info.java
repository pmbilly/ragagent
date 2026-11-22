/**
 * 跨引擎晶格形状（终态 8 类，纯 JDK）：retrieval-engine 与 model-runtime 互不可见、
 * 又都被 app/agent-engine 消费——framework 是它们唯一的共同可见层。
 *
 * <p><b>成员与钉子</b>：{@code SearchResult}（双引擎+storage Rewriter）、
 * {@code ChunkView}（双引擎）、{@code GraphNode}/{@code GraphRelation}（双引擎）、
 * {@code ToolResult}（model-runtime modelcontext 打包消费）、
 * {@code KnowledgeBaseGateway}/{@code KnowledgeBaseFacts}（auth→knowledge 只读端口，
 * 待 Provider 反转批）、{@code SseSink}（framework/web/ServletSseSink 实现，待 SseEmitter 批）。</p>
 *
 * <p><b>约束</b>：只放纯 JDK 形状；成员清单由 {@code VocabularyManifestTest} 棘轮钉住——
 * 只减不增，迁出即删条目。会话消息簇（原 MessageView 等 11 类）已随 channel 合并批
 * 迁 {@code channel.api} 与 {@code agentengine.api}。</p>
 */
package com.ragagent.framework.vocabulary;
