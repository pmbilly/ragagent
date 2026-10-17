package com.ragagent.contract.pipeline;

/**
 * RAG 预设运行端口（契约化）：按请求跑一次完整 RAG 管线并回带全链路产物。
 *
 * <p><b>为什么需要它</b>：原先评估域直接构造管线运行时上下文（{@code ChatManage}）并调用
 * `knowledgeQAByEvent`——那是管线内部形状 ⇒ 管线本体无法归位到会话域。抽出本端口后，
 * 评估侧只认契约（请求子集 + 契约产物），本体可整体归位。</p>
 *
 * <p>失败以运行时异常表达；实现唯一（会话域的 runner）。</p>
 */
public interface KnowledgeQaRunner {

    /** 跑一次 RAG 预设（同步，返回全链路产物）。 */
    KnowledgeQaRunResult runRag(KnowledgeQaRunRequest request);
}
