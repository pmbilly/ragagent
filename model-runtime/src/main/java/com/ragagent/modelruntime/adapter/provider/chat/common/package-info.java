/**
 * 跨 provider 共享的对话底座：传输与装配（LlmTransport/LlmTransportWiring/ProviderAdapter(s)）、并发客户端、思考策略与流式修正，（流句柄三件已迁 `platform.stream`——契约 ChatStream 的队列实现与追踪装饰器共用）。 由 `llm.chat` 归位。
 */
package com.ragagent.modelruntime.adapter.provider.chat.common;
