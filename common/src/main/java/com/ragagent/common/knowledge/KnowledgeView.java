package com.ragagent.common.knowledge;

/**
 * 知识条目（{@code knowledge} 行）只读视图（B98/C2）。
 *
 * <p>wiki 侧只用到「标题」做文档标题回落（{@code resolveDocTitle}），
 * 以及解析状态判定（已由 {@link KnowledgeBaseLookup#knowledgeGone(String)} 收口）。</p>
 */
public record KnowledgeView(String id, String title) {
}
