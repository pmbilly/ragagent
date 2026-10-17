/**
 * 模型聊天契约面：聊天消息/选项/响应与工具定义等跨域共享形状。
 *
 * <p>这些类原本是引擎内部类型（{@code llm.domain}），因模型运行时模块化后
 * 被各业务域与检索面跨模块消费而上提。{@code format}/{@code parameters}/
 * {@code providerMetadata} 等自由度高的字段持**不透明 JSON**（{@code Object}）。</p>
 */
package com.ragagent.contract.model.chat;
