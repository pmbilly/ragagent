package com.ragagent.contract.tenant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
 *
 * <p><b>作用域</b>：本配置的引擎端点/密钥是租户级默认；解析规则分三层——
 * agent 的 {@code chatParserEngineRules} 用于聊天附件上传时的首层回落，
 * KB 的 {@code chunking_config.parserEngineRules} 用于知识入库（按文件类型选引擎），
 * 本段的 {@code chatParserEngineRules} 只作两者的历史兜底。</p>
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

    /**
     * 从租户配置 jsonb 的 Map 形态提取解析引擎覆盖项（snake 键）。
     *
     * <p>纯 JDK：不依赖 Jackson——调用方负责把 jsonb 转成 {@code Map<String,Object>}。
     * 键集是租户配置契约的一部分，所以和配置类放在一起，不留在管理面控制器里。</p>
     */
    public static Map<String, String> overridesFrom(Map<String, Object> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        putString(out, raw, "mineru_endpoint");
        putString(out, raw, "mineru_api_key");
        putString(out, raw, "mineru_model");
        putString(out, raw, "mineru_vlm_server_url");
        putBool(out, raw, "mineru_enable_formula");
        putBool(out, raw, "mineru_enable_table");
        // mineru_parse_method：显式值归一；否则 legacy enable_ocr 推导
        String method = str(raw, "mineru_parse_method").trim().toLowerCase();
        Object legacyOcr = raw.get("mineru_enable_ocr");
        String resolved = switch (method) {
            case "auto" -> "auto";
            case "ocr" -> "ocr";
            case "txt" -> "txt";
            default -> legacyOcr instanceof Boolean b && !b ? "txt" : "auto";
        };
        if (!str(raw, "mineru_parse_method").isEmpty() || legacyOcr instanceof Boolean) {
            out.put("mineru_parse_method", resolved);
        }
        putBool(out, raw, "mineru_enable_ocr");
        putString(out, raw, "mineru_language");
        putString(out, raw, "mineru_cloud_model");
        putBool(out, raw, "mineru_cloud_enable_formula");
        putBool(out, raw, "mineru_cloud_enable_table");
        putBool(out, raw, "mineru_cloud_enable_ocr");
        putString(out, raw, "mineru_cloud_language");
        putString(out, raw, "odl_hybrid");
        putString(out, raw, "odl_hybrid_url");
        putString(out, raw, "odl_hybrid_mode");
        putBool(out, raw, "odl_hybrid_fallback");
        putBool(out, raw, "odl_markdown_with_html");
        putString(out, raw, "paddleocr_vl_endpoint");
        putBool(out, raw, "paddleocr_vl_use_seal_recognition");
        putBool(out, raw, "paddleocr_vl_use_chart_recognition");
        putString(out, raw, "paddleocr_vl_cloud_token");
        putString(out, raw, "paddleocr_vl_cloud_model");
        putBool(out, raw, "paddleocr_vl_cloud_use_seal_recognition");
        putBool(out, raw, "paddleocr_vl_cloud_use_chart_recognition");
        return out;
    }

    private static String str(Map<String, Object> raw, String field) {
        Object v = raw.get(field);
        return v instanceof String s ? s : "";
    }

    private static void putString(Map<String, String> out, Map<String, Object> raw, String field) {
        String v = str(raw, field);
        if (!v.isEmpty()) {
            out.put(field, v);
        }
    }

    private static void putBool(Map<String, String> out, Map<String, Object> raw, String field) {
        Object v = raw.get(field);
        if (v instanceof Boolean b) {
            out.put(field, String.valueOf(b));
        }
    }
}
