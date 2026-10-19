package com.ragagent.knowledge.support;

import com.ragagent.knowledge.domain.Knowledge;
import java.util.List;

/** 知识条目分页结果（自有形状，不暴露分页插件类型）。 */
public record KnowledgePage(List<Knowledge> items, long total) {
}
