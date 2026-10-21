/**
 * 检索引擎的支撑层：检索文本归一/分词/哈希、chunk 内容拼接、grep 结果视图与打分等无状态算法
 * （原顶层包 {@code searchutil}， 并入）。
 *
 * <p>{@code GrepChunkHit} 是工具实现面与检索面共用的结果视图，落本层避免两边各持一份；
 * {@code GrepChunksScoring} 只在工具实现面消费，留在 {@code agenttools.knowledge}。
 * 正则命中判定 {@code regexMatchesAny} 在 {@code SearchTextUtil}，{@code FaqSnippet} 委托调用。</p>
 */
package com.ragagent.retrieval.support;
