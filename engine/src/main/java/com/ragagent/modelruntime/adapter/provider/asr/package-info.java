/**
 * ASR（语音转写）适配：{@code AsrTranscriber}。B262 由 `llm.asr` 归位——它是 provider 适配
 * 面而非 LLM 对话面，且原先反向依赖 chat 的 {@code LlmTransport}（把 `llm` 与 `modelruntime`
 * 连成一对环，本批随搬迁一并解开）。
 */
package com.ragagent.modelruntime.adapter.provider.asr;
