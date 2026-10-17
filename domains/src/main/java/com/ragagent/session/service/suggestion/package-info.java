/**
 * 消息建议面（由 {@code session.service} 切出，对齐方案 的 {@code service.suggestion/}）：
 * {@code MessageSuggestionService}（建议生成的编排与落库）与
 * {@code MessageSuggestionPipeline}（候选收集、证据与排序管线）。
 *
 * <p><b>切出依据</b>：本面是 {@code session} 内**唯一双向边为零**的簇——外部对它的引用只有
 * 回合收尾（{@code QaTurnFinalizer}）一处；它对域内的引用只有 {@code MessageService}。
 * 其余候选簇（attachment / history / turn）与 {@code service} 互有引用，需先做重构级断边
 * 才能拆面。</p>
 */
package com.ragagent.session.service.suggestion;
