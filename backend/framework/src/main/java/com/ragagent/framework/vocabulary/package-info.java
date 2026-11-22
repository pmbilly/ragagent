/**
 * 跨域词汇：被两个以上模块共用、且无法落到任何单个域/引擎 api 的形状（纯 JDK）。
 *
 * <p><b>为什么是单包</b>：这些形状互相引用（会话视图含检索结果与 agent 步骤），拆成兄弟包
 * 会立刻形成包级环；它们又都被 framework 的技术层（Web / 持久化）引用，因此留在 framework。</p>
 *
 * <p><b>约束</b>：只放纯 JDK 的形状与常量，不放实现、不放 I/O、不放 Spring。
 * 成员清单由 {@code VocabularyManifestTest} 棘轮钉住——只许减不许增，新增前先论证
 * "是否真的没有更合适的属主"。</p>
 *
 * <h2>按领域查（终态 19 类）</h2>
 * <ul>
 *   <li><b>会话消息聚合</b>：{@link com.ragagent.framework.vocabulary.MessageView}
 *       （消息线格式视图，字段聚合了下面多数类型，是这一簇的锚——channel 合并批重构它的
 *       字段后，被它钉住的成员随之迁出）；被其字段钉住的 {@code AgentStep}、
 *       {@code AgentToolCall}、{@code ToolCallTarget}、{@code ToolResult}、
 *       {@code MentionedItem}、{@code MessageArtifact}、{@code MessageAttachment}、
 *       {@code MessageExecutionContext}、{@code MessageImage}、{@code UsedMemory}、
 *       {@code SuggestionAttribution}</li>
 *   <li><b>检索</b>：{@code SearchResult}（agent-engine 与 model-runtime 双引擎消费）、
 *       {@code ChunkView}（retrieval-engine 与 model-runtime 双引擎消费）</li>
 *   <li><b>图谱</b>：{@code GraphNode}、{@code GraphRelation}（retrieval-engine 与
 *       model-runtime 双引擎消费）</li>
 *   <li><b>知识与工具</b>：{@code KnowledgeBaseGateway}（auth→knowledge 的只读端口，待
 *       Provider 模式反转）、{@code KnowledgeBaseFacts}（被 Gateway 签名钉住）、
 *       {@code SseSink}（framework/web/ServletSseSink 实现；SSE 技术件，待 SseEmitter 批）</li>
 * </ul>
 *
 * <p><b>已迁出的成员</b>：会话/知识/提示词等词汇已归位各属主——见
 * {@code app session.api}、{@code im.api}、{@code embedchannel.api}、
 * {@code knowledge.api}（含 faq）、{@code agenttools.api}、{@code auth.api}、
 * {@code retrieval.api}、{@code llm.api}、{@code agentengine.api}、{@code mcp.api}。</p>
 */
package com.ragagent.framework.vocabulary;
