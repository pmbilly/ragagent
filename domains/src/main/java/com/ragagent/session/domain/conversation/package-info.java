/**
 * 会话实体面（由 {@code session.domain} 切出，对齐方案 §8.8.1）：
 * {@code Session} 及其列表/分页/状态（{@code SessionListItem} / {@code SessionListQuery} /
 * {@code SessionPage} / {@code SessionLastRequestState} / {@code SessionOwnerIds} /
 * {@code SessionNotFoundException}）与访客嵌入会话行（{@code EmbedVisitorSessionRow}）。
 *
 * <p><b>切出依据</b>：对留存的 {@code session.domain} 只有单向入边（如
 * {@code SessionLastRequestState → MentionedItem}），无反向引用 ⇒ SCC 不扩大。</p>
 */
package com.ragagent.session.domain.conversation;
