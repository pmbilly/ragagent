/**
 * 处理轨迹（span）追踪：{@code SpanTracker} 维护 attempt/stage/sub-span 的开启与收口，
 * 落库经 {@code KnowledgeSpanRepository}。
 *
 * <p><b>为什么在 {@code domain.span} 而不是 {@code service}</b>（B263㉜）：{@code task} 侧的
 * {@code KnowledgeProcessWorker} 需要它 ⇒ 留在 service 会形成 {@code task → service} 边，
 * 与既有的 {@code service → task} 合成包级环。移到中立面后方向固定为
 * {@code service → domain.span} 与 {@code task → domain.span}（都合法），环消解。</p>
 */
package com.ragagent.knowledge.domain.span;
