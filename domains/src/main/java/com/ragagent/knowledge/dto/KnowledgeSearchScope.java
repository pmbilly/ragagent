package com.ragagent.knowledge.dto;

/**
 * 检索作用域：一个 (租户, 知识库) 对。
 *
 * <p> 由 {@code KnowledgeSearchService} 的嵌套 record 移到 domain——仓储的
 * 检索方法也需要它，而仓储不该依赖 service 包的类型（分层方向：service → repository → domain）。</p>
 */
public record KnowledgeSearchScope(long tenantId, String kbId) {}
