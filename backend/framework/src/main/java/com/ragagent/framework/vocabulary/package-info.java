/**
 * 跨域词汇：被两个以上模块共用、且无法下沉到任何单个域的形状。
 *
 * <p><b>为什么是单包</b>：这些形状互相引用（会话视图含检索结果与 agent 步骤），
 * 拆成兄弟包会立刻形成包级环；它们又都被 framework 的技术层（Web / 持久化）引用，
 * 因此只能留在 framework 内。</p>
 *
 * <p><b>约束</b>：只放纯 JDK 的形状与常量，不放实现、不放 I/O、不放 Spring；
 * 每个类型都必须有跨模块消费方，否则应回到属主域的 {@code api}。</p>
 *
 * <h2>按领域查</h2>
 * <ul>
 *   <li><b>会话 / 消息</b>：{@link com.ragagent.framework.vocabulary.MessageView}、
 *       {@code MessageAttachment}、{@code MessageImage}、{@code MessageArtifact}、
 *       {@code MentionedItem}、{@code UsedMemory}、{@code MessageExecutionContext}、
 *       {@code SessionLastRequestState}、{@code SuggestionItem}、{@code SuggestionAttribution}、
 *       {@code SseSink}、{@code PipelineMessageAttachmentView}</li>
 *   <li><b>Agent 执行</b>：{@code AgentStep}、{@code AgentToolCall}、{@code ToolCallTarget}、
 *       {@code SearchAuth}</li>
 *   <li><b>检索与图谱</b>：{@code SearchResult}、{@code SearchTarget}、
 *       {@code GraphNode}、{@code GraphRelation}</li>
 *   <li><b>知识</b>：{@code ChunkFacts}、{@code ChunkView}、{@code KnowledgeView}、
 *       {@code FaqChunkMetadata}、{@code KnowledgeBaseFacts}、{@code KnowledgeBaseGateway}、
 *       {@code WikiLanguageSupport}</li>
 *   <li><b>工具与记忆</b>：{@code ToolResult}、{@code MemoryKeys}、{@code MemoryKinds}</li>
 * </ul>
 *
 * <p>行为较重的成员（{@code SearchAuth} 的作用域判定、{@code WikiLanguageSupport} 的语言解析、
 * {@code MemoryKeys} 的键解析、{@code FaqChunkMetadata} 的编解码）计划随属主能力包一起下沉，
 * 本包最终只留数据形状。</p>
 */
package com.ragagent.framework.vocabulary;
