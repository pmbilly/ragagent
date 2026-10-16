package com.ragagent.contract.knowledge.domain;


/** 索引策略视图：向量 / 关键词 / wiki / 图 四类索引的启用开关。 */
public record IndexingStrategyView(boolean vectorEnabled, boolean keywordEnabled,
                                   boolean wikiEnabled, boolean graphEnabled) {
}
