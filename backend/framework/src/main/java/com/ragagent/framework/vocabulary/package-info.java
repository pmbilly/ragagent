/**
 * 跨引擎形状的最后一站（终态 1 类）：{@code SseSink}——SSE 帧出口接口，被
 * {@code framework/web/ServletSseSink} 实现；随 SseEmitter 替换批（蓝图 5.6.6）一并消亡。
 *
 * <p>原跨域词汇簇已全部归位：会话消息簇 → {@code channel.api}、Agent 形状 →
 * {@code agentengine.api}、检索/图谱/工具形状 → {@code llm.api}、知识库端口 →
 * {@code audit.api}（D25 Provider：消费方声明、knowledge 实现）。成员清单由
 * {@code VocabularyManifestTest} 棘轮钉住。</p>
 */
package com.ragagent.framework.vocabulary;
