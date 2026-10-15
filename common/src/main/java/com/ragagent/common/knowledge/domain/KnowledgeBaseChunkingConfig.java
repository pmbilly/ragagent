package com.ragagent.common.knowledge.domain;

import java.util.ArrayList;
import java.util.List;
import com.ragagent.contract.error.BizException;
import com.ragagent.platform.web.JsonMappers;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 知识库分块配置（jsonb 列 {@code config} 的形状）：分块尺寸与重叠、分隔符、
 * 解析器引擎规则、父子块、token 上限等。
 *
 * <p>字段一律输出（{@code separators} 为 null 时输出 JSON null）。
 */
public class KnowledgeBaseChunkingConfig {

    private int chunkSize;
    private int chunkOverlap;
    private List<String> separators;
    private List<ParserEngineRule> parserEngineRules;
    private boolean enableParentChild;
    private int parentChunkSize;
    private int childChunkSize;
    private String strategy;
    private int tokenLimit;
    private List<String> languages;
    private String tableMetadataInstructions;

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int v) { chunkSize = v; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int v) { chunkOverlap = v; }
    public List<String> getSeparators() { return separators; }
    public void setSeparators(List<String> v) { separators = v; }
    public List<ParserEngineRule> getParserEngineRules() { return parserEngineRules; }
    public void setParserEngineRules(List<ParserEngineRule> v) { parserEngineRules = v; }
    public boolean isEnableParentChild() { return enableParentChild; }
    public void setEnableParentChild(boolean v) { enableParentChild = v; }
    public int getParentChunkSize() { return parentChunkSize; }
    public void setParentChunkSize(int v) { parentChunkSize = v; }
    public int getChildChunkSize() { return childChunkSize; }
    public void setChildChunkSize(int v) { childChunkSize = v; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String v) { strategy = v; }
    public int getTokenLimit() { return tokenLimit; }
    public void setTokenLimit(int v) { tokenLimit = v; }
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> v) { languages = v; }
    public String getTableMetadataInstructions() { return tableMetadataInstructions; }
    public void setTableMetadataInstructions(String v) { tableMetadataInstructions = v; }

        /** 单条解析器引擎匹配规则：按文件扩展名选引擎，可带 xlsx 首行作表头开关。 */
        public static class ParserEngineRule {
        private List<String> fileTypes;
        private String engine;
        private Boolean xlsxFirstRowAsHeader;

        public List<String> getFileTypes() { return fileTypes; }
        public void setFileTypes(List<String> v) { fileTypes = v; }
        public String getEngine() { return engine; }
        public void setEngine(String v) { engine = v; }
        public Boolean getXlsxFirstRowAsHeader() { return xlsxFirstRowAsHeader; }
        public void setXlsxFirstRowAsHeader(Boolean v) { xlsxFirstRowAsHeader = v; }
    }

    /**
     * 从 KB 配置 jsonb 读取。
     *
     * <p>容错边界（K8）：{@code null} / 缺省对象 → 默认值（"没配"是合法状态）；
     * **形状损坏**（类型不匹配等）→ {@link BizException} 明确报错，不再回落默认——否则
     * 配置坏了会悄悄按 512/80 跑完摄取，用户以为自己的尺寸生效了（静默成功）。</p>
     */
    public static KnowledgeBaseChunkingConfig from(JsonNode node) {
        if (node == null || node.isNull()) {
            return new KnowledgeBaseChunkingConfig();
        }
        KnowledgeBaseChunkingConfig parsed;
        try {
            parsed = JsonMappers.lenient().convertValue(node, KnowledgeBaseChunkingConfig.class);
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("chunkingConfig 解析失败：" + e.getMessage());
        }
        return parsed == null ? new KnowledgeBaseChunkingConfig() : parsed;
    }

    /**
     * **主摄取未消费**的键（顺序固定；空 = 全部生效）。
     *
     * <p>K8 能力矩阵：声明了的配置"要么生效、要么被显式告知"。已生效：{@code chunkSize} /
     * {@code chunkOverlap} / {@code separators} / {@code strategy} / {@code tokenLimit} /
     * {@code languages}（{@code KnowledgeProcessWorker.toSplitterConfig} → {@code Chunker}）。
     * 未生效：{@code parserEngineRules}（worker 固定传 {@code parserEngine=null}）、
     * 父子块（worker 只出 {@code text} 块；预览侧 {@code Chunker.splitParentChildWithDiagnostics}
     * 已支持）、{@code tableMetadataInstructions}（全仓无消费点）。</p>
     *
     * <p><b>接进 worker 时请同步删掉对应项</b>——否则标注会撒谎。</p>
     */
    public List<String> ingestionInactiveKeys() {
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
        if (parserEngineRules != null && !parserEngineRules.isEmpty()) {
            inactive.add("parserEngineRules");
        }
        if (tableMetadataInstructions != null && !tableMetadataInstructions.isBlank()) {
            inactive.add("tableMetadataInstructions");
        }
        return inactive;
    }
}
