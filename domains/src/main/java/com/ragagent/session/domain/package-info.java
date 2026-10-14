/**
 * 会话域实体（留存面；已切出三簇：{@code conversation/}（会话与分页）、
 * {@code document/}（临时文档）、{@code message/}（消息族 + artifact 版本化））。
 *
 * <p>留存成员：{@code AbstractJsonListTypeHandler} / {@code AgentStepListTypeHandler} /
 * {@code SearchResultListTypeHandler}（JSONB 列表类型处理器基类与实现）、
 * {@code MentionedItem}({@code ListTypeHandler})、{@code SuggestionItem}({@code ListTypeHandler})、
 * {@code SuggestionAttribution}、{@code UsedMemory}({@code ListTypeHandler})、
 * {@code ChatHistoryKbStats}。</p>
 *
 * <p><b>方向</b>：留存面被三簇单向引用（{@code conversation → domain}、{@code message → domain}），
 * 反向为零 ⇒ 不再有环。</p>
 */
package com.ragagent.session.domain;
