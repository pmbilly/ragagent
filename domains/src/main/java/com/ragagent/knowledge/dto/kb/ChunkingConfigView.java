package com.ragagent.knowledge.dto.kb;

import com.ragagent.knowledge.domain.KnowledgeBaseChunkingConfig;
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
         * 主摄取（worker）**未消费**的键（K8 能力矩阵；空 list = 全部生效）——"接受但未生效"的
         * 显式告知，不再让用户以为配置已生效。语义与来源见
         * {@link KnowledgeBaseChunkingConfig#ingestionInactiveKeys()}。
         */
        List<String> inactiveFeatures) {

    /** 按扩展名指定解析引擎的规则（如 xlsx 用哪个引擎、首行是否表头）。 */
    public record ParserEngineRuleView(List<String> fileTypes, String engine,
                                       Boolean xlsxFirstRowAsHeader) {
    }

    /** 请求侧反向映射（视图 → 领域）。 */
    public KnowledgeBaseChunkingConfig toDomain() {
        KnowledgeBaseChunkingConfig c = new KnowledgeBaseChunkingConfig();
        c.setChunkSize(chunkSize);
        c.setChunkOverlap(chunkOverlap);
        c.setSeparators(separators);
        if (parserEngineRules != null) {
            c.setParserEngineRules(parserEngineRules.stream().map(r -> {
                KnowledgeBaseChunkingConfig.ParserEngineRule rule = new KnowledgeBaseChunkingConfig.ParserEngineRule();
                rule.setFileTypes(r.fileTypes());
                rule.setEngine(r.engine());
                rule.setXlsxFirstRowAsHeader(r.xlsxFirstRowAsHeader());
                return rule;
            }).toList());
        }
        c.setEnableParentChild(enableParentChild);
        c.setParentChunkSize(parentChunkSize);
        c.setChildChunkSize(childChunkSize);
        c.setStrategy(strategy);
        c.setTokenLimit(tokenLimit);
        c.setLanguages(languages);
        c.setTableMetadataInstructions(tableMetadataInstructions);
        return c;
    }

    public static ChunkingConfigView from(KnowledgeBaseChunkingConfig c) {
        if (c == null) {
            return null;
        }
        List<ParserEngineRuleView> rules = c.getParserEngineRules() == null ? null
                : c.getParserEngineRules().stream()
                        .map(r -> new ParserEngineRuleView(r.getFileTypes(), r.getEngine(),
                                r.getXlsxFirstRowAsHeader()))
                        .toList();
        return new ChunkingConfigView(c.getChunkSize(), c.getChunkOverlap(), c.getSeparators(),
                rules, c.isEnableParentChild(), c.getParentChunkSize(), c.getChildChunkSize(),
                c.getStrategy(), c.getTokenLimit(), c.getLanguages(),
                c.getTableMetadataInstructions(), c.ingestionInactiveKeys());
    }
}
