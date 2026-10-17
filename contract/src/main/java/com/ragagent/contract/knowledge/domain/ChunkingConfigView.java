package com.ragagent.contract.knowledge.domain;

import java.util.ArrayList;
import java.util.List;

/** 分块配置视图：分块尺寸/重叠、分隔符、解析器引擎规则、父子块与语言等。 */
public record ChunkingConfigView(
        int chunkSize,
        int chunkOverlap,
        List<String> separators,
        List<ParserEngineRuleView> parserEngineRules,
        boolean enableParentChild,
        int parentChunkSize,
        int childChunkSize,
        String strategy,
        int tokenLimit,
        List<String> languages,
        String tableMetadataInstructions,
        /**
 * 主摄取（worker）**未消费**的键（能力矩阵；空 list = 全部生效）——"接受但未生效"的
 * 显式告知，不再让用户以为配置已生效。语义与来源见
 * {@code KnowledgeBaseChunkingConfig#ingestionInactiveKeys()}（knowledge 域形状）。
 */
        List<String> inactiveFeatures) {

    /** 按扩展名指定解析引擎的规则（如 xlsx 用哪个引擎、首行是否表头）。 */
    public record ParserEngineRuleView(List<String> fileTypes, String engine,
                                       Boolean xlsxFirstRowAsHeader) {
    }

    /** 主摄取未消费的键（顺序固定；空 = 全部生效）；知识域形状 {@code ingestionInactiveKeys()} 委托本方法。 */
    public static List<String> inactiveKeys(boolean enableParentChild, int parentChunkSize,
                                            int childChunkSize, boolean hasParserEngineRules,
                                            String tableMetadataInstructions) {
        List<String> inactive = new ArrayList<>();
        if (enableParentChild) {
            inactive.add("enableParentChild");
            if (parentChunkSize > 0) {
                inactive.add("parentChunkSize");
            }
            if (childChunkSize > 0) {
                inactive.add("childChunkSize");
            }
        }
        if (hasParserEngineRules) {
            inactive.add("parserEngineRules");
        }
        if (tableMetadataInstructions != null && !tableMetadataInstructions.isBlank()) {
            inactive.add("tableMetadataInstructions");
        }
        return inactive;
    }
}
