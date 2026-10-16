package com.ragagent.contract.tenant;

import java.util.List;


/**
 * 解析引擎配置段。
 *
 * <p>字段序 = JSON 键序。仅 mineru_endpoint / mineru_api_key 恒输出
 * （零值 ""），其余空值省略。布尔三态用 {@link Boolean}。</p>
 *
 * <p>线格式注解（键名/省略策略）由 {@code platform.web.ContractJsonMixinsCore.ParserEngineConfigMixin}
 * 承载（契约面纯 JDK）；chat_parser_engine_rules 的元素类型不建强类型——
 * 规则由 agent 侧配置，租户级只是透传保留（合并的 legacy 分支），
 * 用 JsonNode 带过，键序与原文一致。</p>
 */

public class ParserEngineConfig {

    /** 列表空/缺失都省略键；元素透传 */
    private List<Object> chatParserEngineRules;

    private String mineruEndpoint = "";

    private String mineruApiKey = "";

    private String mineruModel = "";

    private String mineruVlmServerUrl = "";

    private Boolean mineruEnableFormula;

    private Boolean mineruEnableTable;

    private String mineruParseMethod = "";

    private Boolean mineruEnableOcr;

    private String mineruLanguage = "";

    private String mineruCloudModel = "";

    private Boolean mineruCloudEnableFormula;

    private Boolean mineruCloudEnableTable;

    private Boolean mineruCloudEnableOcr;

    private String mineruCloudLanguage = "";

    private String odlHybrid = "";

    private String odlHybridUrl = "";

    private String odlHybridMode = "";

    private Boolean odlHybridFallback;

    private Boolean odlMarkdownWithHtml;

    private String paddleOcrVlEndpoint = "";

    private Boolean paddleOcrVlUseSealRecognition;

    private Boolean paddleOcrVlUseChartRecognition;

    private String paddleOcrVlCloudToken = "";

    private String paddleOcrVlCloudModel = "";

    private Boolean paddleOcrVlCloudUseSealRecognition;

    private Boolean paddleOcrVlCloudUseChartRecognition;

    public List<Object> getChatParserEngineRules() { return chatParserEngineRules; }
    public void setChatParserEngineRules(List<Object> v) { chatParserEngineRules = v; }
    public String getMineruEndpoint() { return mineruEndpoint; }
    public void setMineruEndpoint(String v) { mineruEndpoint = v == null ? "" : v; }
    public String getMineruApiKey() { return mineruApiKey; }
    public void setMineruApiKey(String v) { mineruApiKey = v == null ? "" : v; }
    public String getMineruModel() { return mineruModel; }
    public void setMineruModel(String v) { mineruModel = v == null ? "" : v; }
    public String getMineruVlmServerUrl() { return mineruVlmServerUrl; }
    public void setMineruVlmServerUrl(String v) { mineruVlmServerUrl = v == null ? "" : v; }
    public Boolean getMineruEnableFormula() { return mineruEnableFormula; }
    public void setMineruEnableFormula(Boolean v) { mineruEnableFormula = v; }
    public Boolean getMineruEnableTable() { return mineruEnableTable; }
    public void setMineruEnableTable(Boolean v) { mineruEnableTable = v; }
    public String getMineruParseMethod() { return mineruParseMethod; }
    public void setMineruParseMethod(String v) { mineruParseMethod = v == null ? "" : v; }
    public Boolean getMineruEnableOcr() { return mineruEnableOcr; }
    public void setMineruEnableOcr(Boolean v) { mineruEnableOcr = v; }
    public String getMineruLanguage() { return mineruLanguage; }
    public void setMineruLanguage(String v) { mineruLanguage = v == null ? "" : v; }
    public String getMineruCloudModel() { return mineruCloudModel; }
    public void setMineruCloudModel(String v) { mineruCloudModel = v == null ? "" : v; }
    public Boolean getMineruCloudEnableFormula() { return mineruCloudEnableFormula; }
    public void setMineruCloudEnableFormula(Boolean v) { mineruCloudEnableFormula = v; }
    public Boolean getMineruCloudEnableTable() { return mineruCloudEnableTable; }
    public void setMineruCloudEnableTable(Boolean v) { mineruCloudEnableTable = v; }
    public Boolean getMineruCloudEnableOcr() { return mineruCloudEnableOcr; }
    public void setMineruCloudEnableOcr(Boolean v) { mineruCloudEnableOcr = v; }
    public String getMineruCloudLanguage() { return mineruCloudLanguage; }
    public void setMineruCloudLanguage(String v) { mineruCloudLanguage = v == null ? "" : v; }
    public String getOdlHybrid() { return odlHybrid; }
    public void setOdlHybrid(String v) { odlHybrid = v == null ? "" : v; }
    public String getOdlHybridUrl() { return odlHybridUrl; }
    public void setOdlHybridUrl(String v) { odlHybridUrl = v == null ? "" : v; }
    public String getOdlHybridMode() { return odlHybridMode; }
    public void setOdlHybridMode(String v) { odlHybridMode = v == null ? "" : v; }
    public Boolean getOdlHybridFallback() { return odlHybridFallback; }
    public void setOdlHybridFallback(Boolean v) { odlHybridFallback = v; }
    public Boolean getOdlMarkdownWithHtml() { return odlMarkdownWithHtml; }
    public void setOdlMarkdownWithHtml(Boolean v) { odlMarkdownWithHtml = v; }
    public String getPaddleOcrVlEndpoint() { return paddleOcrVlEndpoint; }
    public void setPaddleOcrVlEndpoint(String v) { paddleOcrVlEndpoint = v == null ? "" : v; }
    public Boolean getPaddleOcrVlUseSealRecognition() { return paddleOcrVlUseSealRecognition; }
    public void setPaddleOcrVlUseSealRecognition(Boolean v) { paddleOcrVlUseSealRecognition = v; }
    public Boolean getPaddleOcrVlUseChartRecognition() { return paddleOcrVlUseChartRecognition; }
    public void setPaddleOcrVlUseChartRecognition(Boolean v) { paddleOcrVlUseChartRecognition = v; }
    public String getPaddleOcrVlCloudToken() { return paddleOcrVlCloudToken; }
    public void setPaddleOcrVlCloudToken(String v) { paddleOcrVlCloudToken = v == null ? "" : v; }
    public String getPaddleOcrVlCloudModel() { return paddleOcrVlCloudModel; }
    public void setPaddleOcrVlCloudModel(String v) { paddleOcrVlCloudModel = v == null ? "" : v; }
    public Boolean getPaddleOcrVlCloudUseSealRecognition() { return paddleOcrVlCloudUseSealRecognition; }
    public void setPaddleOcrVlCloudUseSealRecognition(Boolean v) { paddleOcrVlCloudUseSealRecognition = v; }
    public Boolean getPaddleOcrVlCloudUseChartRecognition() { return paddleOcrVlCloudUseChartRecognition; }
    public void setPaddleOcrVlCloudUseChartRecognition(Boolean v) { paddleOcrVlCloudUseChartRecognition = v; }
}
