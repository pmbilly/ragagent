/**
 * 记忆域实体（留存面）：抽取会话/批次/失败/状态（{@code MemoryExtraction*}）、
 * 渲染与投影（{@code MemoryRender} / {@code MemoryDocView} / {@code MemoryTopicView} /
 * {@code MemoryDocAffinity} / {@code MemoryTopicStat}）、文本与分页
 * （{@code MemoryText} / {@code MemoryPage} / {@code MemoryMessageCursor}）与 JSONB 类型处理器。
 *
 * <p><b>已切出</b>：{@code item/}（条目与墓碑）、{@code scope/}（作用域与开关）、
 * {@code vector/}（向量检索）。<b>方向</b>：本留存面 → {@code item}（{@code MemoryRender} 渲染条目），
 * {@code scope}/{@code vector} → 本面与 {@code item}，反向为零 ⇒ 无环。</p>
 */
package com.ragagent.memory.domain;
