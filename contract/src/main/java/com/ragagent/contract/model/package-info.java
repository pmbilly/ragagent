/**
 * 模型契约面：模型事实、运行时载荷与跨域能力端口。
 *
 * <p>读面分工：{@code ModelFactsPort} 只提供 id → 最小事实集；{@code ModelCatalogPort}
 * 提供目录/配置面。嵌入走 {@code EmbeddingPort}（按模型事实嵌入），模型调用走
 * {@code ModelRuntime} / {@code LlmChatClient}。</p>
 */
package com.ragagent.contract.model;
