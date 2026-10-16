/**
 * 检索引擎的支撑层：检索文本归一/分词/哈希、chunk 内容拼接、grep 结果视图与打分等无状态算法
 * （原顶层包 {@code searchutil}，2026-09-30 并入）。
 *
 * <p>{@code GrepChunkView} 与 {@code GrepChunksScoring} 由工具实现面
 * （{@code agenttools.knowledge}）归位到本层；正则命中判定 {@code regexMatchesAny} 由
 * {@code FaqSnippet} 归位到 {@code SearchTextUtil}（FaqSnippet 改为委托）——工具侧不再持有
 * 第二套检索侧算法。</p>
 */
package com.ragagent.retrieval.support;
