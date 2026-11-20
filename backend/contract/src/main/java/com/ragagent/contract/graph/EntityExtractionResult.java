package com.ragagent.contract.graph;

import java.util.List;

/**
 * 实体关系抽取结果（两列表；无命中时为空列表）。
 *
 * <p><b>上提</b>：原先业务域直接读模型运行时内部类 {@code EntityExtraction.EntityGraph}
 * 的**可变字段**（{@code graph.node}/{@code graph.relation}）——那是实现侧形状；本 record 是
 * 契约面只读版本，实现侧在适配器里做一次搬运。</p>
 */
public record EntityExtractionResult(List<GraphNode> nodes, List<GraphRelation> relations) {
}
