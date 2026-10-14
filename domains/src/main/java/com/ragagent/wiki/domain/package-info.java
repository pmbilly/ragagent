/**
 * wiki 域留存面（拆簇后）：{@code WikiConfig} / {@code WikiConstants} / {@code WikiException} /
 * {@code WikiExtractionGranularity} / {@code WikiSourceRefs} / {@code SourceRefNeedle} /
 * {@code EmptyListAsNullSerializer} / {@code WikiStringListTypeHandler}（配置与通用类型）+
 * {@code TaskDeadLetter} / {@code TaskPendingOp}（任务死信与待办行）。
 *
 * <p><b>已切出</b>：{@code folder/}（文件夹）、{@code page/}（页面与修订，含 {@code WikiStats}）、
 * {@code index/}（索引与分类路径）、{@code issue/}（lint 问题）、{@code graph/}（链接图）——
 * 五个子面都只单向引用本留存面（{@code 子面 → domain}），无反向边。</p>
 */
package com.ragagent.wiki.domain;
